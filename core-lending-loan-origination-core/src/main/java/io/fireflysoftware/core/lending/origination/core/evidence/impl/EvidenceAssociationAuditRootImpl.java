/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence.impl;
import io.fireflysoftware.core.lending.origination.core.evidence.*;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender;
import org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService;
import org.fireflyframework.core.audit.impl.SpringDataLocalAuditRepository;
import io.fireflysoftware.core.lending.origination.models.repositories.evidence.EvidenceIntakeRepository;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

public final class EvidenceAssociationAuditRootImpl {
    public record Replay<T>(T value,long sequence,String recordHash){
        public Replay{Objects.requireNonNull(value);if(sequence<1||recordHash==null||!recordHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact retained audit record required");}
    }
    public record Plan<T>(UUID applicationId,UUID eventId,Instant occurredAt,String policyRevision,String effectDigest,Optional<Replay<T>> replay,BiFunction<UUID,AuditRecord,Mono<T>> writes){
        public Plan{Objects.requireNonNull(applicationId);Objects.requireNonNull(eventId);Objects.requireNonNull(occurredAt);Objects.requireNonNull(replay);
            if(applicationId.equals(new UUID(0,0))||eventId.equals(new UUID(0,0))||policyRevision==null||policyRevision.isBlank()||effectDigest==null||!effectDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact loan evidence association required");
            if(replay.isEmpty())Objects.requireNonNull(writes);else if(writes!=null)throw new IllegalArgumentException("Replay cannot carry business writes");}
    }
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate template;
    private final BoundedRootTransaction root;
    private final Function<UUID,TransactionalAuditAppender> append;
    private final CompletedAuditAppendService completedAppend;
    private final LocalAuditRepository audit;private final AuditNativeContextRepository nativeContext;private final EvidenceIntakeRepository intakes;
    private final EvidenceAssociationAdmission authority;
    private final Clock clock;
    private final LongSupplier nanos;
    public EvidenceAssociationAuditRootImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,
            R2dbcTransactionManager manager,CleanupPolicy cleanup,EvidenceAssociationAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.template=template;this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        this.root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));
        this.append=nonce->new SpringDataTransactionalAuditAppender(this.resources,client,template,manager,authority,clock,nanos,()->nonce);
        this.completedAppend=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);
        this.audit=new SpringDataLocalAuditRepository(this.resources,client,template,manager);var repositories=new R2dbcRepositoryFactory(template);this.nativeContext=repositories.getRepository(AuditNativeContextRepository.class);this.intakes=repositories.getRepository(EvidenceIntakeRepository.class);
    }
    public R2dbcEntityTemplate template(){return template;}
    public Clock clock(){return clock;}
    public <T> Mono<T> execute(EvidenceAssociationIntent intent,Function<EvidenceAssociationAdmission.Permit,Mono<Plan<T>>> prepare){return Mono.defer(()->{
        var deliberate=new java.util.concurrent.atomic.AtomicReference<org.fireflyframework.web.error.exceptions.BusinessException>();
        var appendAttempted=new java.util.concurrent.atomic.AtomicBoolean();
        long observedNanos=nanos.getAsLong();Instant observedAt=clock.instant();
        var initial=R2dbcOperationBudget.observed(observedAt,observedAt.plusSeconds(5),observedNanos,Duration.ofSeconds(5),clock,nanos);
        return authority.requireCurrent(intent).switchIfEmpty(Mono.error(unavailable())).timeout(initial.remaining())
            .onErrorMap(error->preDispatchFailure(intent.actionId(),error)).flatMap(permit->{
            if(!intent.actionId().equals(permit.actionId()) || !intent.digest().equals(permit.intentDigest()) || !intent.partyId().equals(permit.partyId()) || !intent.applicationId().equals(permit.applicationId()) || !intent.documentResourceTenantId().equals(permit.documentResourceTenantId()))return Mono.error(new EvidenceAssociationFailure(intent.actionId(),EvidenceAssociationFailure.Outcome.REFUSED));
            final R2dbcOperationBudget budget;
            try{budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos).intersect(initial);}catch(RuntimeException expired){return Mono.error(new EvidenceAssociationFailure(intent.actionId(),EvidenceAssociationFailure.Outcome.REFUSED));}
            var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope)
                .then(authority.revalidate(permit)).then(Mono.defer(()->prepare.apply(permit))).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(plan->{budget.remaining();scope.remaining();
                    if(!intent.applicationId().equals(plan.applicationId())||!permit.applicationId().equals(plan.applicationId()))return Mono.error(unavailable());
                    var event=new AuditEvent(plan.eventId(),new AuditStream(permit.resourceTenantId(),"core-loan-origination","loan-application",plan.applicationId()),
                        permit.actor(),"lending.application.evidence.attach",AuditEvent.Outcome.COMPLETED,permit.actionId(),permit.actionId(),null,
                        plan.occurredAt(),plan.effectDigest(),plan.policyRevision());
                    if(plan.occurredAt().isAfter(clock.instant())||plan.replay().isEmpty()&&!permit.policyRevision().equals(plan.policyRevision()))return Mono.error(unavailable());
                    UUID appendNonce=UUID.randomUUID();
                    return authority.revalidate(permit).then(proveManagedIntake(scope,permit)).then(Mono.defer(()->{appendAttempted.set(true);return authority.withinExpectedAppend(permit,event,()->append.apply(appendNonce).append(scope,event));}))
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(provisional->{
                            if(provisional.replay()!=plan.replay().isPresent() || provisional.record()==null)return Mono.error(unavailable());
                            return completedAppend.requireCompleted(scope,appendNonce,provisional).switchIfEmpty(Mono.error(unavailable())).flatMap(record->{
                                if(!event.equals(record.event()) || !record.equals(AuditRecordHasher.record(
                                    new AuditCheckpoint(event.stream(),record.sequence()-1,record.previousHash()),event,record.recordedAt())))return Mono.error(unavailable());
                                if(plan.replay().isPresent()){
                                    var expected=plan.replay().orElseThrow();
                                    if(record.sequence()!=expected.sequence()||!record.recordHash().equals(expected.recordHash()))return Mono.error(unavailable());
                                    return authority.revalidate(permit).thenReturn(expected.value());
                                }
                                return resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit))
                                    .then(Mono.defer(()->plan.writes().apply(appendNonce,record))).switchIfEmpty(Mono.error(unavailable()))
                                    .flatMap(value->authority.revalidate(permit).thenReturn(value));
                            });
                        });
                }).timeout(scope.remaining()).onErrorResume(error->{
                    if(!appendAttempted.get() && error instanceof org.fireflyframework.web.error.exceptions.BusinessException business
                        && (business.getStatus()==org.springframework.http.HttpStatus.BAD_REQUEST || business.getStatus()==org.springframework.http.HttpStatus.CONFLICT)) deliberate.compareAndSet(null,business);
                    return resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error));}))
                .map(completed->{var facts=completed.transactionFacts();
                    if(!facts.nativeAttempted() || !facts.commitAcknowledged() || !facts.boundaryCompleted() || facts.rollbackAcknowledged() || completed.cleanupFact()!=CleanupFact.COMPLETE)
                        throw new EvidenceAssociationFailure(intent.actionId(),facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?EvidenceAssociationFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:EvidenceAssociationFailure.Outcome.OUTCOME_UNKNOWN);
                    return completed.value();
                }).onErrorMap(RootTransactionException.class,error->{var facts=error.transactionFacts();
                    if(deliberate.get()!=null && facts.rollbackAcknowledged() && !facts.commitAcknowledged() && error.cleanupFact()==CleanupFact.COMPLETE)return deliberate.get();
                    return new EvidenceAssociationFailure(intent.actionId(),facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?EvidenceAssociationFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:
                        facts.rollbackAcknowledged()&&!facts.commitAcknowledged()&&error.cleanupFact()==CleanupFact.COMPLETE?EvidenceAssociationFailure.Outcome.REFUSED:EvidenceAssociationFailure.Outcome.OUTCOME_UNKNOWN);
                }).flatMap(value->Mono.defer(()->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;})).timeout(budget.remaining()))
                    .onErrorMap(error->new EvidenceAssociationFailure(intent.actionId(),EvidenceAssociationFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE)));
        });});}
    private Mono<Void> proveManagedIntake(RootTransactionScope scope,EvidenceAssociationAdmission.Permit permit){return EvidenceAssociationDataLoaderImpl.one(intakes.findAllByResourceTenantIdAndApplicationIdAndPartyId(permit.resourceTenantId(),permit.applicationId(),permit.partyId())).flatMap(intake->Mono.defer(()->{
        var stream=new AuditStream(permit.resourceTenantId(),"core-loan-origination","loan-application",permit.applicationId());var actor=permit.actor();UUID nonce=UUID.randomUUID();long before=nanos.getAsLong();Instant until=Collections.min(List.of(permit.notAfter(),clock.instant().plus(scope.remaining()),clock.instant().plusSeconds(5)));
        return nativeContext.enter(nonce,stream.resourceTenantId(),stream.service(),stream.resourceType(),stream.resourceId(),intake.auditEventId(),actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),actor.staffRealm(),actor.staffId(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC)).switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
            if(!nonce.equals(stamp.contextNonce())||!intake.auditEventId().equals(stamp.eventId())||stamp.dbNowMillis()==null||stamp.untilMillis()==null||stamp.dbNowMillis()<=0||stamp.untilMillis()>until.toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())return Mono.error(unavailable());
            scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),Instant.ofEpochMilli(stamp.untilMillis()-1),before,Duration.ofSeconds(5),clock,nanos));
            return audit.find(scope,stream.resourceTenantId(),intake.auditEventId()).filter(record->record.recordHash().equals(intake.auditRecordHash())&&stream.equals(record.event().stream())&&actor.equals(record.event().actor())&&"lending.application.draft.create".equals(record.event().action())&&record.event().outcome()==AuditEvent.Outcome.COMPLETED&&intake.requestId().equals(record.event().processId())&&intake.requestId().equals(record.event().correlationId())&&record.event().causationId()==null).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(record->audit.head(scope,stream).filter(head->head.lastSequence()>=record.sequence()&&(head.lastSequence()!=record.sequence()||head.lastHash().equals(record.recordHash()))).switchIfEmpty(Mono.error(unavailable()))
                    .then(authority.revalidate(permit)).then(nativeContext.complete(nonce,record.sequence(),record.recordHash())).filter(nonce::equals).switchIfEmpty(Mono.error(unavailable()))
                    .then(completedAppend.requireCompleted(scope,nonce,new TransactionalAuditAppender.ProvisionalAppend(record,true))).filter(record::equals).switchIfEmpty(Mono.error(unavailable())).then());
        });
    }));}

    private static Throwable preDispatchFailure(UUID action,Throwable error){
        if(error instanceof org.springframework.web.server.ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;
        return new EvidenceAssociationFailure(action,EvidenceAssociationFailure.Outcome.REFUSED);
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact paired loan evidence association audit and completed owned root required");}
}
