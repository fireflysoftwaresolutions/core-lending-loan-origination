/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.intake;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Requested terms must exactly match the retained PERSONAL_LOAN simulation. */
public record DraftIntakeRequest(@NotNull UUID requestId, @NotNull UUID simulationId,
        @NotNull UUID productId, @NotNull UUID partyId,
        @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal requestedAmount,
        @NotNull @Min(1) Integer term, @NotNull @Pattern(regexp="[A-Z]{3}") String currency,
        @io.swagger.v3.oas.annotations.media.Schema(maxLength=50) String purpose) {
    @Override public String toString() { return "DraftIntakeRequest[redacted]"; }
}
