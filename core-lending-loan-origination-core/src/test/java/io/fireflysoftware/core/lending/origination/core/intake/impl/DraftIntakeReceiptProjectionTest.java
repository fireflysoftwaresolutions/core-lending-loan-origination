/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake.impl;

import io.fireflysoftware.core.lending.origination.core.intake.DraftIntakeIntent;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DraftIntakeReceiptProjectionTest {
    @Test void createAndReplayKeepTheSameRetainedAssociationAndCanonicalDigest() {
        var intent=new DraftIntakeIntent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
            new BigDecimal("1234.50"),24,"EUR","vehicle");
        var row=new DraftIntake(UUID.randomUUID(),UUID.randomUUID(),intent.requestId(),UUID.randomUUID(),intent.partyId(),
            intent.simulationId(),intent.productId(),intent.amount(),intent.term(),intent.currency(),intent.purpose(),intent.digest(),
            UUID.randomUUID(),1,"a".repeat(64),UUID.randomUUID(),"b".repeat(64),Instant.parse("2026-11-01T10:00:00.123456Z"));
        var created=SpringDataDraftIntakeServiceImpl.receipt(row,false);
        var replayed=SpringDataDraftIntakeServiceImpl.receipt(row,true);
        assertThat(created.intentDigest()).isEqualTo(row.intentDigest()).isEqualTo(intent.digest());
        assertThat(replayed.intentDigest()).isEqualTo(created.intentDigest());
        assertThat(created.applicationId()).isEqualTo(row.applicationId());
        assertThat(replayed).usingRecursiveComparison().ignoringFields("replayed").isEqualTo(created);
        assertThat(created.replayed()).isFalse();assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.initialStatusCode()).isEqualTo("DRAFT");
        assertThat(replayed.initialSubStatusCode()).isEqualTo("PENDING_DOCUMENTS");
    }
}
