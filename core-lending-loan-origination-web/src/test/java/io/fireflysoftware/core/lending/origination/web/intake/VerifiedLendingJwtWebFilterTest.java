/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake;
import io.fireflysoftware.core.lending.origination.web.intake.impl.VerifiedLendingJwtWebFilterImpl;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.rs.*;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

class VerifiedLendingJwtWebFilterTest {
    static final String ISSUER="https://issuer.invalid",TENANT="opaque-platform-tenant",SUBJECT="opaque:customer@example.invalid";
    static final String PATH="/api/v1/loan-application-intakes",SCOPE="lending:applications:draft:create";
    final KeyPair keys=keys();
    static KeyPair keys(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(GeneralSecurityException error){throw new IllegalStateException(error);}}
    String token(KeyPair pair){var key=new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(ISSUER).subject(SUBJECT).audience(List.of("lending-core")).issuedAt(now).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(60))
            .claim("client_id","domain-lending").claim("tenant_id",TENANT).claim("party_id","da2e5152-36ea-490a-a211-e3d9e5668511").claim("principal_kind","HUMAN").claim("scope",SCOPE).claim("roles",List.of("APPLICANT")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();}
    ReactiveJwtDecoder decoder(){var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)keys.getPublic()).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));return decoder;}
    Converter<Jwt,Mono<AbstractAuthenticationToken>> projection(){return new JwtToFireflyPrincipalConverter(
        new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_"))),
        new ConfigurableTenantResolver(List.of("tenant_id")),true);}
    VerifiedLendingJwtWebFilterImpl filter(ReactiveJwtDecoder decoder,Converter<Jwt,Mono<AbstractAuthenticationToken>> projection){return new VerifiedLendingJwtWebFilterImpl(decoder,projection,ISSUER,TENANT,PATH);}
    WebTestClient client(ProbeController controller){var decoder=decoder();var projection=projection();
        var global=new ResourceServerAutoConfiguration().fireflySecurityWebFilterChain(ServerHttpSecurity.http(),decoder,projection,(principal,action,resource,context)->{
            assertThat(principal.subject()).isEqualTo(SUBJECT);assertThat(principal.issuer()).isEqualTo(ISSUER);assertThat(principal.tenantId()).isEqualTo(TENANT);
            return Mono.just(Decision.permit());
        },new ResourceServerProperties());
        return WebTestClient.bindToController(controller).webFilter(new WebFilterChainProxy(global),
            (exchange,chain)->new org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver().resolve(exchange)
                .doOnNext(controller.originalScope::set).then(chain.filter(exchange)),filter(decoder,projection)).build();}
    @Test void realSignedJwtPreservesNativeTokenAndDefaultRichPrincipalProjection(){
        String token=token(keys);var controller=new ProbeController(token);var client=client(controller);
        client.get().uri(PATH+"/projection").headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.nativeTokenRetained").isEqualTo(true).jsonPath("$.richPrincipalRetained").isEqualTo(true)
            .jsonPath("$.subject").isEqualTo(SUBJECT).jsonPath("$.issuer").isEqualTo(ISSUER).jsonPath("$.tenant").isEqualTo(TENANT)
            .jsonPath("$.scopeRetained").isEqualTo(true).jsonPath("$.roleRetained").isEqualTo(true).jsonPath("$.idempotencyScopeUnchanged").isEqualTo(true).jsonPath("$.auditActorPreserved").isEqualTo(true);
        assertThat(controller.calls).hasValue(1);assertThat(controller.nativeToken.get()).isNotNull();assertThat(controller.nativeToken.get().isAuthenticated()).isFalse();
    }
    @Test void invalidSignaturesMissingOrDuplicateCredentialsNeverReachController(){
        String token=token(keys);var controller=new ProbeController(token);var client=client(controller);
        client.get().uri(PATH+"/projection").headers(h->h.setBearerAuth(token(keys()))).exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH+"/projection").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH+"/projection").header(HttpHeaders.AUTHORIZATION,"Bearer "+token,"Bearer unrelated-unit-token").exchange().expectStatus().isUnauthorized();
        assertThat(controller.calls).hasValue(0);
    }
    @Test void unrelatedRoutesRemainProtectedByTheOriginalGlobalChain(){
        String token=token(keys);var controller=new ProbeController(token);var client=client(controller);
        client.get().uri("/legacy-projection").exchange().expectStatus().isUnauthorized();
        client.get().uri("/legacy-projection").headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.originalFireflyToken").isEqualTo(true).jsonPath("$.richPrincipalRetained").isEqualTo(true);
    }
    @Test void cancellationRevokesTheOwnedNativeTokenWithoutReplacingGlobalProjection(){
        var projected=new AtomicReference<AbstractAuthenticationToken>();var projection=projection();var nativeToken=new AtomicReference<JwtAuthenticationToken>();Sinks.One<Void> reached=Sinks.one();
        var filter=filter(decoder(),jwt->projection.convert(jwt).doOnNext(projected::set));
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get(PATH+"/projection").header(HttpHeaders.AUTHORIZATION,"Bearer "+token(keys)));
        var operation=filter.filter(exchange,ignored->ReactiveSecurityContextHolder.getContext().doOnNext(context->{
            nativeToken.set((JwtAuthenticationToken)context.getAuthentication());reached.tryEmitEmpty();
        }).then(Mono.never()));
        StepVerifier.create(Mono.when(operation,reached.asMono().then(Mono.error(new CancellationTriggerImpl())))).expectError(CancellationTriggerImpl.class).verify();
        assertThat(nativeToken.get()).isNotNull();assertThat(nativeToken.get().isAuthenticated()).isFalse();
        assertThat(projected.get()).isInstanceOf(FireflyAuthenticationToken.class);assertThat(projected.get().isAuthenticated()).isTrue();
    }
    static final class CancellationTriggerImpl extends RuntimeException {}
    @RestController static final class ProbeController {
        final String token;final AtomicInteger calls=new AtomicInteger();final AtomicReference<JwtAuthenticationToken> nativeToken=new AtomicReference<>();
        final ReactorSecurityContextAdapter defaults=new ReactorSecurityContextAdapter();final AtomicReference<String> originalScope=new AtomicReference<>();
        ProbeController(String token){this.token=token;}
        @GetMapping(PATH+"/projection") Mono<Map<String,Object>> projection(org.springframework.web.server.ServerWebExchange exchange){calls.incrementAndGet();return ReactiveSecurityContextHolder.getContext().flatMap(context->{
            var auth=(JwtAuthenticationToken)context.getAuthentication();nativeToken.set(auth);
            return Mono.zip(defaults.currentPrincipal(),new org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver().resolve(exchange)).map(pair->{
                var principal=pair.getT1();var actor=new org.fireflyframework.kernel.audit.AuditActor(principal.issuer(),principal.subject(),principal.claim("client_id",String.class),principal.tenantId(),null,null);
                return Map.<String,Object>of("nativeTokenRetained",token.equals(auth.getToken().getTokenValue()),
                    "richPrincipalRetained",principal==auth.getPrincipal(),"subject",principal.subject(),"issuer",principal.issuer(),"tenant",principal.tenantId(),
                    "scopeRetained",principal.hasScope(SCOPE),"roleRetained",principal.authorities().contains("ROLE_APPLICANT"),
                    "idempotencyScopeUnchanged",pair.getT2().equals(originalScope.get()),"auditActorPreserved",actor.subject().equals(SUBJECT)&&actor.platformTenantId().equals(TENANT)&&actor.issuer().equals(ISSUER)&&actor.clientId().equals("domain-lending"));
            });
        });}
        @GetMapping("/legacy-projection") Mono<Map<String,Object>> legacy(){return ReactiveSecurityContextHolder.getContext().flatMap(context->
            defaults.currentPrincipal().map(principal->Map.<String,Object>of("originalFireflyToken",context.getAuthentication() instanceof FireflyAuthenticationToken,
                "richPrincipalRetained",principal.subject().equals(SUBJECT)&&principal.tenantId().equals(TENANT)&&principal.hasScope(SCOPE))));}
    }
}
