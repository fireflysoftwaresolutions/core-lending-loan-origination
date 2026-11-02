/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis;
import java.util.UUID;
public record ApplicationContractBasisIdentity(UUID actionId,UUID applicationId,UUID partyId){
    public ApplicationContractBasisIdentity{for(UUID value:new UUID[]{actionId,applicationId,partyId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact original application read identity required");}
    @Override public String toString(){return "ApplicationContractBasisIdentity[redacted]";}
}
