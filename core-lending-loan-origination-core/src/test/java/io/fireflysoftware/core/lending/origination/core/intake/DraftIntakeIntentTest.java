/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;

import io.fireflysoftware.core.lending.origination.interfaces.intake.DraftIntakeRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DraftIntakeIntentTest {
    private final UUID request=UUID.fromString("b693b1ad-8cd5-4dc9-b4d1-1da08bcc634c");
    private final UUID simulation=UUID.fromString("a8531d6e-6ed8-4ff2-afc0-a7fdcd8fe2bd");
    private final UUID product=UUID.fromString("3a5cf1ed-56cc-414d-be59-e2710af1aa94");
    private final UUID party=UUID.fromString("0582907d-1c4a-4452-8da7-5a72d5768871");
    private DraftIntakeRequest request(String amount,String purpose) {
        return new DraftIntakeRequest(request,simulation,product,party,new BigDecimal(amount),24,"EUR",purpose);
    }
    @Test void equivalentExactAmountsHaveTheSameCanonicalIntent() {
        var a=DraftIntakeIntent.capture(request("1234.50","vehicle"));
        var b=DraftIntakeIntent.capture(request("1234.500","vehicle"));
        assertThat(a.digest()).matches("[0-9a-f]{64}").isEqualTo(b.digest());
        assertThat(a.amount()).isEqualTo(new BigDecimal("1234.50"));
    }
    @Test void purposeNullEmptyAndFramingAreDistinct() {
        assertThat(DraftIntakeIntent.capture(request("1234.50",null)).digest())
            .isNotEqualTo(DraftIntakeIntent.capture(request("1234.50","")).digest());
        assertThat(DraftIntakeIntent.capture(request("1234.50","a\nb")).digest())
            .isNotEqualTo(DraftIntakeIntent.capture(request("1234.50","ab")).digest());
    }
    @Test void changedPartyProductSimulationAndTermsChangeIntent() {
        var a=request("1234.50","vehicle");var baseline=DraftIntakeIntent.capture(a).digest();
        assertThat(DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,UUID.randomUUID(),a.requestedAmount(),a.term(),a.currency(),a.purpose())).digest()).isNotEqualTo(baseline);
        assertThat(DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,UUID.randomUUID(),party,a.requestedAmount(),a.term(),a.currency(),a.purpose())).digest()).isNotEqualTo(baseline);
        assertThat(DraftIntakeIntent.capture(new DraftIntakeRequest(request,UUID.randomUUID(),product,party,a.requestedAmount(),a.term(),a.currency(),a.purpose())).digest()).isNotEqualTo(baseline);
        assertThat(DraftIntakeIntent.capture(request("1234.51","vehicle")).digest()).isNotEqualTo(baseline);
        assertThat(DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,party,a.requestedAmount(),25,"EUR",a.purpose())).digest()).isNotEqualTo(baseline);
        assertThat(DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,party,a.requestedAmount(),24,"USD",a.purpose())).digest()).isNotEqualTo(baseline);
    }
    @Test void rejectsNonrepresentableOrOutOfRangeStoredSimulationAmounts() {
        for(String value:new String[]{"0","-1","1.001","100000000000000000.00"})
            assertThatThrownBy(()->DraftIntakeIntent.capture(request(value,"vehicle"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMissingOrZeroIdentityAndMissingTerms() {
        assertThatThrownBy(()->DraftIntakeIntent.capture(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,null,new BigDecimal("10"),24,"EUR",null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DraftIntakeIntent.capture(new DraftIntakeRequest(new UUID(0,0),simulation,product,party,new BigDecimal("10"),24,"EUR",null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,party,new BigDecimal("10"),0,"EUR",null))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void currencyAndPurposeMatchExactPersistableShape() {
        for(String value:new String[]{"eur","EUR ","EU","EURO"})
            assertThatThrownBy(()->DraftIntakeIntent.capture(new DraftIntakeRequest(request,simulation,product,party,new BigDecimal("10"),24,value,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DraftIntakeIntent.capture(request("10","a".repeat(51)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DraftIntakeIntent.capture(request("10",String.valueOf((char)0xd800)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void simulationMustMatchAllRequestedTermsWithoutRounding() {
        var intent=DraftIntakeIntent.capture(request("1234.50","vehicle"));
        var row=io.fireflysoftware.core.lending.origination.models.entities.Simulation.builder()
            .simulationId(simulation).productId(product).productType("PERSONAL_LOAN")
            .requestedAmount(new BigDecimal("1234.50")).term(24).currency("EUR").purpose("vehicle").build();
        assertThatCode(()->intent.requireSimulation(row)).doesNotThrowAnyException();
        row.setRequestedAmount(new BigDecimal("1234.51"));
        assertThatThrownBy(()->intent.requireSimulation(row)).isInstanceOf(IllegalArgumentException.class);
        row.setRequestedAmount(new BigDecimal("1234.50"));row.setProductType("LEASING");
        assertThatThrownBy(()->intent.requireSimulation(row)).isInstanceOf(IllegalArgumentException.class);
    }
}
