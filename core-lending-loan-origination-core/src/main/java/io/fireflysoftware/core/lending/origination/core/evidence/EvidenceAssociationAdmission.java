/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;
/** Only an owning current full-authentication bridge can establish a permit; request reference values are not grants. */
public interface EvidenceAssociationAdmission extends AuditAppendAuthority{
    record Permit(UUID actionId,UUID applicationId,UUID partyId,UUID resourceTenantId,UUID documentResourceTenantId,String intentDigest,
            AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,Instant observedAt,long observedNanos,Instant notAfter) implements EvidenceAssociationScope{
        public Permit{for(UUID value:new UUID[]{actionId,applicationId,partyId,resourceTenantId,documentResourceTenantId,bindingId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact owner resource binding required");Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);if(actor.staffRealm()!=null||actor.staffId()!=null||bindingVersion<1||intentDigest==null||!intentDigest.matches("[a-f0-9]{64}")||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Finite current HUMAN association authority required");}
        @Override public String toString(){return "EvidenceAssociationPermit[redacted]";}
    }
    Mono<Permit> requireCurrent(EvidenceAssociationIntent intent);
    Mono<Void> revalidate(Permit original);
    <T> Mono<T> withinExpectedAppend(Permit original,AuditEvent event,Supplier<Mono<T>> append);
}
