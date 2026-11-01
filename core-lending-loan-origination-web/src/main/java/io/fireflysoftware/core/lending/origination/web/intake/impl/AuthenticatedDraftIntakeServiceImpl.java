/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.lending.origination.web.intake.impl;
import io.fireflysoftware.core.lending.origination.web.intake.*;
import io.fireflysoftware.core.lending.origination.core.intake.*;
import io.fireflysoftware.core.lending.origination.interfaces.intake.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public final class AuthenticatedDraftIntakeServiceImpl implements DraftIntakeService {
    private final DraftIntakeStore store;
    private final ObjectProvider<AuthenticatedDraftIntakeAdmissionImpl> authority;
    public AuthenticatedDraftIntakeServiceImpl(DraftIntakeStore store,ObjectProvider<AuthenticatedDraftIntakeAdmissionImpl> authority){this.store=store;this.authority=authority;}
    @Override public Mono<DraftIntakeReceipt> create(DraftIntakeRequest request){return Mono.defer(()->{
        var current=authority.getIfUnique();if(current==null)return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Audited lending intake is unavailable"));
        return current.invoke(request,store);
    });}
}
