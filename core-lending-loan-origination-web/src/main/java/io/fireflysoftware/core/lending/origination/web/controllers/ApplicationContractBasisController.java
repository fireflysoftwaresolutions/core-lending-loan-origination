/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.controllers;
import io.fireflysoftware.core.lending.origination.core.contractbasis.*;
import io.fireflysoftware.core.lending.origination.interfaces.contractbasis.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/loan-application-contract-basis")
@SecurityRequirement(name="lendingIntakeBearer")
public class ApplicationContractBasisController {
    private final ObjectProvider<ApplicationContractBasisAdmission> authorities;private final ObjectProvider<ApplicationContractBasisReader> readers;
    public ApplicationContractBasisController(ObjectProvider<ApplicationContractBasisAdmission> authorities,ObjectProvider<ApplicationContractBasisReader> readers){this.authorities=authorities;this.readers=readers;}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeInitialPrimaryLoanContractBasis",summary="Read actual current primary ownership and original persisted managed Loan terms, without a business write")
    @ApiResponses({@ApiResponse(responseCode="200",description="Current ownership and canonical original intake verified",content=@Content(schema=@Schema(implementation=ApplicationContractBasis.class))),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current exact party, tenant or application authority refused",content=@Content),
        @ApiResponse(responseCode="503",description="OBSERVATION_UNAVAILABLE; no contract or business mutation dispatched",content=@Content(schema=@Schema(implementation=ApplicationContractBasisError.class)))})
    public Mono<ResponseEntity<ApplicationContractBasis>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return Mono.defer(()->{
        try{
            var authority=authorities.getIfUnique();var reader=readers.getIfUnique();if(authority==null||reader==null)return Mono.error(new ApplicationContractBasisFailure(actionId));
            return authority.invoke(applicationId,actionId,reader::observe).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value));
        }catch(RuntimeException setup){return Mono.error(new ApplicationContractBasisFailure(actionId));}
    }).onErrorMap(error->{if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return error instanceof ApplicationContractBasisFailure?error:new ApplicationContractBasisFailure(actionId);});}
    @ExceptionHandler(ApplicationContractBasisFailure.class) public ResponseEntity<ApplicationContractBasisError> unavailable(ApplicationContractBasisFailure error){return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new ApplicationContractBasisError(error.actionId(),error.getCode(),"OBSERVATION_UNAVAILABLE","SAME_ACTION_OBSERVE"));}
}
