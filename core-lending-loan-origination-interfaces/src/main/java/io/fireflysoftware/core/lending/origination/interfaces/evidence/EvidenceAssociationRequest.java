/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.evidence;
import jakarta.validation.constraints.*;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/** Reference values only. Current signed party/application authority is established independently by the owner. */
public record EvidenceAssociationRequest(@NotNull UUID actionId,@NotNull UUID applicationId,@NotNull UUID partyId,
        @NotNull UUID documentResourceTenantId,@NotNull UUID documentId,@NotBlank @Size(max=200) String fileName,
        @NotNull @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}") String documentTypeCode,@NotBlank @Size(max=100) String mediaType,
        @Min(1) @Max(2097152) long contentLength,@NotNull @Pattern(regexp="[a-f0-9]{64}") String contentDigest,
        @NotNull @Pattern(regexp="[a-f0-9]{64}") String documentIntentDigest,@NotNull @Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant documentStoredAt){
    @Override public String toString(){return "EvidenceAssociationRequest[redacted]";}
}
