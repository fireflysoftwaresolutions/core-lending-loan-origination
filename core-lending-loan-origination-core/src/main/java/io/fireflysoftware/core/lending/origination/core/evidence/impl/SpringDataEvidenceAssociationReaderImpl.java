/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence.impl;
import io.fireflysoftware.core.lending.origination.core.evidence.*;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.*;
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
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/** Proves an existing association, or absence under a genuine managed application, without appending an event. */
public final class SpringDataEvidenceAssociationReaderImpl implements EvidenceAssociationReader{
    record Snapshot(UUID eventId,String recordHash,Optional<AuditEvent> exactEvent,OptionalLong sequence,UUID initialRequest,Optional<EvidenceAssociationReceipt> receipt){@Override public String toString(){return "AssociationReadSnapshot[redacted]";}}
    private final BoundedR2dbcResources resources;private final BoundedRootTransaction root;private final EvidenceAssociationDataLoaderImpl data;
    private final AuditNativeContextRepository nativeContext;private final LocalAuditRepository audit;private final CompletedAuditAppendService completed;
    private final EvidenceAssociationReadAdmission authority;private final Clock clock;private final LongSupplier nanos;
    public SpringDataEvidenceAssociationReaderImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager,CleanupPolicy cleanup,EvidenceAssociationReadAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);root=new DefaultBoundedRootTransaction(this.resources,cleanup);data=new EvidenceAssociationDataLoaderImpl(template);
        nativeContext=new R2dbcRepositoryFactory(template).getRepository(AuditNativeContextRepository.class);audit=new SpringDataLocalAuditRepository(this.resources,client,template,manager);completed=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);
        this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<Optional<EvidenceAssociationReceipt>> observe(EvidenceAssociationReadIdentity identity){return Mono.defer(()->{
        Instant observed=clock.instant();long before=nanos.getAsLong();var initial=R2dbcOperationBudget.observed(observed,observed.plusSeconds(5),before,Duration.ofSeconds(5),clock,nanos);
        return authority.requireCurrent(identity).timeout(initial.remaining()).switchIfEmpty(Mono.error(unavailable())).flatMap(permit->{
            if(!identity.equals(permit.identity()))return Mono.error(unavailable());var budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos).intersect(initial);
            var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit))
                .then(snapshot(permit)).flatMap(snapshot->paired(scope,permit,snapshot)).timeout(scope.remaining())
                .onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error))))
                .map(result->{var facts=result.transactionFacts();if(!facts.nativeAttempted()||!facts.commitAcknowledged()||!facts.boundaryCompleted()||facts.rollbackAcknowledged()||result.cleanupFact()!=CleanupFact.COMPLETE)throw unavailable();return result.value();})
                .flatMap(value->Mono.defer(()->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;})).timeout(budget.remaining())));
        }).onErrorMap(error->{if(error instanceof org.springframework.web.server.ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return new EvidenceAssociationReadFailure(identity.actionId());});
    });}
    private Mono<Snapshot> snapshot(EvidenceAssociationReadAdmission.Permit permit){return data.parent(permit,null,false).flatMap(parent->data.retained(permit).flatMap(found->{
        if(found.isEmpty()){var intake=parent.intake();return Mono.just(new Snapshot(intake.auditEventId(),intake.auditRecordHash(),Optional.empty(),OptionalLong.empty(),intake.requestId(),Optional.empty()));}
        return data.exact(permit,found.orElseThrow()).map(row->{var event=new AuditEvent(row.auditEventId(),new AuditStream(row.resourceTenantId(),"core-loan-origination","loan-application",row.applicationId()),permit.actor(),"lending.application.evidence.attach",AuditEvent.Outcome.COMPLETED,row.actionId(),row.actionId(),null,row.attachedAt(),row.effectDigest(),row.policyRevision());
            return new Snapshot(row.auditEventId(),row.auditRecordHash(),Optional.of(event),OptionalLong.of(row.auditSequence()),parent.intake().requestId(),Optional.of(EvidenceAssociationDataLoaderImpl.receipt(row,true)));
        });
    }));}
    private Mono<Optional<EvidenceAssociationReceipt>> paired(RootTransactionScope scope,EvidenceAssociationReadAdmission.Permit permit,Snapshot snapshot){return Mono.defer(()->{
        if(snapshot.eventId()==null||snapshot.recordHash()==null||!snapshot.recordHash().matches("[a-f0-9]{64}"))throw unavailable();
        UUID nonce=UUID.randomUUID();var stream=new AuditStream(permit.resourceTenantId(),"core-loan-origination","loan-application",permit.applicationId());var actor=permit.actor();Instant until=Collections.min(List.of(permit.notAfter(),clock.instant().plus(scope.remaining()),clock.instant().plusSeconds(5)));long before=nanos.getAsLong();
        return authority.revalidate(permit).then(nativeContext.enter(nonce,stream.resourceTenantId(),stream.service(),stream.resourceType(),stream.resourceId(),snapshot.eventId(),actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),actor.staffRealm(),actor.staffId(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC))).switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
            if(!nonce.equals(stamp.contextNonce())||!snapshot.eventId().equals(stamp.eventId())||stamp.dbNowMillis()==null||stamp.untilMillis()==null||stamp.dbNowMillis()<=0||stamp.untilMillis()>until.toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())return Mono.error(unavailable());
            scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),Instant.ofEpochMilli(stamp.untilMillis()-1),before,Duration.ofSeconds(5),clock,nanos));
            return audit.find(scope,stream.resourceTenantId(),snapshot.eventId()).filter(record->record.event().stream().equals(stream)&&record.event().actor().equals(actor)&&record.recordHash().equals(snapshot.recordHash())
                &&(snapshot.sequence().isEmpty()||snapshot.sequence().getAsLong()==record.sequence())
                &&(snapshot.exactEvent().isPresent()?snapshot.exactEvent().orElseThrow().equals(record.event()):"lending.application.draft.create".equals(record.event().action())&&record.event().outcome()==AuditEvent.Outcome.COMPLETED&&snapshot.initialRequest().equals(record.event().processId())&&snapshot.initialRequest().equals(record.event().correlationId())&&record.event().causationId()==null))
                .switchIfEmpty(Mono.error(unavailable())).flatMap(record->audit.head(scope,stream).filter(head->head.lastSequence()>=record.sequence()&&(head.lastSequence()!=record.sequence()||head.lastHash().equals(record.recordHash()))).switchIfEmpty(Mono.error(unavailable()))
                    .then(authority.revalidate(permit)).then(nativeContext.complete(nonce,record.sequence(),record.recordHash())).filter(nonce::equals).switchIfEmpty(Mono.error(unavailable()))
                    .then(completed.requireCompleted(scope,nonce,new TransactionalAuditAppender.ProvisionalAppend(record,true))).filter(record::equals).switchIfEmpty(Mono.error(unavailable()))
                    .then(authority.revalidate(permit)).then(Mono.fromCallable(()->{scope.remaining();return snapshot.receipt();})));
        });
    });}
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact current managed application and original completed association evidence required");}
}
