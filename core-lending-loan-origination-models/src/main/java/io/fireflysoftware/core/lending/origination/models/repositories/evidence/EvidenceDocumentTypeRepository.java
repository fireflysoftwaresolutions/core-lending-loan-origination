/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.evidence;
import io.fireflysoftware.core.lending.origination.models.entities.DocumentType;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
@NoRepositoryBean
public interface EvidenceDocumentTypeRepository extends ReactiveCrudRepository<DocumentType,UUID>{
    @Lock(LockMode.PESSIMISTIC_READ)
    Flux<DocumentType> findAllByCode(String code);
}
