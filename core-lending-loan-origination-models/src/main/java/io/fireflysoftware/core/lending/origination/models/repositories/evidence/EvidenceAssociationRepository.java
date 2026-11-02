/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.models.repositories.evidence;
import io.fireflysoftware.core.lending.origination.models.entities.evidence.EvidenceAssociation;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
@NoRepositoryBean
public interface EvidenceAssociationRepository extends ReactiveCrudRepository<EvidenceAssociation,UUID>{
    Flux<EvidenceAssociation> findAllByResourceTenantIdAndActionId(UUID resourceTenantId,UUID actionId);
    @Query("SELECT TRUE AS locked FROM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('loan-evidence-action:' || CAST(:tenant AS text) || ':' || CAST(:action AS text),0))")
    Mono<Boolean> lockAction(UUID tenant,UUID action);
}
