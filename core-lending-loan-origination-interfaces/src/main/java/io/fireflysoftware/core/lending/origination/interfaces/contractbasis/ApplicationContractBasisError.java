/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.contractbasis;
import java.util.UUID;
public record ApplicationContractBasisError(UUID actionId,String code,String outcome,String recovery){}
