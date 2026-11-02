/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.contractbasis;
import io.fireflysoftware.core.lending.origination.core.contractbasis.*;
import io.fireflysoftware.core.lending.origination.web.controllers.ApplicationContractBasisController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
class ApplicationContractBasisProviderTest {
    final UUID app=new UUID(0,1),action=new UUID(0,2);
    @SuppressWarnings("unchecked") @Test void absentProviderIsTypedObservationUnavailableBeforeAnyReader(){ObjectProvider<ApplicationContractBasisAdmission> a=mock(ObjectProvider.class);ObjectProvider<ApplicationContractBasisReader> r=mock(ObjectProvider.class);StepVerifier.create(new ApplicationContractBasisController(a,r).observe(app,action)).expectErrorMatches(e->e instanceof ApplicationContractBasisFailure f&&action.equals(f.actionId())).verify();}
    @SuppressWarnings("unchecked") @Test void providerConstructionFailureStaysInsideTheTypedReadEnvelope(){ObjectProvider<ApplicationContractBasisAdmission> a=mock(ObjectProvider.class);ObjectProvider<ApplicationContractBasisReader> r=mock(ObjectProvider.class);when(a.getIfUnique()).thenThrow(new IllegalStateException("controlled provider construction refusal"));StepVerifier.create(new ApplicationContractBasisController(a,r).observe(app,action)).expectErrorMatches(e->e instanceof ApplicationContractBasisFailure f&&action.equals(f.actionId())).verify();verifyNoInteractions(r);}
}
