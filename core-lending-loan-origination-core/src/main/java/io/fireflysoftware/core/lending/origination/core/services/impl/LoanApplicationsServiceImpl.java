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


package io.fireflysoftware.core.lending.origination.core.services.impl;

import io.fireflysoftware.core.lending.origination.core.intake.impl.GovernedDraftWriteErrorMapperImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.lending.origination.core.mappers.LoanApplicationMapper;
import io.fireflysoftware.core.lending.origination.core.services.LoanApplicationsService;
import io.fireflysoftware.core.lending.origination.interfaces.dtos.LoanApplicationDTO;
import io.fireflysoftware.core.lending.origination.models.entities.LoanApplication;
import io.fireflysoftware.core.lending.origination.models.repositories.LoanApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
public class LoanApplicationsServiceImpl implements LoanApplicationsService {

    @Autowired
    private LoanApplicationRepository repository;

    @Autowired
    private LoanApplicationMapper mapper;

    @Override
    public Mono<PaginationResponse<LoanApplicationDTO>> filterLoanApplications(FilterRequest<LoanApplicationDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        LoanApplication.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<LoanApplicationDTO>> listLoanApplications(PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findAllBy(pageable),
                () -> repository.count()
        );
    }

    @Override
    public Mono<LoanApplicationDTO> createLoanApplication(LoanApplicationDTO dto) {
        return Mono.just(dto)
                .map(loanAppDto -> {
                    // Auto-generate UUID for applicationNumber at service level
                    loanAppDto.setApplicationNumber(UUID.randomUUID());
                    return loanAppDto;
                })
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO).onErrorMap(GovernedDraftWriteErrorMapperImpl::map);
    }

    @Override
    public Mono<LoanApplicationDTO> getLoanApplication(UUID applicationId) {
        return repository.findById(applicationId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<LoanApplicationDTO> updateLoanApplication(UUID applicationId, LoanApplicationDTO dto) {
        return repository.findById(applicationId)
                .flatMap(existing -> {
                    mapper.updateEntityFromDto(dto, existing);
                    return repository.save(existing);
                })
                .map(mapper::toDTO).onErrorMap(GovernedDraftWriteErrorMapperImpl::map);
    }

    @Override
    public Mono<Void> deleteLoanApplication(UUID applicationId) {
        return repository.findById(applicationId)
                .flatMap(repository::delete).onErrorMap(GovernedDraftWriteErrorMapperImpl::map);
    }
}