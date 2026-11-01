/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;

import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import reactor.core.publisher.Mono;

public interface DraftIntakeService {
    Mono<DraftIntakeReceipt> create(DraftIntakeRequest request);
}
