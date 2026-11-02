/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.evidence;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
@NoRepositoryBean
public interface EvidenceIntakeRepository extends ReactiveCrudRepository<DraftIntake,UUID>{
    Flux<DraftIntake> findAllByResourceTenantIdAndApplicationIdAndPartyId(UUID resourceTenantId,UUID applicationId,UUID partyId);
}
