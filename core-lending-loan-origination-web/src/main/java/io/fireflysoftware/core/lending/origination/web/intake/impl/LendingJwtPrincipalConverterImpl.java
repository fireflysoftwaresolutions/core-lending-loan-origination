/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake.impl;
import java.util.Objects;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.spi.TenantResolverPort;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/** Projection only: the native authentication manager must validate the JWT first. */
public final class LendingJwtPrincipalConverterImpl implements Converter<Jwt,Mono<AbstractAuthenticationToken>> {
    private final PrincipalFactory factory;private final TenantResolverPort tenants;
    public LendingJwtPrincipalConverterImpl(PrincipalFactory factory,TenantResolverPort tenants){this.factory=Objects.requireNonNull(factory);this.tenants=Objects.requireNonNull(tenants);}
    @Override public Mono<AbstractAuthenticationToken> convert(Jwt jwt){return Mono.defer(()->{
        var principal=factory.fromClaims(jwt.getSubject(),jwt.getIssuer()==null?null:jwt.getIssuer().toString(),jwt.getClaims());
        return tenants.resolveTenant(principal).filter(value->!value.isBlank())
            .<AbstractAuthenticationToken>map(value->new FireflyAuthenticationToken(principal.toBuilder().tenantId(value).build())).switchIfEmpty(Mono.error(denied()));
    });}
    private static OAuth2AuthenticationException denied(){return new OAuth2AuthenticationException(new OAuth2Error("invalid_token","Exact signed tenant projection required",null));}
}
