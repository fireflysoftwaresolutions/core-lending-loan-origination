/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake.impl;
import io.r2dbc.spi.R2dbcException;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;

public final class GovernedDraftWriteErrorMapperImpl {
    private GovernedDraftWriteErrorMapperImpl(){}
    public static Throwable map(Throwable error){
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());Throwable current=error;
        for(int depth=0;depth<8 && current!=null && seen.add(current);depth++,current=current.getCause()){
            if(current instanceof R2dbcException database && "PZL01".equals(database.getSqlState()))
                return new BusinessException(HttpStatus.CONFLICT,"LENDING_GOVERNED_APPLICATION_IMMUTABLE","This governed application requires an audited lending transition");
        }
        return error;
    }
}
