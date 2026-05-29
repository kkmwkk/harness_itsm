package com.nkia.itg.itsm.requesttype.repository;

import com.nkia.itg.itsm.requesttype.entity.TicketRequestType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRequestTypeRepository extends JpaRepository<TicketRequestType, String> {

    /** 활성 요청 유형만 (사용자 화면 노출용). */
    List<TicketRequestType> findByActiveTrueOrderByCodeAsc();
}
