/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.controllers;
import io.fireflysoftware.core.lending.origination.core.intake.DraftIntakeService;
import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/loan-application-intakes")
@Tag(name="DraftLoanIntakes")
@SecurityScheme(name="lendingIntakeBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@SecurityRequirement(name="lendingIntakeBearer")
public final class DraftIntakeController {
    private final DraftIntakeService service;
    public DraftIntakeController(DraftIntakeService service){this.service=service;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId="createDraftLoanIntake",summary="Create or replay an authorized DRAFT loan intake",
        description="Preserves exact retained simulation terms. The receipt contains the initial DRAFT/PENDING_DOCUMENTS status, not an offer, contract, approval, or disbursement.")
    public Mono<ResponseEntity<DraftIntakeReceipt>> create(@Valid @RequestBody DraftIntakeRequest request){
        return service.create(request).map(value->ResponseEntity.status(value.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(value));
    }
}
