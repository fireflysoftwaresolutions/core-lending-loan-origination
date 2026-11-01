/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake;

import io.fireflysoftware.core.lending.origination.core.intake.impl.DraftIntakeAuditRootImpl;
import io.fireflysoftware.core.lending.origination.web.intake.impl.AuthenticatedDraftIntakeAdmissionImpl;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.UUID;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.intake.enabled",havingValue="true")
public class DraftIntakeConfiguration {
    @Bean public DraftIntakeSettings draftIntakeSettings(Environment env){String prefix="lending.intake.";
        return new DraftIntakeSettings(env.getRequiredProperty(prefix+"issuer"),env.getRequiredProperty(prefix+"audience"),
            env.getRequiredProperty(prefix+"allowed-client-id"),env.getRequiredProperty(prefix+"platform-tenant"),
            UUID.fromString(env.getRequiredProperty(prefix+"resource-tenant-id")),UUID.fromString(env.getRequiredProperty(prefix+"binding-id")),
            Long.parseLong(env.getRequiredProperty(prefix+"binding-version")),env.getRequiredProperty(prefix+"binding-digest"),
            Instant.parse(env.getRequiredProperty(prefix+"not-after")));
    }
    @Bean public AuthenticatedDraftIntakeAdmissionImpl draftIntakeAdmission(DraftIntakeSettings settings,ObjectProvider<PolicyEvaluationPort> policies){
        var pdp=policies.getIfUnique();if(pdp==null)throw new IllegalStateException("Exactly one current lending policy evaluator required");
        return new AuthenticatedDraftIntakeAdmissionImpl(settings,pdp,Clock.systemUTC(),System::nanoTime);
    }
    @Bean public DraftIntakeAuditRootImpl draftIntakeAuditRoot(@Qualifier("connectionFactory") ConnectionFactory raw,AuthenticatedDraftIntakeAdmissionImpl authority){
        var factory=new OwnedLeaseConnectionFactory(raw);var client=DatabaseClient.create(factory);
        var template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);
        return new DraftIntakeAuditRootImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,
            new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),System::nanoTime);
    }
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.fireflyframework.security.spi.SecurityContextPort.class)
    public org.fireflyframework.security.spi.SecurityContextPort lendingIntakeSecurityContext(){
        return new org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter();
    }
    @Bean @Order(-50) public org.springframework.web.server.WebFilter draftIntakeVerifiedSubject(
            ObjectProvider<org.springframework.security.oauth2.jwt.ReactiveJwtDecoder> decoders,
            @Qualifier("fireflyJwtAuthenticationConverter") ObjectProvider<org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt,
                reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken>>> projections,
            ObjectProvider<org.fireflyframework.security.core.context.PrincipalFactory> factories,DraftIntakeSettings settings,Environment env){
        var existingDecoders=decoders.stream().limit(2).toList();if(existingDecoders.size()>1)throw new IllegalStateException("One trusted intake decoder required");
        org.springframework.security.oauth2.jwt.ReactiveJwtDecoder decoder;
        if(existingDecoders.isEmpty()){
            String value=env.getRequiredProperty("lending.intake.jwk-set-uri");var uri=java.net.URI.create(value);
            boolean sandbox=Boolean.parseBoolean(env.getProperty("lending.intake.owned-loopback-sandbox","false"));
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !("https".equals(uri.getScheme()) || sandbox && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort()>0))
                throw new IllegalArgumentException("Explicit trusted intake JWK endpoint required");
            var owned=org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(value).build();
            owned.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(settings.issuer()));decoder=owned;
        }else decoder=existingDecoders.getFirst();
        var existingProjections=projections.stream().limit(2).toList();if(existingProjections.size()>1)throw new IllegalStateException("One configured intake projection required");
        org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt,reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken>> projection;
        if(existingProjections.isEmpty()){
            var configured=factories.stream().limit(2).toList();if(configured.size()>1)throw new IllegalStateException("One configured principal factory required");
            var factory=configured.isEmpty()?new org.fireflyframework.security.core.context.PrincipalFactory(
                new org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper(new org.fireflyframework.security.core.authority.AuthorityMappingProperties(
                    java.util.Arrays.asList(env.getRequiredProperty("lending.intake.role-claim-paths").split(",",-1)),
                    java.util.Arrays.asList(env.getRequiredProperty("lending.intake.scope-claim-paths").split(",",-1)),
                    env.getRequiredProperty("lending.intake.authority-prefix")))):configured.getFirst();
            projection=new io.fireflysoftware.core.lending.origination.web.intake.impl.LendingJwtPrincipalConverterImpl(factory,
                new org.fireflyframework.security.core.tenant.ConfigurableTenantResolver(java.util.List.of("tenant_id")));
        }else projection=existingProjections.getFirst();
        return new io.fireflysoftware.core.lending.origination.web.intake.impl.VerifiedLendingJwtWebFilterImpl(decoder,projection,
            settings.issuer(),settings.platformTenant(),"/api/v1/loan-application-intakes");
    }
}
