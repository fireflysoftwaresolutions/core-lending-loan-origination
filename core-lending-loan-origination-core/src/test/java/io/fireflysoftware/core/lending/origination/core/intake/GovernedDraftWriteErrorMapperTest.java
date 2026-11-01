/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;
import io.fireflysoftware.core.lending.origination.core.intake.impl.GovernedDraftWriteErrorMapperImpl;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
class GovernedDraftWriteErrorMapperTest {
    @Test void onlyTheDedicatedGovernedWriteStateBecomesSafeConflict(){
        var nativeError=new R2dbcNonTransientResourceException("private-sql-sentinel", "PZL01",0);
        var result=GovernedDraftWriteErrorMapperImpl.map(new IllegalStateException("private-path-sentinel",nativeError));
        assertThat(result).isInstanceOf(BusinessException.class);var conflict=(BusinessException)result;
        assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);assertThat(conflict.getCode()).isEqualTo("LENDING_GOVERNED_APPLICATION_IMMUTABLE");
        assertThat(conflict.getMessage()).doesNotContain("private-sql-sentinel","private-path-sentinel");assertThat(conflict.getCause()).isNull();
    }
    @Test void otherDatabaseFailuresRemainTheExactSameError(){
        for(String state:new String[]{"23514","23505","40001","08006"}){var error=new R2dbcNonTransientResourceException("private",state,0);
            assertThat(GovernedDraftWriteErrorMapperImpl.map(error)).isSameAs(error);}
    }
    @Test void nestedMalformedCauseGraphsAreBounded(){
        var cycle=new RuntimeException("private"){@Override public synchronized Throwable getCause(){return this;}};
        assertThat(GovernedDraftWriteErrorMapperImpl.map(cycle)).isSameAs(cycle);
    }
}
