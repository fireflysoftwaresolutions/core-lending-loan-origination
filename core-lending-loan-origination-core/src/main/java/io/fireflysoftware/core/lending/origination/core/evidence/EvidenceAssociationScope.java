/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
/** Scope data shared by the mapped loader; authority still requires the private original execution. */
public interface EvidenceAssociationScope {
    UUID actionId();UUID applicationId();UUID partyId();UUID resourceTenantId();UUID documentResourceTenantId();
    AuditActor actor();UUID bindingId();long bindingVersion();String bindingDigest();String policyRevision();
    Instant observedAt();long observedNanos();Instant notAfter();
}
