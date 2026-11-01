/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake.impl;

import io.fireflysoftware.core.lending.origination.core.intake.*;
import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import io.fireflysoftware.core.lending.origination.models.entities.*;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import io.fireflysoftware.core.lending.origination.models.repositories.intake.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;

@Service("springDataDraftIntakeService")
public final class SpringDataDraftIntakeServiceImpl implements DraftIntakeStore {
    private final DraftIntakeAuditRootImpl owner;
    private final R2dbcEntityTemplate template;
    private final DraftIntakeRepository intakes;
    private final ApplicationStatusIntakeRepository statuses;
    private final ApplicationSubStatusIntakeRepository subStatuses;
    private final SubmissionChannelIntakeRepository channels;
    private final SimulationIntakeRepository simulations;
    private final RoleCodeIntakeRepository roles;
    private final ApplicationPartyIntakeRepository parties;
    @Autowired public SpringDataDraftIntakeServiceImpl(ObjectProvider<DraftIntakeAuditRootImpl> resources){this(resources.getIfAvailable());}
    public SpringDataDraftIntakeServiceImpl(DraftIntakeAuditRootImpl owner){
        this.owner=owner;template=owner==null?null:owner.template();
        var factory=template==null?null:new R2dbcRepositoryFactory(template);
        intakes=factory==null?null:factory.getRepository(DraftIntakeRepository.class);
        statuses=factory==null?null:factory.getRepository(ApplicationStatusIntakeRepository.class);
        subStatuses=factory==null?null:factory.getRepository(ApplicationSubStatusIntakeRepository.class);
        channels=factory==null?null:factory.getRepository(SubmissionChannelIntakeRepository.class);
        simulations=factory==null?null:factory.getRepository(SimulationIntakeRepository.class);
        roles=factory==null?null:factory.getRepository(RoleCodeIntakeRepository.class);
        parties=factory==null?null:factory.getRepository(ApplicationPartyIntakeRepository.class);
    }
    @Override public Mono<DraftIntakeReceipt> create(DraftIntakeRequest request){
        final DraftIntakeIntent intent;
        try{intent=DraftIntakeIntent.capture(request);}catch(IllegalArgumentException invalid){return Mono.error(invalidInput());}
        return Mono.defer(()->{
            if(owner==null)return Mono.error(unavailable());
            return owner.execute(intent,permit->retained(permit).flatMap(found->{
                if(found.isPresent())return replayPlan(intent,permit,found.orElseThrow());
                // The retained DRAFT row serializes first creation; the post-lock statement sees committed retries.
                return one(statuses.findAllByCode("DRAFT")).flatMap(status->retained(permit).flatMap(afterLock->{
                    if(afterLock.isPresent())return replayPlan(intent,permit,afterLock.orElseThrow());
                    if(!"DRAFT".equals(status.getCode()) || !Boolean.TRUE.equals(status.getIsActive()) || status.getApplicationStatusId()==null)return Mono.error(unavailable());
                    return one(subStatuses.findAllByCode("PENDING_DOCUMENTS")).flatMap(sub->one(channels.findAllByCode("ONLINE")).flatMap(channel->{
                        if(!"PENDING_DOCUMENTS".equals(sub.getCode()) || !Boolean.TRUE.equals(sub.getIsActive()) || sub.getApplicationSubStatusId()==null
                            || !"ONLINE".equals(channel.getCode()) || !Boolean.TRUE.equals(channel.getIsActive()) || channel.getSubmissionChannelId()==null)return Mono.error(unavailable());
                        return simulations.findBySimulationId(intent.simulationId()).switchIfEmpty(Mono.error(invalidInput())).flatMap(simulation->{
                            try{intent.requireSimulation(simulation);}catch(IllegalArgumentException mismatch){return Mono.error(invalidInput());}
                            return one(roles.findAllByCode("PRIMARY_APPLICANT")).flatMap(role->{
                                if(!"PRIMARY_APPLICANT".equals(role.getCode()) || !Boolean.TRUE.equals(role.getIsActive()) || role.getRoleCodeId()==null)return Mono.error(unavailable());
                                UUID applicationId=UUID.randomUUID();Instant created=owner.clock().instant().truncatedTo(ChronoUnit.MICROS);
                                var app=LoanApplication.builder().loanApplicationId(applicationId).applicationNumber(UUID.randomUUID())
                                    .applicationStatusId(status.getApplicationStatusId()).applicationSubStatusId(sub.getApplicationSubStatusId())
                                    .submissionChannelId(channel.getSubmissionChannelId()).applicationDate(created.atOffset(ZoneOffset.UTC).toLocalDate())
                                    .simulationId(intent.simulationId()).loanPurpose(intent.purpose()).createdAt(LocalDateTime.ofInstant(created,ZoneOffset.UTC))
                                    .updatedAt(LocalDateTime.ofInstant(created,ZoneOffset.UTC)).build();
                                var primary=ApplicationParty.builder().applicationPartyId(UUID.randomUUID()).loanApplicationId(applicationId)
                                    .partyId(intent.partyId()).roleCodeId(role.getRoleCodeId()).isPrimary(true)
                                    .createdAt(app.getCreatedAt()).updatedAt(app.getCreatedAt()).build();
                                String evidence=evidence(intent,permit,applicationId,created,status.getApplicationStatusId(),sub.getApplicationSubStatusId(),channel.getSubmissionChannelId(),primary.getApplicationPartyId(),role.getRoleCodeId());
                                return Mono.just(new DraftIntakeAuditRootImpl.Plan<DraftIntakeReceipt>(applicationId,evidence,Optional.empty(),record->{
                                    var row=row(intent,permit,applicationId,created,record);
                                    return insert(app).then(insert(primary)).then(insert(row)).then(retained(permit)).flatMap(read->read.isEmpty()?Mono.error(unavailable()):
                                        verifyApplication(intent,permit,read.orElseThrow(),primary).map(loaded->{
                                            if(!row.equals(loaded))throw unavailable();return receipt(loaded,false);
                                        }));
                                }));
                            });
                        });
                    }));
                }));
            }));
        });
    }
    private Mono<DraftIntakeAuditRootImpl.Plan<DraftIntakeReceipt>> replayPlan(DraftIntakeIntent intent,DraftIntakeAdmission.Permit permit,DraftIntake row){
        return verifyApplication(intent,permit,row).map(loaded->new DraftIntakeAuditRootImpl.Plan<>(loaded.applicationId(),loaded.intentDigest(),Optional.of(receipt(loaded,true)),null));
    }
    private Mono<Optional<DraftIntake>> retained(DraftIntakeAdmission.Permit permit){
        return intakes.findAllByResourceTenantIdAndRequestId(permit.resourceTenantId(),permit.requestId()).take(2).collectList().map(rows->{
            if(rows.size()>1 || rows.stream().anyMatch(row->!permit.resourceTenantId().equals(row.resourceTenantId()) || !permit.requestId().equals(row.requestId())))throw unavailable();
            return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());
        });
    }
    private Mono<DraftIntake> verifyApplication(DraftIntakeIntent intent,DraftIntakeAdmission.Permit permit,DraftIntake row){
        return verifyApplication(intent,permit,row,null);
    }
    private Mono<DraftIntake> verifyApplication(DraftIntakeIntent intent,DraftIntakeAdmission.Permit permit,DraftIntake row,ApplicationParty expectedPrimary){return Mono.defer(()->{
        DraftIntakeIntent historical;
        try{historical=new DraftIntakeIntent(row.requestId(),row.simulationId(),row.productId(),row.partyId(),row.requestedAmount(),row.term(),row.currency(),row.purpose());}
        catch(RuntimeException malformed){return Mono.error(unavailable());}
        if(!historical.digest().equals(row.intentDigest()) || row.intakeId()==null || row.applicationId()==null || row.createdAt()==null || row.auditEventId()==null
            || row.auditRecordHash()==null || !row.auditRecordHash().matches("[0-9a-f]{64}") || !permit.resourceTenantId().equals(row.resourceTenantId())
            || !permit.requestId().equals(row.requestId()) || !permit.bindingId().equals(row.bindingId()) || permit.bindingVersion()!=row.bindingVersion()
            || !permit.bindingDigest().equals(row.bindingDigest()))return Mono.error(unavailable());
        if(!historical.equals(intent))return Mono.error(conflict());
        return template.selectOne(Query.query(Criteria.where("loanApplicationId").is(row.applicationId())),LoanApplication.class)
            .filter(app->row.applicationId().equals(app.getLoanApplicationId()) && row.simulationId().equals(app.getSimulationId())
                && Objects.equals(row.purpose(),app.getLoanPurpose()) && app.getApplicationStatusId()!=null && app.getApplicationSubStatusId()!=null)
            .switchIfEmpty(Mono.error(unavailable()))
            .then(one(parties.findAllByLoanApplicationIdAndIsPrimaryTrue(row.applicationId())))
            .filter(party->party.getApplicationPartyId()!=null && row.applicationId().equals(party.getLoanApplicationId())
                && row.partyId().equals(party.getPartyId()) && Boolean.TRUE.equals(party.getIsPrimary()) && party.getRoleCodeId()!=null
                && (expectedPrimary==null || expectedPrimary.equals(party)))
            .switchIfEmpty(Mono.error(unavailable()))
            .flatMap(party->parties.verifyRetainedPrimary(row.resourceTenantId(),row.applicationId(),row.partyId(),party.getApplicationPartyId()))
            .filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable())).thenReturn(row);
    });}
    private <T> Mono<T> one(Flux<T> values){return values.take(2).collectList().flatMap(rows->rows.size()==1?Mono.just(rows.getFirst()):Mono.error(unavailable()));}
    private <T> Mono<Void> insert(T row){var strategy=template.getDataAccessStrategy();var mapper=strategy.getStatementMapper().forType(row.getClass());
        StatementMapper.InsertSpec statement=mapper.createInsert(strategy.getTableName(row.getClass()));var outbound=strategy.getOutboundRow(row);
        for(var column:outbound.keySet())statement=statement.withColumn(column,outbound.get(column));
        return template.getDatabaseClient().sql(mapper.getMappedObject(statement)).fetch().rowsUpdated().switchIfEmpty(Mono.error(unavailable()))
            .flatMap(count->count==1?Mono.empty():Mono.error(unavailable()));
    }
    private static DraftIntake row(DraftIntakeIntent intent,DraftIntakeAdmission.Permit permit,UUID applicationId,Instant created,AuditRecord record){
        return new DraftIntake(UUID.randomUUID(),permit.resourceTenantId(),intent.requestId(),applicationId,intent.partyId(),intent.simulationId(),intent.productId(),
            intent.amount(),intent.term(),intent.currency(),intent.purpose(),intent.digest(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest(),record.event().eventId(),record.recordHash(),created);
    }
    static DraftIntakeReceipt receipt(DraftIntake row,boolean replay){return new DraftIntakeReceipt(row.requestId(),row.applicationId(),row.simulationId(),row.productId(),row.partyId(),
        row.requestedAmount(),row.term(),row.currency(),row.purpose(),"DRAFT","PENDING_DOCUMENTS",row.createdAt(),replay,row.intentDigest());}
    private static String evidence(DraftIntakeIntent intent,DraftIntakeAdmission.Permit permit,UUID app,Instant created,UUID status,UUID sub,UUID channel,UUID primary,UUID role){
        try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);
            for(String value:List.of("LENDING_INTAKE_EFFECT_2",intent.digest(),permit.resourceTenantId().toString(),permit.bindingId().toString(),Long.toString(permit.bindingVersion()),
                permit.bindingDigest(),app.toString(),created.toString(),status.toString(),sub.toString(),channel.toString(),primary.toString(),role.toString())){var text=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);}
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException impossible){throw unavailable();}
    }
    private static BusinessException conflict(){return new BusinessException(HttpStatus.CONFLICT,"LENDING_INTAKE_REQUEST_CONFLICT","The request identifier is already bound to different intake terms");}
    private static BusinessException invalidInput(){return new BusinessException(HttpStatus.BAD_REQUEST,"LENDING_INTAKE_INPUT_INVALID","Exact retained PERSONAL_LOAN simulation terms required");}
    private static BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"LENDING_INTAKE_UNAVAILABLE","Audited lending intake is unavailable");}
}
