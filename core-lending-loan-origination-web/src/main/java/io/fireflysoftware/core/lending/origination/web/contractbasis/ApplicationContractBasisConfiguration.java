/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.contractbasis;
import io.fireflysoftware.core.lending.origination.core.contractbasis.*;
import io.fireflysoftware.core.lending.origination.core.contractbasis.impl.SpringDataApplicationContractBasisReaderImpl;
import io.fireflysoftware.core.lending.origination.web.contractbasis.impl.AuthenticatedApplicationContractBasisAdmissionImpl;
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
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.contract-basis.enabled",havingValue="true")
public class ApplicationContractBasisConfiguration {
    @Bean ApplicationContractBasisSettings applicationContractBasisSettings(Environment env){String p="lending.contract-basis.";return new ApplicationContractBasisSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"audience"),env.getRequiredProperty(p+"allowed-client-id"),env.getRequiredProperty(p+"platform-tenant"),UUID.fromString(env.getRequiredProperty(p+"resource-tenant-id")),UUID.fromString(env.getRequiredProperty(p+"binding-id")),Long.parseLong(env.getRequiredProperty(p+"binding-version")),env.getRequiredProperty(p+"binding-digest"),Instant.parse(env.getRequiredProperty(p+"not-after")));}
    @Bean ApplicationContractBasisAdmission applicationContractBasisAdmission(ApplicationContractBasisSettings settings,ObjectProvider<PolicyEvaluationPort> policies){var policy=policies.getIfUnique();if(policy==null)throw new IllegalStateException("Exactly one current Loan contract-basis policy evaluator required");return new AuthenticatedApplicationContractBasisAdmissionImpl(settings,policy,Clock.systemUTC(),System::nanoTime);}
    @Bean ApplicationContractBasisReader applicationContractBasisReader(@Qualifier("connectionFactory")ConnectionFactory business,ApplicationContractBasisAdmission authority){var factory=new OwnedLeaseConnectionFactory(business);var client=DatabaseClient.create(factory);var template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);return new SpringDataApplicationContractBasisReaderImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),System::nanoTime);}
    @Bean @Order(-50) WebFilter applicationContractBasisVerifiedSubject(ReactiveJwtDecoder decoder,@Qualifier("fireflyJwtAuthenticationConverter")Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,ApplicationContractBasisSettings settings){return new io.fireflysoftware.core.lending.origination.web.intake.impl.VerifiedLendingJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/loan-application-contract-basis");}
}
