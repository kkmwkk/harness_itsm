package com.nkia.itg.itsm.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ticket 도메인 메서드 단위 테스트 (상태 전이 매트릭스)")
class TicketTest {

    private Ticket ticketWith(TicketStatus status) {
        return Ticket.builder()
                .ticketNo("ITSM-00001")
                .title("샘플 티켓")
                .content("샘플 내용")
                .priority(Priority.MEDIUM)
                .status(status)
                .category("BUG")
                .assigneeId("assignee-sample-1")
                .build();
    }

    @Test
    @DisplayName("changeStatus — OPEN 에서 IN_PROGRESS 허용")
    void changeStatus_OPEN_에서_IN_PROGRESS_허용() {
        // given
        Ticket ticket = ticketWith(TicketStatus.OPEN);

        // when
        ticket.changeStatus(TicketStatus.IN_PROGRESS);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("changeStatus — OPEN 에서 RESOLVED 허용")
    void changeStatus_OPEN_에서_RESOLVED_허용() {
        // given
        Ticket ticket = ticketWith(TicketStatus.OPEN);

        // when
        ticket.changeStatus(TicketStatus.RESOLVED);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("changeStatus — OPEN 에서 CLOSED 허용 시 closedAt set")
    void changeStatus_OPEN_에서_CLOSED_허용_시_closedAt_set() {
        // given
        Ticket ticket = ticketWith(TicketStatus.OPEN);

        // when
        ticket.changeStatus(TicketStatus.CLOSED);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus — IN_PROGRESS 에서 OPEN 불허 (IllegalStateException)")
    void changeStatus_IN_PROGRESS_에서_OPEN_불허_IllegalStateException() {
        // given
        Ticket ticket = ticketWith(TicketStatus.IN_PROGRESS);

        // when & then
        assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.OPEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은 상태 전이");
    }

    @Test
    @DisplayName("changeStatus — IN_PROGRESS 에서 RESOLVED 허용")
    void changeStatus_IN_PROGRESS_에서_RESOLVED_허용() {
        // given
        Ticket ticket = ticketWith(TicketStatus.IN_PROGRESS);

        // when
        ticket.changeStatus(TicketStatus.RESOLVED);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    @DisplayName("changeStatus — RESOLVED 에서 IN_PROGRESS 허용 (재오픈)")
    void changeStatus_RESOLVED_에서_IN_PROGRESS_허용() {
        // given
        Ticket ticket = ticketWith(TicketStatus.RESOLVED);

        // when
        ticket.changeStatus(TicketStatus.IN_PROGRESS);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("changeStatus — CLOSED 에서 어떤 전이도 불허")
    void changeStatus_CLOSED_에서_어떤_전이도_불허() {
        // given
        Ticket ticket = ticketWith(TicketStatus.CLOSED);

        // when & then
        for (TicketStatus next : TicketStatus.values()) {
            assertThatThrownBy(() -> ticket.changeStatus(next))
                    .as("CLOSED → %s", next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLOSED 티켓은 상태를 변경할 수 없습니다");
        }
    }

    @Test
    @DisplayName("changeStatus — 같은 상태 재호출 시 no-op")
    void changeStatus_같은_상태_재호출_시_noop() {
        // given
        Ticket ticket = ticketWith(TicketStatus.IN_PROGRESS);

        // when
        ticket.changeStatus(TicketStatus.IN_PROGRESS);

        // then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("changePriority — CLOSED 불허")
    void changePriority_CLOSED_불허() {
        // given
        Ticket ticket = ticketWith(TicketStatus.CLOSED);

        // when & then
        assertThatThrownBy(() -> ticket.changePriority(Priority.HIGH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 우선순위를 변경할 수 없습니다");
    }

    @Test
    @DisplayName("assign — CLOSED 불허")
    void assign_CLOSED_불허() {
        // given
        Ticket ticket = ticketWith(TicketStatus.CLOSED);

        // when & then
        assertThatThrownBy(() -> ticket.assign("assignee-sample-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 담당자 할당이 불가합니다");
    }

    @Test
    @DisplayName("updateContent — CLOSED 불허")
    void updateContent_CLOSED_불허() {
        // given
        Ticket ticket = ticketWith(TicketStatus.CLOSED);

        // when & then
        assertThatThrownBy(() -> ticket.updateContent("새 제목", "새 내용", "REQ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 내용을 수정할 수 없습니다");
    }

    @Test
    @DisplayName("updateContent — 부분 업데이트, null 은 변경 없음")
    void updateContent_부분_업데이트_null_은_변경_없음() {
        // given
        Ticket ticket = ticketWith(TicketStatus.OPEN);

        // when — title 만 변경, content·category 는 null 로 유지
        ticket.updateContent("변경된 제목", null, null);

        // then
        assertThat(ticket.getTitle()).isEqualTo("변경된 제목");
        assertThat(ticket.getContent()).isEqualTo("샘플 내용");
        assertThat(ticket.getCategory()).isEqualTo("BUG");
    }
}
