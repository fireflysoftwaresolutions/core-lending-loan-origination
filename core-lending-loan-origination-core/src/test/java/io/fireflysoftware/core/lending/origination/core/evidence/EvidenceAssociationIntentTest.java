/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.EvidenceAssociationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class EvidenceAssociationIntentTest {
    final EvidenceAssociationRequest request=new EvidenceAssociationRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"proof.pdf","ID_DOCUMENT","application/pdf",3,"a".repeat(64),"b".repeat(64),Instant.parse("2026-11-01T00:00:00.123456Z"));
    @Test void exactOriginalFullReferenceHasAStableOwnerDigest(){var first=EvidenceAssociationIntent.capture(request);var replay=EvidenceAssociationIntent.capture(request);assertThat(first).isEqualTo(replay);assertThat(first.digest()).isEqualTo(replay.digest()).matches("[a-f0-9]{64}");}
    @Test void equalLengthDifferentContentDigestChangesOriginalActionIntent(){var changed=new EvidenceAssociationRequest(request.actionId(),request.applicationId(),request.partyId(),request.documentResourceTenantId(),request.documentId(),request.fileName(),request.documentTypeCode(),request.mediaType(),request.contentLength(),"c".repeat(64),request.documentIntentDigest(),request.documentStoredAt());assertThat(EvidenceAssociationIntent.capture(changed).digest()).isNotEqualTo(EvidenceAssociationIntent.capture(request).digest());}
    @Test void sameBytesCannotMoveTheOriginalDocumentToAnotherApplication(){var changed=new EvidenceAssociationRequest(request.actionId(),UUID.randomUUID(),request.partyId(),request.documentResourceTenantId(),request.documentId(),request.fileName(),request.documentTypeCode(),request.mediaType(),request.contentLength(),request.contentDigest(),request.documentIntentDigest(),request.documentStoredAt());assertThat(EvidenceAssociationIntent.capture(changed).digest()).isNotEqualTo(EvidenceAssociationIntent.capture(request).digest());}
    @Test void exactTypeCodeAndBoundedActualDocumentReferenceAreRequired(){
        for(String code:new String[]{"","unknown code"," ID_DOCUMENT","id_document"})assertThatThrownBy(()->EvidenceAssociationIntent.capture(new EvidenceAssociationRequest(request.actionId(),request.applicationId(),request.partyId(),request.documentResourceTenantId(),request.documentId(),request.fileName(),code,request.mediaType(),request.contentLength(),request.contentDigest(),request.documentIntentDigest(),request.documentStoredAt()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->EvidenceAssociationIntent.capture(new EvidenceAssociationRequest(request.actionId(),request.applicationId(),request.partyId(),request.documentResourceTenantId(),request.documentId(),request.fileName(),request.documentTypeCode(),request.mediaType(),2097153,request.contentDigest(),request.documentIntentDigest(),request.documentStoredAt()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void actorAndReferenceDiagnosticsDoNotExposeCustody(){assertThat(EvidenceAssociationIntent.capture(request).toString()).doesNotContain(request.partyId().toString(),request.documentId().toString(),request.contentDigest());}
}
