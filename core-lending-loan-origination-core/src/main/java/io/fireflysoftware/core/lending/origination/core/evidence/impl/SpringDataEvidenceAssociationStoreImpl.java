/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence.impl;
import io.fireflysoftware.core.lending.origination.core.evidence.*;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.*;
import io.fireflysoftware.core.lending.origination.models.entities.evidence.EvidenceAssociation;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/** Owns only Loan association; it never creates, verifies, deletes, or rewrites Document custody. */
public final class SpringDataEvidenceAssociationStoreImpl implements EvidenceAssociationStore {
    private final EvidenceAssociationAuditRootImpl owner;private final EvidenceAssociationDataLoaderImpl data;
    public SpringDataEvidenceAssociationStoreImpl(EvidenceAssociationAuditRootImpl owner){this.owner=Objects.requireNonNull(owner);data=new EvidenceAssociationDataLoaderImpl(owner.template());}
    @Override public Mono<EvidenceAssociationReceipt> attach(EvidenceAssociationRequest request){
        final EvidenceAssociationIntent intent;try{intent=EvidenceAssociationIntent.capture(request);}catch(RuntimeException malformed){return Mono.error(new BusinessException(HttpStatus.BAD_REQUEST,"LENDING_EVIDENCE_INPUT_INVALID","Exact bounded original document reference required"));}
        return owner.execute(intent,permit->data.rows.lockAction(permit.resourceTenantId(),permit.actionId()).switchIfEmpty(Mono.error(EvidenceAssociationDataLoaderImpl.unavailable()))
            .then(data.retained(permit)).flatMap(found->{
                if(found.isPresent()){
                    var row=found.orElseThrow();if(!intent.digest().equals(row.intentDigest()))return Mono.error(new EvidenceAssociationConflict(intent.actionId()));
                    return data.exact(permit,row).map(verified->new EvidenceAssociationAuditRootImpl.Plan<>(row.applicationId(),row.auditEventId(),row.attachedAt(),row.policyRevision(),row.effectDigest(),Optional.of(new EvidenceAssociationAuditRootImpl.Replay<>(EvidenceAssociationDataLoaderImpl.receipt(row,true),row.auditSequence(),row.auditRecordHash())),null));
                }
                return data.parent(permit,intent.documentTypeCode(),true).map(parent->{
                    UUID association=UUID.randomUUID(),event=UUID.randomUUID();Instant attached=owner.clock().instant().truncatedTo(ChronoUnit.MICROS);
                    String effect=EvidenceAssociationDataLoaderImpl.effect(intent,permit,parent,association,attached);
                    return new EvidenceAssociationAuditRootImpl.Plan<EvidenceAssociationReceipt>(intent.applicationId(),event,attached,permit.policyRevision(),effect,Optional.empty(),(nonce,record)->{
                        var row=row(intent,permit,parent,association,attached,effect,nonce,record);
                        return owner.template().insert(EvidenceAssociationDataLoaderImpl.expected(row)).then(owner.template().insert(row))
                            .then(data.retained(permit)).flatMap(read->read.isEmpty()?Mono.error(EvidenceAssociationDataLoaderImpl.unavailable()):data.exact(permit,read.orElseThrow()))
                            .map(verified->{if(!row.equals(verified))throw EvidenceAssociationDataLoaderImpl.unavailable();return EvidenceAssociationDataLoaderImpl.receipt(verified,false);});
                    });
                });
            }));
    }
    private static EvidenceAssociation row(EvidenceAssociationIntent intent,EvidenceAssociationAdmission.Permit permit,EvidenceAssociationDataLoaderImpl.Parent parent,
            UUID association,Instant attached,String effect,UUID nonce,AuditRecord record){
        return new EvidenceAssociation(association,permit.resourceTenantId(),intent.actionId(),intent.applicationId(),intent.partyId(),intent.documentResourceTenantId(),intent.documentId(),parent.type().getDocumentTypeId(),intent.documentTypeCode(),intent.fileName(),intent.mediaType(),intent.contentLength(),intent.contentDigest(),intent.documentIntentDigest(),intent.documentStoredAt().getEpochSecond(),intent.documentStoredAt().getNano(),intent.digest(),effect,parent.primary().getApplicationPartyId(),parent.role().getRoleCodeId(),parent.application().getApplicationStatusId(),parent.application().getApplicationSubStatusId(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest(),permit.actor().issuer(),permit.actor().subject(),permit.actor().clientId(),permit.actor().platformTenantId(),permit.policyRevision(),attached,nonce,record.event().eventId(),record.sequence(),record.recordHash());
    }
}
