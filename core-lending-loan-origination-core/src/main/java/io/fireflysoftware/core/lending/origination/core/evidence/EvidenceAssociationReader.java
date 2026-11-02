/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.EvidenceAssociationReceipt;
import java.util.Optional;
import reactor.core.publisher.Mono;
public interface EvidenceAssociationReader{Mono<Optional<EvidenceAssociationReceipt>> observe(EvidenceAssociationReadIdentity identity);}
