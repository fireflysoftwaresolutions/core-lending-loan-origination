/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.entities.intake;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;

@Table("loan_application_intake")
public record DraftIntake(@Id @Column("intake_id") UUID intakeId,
        @Column("resource_tenant_id") UUID resourceTenantId,@Column("request_id") UUID requestId,
        @Column("loan_application_id") UUID applicationId,@Column("party_id") UUID partyId,
        @Column("simulation_id") UUID simulationId,@Column("product_id") UUID productId,
        @Column("requested_amount") BigDecimal requestedAmount,@Column("term") int term,
        @Column("currency") String currency,@Column("purpose") String purpose,
        @Column("intent_digest") String intentDigest,@Column("binding_id") UUID bindingId,
        @Column("binding_version") long bindingVersion,@Column("binding_digest") String bindingDigest,
        @Column("audit_event_id") UUID auditEventId,@Column("audit_record_hash") String auditRecordHash,
        @Column("created_at") Instant createdAt) {
    @Override public String toString(){return "DraftIntake[redacted]";}
}
