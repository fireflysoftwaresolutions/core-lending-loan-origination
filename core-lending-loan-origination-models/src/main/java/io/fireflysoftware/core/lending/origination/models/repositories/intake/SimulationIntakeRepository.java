/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.intake;

import io.fireflysoftware.core.lending.origination.models.entities.Simulation;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/** Constructed only from the exact audited owner template. */
@NoRepositoryBean
public interface SimulationIntakeRepository extends ReactiveCrudRepository<Simulation,java.util.UUID> {
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<Simulation> findBySimulationId(java.util.UUID simulationId);
}
