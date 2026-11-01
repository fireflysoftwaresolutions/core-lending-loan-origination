/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake;
import java.time.Instant;
import java.util.UUID;

/** Explicit server-owned single-dataset binding; the legacy tables have no tenant discriminator. */
public record DraftIntakeSettings(String issuer,String audience,String allowedClientId,String platformTenant,
        UUID resourceTenantId,UUID bindingId,long bindingVersion,String bindingDigest,Instant notAfter){
    public DraftIntakeSettings{
        for(String value:new String[]{issuer,audience,allowedClientId,platformTenant})
            if(value==null || value.isBlank() || value.length()>512 || value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit intake deployment binding required");
        for(UUID id:new UUID[]{resourceTenantId,bindingId})if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact intake dataset required");
        if(bindingVersion<1 || bindingDigest==null || !bindingDigest.matches("[0-9a-f]{64}") || notAfter==null)throw new IllegalArgumentException("Versioned intake binding required");
    }
    public void requireCurrent(Instant now){if(!now.isBefore(notAfter))throw new IllegalStateException("Current intake binding required");}
    @Override public String toString(){return "DraftIntakeSettings[redacted]";}
}
