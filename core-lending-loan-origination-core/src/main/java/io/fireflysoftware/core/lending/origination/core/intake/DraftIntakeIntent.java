/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.core.intake;

import io.fireflysoftware.core.lending.origination.interfaces.intake.DraftIntakeRequest;
import io.fireflysoftware.core.lending.origination.models.entities.Simulation;
import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

public record DraftIntakeIntent(UUID requestId, UUID simulationId, UUID productId, UUID partyId,
        BigDecimal amount, int term, String currency, String purpose) {
    public DraftIntakeIntent {
        for (UUID id : new UUID[]{requestId,simulationId,productId,partyId})
            if (id == null || id.equals(new UUID(0,0))) throw invalid();
        try { amount=Objects.requireNonNull(amount).setScale(2,RoundingMode.UNNECESSARY); }
        catch (RuntimeException error) { throw invalid(); }
        if (amount.signum()<=0 || amount.precision()>19 || term<1 || currency==null || !currency.matches("[A-Z]{3}")) throw invalid();
        if (purpose!=null) {
            if (purpose.indexOf(0)>=0 || purpose.codePointCount(0,purpose.length())>50) throw invalid();
            try { StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(purpose)); }
            catch (CharacterCodingException error) { throw invalid(); }
        }
    }
    public static DraftIntakeIntent capture(DraftIntakeRequest request) {
        if (request==null || request.term()==null) throw invalid();
        return new DraftIntakeIntent(request.requestId(),request.simulationId(),request.productId(),request.partyId(),
            request.requestedAmount(),request.term(),request.currency(),request.purpose());
    }
    public String digest() {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"FIREFLY_LENDING_DRAFT_INTAKE_1");
            for(UUID id:new UUID[]{requestId,simulationId,productId,partyId}) {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
            text(out,amount.toPlainString());out.writeInt(term);text(out,currency);
            out.writeBoolean(purpose!=null);if(purpose!=null)text(out,purpose);out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException|NoSuchAlgorithmException impossible) {throw new IllegalStateException("Canonical intake encoding unavailable",impossible);}
    }
    public void requireSimulation(Simulation row) {
        if(row==null || !simulationId.equals(row.getSimulationId()) || !productId.equals(row.getProductId())
            || !"PERSONAL_LOAN".equals(row.getProductType()) || row.getRequestedAmount()==null
            || amount.compareTo(row.getRequestedAmount())!=0 || !Objects.equals(term,row.getTerm())
            || !currency.equals(row.getCurrency()) || !Objects.equals(purpose,row.getPurpose())) throw invalid();
    }
    private static void text(DataOutputStream out,String text)throws IOException {var bytes=text.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact retained simulation terms and intake identity required");}
    @Override public String toString(){return "DraftIntakeIntent[redacted]";}
}
