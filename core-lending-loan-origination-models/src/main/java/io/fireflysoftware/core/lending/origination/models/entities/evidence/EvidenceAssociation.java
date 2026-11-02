/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.entities.evidence;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(name="association",schema="loan_evidence")
public record EvidenceAssociation(
    @Id @Column("association_id") UUID associationId,
    @Column("resource_tenant_id") UUID resourceTenantId,
    @Column("action_id") UUID actionId,
    @Column("application_id") UUID applicationId,
    @Column("party_id") UUID partyId,
    @Column("document_resource_tenant_id") UUID documentResourceTenantId,
    @Column("document_id") UUID documentId,
    @Column("document_type_id") UUID documentTypeId,
    @Column("document_type_code") String documentTypeCode,
    @Column("file_name") String fileName,
    @Column("media_type") String mediaType,
    @Column("content_length") long contentLength,
    @Column("content_digest") String contentDigest,
    @Column("document_intent_digest") String documentIntentDigest,
    @Column("document_stored_seconds") long documentStoredSeconds,
    @Column("document_stored_nanos") int documentStoredNanos,
    @Column("intent_digest") String intentDigest,
    @Column("effect_digest") String effectDigest,
    @Column("primary_party_id") UUID primaryPartyId,
    @Column("primary_role_id") UUID primaryRoleId,
    @Column("initial_status_id") UUID initialStatusId,
    @Column("initial_sub_status_id") UUID initialSubStatusId,
    @Column("binding_id") UUID bindingId,
    @Column("binding_version") long bindingVersion,
    @Column("binding_digest") String bindingDigest,
    @Column("actor_issuer") String actorIssuer,
    @Column("actor_subject") String actorSubject,
    @Column("actor_client_id") String actorClientId,
    @Column("actor_platform_tenant_id") String actorPlatformTenantId,
    @Column("policy_revision") String policyRevision,
    @Column("attached_at") Instant attachedAt,
    @Column("audit_context_nonce") UUID auditContextNonce,
    @Column("audit_event_id") UUID auditEventId,
    @Column("audit_sequence") long auditSequence,
    @Column("audit_record_hash") String auditRecordHash ){
    @Override public String toString(){return "EvidenceAssociation[redacted]";}
}
