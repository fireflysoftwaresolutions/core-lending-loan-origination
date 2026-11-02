/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis.impl;
import io.fireflysoftware.core.lending.origination.core.contractbasis.*;
import io.fireflysoftware.core.lending.origination.core.intake.DraftIntakeIntent;
import io.fireflysoftware.core.lending.origination.interfaces.contractbasis.ApplicationContractBasis;
import io.fireflysoftware.core.lending.origination.models.entities.*;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import io.fireflysoftware.core.lending.origination.models.repositories.evidence.EvidenceIntakeRepository;
import io.fireflysoftware.core.lending.origination.models.repositories.intake.ApplicationPartyIntakeRepository;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;

/** No business writes or new events: current primary ownership and original completed intake pair under one finite snapshot. */
public final class SpringDataApplicationContractBasisReaderImpl implements ApplicationContractBasisReader {
    record Parent(LoanApplication application,DraftIntake intake,ApplicationParty primary,RoleCode role){}
    private final BoundedR2dbcResources resources;private final BoundedRootTransaction root;private final R2dbcEntityTemplate template;
    private final EvidenceIntakeRepository intakes;private final ApplicationPartyIntakeRepository parties;
    private final AuditNativeContextRepository nativeContext;private final LocalAuditRepository audit;private final CompletedAuditAppendService completed;
    private final ApplicationContractBasisAdmission authority;private final Clock clock;private final LongSupplier nanos;
    public SpringDataApplicationContractBasisReaderImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager,CleanupPolicy cleanup,ApplicationContractBasisAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);this.root=new DefaultBoundedRootTransaction(this.resources,cleanup);this.template=Objects.requireNonNull(template);
        var factory=new R2dbcRepositoryFactory(template);intakes=factory.getRepository(EvidenceIntakeRepository.class);parties=factory.getRepository(ApplicationPartyIntakeRepository.class);nativeContext=factory.getRepository(AuditNativeContextRepository.class);
        audit=new SpringDataLocalAuditRepository(this.resources,client,template,manager);completed=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<ApplicationContractBasis> observe(ApplicationContractBasisIdentity identity){return Mono.defer(()->{
        Instant observed=clock.instant();long before=nanos.getAsLong();var initial=R2dbcOperationBudget.observed(observed,observed.plusSeconds(5),before,Duration.ofSeconds(5),clock,nanos);
        return authority.requireCurrent(identity).timeout(initial.remaining()).switchIfEmpty(Mono.error(unavailable())).flatMap(permit->{
            if(!identity.equals(permit.identity()))return Mono.error(unavailable());var budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos).intersect(initial);
            var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit)).then(parent(permit))
                .flatMap(parent->paired(scope,permit,parent)).timeout(scope.remaining()).onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error))))
                .map(result->{var facts=result.transactionFacts();if(!facts.nativeAttempted()||!facts.commitAcknowledged()||!facts.boundaryCompleted()||facts.rollbackAcknowledged()||result.cleanupFact()!=CleanupFact.COMPLETE)throw unavailable();return result.value();})
                .flatMap(value->Mono.defer(()->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;})).timeout(budget.remaining())));
        }).onErrorMap(error->{if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return new ApplicationContractBasisFailure(identity.actionId());});
    });}
    private Mono<Parent> parent(ApplicationContractBasisAdmission.Permit permit){return template.selectOne(Query.query(Criteria.where("loanApplicationId").is(permit.applicationId())),LoanApplication.class)
        .switchIfEmpty(Mono.error(denied())).flatMap(app->one(intakes.findAllByResourceTenantIdAndApplicationIdAndPartyId(permit.resourceTenantId(),permit.applicationId(),permit.partyId())))
        .flatMap(intake->template.selectOne(Query.query(Criteria.where("loanApplicationId").is(permit.applicationId())),LoanApplication.class).switchIfEmpty(Mono.error(denied())).flatMap(app->
            one(template.select(Query.query(Criteria.where("loanApplicationId").is(permit.applicationId()).and("isPrimary").is(true)),ApplicationParty.class)).flatMap(primary->
                one(template.select(Query.query(Criteria.where("code").is("PRIMARY_APPLICANT")),RoleCode.class)).flatMap(role->{
                    if(!permit.resourceTenantId().equals(intake.resourceTenantId())||!permit.applicationId().equals(intake.applicationId())||!permit.applicationId().equals(app.getLoanApplicationId())||!permit.partyId().equals(intake.partyId())
                        ||!permit.partyId().equals(primary.getPartyId())||!Boolean.TRUE.equals(primary.getIsPrimary())||!Objects.equals(primary.getRoleCodeId(),role.getRoleCodeId())||!Boolean.TRUE.equals(role.getIsActive())||primary.getApplicationPartyId()==null
                        ||!Objects.equals(app.getSimulationId(),intake.simulationId()))return Mono.error(denied());
                    var original=new DraftIntakeIntent(intake.requestId(),intake.simulationId(),intake.productId(),intake.partyId(),intake.requestedAmount(),intake.term(),intake.currency(),intake.purpose());
                    if(!original.digest().equals(intake.intentDigest()))return Mono.error(unavailable());
                    return parties.verifyRetainedPrimary(permit.resourceTenantId(),permit.applicationId(),permit.partyId(),primary.getApplicationPartyId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable()))
                        .then(template.selectOne(Query.query(Criteria.where("simulationId").is(intake.simulationId())),Simulation.class)).switchIfEmpty(Mono.error(unavailable())).map(simulation->{original.requireSimulation(simulation);return new Parent(app,intake,primary,role);});
                }))));}
    private Mono<ApplicationContractBasis> paired(RootTransactionScope scope,ApplicationContractBasisAdmission.Permit permit,Parent parent){return Mono.defer(()->{
        var row=parent.intake();if(row.auditEventId()==null||row.auditRecordHash()==null||!row.auditRecordHash().matches("[a-f0-9]{64}")||row.createdAt()==null)throw unavailable();
        UUID nonce=UUID.randomUUID();var stream=new AuditStream(permit.resourceTenantId(),"core-loan-origination","loan-application",permit.applicationId());var actor=permit.actor();Instant until=Collections.min(List.of(permit.notAfter(),clock.instant().plus(scope.remaining()),clock.instant().plusSeconds(5)));long before=nanos.getAsLong();
        return authority.revalidate(permit).then(nativeContext.enter(nonce,stream.resourceTenantId(),stream.service(),stream.resourceType(),stream.resourceId(),row.auditEventId(),actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),actor.staffRealm(),actor.staffId(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC))).switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
            if(!nonce.equals(stamp.contextNonce())||!row.auditEventId().equals(stamp.eventId())||stamp.dbNowMillis()==null||stamp.untilMillis()==null||stamp.dbNowMillis()<=0||stamp.untilMillis()>until.toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())return Mono.error(unavailable());
            scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),Instant.ofEpochMilli(stamp.untilMillis()-1),before,Duration.ofSeconds(5),clock,nanos));
            return audit.find(scope,stream.resourceTenantId(),row.auditEventId()).filter(record->originalRecord(record,stream,actor,row))
                .switchIfEmpty(Mono.error(unavailable())).flatMap(record->audit.head(scope,stream).filter(head->head.lastSequence()>=record.sequence()&&(head.lastSequence()!=record.sequence()||head.lastHash().equals(record.recordHash()))).switchIfEmpty(Mono.error(unavailable()))
                    .then(authority.revalidate(permit)).then(nativeContext.complete(nonce,record.sequence(),record.recordHash())).filter(nonce::equals).switchIfEmpty(Mono.error(unavailable()))
                    .then(completed.requireCompleted(scope,nonce,new TransactionalAuditAppender.ProvisionalAppend(record,true))).filter(record::equals).switchIfEmpty(Mono.error(unavailable()))
                    .then(authority.revalidate(permit)).then(Mono.fromCallable(()->{scope.remaining();return new ApplicationContractBasis(row.applicationId(),row.resourceTenantId(),row.partyId(),parent.primary().getApplicationPartyId(),row.simulationId(),row.productId(),row.requestedAmount(),row.term(),row.currency(),row.intentDigest());})));
        });
    });}
    static boolean originalRecord(AuditRecord record,AuditStream stream,AuditActor actor,DraftIntake row){
        return row.resourceTenantId().equals(stream.resourceTenantId())&&row.applicationId().equals(stream.resourceId())&&row.auditEventId().equals(record.event().eventId())
            &&record.event().stream().equals(stream)&&record.event().actor().equals(actor)&&record.recordHash().equals(row.auditRecordHash())
            &&"lending.application.draft.create".equals(record.event().action())&&record.event().outcome()==AuditEvent.Outcome.COMPLETED&&row.requestId().equals(record.event().processId())&&row.requestId().equals(record.event().correlationId())&&record.event().causationId()==null
            &&record.equals(AuditRecordHasher.record(new AuditCheckpoint(stream,record.sequence()-1,record.previousHash()),record.event(),record.recordedAt()));
    }
    private static <T> Mono<T> one(Flux<T> source){return source.take(2).collectList().flatMap(rows->rows.size()==1?Mono.just(rows.getFirst()):Mono.error(denied()));}
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact original completed intake and current primary ownership required");}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original application party and namespace required");}
}
