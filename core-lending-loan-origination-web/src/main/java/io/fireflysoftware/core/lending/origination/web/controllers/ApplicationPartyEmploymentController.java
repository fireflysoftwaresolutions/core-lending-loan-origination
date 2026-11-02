/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.lending.origination.web.controllers;

import io.fireflysoftware.core.lending.origination.core.services.ApplicationPartyService;
import io.fireflysoftware.core.lending.origination.interfaces.dtos.ApplicationPartyDTO;
import io.fireflysoftware.core.lending.origination.interfaces.dtos.EmploymentDataPatchDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Endpoints for the BE-4 economic / employment data on application parties:
 *
 * <ul>
 *     <li>{@code PATCH /api/v1/application-parties/{id}/employment-data}
 *         &mdash; partial update of the 12 economic fields on a party row.</li>
 *     <li>{@code GET /api/v1/application-parties/by-application/{applicationId}/primary}
 *         &mdash; resolves the primary application party for a given loan application.</li>
 * </ul>
 *
 * <p>Kept in a dedicated controller (separate from {@link ApplicationPartyController})
 * so that the URL surface for these features lives under {@code /api/v1/application-parties}
 * — independent from the application-scoped CRUD that lives under
 * {@code /api/v1/loan-applications/{applicationId}/parties}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/application-parties")
@RequiredArgsConstructor
@Tag(name = "ApplicationPartyEmployment",
        description = "Economic / employment data on application parties (BE-4)")
public class ApplicationPartyEmploymentController {

    private final ApplicationPartyService service;

    @PatchMapping("/{id}/employment-data")
    @Operation(
            operationId = "updateApplicationPartyEmploymentData",
            summary = "Patch the economic / employment fields of an application party",
            description = "Applies a partial update to the 12 BE-4 economic fields. " +
                    "Null fields on the payload are ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application party updated",
                    content = @Content(schema = @Schema(implementation = ApplicationPartyDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid patch payload"),
            @ApiResponse(responseCode = "404", description = "Application party not found")
    })
    public Mono<ResponseEntity<ApplicationPartyDTO>> updateApplicationPartyEmploymentData(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EmploymentDataPatchDTO patch) {
        return service.updateEmploymentData(id, patch)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/by-application/{applicationId}/primary")
    @SecurityRequirement(name = "lendingIntakeBearer")
    @Operation(
            operationId = "findPrimaryApplicationParty",
            summary = "Find the primary application party for an application",
            description = "Returns the application party row whose is_primary flag is TRUE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Primary party found",
                    content = @Content(schema = @Schema(implementation = ApplicationPartyDTO.class))),
            @ApiResponse(responseCode = "404", description = "No primary party for the given application")
    })
    public Mono<ResponseEntity<ApplicationPartyDTO>> findPrimaryApplicationParty(
            @PathVariable("applicationId") UUID applicationId) {
        return service.findPrimaryByApplicationId(applicationId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
