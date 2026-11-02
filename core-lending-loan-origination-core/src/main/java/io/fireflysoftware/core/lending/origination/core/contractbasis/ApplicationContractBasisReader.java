/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis;
import io.fireflysoftware.core.lending.origination.interfaces.contractbasis.ApplicationContractBasis;
import reactor.core.publisher.Mono;
@FunctionalInterface
public interface ApplicationContractBasisReader{Mono<ApplicationContractBasis> observe(ApplicationContractBasisIdentity original);}
