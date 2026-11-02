/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake.impl;
import io.fireflysoftware.core.lending.origination.web.intake.*;

import io.fireflysoftware.core.lending.origination.core.intake.*;
import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Requires the resource-server-verified JWT; no sample actor or subject-to-UUID conversion. */
public final class AuthenticatedDraftIntakeAdmissionImpl implements DraftIntakeAdmission {
    public static final String ACTION="lending:applications:draft:create";
    private final DraftIntakeSettings settings;
    private final PolicyEvaluationPort pdp;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Object executionKey=new Object(),appendKey=new Object();
    public AuthenticatedDraftIntakeAdmissionImpl(DraftIntakeSettings settings,PolicyEvaluationPort pdp,Clock clock,LongSupplier nanos){
        this.settings=Objects.requireNonNull(settings);this.pdp=Objects.requireNonNull(pdp);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    public Mono<DraftIntakeReceipt> invoke(DraftIntakeRequest request,DraftIntakeStore delegate){
        final DraftIntakeIntent intent;try{intent=DraftIntakeIntent.capture(request);}catch(IllegalArgumentException invalid){return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact intake terms required"));}
        return Mono.deferContextual(context->{if(context.hasKey(executionKey))return Mono.error(denied());
            Instant observed=clock.instant();long before=nanos.getAsLong();
            return authentication().flatMap(auth->{SecurityPrincipal principal=principal(auth,intent);Instant until=observed.plusSeconds(5);
                if(auth.getToken().getExpiresAt().isBefore(until))until=auth.getToken().getExpiresAt();if(settings.notAfter().isBefore(until))until=settings.notAfter();
                var execution=new ExecutionImpl(intent,auth,principal,observed,before,until);
                return Mono.using(()->execution,held->Mono.defer(()->delegate.create(request)).switchIfEmpty(Mono.error(unavailable())).timeout(remaining(held))
                    .contextWrite(current->current.put(executionKey,held)),held->held.active.set(false),true).map(value->{
                    try{window(execution);}catch(RuntimeException expired){throw new DraftIntakeFailure(DraftIntakeFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);}return value;});
            }).timeout(Duration.ofSeconds(5));
        });
    }
    @Override public Mono<DraftIntakeAdmission.Permit> requireCurrent(DraftIntakeIntent intent){return Mono.deferContextual(context->{
        var execution=current(context);if(!execution.intent.equals(intent) || execution.permit.get()!=null)return Mono.error(denied());
        return evaluate(execution,"authorize").map(evaluation->{String revision=evaluation.provenance().orElseThrow().policyRevision();
            if(!revision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))throw unavailable();
            var permit=new DraftIntakeAdmission.Permit(intent.requestId(),intent.digest(),settings.resourceTenantId(),intent.partyId(),
                new AuditActor(settings.issuer(),execution.principal.subject(),settings.allowedClientId(),settings.platformTenant(),null,null),
                settings.bindingId(),settings.bindingVersion(),settings.bindingDigest(),intent.requestId(),intent.requestId(),revision,
                execution.observed,execution.nanos,execution.until);
            if(!execution.permit.compareAndSet(null,permit))throw denied();return permit;
        });
    });}
    @Override public Mono<Void> revalidate(DraftIntakeAdmission.Permit permit){return Mono.deferContextual(context->{var execution=current(context);
        if(execution.permit.get()!=permit)return Mono.error(denied());return evaluate(execution,"current").flatMap(evaluation->
            permit.policyRevision().equals(evaluation.provenance().orElseThrow().policyRevision())?Mono.empty():Mono.error(denied()));
    });}
    @Override public <T> Mono<T> withinExpectedAppend(DraftIntakeAdmission.Permit permit,AuditEvent event,Supplier<Mono<T>> append){return Mono.deferContextual(context->{
        var execution=current(context);if(context.hasKey(appendKey) || execution.permit.get()!=permit || !execution.appended.compareAndSet(false,true)
            || !permit.actor().equals(event.actor()) || !permit.resourceTenantId().equals(event.stream().resourceTenantId())
            || !"lending.application.draft.create".equals(event.action()) || event.outcome()!=AuditEvent.Outcome.COMPLETED
            || !permit.processId().equals(event.processId()) || !permit.correlationId().equals(event.correlationId())
            || event.causationId()!=null || !permit.policyRevision().equals(event.policyRevision()))return Mono.error(denied());
        var expected=new ExpectedImpl(execution,event);
        return Mono.using(()->expected,held->Mono.defer(append).contextWrite(current->current.put(appendKey,held)),held->held.active.set(false),true);
    });}
    @Override public Mono<org.fireflyframework.core.audit.AuditAppendAuthority.Permit> requireCurrent(AuditEvent event){return Mono.deferContextual(context->{
        var execution=current(context);Object raw=context.getOrDefault(appendKey,null);
        if(!(raw instanceof ExpectedImpl expected) || !expected.active.get() || expected.execution!=execution || !expected.event.equals(event))return Mono.error(denied());
        return revalidate(execution.permit.get()).thenReturn(new org.fireflyframework.core.audit.AuditAppendAuthority.Permit(event.stream(),event.actor(),execution.until));
    });}
    private Mono<PolicyEvaluation> evaluate(ExecutionImpl execution,String stage){return same(execution).flatMap(principal->{
        var facts=new LinkedHashMap<String,Object>(Map.<String,Object>of("profile","single-dataset-loan-draft-intake-1","stage",stage,"resourceTenantId",settings.resourceTenantId(),
            "bindingId",settings.bindingId(),"bindingVersion",settings.bindingVersion(),"bindingDigest",settings.bindingDigest(),
            "requestId",execution.intent.requestId(),"partyId",execution.intent.partyId(),"simulationId",execution.intent.simulationId(),"intentDigest",execution.intent.digest()));
        facts.put("productId",execution.intent.productId());facts.put("requestedAmount",execution.intent.amount().toPlainString());facts.put("term",execution.intent.term());facts.put("currency",execution.intent.currency());
        return pdp.evaluate(principal,ACTION,"urn:firefly:loan-intake:dataset:"+settings.bindingId()+":party:"+execution.intent.partyId()+":request:"+execution.intent.requestId(),facts)
            .switchIfEmpty(Mono.error(unavailable())).flatMap(evaluation->{
                if(!evaluation.decision().granted())return Mono.error(denied());
                if(!evaluation.decision().obligations().isEmpty() || evaluation.provenance().isEmpty())return Mono.error(unavailable());
                return same(execution).thenReturn(evaluation);
            });
    }).timeout(remaining(execution));}
    private Mono<SecurityPrincipal> same(ExecutionImpl execution){return authentication().filter(auth->auth==execution.authentication).switchIfEmpty(Mono.error(denied()))
        .map(auth->{var value=principal(auth,execution.intent);if(!value.equals(execution.principal))throw denied();remaining(execution);return value;});}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication())
        .filter(auth->auth instanceof JwtAuthenticationToken && auth.isAuthenticated()).cast(JwtAuthenticationToken.class).switchIfEmpty(Mono.error(denied()));}
    private SecurityPrincipal principal(JwtAuthenticationToken authentication,DraftIntakeIntent intent){Jwt jwt=authentication.getToken();Instant now=clock.instant();settings.requireCurrent(now);
        Object rawParty=jwt.getClaims().get("party_id"),rawScope=jwt.getClaims().get("scope");
        if((jwt.getIssuer()==null || !settings.issuer().equals(jwt.getIssuer().toString())) || jwt.getSubject()==null || jwt.getSubject().isBlank()
            || !List.of(settings.audience()).equals(jwt.getAudience()) || !settings.allowedClientId().equals(jwt.getClaims().get("client_id"))
            || !"HUMAN".equals(jwt.getClaims().get("principal_kind")) || !settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            || jwt.getExpiresAt()==null || jwt.getNotBefore()==null || !now.isBefore(jwt.getExpiresAt()) || now.isBefore(jwt.getNotBefore())
            || !(rawParty instanceof String party) || !intent.partyId().toString().equals(party) || !(rawScope instanceof String scope)
            || !Set.copyOf(Arrays.asList(scope.split(" +"))).contains(ACTION))throw denied();
        if(!(authentication.getPrincipal() instanceof SecurityPrincipal projected) || !settings.issuer().equals(projected.issuer())
            || !jwt.getSubject().equals(projected.subject()) || !settings.platformTenant().equals(projected.tenantId())
            || !projected.claims().equals(jwt.getClaims()) || !projected.hasScope(ACTION))throw denied();
        return projected;
    }
    private ExecutionImpl current(reactor.util.context.ContextView context){Object raw=context.getOrDefault(executionKey,null);
        if(!(raw instanceof ExecutionImpl execution) || !execution.active.get())throw denied();remaining(execution);return execution;}
    private Duration remaining(ExecutionImpl execution){if(!execution.active.get())throw denied();return window(execution);}
    private Duration window(ExecutionImpl execution){long elapsed=nanos.getAsLong()-execution.nanos;
        var wall=Duration.between(clock.instant(),execution.until);var monotonic=Duration.between(execution.observed,execution.until).minusNanos(elapsed);
        if(elapsed<0 || wall.isNegative() || wall.isZero() || monotonic.isNegative() || monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private static final class ExecutionImpl {
        final DraftIntakeIntent intent;final JwtAuthenticationToken authentication;final SecurityPrincipal principal;final Instant observed,until;final long nanos;
        final AtomicBoolean active=new AtomicBoolean(true),appended=new AtomicBoolean();final AtomicReference<DraftIntakeAdmission.Permit> permit=new AtomicReference<>();
        ExecutionImpl(DraftIntakeIntent intent,JwtAuthenticationToken authentication,SecurityPrincipal principal,Instant observed,long nanos,Instant until){
            this.intent=intent;this.authentication=authentication;this.principal=principal;this.observed=observed;this.nanos=nanos;this.until=until;}
    }
    private static final class ExpectedImpl {final ExecutionImpl execution;final AuditEvent event;final AtomicBoolean active=new AtomicBoolean(true);
        ExpectedImpl(ExecutionImpl execution,AuditEvent event){this.execution=execution;this.event=event;}}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Lending intake is not permitted");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current lending intake authority is unavailable");}
}
