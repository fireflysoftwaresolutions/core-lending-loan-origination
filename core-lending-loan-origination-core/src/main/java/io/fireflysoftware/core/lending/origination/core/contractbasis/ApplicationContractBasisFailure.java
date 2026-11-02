/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ApplicationContractBasisFailure extends BusinessException{
    private final UUID action;
    public ApplicationContractBasisFailure(UUID action){super(HttpStatus.SERVICE_UNAVAILABLE,"LOAN_CONTRACT_BASIS_OBSERVATION_UNAVAILABLE","Current original application contract basis unavailable",Map.of("actionId",Objects.requireNonNull(action).toString(),"outcome","OBSERVATION_UNAVAILABLE","recovery","SAME_ACTION_OBSERVE"));this.action=action;}
    public UUID actionId(){return action;}
}
