/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class EvidenceAssociationConflict extends BusinessException{
    private final UUID action;
    public EvidenceAssociationConflict(UUID action){super(HttpStatus.CONFLICT,"LENDING_EVIDENCE_ACTION_CONFLICT","The original action is bound to a different document reference");this.action=Objects.requireNonNull(action);}
    public UUID actionId(){return action;}
}
