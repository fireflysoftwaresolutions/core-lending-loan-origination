/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.contractbasis;
import java.time.Instant;
import java.util.UUID;

/** Explicit server-owned application resource binding; never derived from caller request data. */
public record ApplicationContractBasisSettings(String issuer,String audience,String allowedClientId,String platformTenant,
        UUID resourceTenantId,UUID bindingId,long bindingVersion,String bindingDigest,Instant notAfter){
    public ApplicationContractBasisSettings{
        for(String value:new String[]{issuer,audience,allowedClientId,platformTenant})
            if(value==null || value.isBlank() || value.length()>512 || value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit contract-basis read deployment binding required");
        for(UUID id:new UUID[]{resourceTenantId,bindingId})if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact contract-basis read dataset required");
        if(bindingVersion<1 || bindingDigest==null || !bindingDigest.matches("[0-9a-f]{64}") || notAfter==null)throw new IllegalArgumentException("Versioned contract-basis read binding required");
    }
    public void requireCurrent(Instant now){if(!now.isBefore(notAfter))throw new IllegalStateException("Current contract-basis read binding required");}
    @Override public String toString(){return "ApplicationContractBasisSettings[redacted]";}
}
