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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/application-parties")
@RequiredArgsConstructor
@Tag(name = "ApplicationPartyQuery", description = "Cross-application queries over application-party rows")
public class ApplicationPartyQueryController {

    private final ApplicationPartyService service;

    @GetMapping("/by-party/{partyId}")
    @SecurityRequirement(name = "lendingIntakeBearer")
    @Operation(
            summary = "List application-party links for a party",
            description = "Returns every application-party row that references the given party, "
                    + "across any loan application. Backed by the ix_application_party_party_id index."
    )
    public Flux<ApplicationPartyDTO> findByPartyId(@PathVariable UUID partyId) {
        return service.findByPartyId(partyId);
    }
}
