/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;

/** The owner supplies current signed-party, policy, dataset and append authority together. */
public interface DraftIntakeAdmission extends AuditAppendAuthority {
    record Permit(UUID requestId,String intentDigest,UUID resourceTenantId,UUID partyId,AuditActor actor,
            UUID bindingId,long bindingVersion,String bindingDigest,UUID processId,UUID correlationId,
            String policyRevision,Instant observedAt,long observedNanos,Instant notAfter) {
        public Permit {
            for(UUID id:new UUID[]{requestId,resourceTenantId,partyId,bindingId,processId,correlationId})
                if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact intake authority required");
            Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);
            if(bindingVersion<1 || intentDigest==null || !intentDigest.matches("[0-9a-f]{64}")
                || bindingDigest==null || !bindingDigest.matches("[0-9a-f]{64}") || policyRevision==null || policyRevision.isBlank()
                || !observedAt.isBefore(notAfter) || Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)
                throw new IllegalArgumentException("Finite current intake authority required");
        }
        @Override public String toString(){return "DraftIntakePermit[redacted]";}
    }
    Mono<Permit> requireCurrent(DraftIntakeIntent intent);
    Mono<Void> revalidate(Permit permit);
    <T> Mono<T> withinExpectedAppend(Permit permit,AuditEvent event,Supplier<Mono<T>> append);
}
