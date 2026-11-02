/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake;

import io.fireflysoftware.core.lending.origination.core.intake.*;
import io.fireflysoftware.core.lending.origination.web.intake.impl.AuthenticatedDraftIntakeAdmissionImpl;
import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import static org.assertj.core.api.Assertions.*;

class AuthenticatedDraftIntakeAdmissionTest {
    final UUID requestId=UUID.fromString("dd366be2-4dbe-4761-a71e-f98d2d929306"),simulation=UUID.fromString("3cc6118a-ab59-4f04-b8ee-159c84858b58"),
        product=UUID.fromString("1f5af31c-d26b-4d7c-a24a-25ac0bfcd2ba"),party=UUID.fromString("da2e5152-36ea-490a-a211-e3d9e5668511"),
        tenant=UUID.fromString("58848fed-4189-4b63-913b-589191cd3127"),binding=UUID.fromString("5d399b5e-f881-40af-a468-fca584ae078b");
    final Instant now=Instant.parse("2026-11-01T10:00:00Z");
    final DraftIntakeRequest request=new DraftIntakeRequest(requestId,simulation,product,party,new BigDecimal("1000.00"),12,"EUR","vehicle");
    DraftIntakeSettings settings(){return new DraftIntakeSettings("https://issuer.invalid","lending-core","domain-lending","opaque-platform-tenant",tenant,binding,1,"a".repeat(64),now.plusSeconds(60));}
    PolicyEvaluation permit(){return new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-1","opaque-decision")));}
    JwtAuthenticationToken authentication(Map<String,Object> changes){var claims=new HashMap<String,Object>();
        claims.put("iss","https://issuer.invalid");claims.put("sub","opaque:human/customer@example.invalid");claims.put("aud",List.of("lending-core"));
        claims.put("client_id","domain-lending");claims.put("principal_kind","HUMAN");claims.put("tenant_id","opaque-platform-tenant");
        claims.put("party_id",party.toString());claims.put("scope",AuthenticatedDraftIntakeAdmissionImpl.ACTION);claims.put("nbf",now.minusSeconds(1));claims.put("exp",now.plusSeconds(60));claims.putAll(changes);
        var jwt=Jwt.withTokenValue("unit-token-never-accepted-as-signature-proof").header("alg","RS256").claims(c->c.putAll(claims)).build();
        var converter=new org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter(
            new org.fireflyframework.security.core.context.PrincipalFactory(new org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper(
                new org.fireflyframework.security.core.authority.AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_"))),
            new org.fireflyframework.security.core.tenant.ConfigurableTenantResolver(List.of("tenant_id")),true);
        var projected=java.util.Objects.requireNonNull(converter.convert(jwt).block());
        var authentication=new JwtAuthenticationToken(jwt,projected.getPrincipal(),projected.getAuthorities());
        assertThat(authentication.isAuthenticated()).isTrue();return authentication;}
    DraftIntakeReceipt receipt(){return new DraftIntakeReceipt(requestId,UUID.fromString("f75d1012-e047-4d1a-9991-cef0a6e109ab"),simulation,product,party,new BigDecimal("1000.00"),12,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",now,false,DraftIntakeIntent.capture(request).digest());}
    AuthenticatedDraftIntakeAdmissionImpl admission(PolicyEvaluationPort pdp){return new AuthenticatedDraftIntakeAdmissionImpl(settings(),pdp,Clock.fixed(now,ZoneOffset.UTC),System::nanoTime);}
    @Test void preservesOpaqueSubjectAndUsesSignedPartyAndServerDataset(){
        var adapter=admission((p,a,r,c)->{assertThat(p.subject()).isEqualTo("opaque:human/customer@example.invalid");assertThat(c.get("partyId")).isEqualTo(party);
            assertThat(c.get("resourceTenantId")).isEqualTo(tenant);assertThat(c.get("productId")).isEqualTo(product);return Mono.just(permit());});
        var seen=new AtomicReference<DraftIntakeAdmission.Permit>();
        StepVerifier.create(adapter.invoke(request,value->adapter.requireCurrent(DraftIntakeIntent.capture(value)).flatMap(auth->{seen.set(auth);return adapter.revalidate(auth).thenReturn(receipt());}))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectNext(receipt()).verifyComplete();
        assertThat(seen.get().actor().subject()).isEqualTo("opaque:human/customer@example.invalid");assertThat(seen.get().partyId()).isEqualTo(party);
        assertThat(seen.get().actor().platformTenantId()).isEqualTo("opaque-platform-tenant");
    }
    @Test void missingAuthenticationFailsBeforeOwnerCall(){var calls=new AtomicInteger();var adapter=admission((p,a,r,c)->Mono.just(permit()));
        StepVerifier.create(adapter.invoke(request,value->{calls.incrementAndGet();return Mono.just(receipt());})).expectError(ResponseStatusException.class).verify();
        var unauthenticated=new JwtAuthenticationToken(authentication(Map.of()).getToken());assertThat(unauthenticated.isAuthenticated()).isFalse();
        StepVerifier.create(adapter.invoke(request,value->{calls.incrementAndGet();return Mono.just(receipt());}).contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated))).expectError(ResponseStatusException.class).verify();assertThat(calls).hasValue(0);}
    @Test void mismatchedSignedClaimsFailBeforeOwnerCall(){
        for(var change:List.<Map<String,Object>>of(Map.of("party_id",product.toString()),Map.of("principal_kind","STAFF"),Map.of("tenant_id",tenant.toString()),
            Map.of("aud",List.of("other")),Map.of("client_id","other"),Map.of("iss","https://other.invalid"),Map.of("scope","lending:applications:read"),Map.of("exp",now))){
            var calls=new AtomicInteger();var adapter=admission((p,a,r,c)->Mono.just(permit()));
            StepVerifier.create(adapter.invoke(request,value->{calls.incrementAndGet();return Mono.just(receipt());}).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(change))))
                .expectError(ResponseStatusException.class).verify();assertThat(calls).hasValue(0);
        }
    }
    @Test void denyAndMissingProvenanceCannotCreatePermit(){for(var policy:List.of(new PolicyEvaluation(Decision.deny("no"),Optional.of(new PolicyEvaluationProvenance("policy-1","decision"))),new PolicyEvaluation(Decision.permit(),Optional.empty()))){
        var evaluations=new AtomicInteger();var adapter=admission((p,a,r,c)->{evaluations.incrementAndGet();return Mono.just(policy);});var writes=new AtomicInteger();
        StepVerifier.create(adapter.invoke(request,value->adapter.requireCurrent(DraftIntakeIntent.capture(value)).map(ignored->{writes.incrementAndGet();return receipt();}))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectError(ResponseStatusException.class).verify();assertThat(writes).hasValue(0);assertThat(evaluations).hasValue(1);
    }}
    @Test void replacementAuthenticationCannotReusePermit(){var adapter=admission((p,a,r,c)->Mono.just(permit()));var issued=new AtomicInteger();
        StepVerifier.create(adapter.invoke(request,value->adapter.requireCurrent(DraftIntakeIntent.capture(value)).doOnNext(ignored->issued.incrementAndGet()).flatMap(auth->adapter.revalidate(auth)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).thenReturn(receipt()))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectError(ResponseStatusException.class).verify();assertThat(issued).hasValue(1);}
    @Test void nestedInvocationCannotStartAnotherOwnerPhase(){var adapter=admission((p,a,r,c)->Mono.just(permit()));var calls=new AtomicInteger();var outer=new AtomicInteger();
        StepVerifier.create(adapter.invoke(request,value->{outer.incrementAndGet();return adapter.invoke(value,second->{calls.incrementAndGet();return Mono.just(receipt());});})
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectError(ResponseStatusException.class).verify();assertThat(calls).hasValue(0);assertThat(outer).hasValue(1);}
    @Test void successfulTerminationRevokesCapturedAuthorityBeforeDownstream(){var adapter=admission((p,a,r,c)->Mono.just(permit()));var context=new AtomicReference<Context>();var auth=new AtomicReference<DraftIntakeAdmission.Permit>();
        var operation=adapter.invoke(request,value->Mono.deferContextual(current->{context.set(Context.of(current));return adapter.requireCurrent(DraftIntakeIntent.capture(value)).map(permit->{auth.set(permit);return receipt();});}))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())));
        StepVerifier.create(operation.flatMap(value->adapter.revalidate(auth.get()).contextWrite(context.get()).thenReturn(value))).expectError(ResponseStatusException.class).verify();assertThat(context.get()).isNotNull();assertThat(auth.get()).isNotNull();}
    @Test void lateProjectionNeverEmitsAReceipt(){var instant=new AtomicReference<>(now);Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant.get();}};
        var adapter=new AuthenticatedDraftIntakeAdmissionImpl(settings(),(p,a,r,c)->Mono.just(permit()),clock,System::nanoTime);
        StepVerifier.create(adapter.invoke(request,value->Mono.fromCallable(()->{instant.set(now.plusSeconds(6));return receipt();}))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(DraftIntakeFailure.class);
                assertThat(((DraftIntakeFailure)error).outcome()).isEqualTo(DraftIntakeFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);}).verify();}
}
