package com.nkia.itg.itsm.ticket.repository;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNo(String ticketNo);

    /** 페이지 + 정렬. status·priority·assigneeId 필터 옵션 (null 이면 무시). */
    @Query("""
            select t from Ticket t
             where (:status     is null or t.status     = :status)
               and (:priority   is null or t.priority   = :priority)
               and (:assigneeId is null or t.assigneeId = :assigneeId)
            """)
    Page<Ticket> search(
            @Param("status")     TicketStatus status,
            @Param("priority")   Priority     priority,
            @Param("assigneeId") String       assigneeId,
            Pageable             pageable
    );
}
