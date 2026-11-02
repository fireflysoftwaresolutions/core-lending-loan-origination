/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.interfaces.evidence;
import java.util.UUID;
public record EvidenceAssociationError(UUID actionId,String code,Outcome outcome,String recovery){
    public enum Outcome{REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE}
}
