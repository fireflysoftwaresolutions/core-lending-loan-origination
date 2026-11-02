/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence.impl;
import io.fireflysoftware.core.lending.origination.core.evidence.*;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.*;
import io.fireflysoftware.core.lending.origination.models.entities.*;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import io.fireflysoftware.core.lending.origination.models.entities.evidence.EvidenceAssociation;
import io.fireflysoftware.core.lending.origination.models.repositories.evidence.*;
import io.fireflysoftware.core.lending.origination.models.repositories.intake.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.query.*;
import reactor.core.publisher.*;

/** Mapped observations and exact original graph; callers must prove the canonical pair in their owning Root. */
final class EvidenceAssociationDataLoaderImpl {
    record Parent(LoanApplication application,DraftIntake intake,ApplicationParty primary,RoleCode role,DocumentType type){}
    final R2dbcEntityTemplate template;final EvidenceAssociationRepository rows;
    final EvidenceApplicationRepository applications;final EvidenceIntakeRepository intakes;final EvidenceDocumentTypeRepository types;
    final ApplicationPartyIntakeRepository parties;final RoleCodeIntakeRepository roles;final ApplicationStatusIntakeRepository statuses;final ApplicationSubStatusIntakeRepository subStatuses;
    EvidenceAssociationDataLoaderImpl(R2dbcEntityTemplate template){
        this.template=Objects.requireNonNull(template);var factory=new R2dbcRepositoryFactory(template);
        rows=factory.getRepository(EvidenceAssociationRepository.class);applications=factory.getRepository(EvidenceApplicationRepository.class);intakes=factory.getRepository(EvidenceIntakeRepository.class);types=factory.getRepository(EvidenceDocumentTypeRepository.class);
        parties=factory.getRepository(ApplicationPartyIntakeRepository.class);roles=factory.getRepository(RoleCodeIntakeRepository.class);statuses=factory.getRepository(ApplicationStatusIntakeRepository.class);subStatuses=factory.getRepository(ApplicationSubStatusIntakeRepository.class);
    }
    Mono<Optional<EvidenceAssociation>> retained(EvidenceAssociationScope scope){return rows.findAllByResourceTenantIdAndActionId(scope.resourceTenantId(),scope.actionId()).take(2).collectList().map(found->{if(found.size()>1)throw unavailable();if(found.isEmpty())return Optional.empty();if(!scope.resourceTenantId().equals(found.getFirst().resourceTenantId())||!scope.actionId().equals(found.getFirst().actionId()))throw unavailable();return Optional.of(found.getFirst());});}
    Mono<Parent> parent(EvidenceAssociationScope scope,String code,boolean creating){return applications.findByLoanApplicationId(scope.applicationId()).switchIfEmpty(Mono.error(unavailable())).flatMap(app->
        one(intakes.findAllByResourceTenantIdAndApplicationIdAndPartyId(scope.resourceTenantId(),scope.applicationId(),scope.partyId())).flatMap(intake->
            one(parties.findAllByLoanApplicationIdAndIsPrimaryTrue(scope.applicationId())).flatMap(primary->
                one(roles.findAllByCode("PRIMARY_APPLICANT")).flatMap(role->{
                    if(!scope.applicationId().equals(app.getLoanApplicationId())||!scope.applicationId().equals(intake.applicationId())||!scope.partyId().equals(intake.partyId())
                        ||!scope.resourceTenantId().equals(intake.resourceTenantId())||!scope.partyId().equals(primary.getPartyId())||!Boolean.TRUE.equals(primary.getIsPrimary())
                        ||primary.getApplicationPartyId()==null||!Objects.equals(primary.getRoleCodeId(),role.getRoleCodeId())||!"PRIMARY_APPLICANT".equals(role.getCode())||!Boolean.TRUE.equals(role.getIsActive()))return Mono.error(unavailable());
                    return parties.verifyRetainedPrimary(scope.resourceTenantId(),scope.applicationId(),scope.partyId(),primary.getApplicationPartyId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable()))
                        .then(code==null?Mono.just(new Parent(app,intake,primary,role,null)):one(types.findAllByCode(code)).flatMap(type->{
                            if(type.getDocumentTypeId()==null||!code.equals(type.getCode())||creating&&!Boolean.TRUE.equals(type.getIsActive()))return Mono.error(unavailable());
                            var parent=new Parent(app,intake,primary,role,type);
                            if(!creating)return Mono.just(parent);
                            return one(statuses.findAllByCode("DRAFT")).flatMap(status->one(subStatuses.findAllByCode("PENDING_DOCUMENTS")).flatMap(sub->{
                                if(!Boolean.TRUE.equals(status.getIsActive())||!Boolean.TRUE.equals(sub.getIsActive())||!Objects.equals(app.getApplicationStatusId(),status.getApplicationStatusId())||!Objects.equals(app.getApplicationSubStatusId(),sub.getApplicationSubStatusId()))return Mono.error(unavailable());
                                return Mono.just(parent);
                            }));
                        }));
                }))));}
    Mono<EvidenceAssociation> exact(EvidenceAssociationScope scope,EvidenceAssociation row){return Mono.defer(()->{
        identity(scope,row);var original=intent(row);if(!original.digest().equals(row.intentDigest())||!effect(row).equals(row.effectDigest())
            ||row.auditEventId()==null||row.auditContextNonce()==null||row.auditSequence()<1||row.auditRecordHash()==null||!row.auditRecordHash().matches("[a-f0-9]{64}"))return Mono.error(unavailable());
        return parent(scope,row.documentTypeCode(),false).filter(parent->Objects.equals(parent.primary().getApplicationPartyId(),row.primaryPartyId())&&Objects.equals(parent.role().getRoleCodeId(),row.primaryRoleId())&&Objects.equals(parent.type().getDocumentTypeId(),row.documentTypeId()))
            .switchIfEmpty(Mono.error(unavailable())).then(template.selectOne(Query.query(Criteria.where("applicationDocumentId").is(row.associationId())),ApplicationDocument.class))
            .filter(expected(row)::equals).switchIfEmpty(Mono.error(unavailable())).thenReturn(row);
    });}
    static EvidenceAssociationIntent intent(EvidenceAssociation row){return new EvidenceAssociationIntent(row.actionId(),row.applicationId(),row.partyId(),row.documentResourceTenantId(),row.documentId(),row.fileName(),row.documentTypeCode(),row.mediaType(),row.contentLength(),row.contentDigest(),row.documentIntentDigest(),Instant.ofEpochSecond(row.documentStoredSeconds(),row.documentStoredNanos()));}
    static void identity(EvidenceAssociationScope scope,EvidenceAssociation row){
        var actor=scope.actor();
        if(!scope.resourceTenantId().equals(row.resourceTenantId())||!scope.actionId().equals(row.actionId())||!scope.applicationId().equals(row.applicationId())||!scope.partyId().equals(row.partyId())||!scope.documentResourceTenantId().equals(row.documentResourceTenantId())
            ||!scope.bindingId().equals(row.bindingId())||scope.bindingVersion()!=row.bindingVersion()||!scope.bindingDigest().equals(row.bindingDigest())
            ||!actor.issuer().equals(row.actorIssuer())||!actor.subject().equals(row.actorSubject())||!actor.clientId().equals(row.actorClientId())||!actor.platformTenantId().equals(row.actorPlatformTenantId()))throw unavailable();
    }
    static ApplicationDocument expected(EvidenceAssociation row){var attached=LocalDateTime.ofInstant(row.attachedAt(),ZoneOffset.UTC);
        return ApplicationDocument.builder().applicationDocumentId(row.associationId()).loanApplicationId(row.applicationId()).documentId(row.documentId()).documentTypeId(row.documentTypeId())
            .isMandatory(null).isReceived(true).receivedAt(attached).documentName(row.fileName()).fileSizeBytes(row.contentLength()).mimeType(row.mediaType()).verificationStatus("PENDING").createdAt(attached).updatedAt(attached).build();
    }
    static EvidenceAssociationReceipt receipt(EvidenceAssociation row,boolean replayed){return new EvidenceAssociationReceipt(row.actionId(),row.applicationId(),row.partyId(),row.resourceTenantId(),row.associationId(),row.documentResourceTenantId(),row.documentId(),row.documentTypeId(),row.documentTypeCode(),row.fileName(),row.mediaType(),row.contentLength(),row.contentDigest(),row.documentIntentDigest(),Instant.ofEpochSecond(row.documentStoredSeconds(),row.documentStoredNanos()),"ATTACHED","PENDING",row.attachedAt(),replayed,row.intentDigest());}
    static String effect(EvidenceAssociation row){return effectValues(List.of("FIREFLY_LOAN_EVIDENCE_EFFECT_1",row.intentDigest(),row.resourceTenantId().toString(),row.associationId().toString(),row.documentTypeId().toString(),row.primaryPartyId().toString(),row.primaryRoleId().toString(),row.initialStatusId().toString(),row.initialSubStatusId().toString(),row.bindingId().toString(),Long.toString(row.bindingVersion()),row.bindingDigest(),row.actorIssuer(),row.actorSubject(),row.actorClientId(),row.actorPlatformTenantId(),row.policyRevision()),row.attachedAt());}
    static String effect(EvidenceAssociationIntent intent,EvidenceAssociationAdmission.Permit permit,Parent parent,UUID association,Instant attached){return effectValues(List.of("FIREFLY_LOAN_EVIDENCE_EFFECT_1",intent.digest(),permit.resourceTenantId().toString(),association.toString(),parent.type().getDocumentTypeId().toString(),parent.primary().getApplicationPartyId().toString(),parent.role().getRoleCodeId().toString(),parent.application().getApplicationStatusId().toString(),parent.application().getApplicationSubStatusId().toString(),permit.bindingId().toString(),Long.toString(permit.bindingVersion()),permit.bindingDigest(),permit.actor().issuer(),permit.actor().subject(),permit.actor().clientId(),permit.actor().platformTenantId(),permit.policyRevision()),attached);}
    private static String effectValues(List<String> values,Instant attached){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);for(String text:values){byte[] encoded=text.getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);}out.writeLong(attached.getEpochSecond());out.writeInt(attached.getNano());return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw unavailable();}}
    static <T> Mono<T> one(Flux<T> values){return values.take(2).collectList().flatMap(rows->rows.size()==1?Mono.just(rows.getFirst()):Mono.error(unavailable()));}
    static IllegalStateException unavailable(){return new IllegalStateException("Current exact managed application evidence graph required");}
}
