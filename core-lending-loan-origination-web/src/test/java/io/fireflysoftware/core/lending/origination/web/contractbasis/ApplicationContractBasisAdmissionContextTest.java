/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.contractbasis;
import io.fireflysoftware.core.lending.origination.core.contractbasis.*;
import io.fireflysoftware.core.lending.origination.interfaces.contractbasis.ApplicationContractBasis;
import io.fireflysoftware.core.lending.origination.web.contractbasis.impl.AuthenticatedApplicationContractBasisAdmissionImpl;
import io.fireflysoftware.core.lending.origination.web.intake.impl.LendingJwtPrincipalConverterImpl;
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
import static org.junit.jupiter.api.Assertions.*;

/** Constructed-JWT current-context unit proof only; physical owner and signed HTTP proof are separate. */
class ApplicationContractBasisAdmissionContextTest {
    final UUID app=id(1),action=id(2),party=id(3),tenant=id(4),binding=id(5);final Clock clock=Clock.systemUTC();final AtomicBoolean allowed=new AtomicBoolean(true);
    static UUID id(long value){return new UUID(0,value);}
    AuthenticatedApplicationContractBasisAdmissionImpl authority(){var settings=new ApplicationContractBasisSettings("https://issuer.invalid","lending-core","domain-lending","opaque-platform",tenant,binding,1,"a".repeat(64),clock.instant().plusSeconds(60));PolicyEvaluationPort policy=(p,a,r,f)->Mono.just(new PolicyEvaluation(allowed.get()?Decision.permit():Decision.deny("current refusal"),Optional.of(new PolicyEvaluationProvenance("owner-policy-1","current"))));return new AuthenticatedApplicationContractBasisAdmissionImpl(settings,policy,clock,System::nanoTime);}
    JwtAuthenticationToken authentication(UUID signedParty,String scope){var now=clock.instant();var jwt=Jwt.withTokenValue("constructed-context-unit-only").header("alg","RS256").issuer("https://issuer.invalid").subject("opaque-human").audience(List.of("lending-core")).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(60)).claim("client_id","domain-lending").claim("principal_kind","HUMAN").claim("tenant_id","opaque-platform").claim("party_id",signedParty.toString()).claim("scope",scope).build();
        var factory=new org.fireflyframework.security.core.context.PrincipalFactory(new org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper(new org.fireflyframework.security.core.authority.AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));
        var projected=new LendingJwtPrincipalConverterImpl(factory,new org.fireflyframework.security.core.tenant.ConfigurableTenantResolver(List.of("tenant_id"))).convert(jwt).block(Duration.ofSeconds(1));assertNotNull(projected);return new JwtAuthenticationToken(jwt,projected.getPrincipal(),projected.getAuthorities());}
    ApplicationContractBasis value(){return new ApplicationContractBasis(app,tenant,party,id(6),id(7),id(8),new BigDecimal("123.45"),12,"EUR","b".repeat(64));}
    @Test void callerBuiltIdentityIsNeverAnAdmissionGrant(){var a=authority();StepVerifier.create(a.requireCurrent(new ApplicationContractBasisIdentity(action,app,party)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,AuthenticatedApplicationContractBasisAdmissionImpl.ACTION)))).expectErrorMatches(ApplicationContractBasisAdmissionContextTest::forbidden).verify();}
    @Test void copiedPermitCannotReplaceTheOriginalPrivateExecutionReference(){var a=authority();var attempt=a.invoke(app,action,identity->a.requireCurrent(identity).flatMap(p->a.revalidate(new ApplicationContractBasisAdmission.Permit(p.identity(),p.resourceTenantId(),p.actor(),p.bindingId(),p.bindingVersion(),p.bindingDigest(),p.policyRevision(),p.observedAt(),p.observedNanos(),p.notAfter()))).thenReturn(value()));StepVerifier.create(attempt.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,AuthenticatedApplicationContractBasisAdmissionImpl.ACTION)))).expectErrorMatches(ApplicationContractBasisAdmissionContextTest::forbidden).verify();}
    @Test void latePolicyRevocationIsRevalidatedBeforeReturningAnyBasis(){var a=authority();var attempt=a.invoke(app,action,identity->a.requireCurrent(identity).flatMap(p->{allowed.set(false);return a.revalidate(p);}).thenReturn(value()));StepVerifier.create(attempt.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,AuthenticatedApplicationContractBasisAdmissionImpl.ACTION)))).expectErrorMatches(ApplicationContractBasisAdmissionContextTest::forbidden).verify();}
    static boolean forbidden(Throwable error){return error instanceof ResponseStatusException status&&status.getStatusCode().value()==403;}
}
