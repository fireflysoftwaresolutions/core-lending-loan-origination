/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.openapi;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ManagedApplicationEvidenceProducerCoverageIT{
    @Test void completeOriginalProducerRetainsAll112OperationsAndPublishesTwoTypedOwnerOperations()throws Exception{
        var mapper=Yaml.mapper().copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);var before=mapper.readTree(read("lending.evidence.producer.original"));var after=mapper.readTree(read("lending.evidence.producer.candidate"));var old=operations(before);var actual=operations(after);assertThat(old).hasSize(112);
        var expected=new HashSet<>(old);String path="/api/v1/managed-application-evidence/applications/{applicationId}/actions/{actionId}";assertThat(expected.add(List.of(path,"post","attachManagedApplicationEvidence"))).isTrue();assertThat(expected.add(List.of(path,"get","observeManagedApplicationEvidence"))).isTrue();assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        before.path("paths").properties().forEach(entry->assertThat(after.path("paths").path(entry.getKey())).isEqualTo(entry.getValue()));
        before.path("components").path("schemas").properties().forEach(entry->assertThat(after.path("components").path("schemas").path(entry.getKey())).isEqualTo(entry.getValue()));
        var attach=after.path("paths").path(path).path("post");var observe=after.path("paths").path(path).path("get");
        for(String status:List.of("200","201"))assertThat(attach.path("responses").path(status).path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceAssociationReceipt");
        for(String status:List.of("409","503"))assertThat(attach.path("responses").path(status).path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceAssociationError");
        assertThat(observe.path("responses").has("204")).isTrue();assertThat(observe.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceAssociationReceipt");
        for(JsonNode operation:List.of(attach,observe))assertThat(operation.path("security").findValues("lendingIntakeBearer")).hasSize(1);
        for(JsonNode parameter:attach.path("parameters"))assertThat(parameter.path("name").asText()).isNotEqualToIgnoringCase("X-Idempotency-Key");
        assertThat(after.path("components").path("schemas").path("EvidenceAssociationRequest").path("properties").path("fileName").path("maxLength").asInt()).isEqualTo(200);
        var schemas=after.path("components").path("schemas");for(String type:List.of("EvidenceAssociationRequest","EvidenceAssociationReceipt"))assertThat(schemas.path(type).path("properties").path("documentStoredAt").path("$ref").asText()).isEqualTo("#/components/schemas/FireflyInstant");
        assertThat(schemas.path("EvidenceAssociationReceipt").path("properties").path("attachedAt").path("$ref").asText()).isEqualTo("#/components/schemas/FireflyInstant");assertThat(schemas.path("FireflyInstant").path("type").asText()).isEqualTo("string");assertThat(schemas.path("FireflyInstant").path("format").asText()).isEqualTo("date-time");
        Files.writeString(Path.of(System.getProperty("lending.evidence.producer.coverage")),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("originalOperations",old,"producedOperations",actual,"nativeYamlSemanticComparison",true,"allOriginalPathAndSchemaNodesPreserved",true))+"\n");
    }
    static Set<List<String>> operations(JsonNode root){var result=new TreeSet<List<String>>(Comparator.comparing(Object::toString));Set<String> verbs=Set.of("get","post","put","patch","delete","head","options","trace");assertThat(root.path("openapi").asText()).startsWith("3.");root.path("paths").properties().forEach(path->path.getValue().properties().forEach(op->{if(verbs.contains(op.getKey())){String id=op.getValue().path("operationId").asText();assertThat(id).isNotBlank();assertThat(result.add(List.of(path.getKey(),op.getKey(),id))).isTrue();}}));return result;}
    static byte[] read(String property)throws Exception{var path=Path.of(Objects.requireNonNull(System.getProperty(property)));assertThat(path).isAbsolute();assertThat(Files.isRegularFile(path)&&!Files.isSymbolicLink(path)).isTrue();byte[] bytes=Files.readAllBytes(path);assertThat(bytes.length).isBetween(1,2097152);return bytes;}
}
