/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import java.util.UUID;
public record EvidenceAssociationReadIdentity(UUID actionId,UUID applicationId,UUID partyId){
    public EvidenceAssociationReadIdentity{for(UUID value:new UUID[]{actionId,applicationId,partyId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact original signed association identity required");}
    @Override public String toString(){return "AssociationReadIdentity[redacted]";}
}
