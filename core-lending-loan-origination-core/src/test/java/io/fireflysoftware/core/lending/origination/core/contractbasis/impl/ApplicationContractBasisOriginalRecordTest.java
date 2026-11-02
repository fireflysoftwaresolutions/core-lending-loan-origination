/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.contractbasis.impl;
import io.fireflysoftware.core.lending.origination.models.entities.intake.DraftIntake;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Codec/predicate proof; owning physical canonical-pair proof is the native40 gate. */
class ApplicationContractBasisOriginalRecordTest {
    final UUID tenant=id(1),app=id(2),request=id(3),party=id(4),eventId=id(5);final AuditStream stream=new AuditStream(tenant,"core-loan-origination","loan-application",app);
    final AuditActor actor=new AuditActor("https://issuer.invalid","opaque-original-human","domain-lending","opaque-platform",null,null);
    final Instant created=Instant.parse("2026-11-02T00:00:00.123456Z"),occurred=created.plusNanos(87654),recorded=occurred.plusMillis(1);
    static UUID id(long n){return new UUID(0,n);}
    AuditEvent event(Instant time){return new AuditEvent(eventId,stream,actor,"lending.application.draft.create",AuditEvent.Outcome.COMPLETED,request,request,null,time,"a".repeat(64),"original-policy-1");}
    AuditRecord record(){return AuditRecordHasher.record(AuditCheckpoint.genesis(stream),event(occurred),recorded);}
    DraftIntake row(AuditRecord record){return new DraftIntake(id(6),tenant,request,app,party,id(7),id(8),new BigDecimal("123.45"),12,"EUR",null,"b".repeat(64),id(9),1,"c".repeat(64),eventId,record.recordHash(),created);}
    @Test void distinctGenuineBusinessCreationAndOriginalEventTimesRemainValid(){var record=record();assertNotEquals(created,record.event().occurredAt());assertNotEquals(record.event().occurredAt(),record.recordedAt());assertTrue(SpringDataApplicationContractBasisReaderImpl.originalRecord(record,stream,actor,row(record)));}
    @Test void changedEventTimeWithTheOriginalStoredHashIsRejected(){var original=record();var changed=new AuditRecord(event(occurred.plusNanos(1)),original.sequence(),original.recordedAt(),original.previousHash(),original.recordHash());assertFalse(SpringDataApplicationContractBasisReaderImpl.originalRecord(changed,stream,actor,row(original)));}
    @Test void samePartyDoesNotAuthorizeADifferentOriginalActor(){var original=record();var different=new AuditActor(actor.issuer(),"different-human",actor.clientId(),actor.platformTenantId(),null,null);assertFalse(SpringDataApplicationContractBasisReaderImpl.originalRecord(original,stream,different,row(original)));}
}
