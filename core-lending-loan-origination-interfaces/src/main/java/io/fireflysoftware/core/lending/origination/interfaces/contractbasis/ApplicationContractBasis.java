/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.contractbasis;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.*;
/** Original intake actor/current primary managed application ownership and original persisted intake terms; no contract association or transition. */
public record ApplicationContractBasis(@NotNull UUID applicationId,@NotNull UUID resourceTenantId,@NotNull UUID partyId,
        @NotNull UUID primaryApplicantId,@NotNull UUID simulationId,@NotNull UUID productId,@NotNull @Positive BigDecimal requestedAmount,
        @Min(1) int term,@NotNull @Pattern(regexp="[A-Z]{3}") String currencyCode,@NotNull @Pattern(regexp="[a-f0-9]{64}") String intakeIntentDigest){
    @Override public String toString(){return "ApplicationContractBasis[redacted]";}
}
