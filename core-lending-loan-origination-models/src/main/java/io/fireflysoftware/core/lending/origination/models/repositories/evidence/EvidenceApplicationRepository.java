/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.evidence;
import io.fireflysoftware.core.lending.origination.models.entities.LoanApplication;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
@NoRepositoryBean
public interface EvidenceApplicationRepository extends ReactiveCrudRepository<LoanApplication,UUID>{
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Mono<LoanApplication> findByLoanApplicationId(UUID applicationId);
}
