/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.intake;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Initial intake status is not an offer, approval, contract, or current servicing status. */
public record DraftIntakeReceipt(UUID requestId, UUID applicationId, UUID simulationId,
        UUID productId, UUID partyId, BigDecimal requestedAmount, int term, String currency,
        String purpose, String initialStatusCode, String initialSubStatusCode,
        Instant createdAt, boolean replayed, String intentDigest) {
    @Override public String toString() { return "DraftIntakeReceipt[redacted]"; }
}
