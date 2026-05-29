package com.nkia.itg.fixture;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;

public final class TicketFixture {

    private TicketFixture() {
    }

    public static Ticket.TicketBuilder baseBuilder() {
        return Ticket.builder()
                .ticketNo("ITSM-99999")
                .title("샘플 티켓")
                .content("샘플 본문")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .category("BUG")
                .assigneeId("assignee-sample-1");
    }

    public static Ticket open() {
        return baseBuilder().status(TicketStatus.OPEN).build();
    }

    public static Ticket inProgress() {
        return baseBuilder().status(TicketStatus.IN_PROGRESS).build();
    }

    public static Ticket resolved() {
        return baseBuilder().status(TicketStatus.RESOLVED).build();
    }

    /** OPEN → CLOSED 도메인 전이로 closedAt 까지 자동 set. */
    public static Ticket closed() {
        Ticket t = open();
        t.changeStatus(TicketStatus.CLOSED);
        return t;
    }
}
