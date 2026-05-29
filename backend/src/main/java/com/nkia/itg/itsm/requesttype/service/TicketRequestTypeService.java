package com.nkia.itg.itsm.requesttype.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeCreateRequest;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeResponse;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeUpdateRequest;
import com.nkia.itg.itsm.requesttype.entity.TicketRequestType;
import com.nkia.itg.itsm.requesttype.repository.TicketRequestTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 유형 CRUD. code 가 PK 이며 불변. 물리 삭제는 제공하지 않고 active=false 로 비활성화한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TicketRequestTypeService {

    private final TicketRequestTypeRepository requestTypeRepository;

    /** 활성 요청 유형 목록 (사용자 화면 노출용). */
    @Transactional(readOnly = true)
    public List<TicketRequestTypeResponse> findActive() {
        return requestTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(TicketRequestTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketRequestTypeResponse getByCode(String code) {
        return TicketRequestTypeResponse.from(loadOrThrow(code));
    }

    public TicketRequestTypeResponse create(TicketRequestTypeCreateRequest req) {
        if (requestTypeRepository.existsById(req.code())) {
            throw new ITGException("REQUEST_TYPE_DUPLICATE",
                    "이미 존재하는 요청 유형 코드입니다: " + req.code());
        }
        TicketRequestType saved = requestTypeRepository.save(TicketRequestType.builder()
                .code(req.code())
                .label(req.label())
                .formMetaGroupId(req.formMetaGroupId())
                .defaultWorkflowCode(req.defaultWorkflowCode())
                .slaMinutesDefault(req.slaMinutesDefault())
                .active(true)
                .build());
        return TicketRequestTypeResponse.from(saved);
    }

    public TicketRequestTypeResponse update(String code, TicketRequestTypeUpdateRequest req) {
        TicketRequestType entity = loadOrThrow(code);
        entity.update(req.label(), req.formMetaGroupId(),
                req.defaultWorkflowCode(), req.slaMinutesDefault(), req.active());
        return TicketRequestTypeResponse.from(entity);
    }

    private TicketRequestType loadOrThrow(String code) {
        return requestTypeRepository.findById(code)
                .orElseThrow(() -> new ITGException("REQUEST_TYPE_NOT_FOUND",
                        "요청 유형을 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }
}
