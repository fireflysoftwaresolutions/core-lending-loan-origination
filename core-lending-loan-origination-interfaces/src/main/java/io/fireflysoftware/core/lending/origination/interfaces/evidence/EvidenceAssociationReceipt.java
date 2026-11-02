/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.evidence;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
public record EvidenceAssociationReceipt(UUID actionId,UUID applicationId,UUID partyId,UUID resourceTenantId,UUID associationId,
        UUID documentResourceTenantId,UUID documentId,UUID documentTypeId,String documentTypeCode,String fileName,String mediaType,
        long contentLength,String contentDigest,String documentIntentDigest,@Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant documentStoredAt,String state,String verificationStatus,
        @Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant attachedAt,boolean replayed,String intentDigest){
    @Override public String toString(){return "EvidenceAssociationReceipt[redacted]";}
}
