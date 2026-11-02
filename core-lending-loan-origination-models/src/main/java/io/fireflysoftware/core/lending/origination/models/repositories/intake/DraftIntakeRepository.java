/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.intake;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import java.util.UUID;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
@NoRepositoryBean
public interface DraftIntakeRepository extends ReactiveCrudRepository<DraftIntake,UUID> {
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Flux<DraftIntake> findAllByResourceTenantIdAndRequestId(UUID tenantId,UUID requestId);
}
