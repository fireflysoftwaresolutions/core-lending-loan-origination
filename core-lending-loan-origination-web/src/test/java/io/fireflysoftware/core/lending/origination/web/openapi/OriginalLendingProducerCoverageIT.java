/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.openapi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OriginalLendingProducerCoverageIT {
    private static final Set<String> VERBS=Set.of("get","post","put","patch","delete","head","options","trace");
    private static final List<String> ADDITION=List.of("/api/v1/loan-application-intakes","post","createDraftLoanIntake");
    @Test void preservesEveryOriginalPathVerbAndOperationIdWithExactlyOneIntakeAddition() throws Exception {
        var mapper=Yaml.mapper().copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        var original=mapper.readTree(bounded("lending.producer.original"));
        var produced=mapper.readTree(bounded("lending.producer.candidate"));
        var before=operations(original);var after=operations(produced);
        assertThat(before).isNotEmpty().doesNotContain(ADDITION);
        var expected=new HashSet<>(before);assertThat(expected.add(ADDITION)).isTrue();
        assertThat(after).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(produced.path("components").path("schemas").path("DraftIntakeReceipt").path("properties")
            .path("intentDigest").path("type").asText()).isEqualTo("string");
        var operation=produced.path("paths").path(ADDITION.getFirst()).path("post");
        assertThat(operation.path("security").findValues("lendingIntakeBearer")).hasSize(1);
        var receipt=new LinkedHashMap<String,Object>();receipt.put("originalOperations",before);receipt.put("producedOperations",after);
        receipt.put("exactNewOperation",ADDITION);receipt.put("nativeYamlSemanticComparison",true);
        Files.writeString(Path.of(required("lending.producer.coverage")),new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter().writeValueAsString(receipt)+"\n");
    }
    private static Set<List<String>> operations(JsonNode document) {
        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("paths").isObject()).isTrue();var result=new TreeSet<List<String>>(Comparator.comparing(Object::toString));
        document.path("paths").properties().forEach(path->path.getValue().properties().forEach(operation->{
            if(VERBS.contains(operation.getKey())) {
                String id=operation.getValue().path("operationId").asText();assertThat(id).isNotBlank();
                assertThat(result.add(List.of(path.getKey(),operation.getKey(),id))).isTrue();
            }
        }));return result;
    }
    private static byte[] bounded(String property) throws Exception {
        var path=Path.of(required(property));assertThat(Files.size(path)).isBetween(1L,2097152L);return Files.readAllBytes(path);
    }
    private static String required(String property) {String value=System.getProperty(property);assertThat(value).isNotBlank();return value;}
}
