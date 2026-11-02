/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis;
import io.fireflysoftware.core.lending.origination.interfaces.contractbasis.ApplicationContractBasis;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;
/** Current read authority only; it cannot authorize business writes or audit append. */
public interface ApplicationContractBasisAdmission{
    record Permit(ApplicationContractBasisIdentity identity,UUID resourceTenantId,AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,Instant observedAt,long observedNanos,Instant notAfter){
        public Permit{Objects.requireNonNull(identity);Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);for(UUID id:new UUID[]{resourceTenantId,bindingId})if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact owner namespace binding required");if(bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||policyRevision==null||policyRevision.isBlank()||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Original finite read authority required");}
        public UUID actionId(){return identity.actionId();}public UUID applicationId(){return identity.applicationId();}public UUID partyId(){return identity.partyId();}
        @Override public String toString(){return "ApplicationContractBasisPermit[redacted]";}
    }
    Mono<ApplicationContractBasis> invoke(UUID applicationId,UUID originalActionId,Function<ApplicationContractBasisIdentity,Mono<ApplicationContractBasis>> reader);
    Mono<Permit> requireCurrent(ApplicationContractBasisIdentity original);
    Mono<Void> revalidate(Permit original);
}
