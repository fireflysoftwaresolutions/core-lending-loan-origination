/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.controllers;
import io.fireflysoftware.core.lending.origination.core.evidence.*;
import io.fireflysoftware.core.lending.origination.interfaces.evidence.*;
import io.fireflysoftware.core.lending.origination.web.evidence.impl.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/managed-application-evidence")
@SecurityRequirement(name="lendingIntakeBearer")
public class ApplicationEvidenceController{
    private final ObjectProvider<AuthenticatedEvidenceAssociationAdmissionImpl> writeAuthorities;private final ObjectProvider<EvidenceAssociationStore> stores;
    private final ObjectProvider<AuthenticatedEvidenceAssociationReadAdmissionImpl> readAuthorities;private final ObjectProvider<EvidenceAssociationReader> readers;
    public ApplicationEvidenceController(ObjectProvider<AuthenticatedEvidenceAssociationAdmissionImpl> writeAuthorities,ObjectProvider<EvidenceAssociationStore> stores,ObjectProvider<AuthenticatedEvidenceAssociationReadAdmissionImpl> readAuthorities,ObjectProvider<EvidenceAssociationReader> readers){this.writeAuthorities=writeAuthorities;this.stores=stores;this.readAuthorities=readAuthorities;this.readers=readers;}
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="attachManagedApplicationEvidence",summary="Attach an exact verified Document reference under current signed application and party authority")
    @ApiResponses({@ApiResponse(responseCode="201",description="Original association committed with pending verification",content=@Content(schema=@Schema(implementation=EvidenceAssociationReceipt.class))),@ApiResponse(responseCode="200",description="Exact original association replayed",content=@Content(schema=@Schema(implementation=EvidenceAssociationReceipt.class))),
        @ApiResponse(responseCode="400",description="Invalid original reference or path binding",content={}),@ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original party authority refused",content={}),
        @ApiResponse(responseCode="409",description="Original action already has different reference terms",content=@Content(schema=@Schema(implementation=EvidenceAssociationError.class))),
        @ApiResponse(responseCode="503",description="REFUSED before dispatch or after proven rollback, OUTCOME_UNKNOWN, or COMMITTED_RESPONSE_UNAVAILABLE; recover same action",content=@Content(schema=@Schema(implementation=EvidenceAssociationError.class)))})
    public Mono<ResponseEntity<EvidenceAssociationReceipt>> attach(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody EvidenceAssociationRequest request){return Mono.defer(()->{
        if(request==null||!applicationId.equals(request.applicationId())||!actionId.equals(request.actionId()))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact original application/action required"));
        final AuthenticatedEvidenceAssociationAdmissionImpl authority;final EvidenceAssociationStore owner;
        try{authority=writeAuthorities.getIfUnique();owner=stores.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(new EvidenceAssociationFailure(actionId,EvidenceAssociationFailure.Outcome.REFUSED));}
        if(authority==null||owner==null)return Mono.error(new EvidenceAssociationFailure(actionId,EvidenceAssociationFailure.Outcome.REFUSED));
        return authority.invoke(request,owner).map(receipt->ResponseEntity.status(receipt.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(receipt))
            .onErrorMap(error->{if(error instanceof EvidenceAssociationFailure||error instanceof EvidenceAssociationConflict)return error;if(error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;return new EvidenceAssociationFailure(actionId,EvidenceAssociationFailure.Outcome.OUTCOME_UNKNOWN);});
    });}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeManagedApplicationEvidence",summary="Read the original immutable association or prove no association under the current managed application")
    @ApiResponses({@ApiResponse(responseCode="200",description="Existing exact association and canonical pair verified",content=@Content(schema=@Schema(implementation=EvidenceAssociationReceipt.class))),@ApiResponse(responseCode="204",description="Current managed application proved; no original association exists",content={}),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original party read authority refused",content={}),
        @ApiResponse(responseCode="503",description="OBSERVATION_UNAVAILABLE without dispatching a write",content=@Content(schema=@Schema(implementation=EvidenceAssociationError.class)))})
    public Mono<ResponseEntity<EvidenceAssociationReceipt>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return Mono.defer(()->{
        final AuthenticatedEvidenceAssociationReadAdmissionImpl authority;final EvidenceAssociationReader reader;
        try{authority=readAuthorities.getIfUnique();reader=readers.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(new EvidenceAssociationReadFailure(actionId));}
        if(authority==null||reader==null)return Mono.error(new EvidenceAssociationReadFailure(actionId));
        return authority.invoke(applicationId,actionId,reader::observe).map(found->found.map(receipt->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt)).orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()))
            .onErrorMap(error->{if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return error instanceof EvidenceAssociationReadFailure?error:new EvidenceAssociationReadFailure(actionId);});
    });}
    @ExceptionHandler(EvidenceAssociationFailure.class) public ResponseEntity<EvidenceAssociationError> unavailable(EvidenceAssociationFailure error){return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new EvidenceAssociationError(error.actionId(),error.getCode(),EvidenceAssociationError.Outcome.valueOf(error.outcome().name()),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
    @ExceptionHandler(EvidenceAssociationReadFailure.class) public ResponseEntity<EvidenceAssociationError> readUnavailable(EvidenceAssociationReadFailure error){return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new EvidenceAssociationError(error.actionId(),error.getCode(),EvidenceAssociationError.Outcome.OBSERVATION_UNAVAILABLE,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
    @ExceptionHandler(EvidenceAssociationConflict.class) public ResponseEntity<EvidenceAssociationError> conflict(EvidenceAssociationConflict error){return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new EvidenceAssociationError(error.actionId(),error.getCode(),EvidenceAssociationError.Outcome.CONFLICT,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}
