/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;
/** Read purpose only: no audit append authority. */
public interface EvidenceAssociationReadAdmission{
    record Permit(EvidenceAssociationReadIdentity identity,UUID resourceTenantId,UUID documentResourceTenantId,AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,Instant observedAt,long observedNanos,Instant notAfter) implements EvidenceAssociationScope{
        public Permit{Objects.requireNonNull(identity);Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);for(UUID value:new UUID[]{resourceTenantId,documentResourceTenantId,bindingId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact current owner namespaces required");if(actor.staffRealm()!=null||actor.staffId()!=null||bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Finite original HUMAN read authority required");}
        public UUID actionId(){return identity.actionId();}public UUID applicationId(){return identity.applicationId();}public UUID partyId(){return identity.partyId();}
        @Override public String toString(){return "AssociationReadPermit[redacted]";}
    }
    Mono<Permit> requireCurrent(EvidenceAssociationReadIdentity identity);
    Mono<Void> revalidate(Permit original);
}
