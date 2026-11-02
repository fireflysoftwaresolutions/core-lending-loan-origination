/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.intake;

import io.fireflysoftware.core.lending.origination.models.entities.ApplicationParty;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface ApplicationPartyIntakeRepository extends ReactiveCrudRepository<ApplicationParty,UUID> {
    @Lock(LockMode.PESSIMISTIC_READ)
    Flux<ApplicationParty> findAllByLoanApplicationIdAndIsPrimaryTrue(UUID applicationId);

    @Query("SELECT public.verify_governed_primary_applicant(:tenant,:application,:party,:primary)")
    Mono<Boolean> verifyRetainedPrimary(@Param("tenant") UUID tenant,@Param("application") UUID application,
            @Param("party") UUID party,@Param("primary") UUID primary);
}
