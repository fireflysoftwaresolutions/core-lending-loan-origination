/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;

/** Does not expose transaction driver messages or turn an unknown write into a conflict. */
public final class DraftIntakeFailure extends org.fireflyframework.web.error.exceptions.BusinessException {
    public enum Outcome { REFUSED, OUTCOME_UNKNOWN, COMMITTED_RESPONSE_UNAVAILABLE }
    private final Outcome outcome;
    public DraftIntakeFailure(Outcome outcome){super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"LENDING_INTAKE_"+outcome.name(),"Lending intake outcome unavailable");this.outcome=java.util.Objects.requireNonNull(outcome);}
    public Outcome outcome(){return outcome;}
}
