/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.evidence;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.EvidenceAssociationRequest;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
/** Loan owner encoding only. Consumers carry its digest opaquely and never reproduce this protocol. */
public record EvidenceAssociationIntent(UUID actionId,UUID applicationId,UUID partyId,UUID documentResourceTenantId,
        UUID documentId,String fileName,String documentTypeCode,String mediaType,long contentLength,
        String contentDigest,String documentIntentDigest,Instant documentStoredAt){
    public EvidenceAssociationIntent{
        for(UUID value:new UUID[]{actionId,applicationId,partyId,documentResourceTenantId,documentId})if(value==null||value.equals(new UUID(0,0)))throw invalid();
        Objects.requireNonNull(documentStoredAt);
        if(contentLength<1||contentLength>2097152||!digest(contentDigest)||!digest(documentIntentDigest)
            ||fileName==null||fileName.isBlank()||fileName.length()>200||!fileName.equals(fileName.strip())||fileName.contains("/")||fileName.contains("\\")||Set.of(".","..").contains(fileName)
            ||documentTypeCode==null||!documentTypeCode.matches("[A-Z][A-Z0-9_]{0,49}")||mediaType==null||mediaType.length()>100||!mediaType.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))throw invalid();
        for(String text:new String[]{fileName,documentTypeCode,mediaType}){if(text.codePoints().anyMatch(Character::isISOControl))throw invalid();try{StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));}catch(CharacterCodingException malformed){throw invalid();}}
    }
    public static EvidenceAssociationIntent capture(EvidenceAssociationRequest value){if(value==null)throw invalid();return new EvidenceAssociationIntent(value.actionId(),value.applicationId(),value.partyId(),value.documentResourceTenantId(),value.documentId(),value.fileName(),value.documentTypeCode(),value.mediaType(),value.contentLength(),value.contentDigest(),value.documentIntentDigest(),value.documentStoredAt());}
    public String digest(){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);text(out,"FIREFLY_LOAN_EVIDENCE_ASSOCIATION_1");for(UUID value:new UUID[]{actionId,applicationId,partyId,documentResourceTenantId,documentId}){out.writeLong(value.getMostSignificantBits());out.writeLong(value.getLeastSignificantBits());}for(String value:new String[]{fileName,documentTypeCode,mediaType,contentDigest,documentIntentDigest})text(out,value);out.writeLong(contentLength);out.writeLong(documentStoredAt.getEpochSecond());out.writeInt(documentStoredAt.getNano());return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Owner evidence framing unavailable");}}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact bounded original document reference required");}
    @Override public String toString(){return "EvidenceAssociationIntent[redacted]";}
}
