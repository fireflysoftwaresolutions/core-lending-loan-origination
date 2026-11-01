/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake.impl;
import io.fireflysoftware.core.lending.origination.core.intake.*;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender;
import org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

public final class DraftIntakeAuditRootImpl {
    public record Plan<T>(UUID applicationId,String effectDigest,Optional<T> replay,Function<AuditRecord,Mono<T>> writes){
        public Plan{Objects.requireNonNull(applicationId);Objects.requireNonNull(replay);
            if(effectDigest==null || !effectDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact intake evidence required");
            if(replay.isEmpty())Objects.requireNonNull(writes);}
    }
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate template;
    private final BoundedRootTransaction root;
    private final Function<UUID,TransactionalAuditAppender> append;
    private final CompletedAuditAppendService completedAppend;
    private final DraftIntakeAdmission authority;
    private final Clock clock;
    private final LongSupplier nanos;
    public DraftIntakeAuditRootImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,
            R2dbcTransactionManager manager,CleanupPolicy cleanup,DraftIntakeAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.template=template;this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        this.root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));
        this.append=nonce->new SpringDataTransactionalAuditAppender(this.resources,client,template,manager,authority,clock,nanos,()->nonce);
        this.completedAppend=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);
    }
    public R2dbcEntityTemplate template(){return template;}
    public Clock clock(){return clock;}
    public <T> Mono<T> execute(DraftIntakeIntent intent,Function<DraftIntakeAdmission.Permit,Mono<Plan<T>>> prepare){return Mono.defer(()->{
        var deliberate=new java.util.concurrent.atomic.AtomicReference<org.fireflyframework.web.error.exceptions.BusinessException>();
        var appendAttempted=new java.util.concurrent.atomic.AtomicBoolean();
        return authority.requireCurrent(intent).switchIfEmpty(Mono.error(unavailable())).flatMap(permit->{
            if(!intent.requestId().equals(permit.requestId()) || !intent.digest().equals(permit.intentDigest()) || !intent.partyId().equals(permit.partyId()))return Mono.error(unavailable());
            var budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos);
            var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope)
                .then(authority.revalidate(permit)).then(Mono.defer(()->prepare.apply(permit))).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(plan->{budget.remaining();scope.remaining();
                    if(plan.replay().isPresent())return authority.revalidate(permit).thenReturn(plan.replay().orElseThrow());
                    var event=new AuditEvent(UUID.randomUUID(),new AuditStream(permit.resourceTenantId(),"core-loan-origination","loan-application",plan.applicationId()),
                        permit.actor(),"lending.application.draft.create",AuditEvent.Outcome.COMPLETED,permit.processId(),permit.correlationId(),null,
                        clock.instant(),plan.effectDigest(),permit.policyRevision());
                    UUID appendNonce=UUID.randomUUID();
                    return authority.revalidate(permit).then(Mono.defer(()->{appendAttempted.set(true);return authority.withinExpectedAppend(permit,event,()->append.apply(appendNonce).append(scope,event));}))
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(provisional->{
                            if(provisional.replay() || provisional.record()==null)return Mono.error(unavailable());
                            return completedAppend.requireCompleted(scope,appendNonce,provisional).switchIfEmpty(Mono.error(unavailable())).flatMap(record->{
                                if(!event.equals(record.event()) || !record.equals(AuditRecordHasher.record(
                                    new AuditCheckpoint(event.stream(),record.sequence()-1,record.previousHash()),event,record.recordedAt())))return Mono.error(unavailable());
                                return resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit))
                                    .then(Mono.defer(()->plan.writes().apply(record))).switchIfEmpty(Mono.error(unavailable()))
                                    .flatMap(value->authority.revalidate(permit).thenReturn(value));
                            });
                        });
                }).timeout(scope.remaining()).onErrorResume(error->{
                    if(!appendAttempted.get() && error instanceof org.fireflyframework.web.error.exceptions.BusinessException business
                        && (business.getStatus()==org.springframework.http.HttpStatus.BAD_REQUEST || business.getStatus()==org.springframework.http.HttpStatus.CONFLICT)) deliberate.compareAndSet(null,business);
                    return resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error));}))
                .map(completed->{var facts=completed.transactionFacts();
                    if(!facts.nativeAttempted() || !facts.commitAcknowledged() || !facts.boundaryCompleted() || facts.rollbackAcknowledged() || completed.cleanupFact()!=CleanupFact.COMPLETE)
                        throw new DraftIntakeFailure(facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?DraftIntakeFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:DraftIntakeFailure.Outcome.OUTCOME_UNKNOWN);
                    return completed.value();
                }).onErrorMap(RootTransactionException.class,error->{var facts=error.transactionFacts();
                    if(deliberate.get()!=null && facts.rollbackAcknowledged() && !facts.commitAcknowledged() && error.cleanupFact()==CleanupFact.COMPLETE)return deliberate.get();
                    return new DraftIntakeFailure(facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?DraftIntakeFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:
                        facts.rollbackAcknowledged()&&!facts.commitAcknowledged()&&error.cleanupFact()==CleanupFact.COMPLETE?DraftIntakeFailure.Outcome.REFUSED:DraftIntakeFailure.Outcome.OUTCOME_UNKNOWN);
                }).flatMap(value->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;}))
                    .onErrorMap(error->new DraftIntakeFailure(DraftIntakeFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE)));
        });});}
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact paired intake audit and completed owned root required");}
}
