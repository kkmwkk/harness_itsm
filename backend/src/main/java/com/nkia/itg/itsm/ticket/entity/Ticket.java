package com.nkia.itg.itsm.ticket.entity;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ticket")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 표시용 티켓 번호 (ITSM-{id5} 패턴). 컬럼은 nullable — IDENTITY 전략은 save() 시점에
     * INSERT 를 즉시 실행하므로, INSERT 시점에는 잠시 null 인 채로 들어갔다가 같은 트랜잭션
     * 안에서 assignTicketNo() 호출 + dirty checking 으로 update 된다. UNIQUE 제약은 NULL
     * 허용. 트랜잭션 외부에서 관찰될 때는 항상 채워져 있다.
     */
    @Column(name = "ticket_no", length = 32)
    private String ticketNo;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10, nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "assignee_id", length = 60)
    private String assigneeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 요청 유형 코드 (TicketRequestType.code soft FK). v2.1 도메인 깊이. */
    @Column(name = "request_type_code", length = 40)
    private String requestTypeCode;

    /** 연결된 워크플로우 인스턴스 id (WorkflowInstance.id soft FK). v2.1. */
    @Column(name = "workflow_instance_id")
    private Long workflowInstanceId;

    /** 요청자 사용자 id (User.id soft FK). assignee 와 별개. v2.1. */
    @Column(name = "requester_user_id")
    private Long requesterUserId;

    /** 등록 당시 폼 메타 id (이력 복원 — ITAM 패턴 적용). v2.1. */
    @Column(name = "page_meta_id_at_registration", length = 100)
    private String pageMetaIdAtRegistration;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = TicketStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상태 전이. 허용 매트릭스:
     *   OPEN         → IN_PROGRESS / RESOLVED / CLOSED
     *   IN_PROGRESS  → RESOLVED / CLOSED
     *   RESOLVED     → CLOSED / IN_PROGRESS
     *   CLOSED       → 전이 불가 (IllegalStateException → 400)
     *
     * CLOSED 전이 시 closedAt = now.
     * CLOSED 에서 재오픈 거부.
     */
    public void changeStatus(TicketStatus next) {
        if (this.status == TicketStatus.CLOSED) {
            throw new IllegalStateException(
                "CLOSED 티켓은 상태를 변경할 수 없습니다. (티켓: " + this.ticketNo + ")");
        }
        if (this.status == next) {
            return;  // no-op
        }
        final boolean allowed = switch (this.status) {
            case OPEN        -> EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED).contains(next);
            case IN_PROGRESS -> EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED).contains(next);
            case RESOLVED    -> EnumSet.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS).contains(next);
            case CLOSED      -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                "허용되지 않은 상태 전이: " + this.status + " → " + next);
        }
        this.status = next;
        if (next == TicketStatus.CLOSED) {
            this.closedAt = LocalDateTime.now();
        }
    }

    public void changePriority(Priority next) {
        if (this.status == TicketStatus.CLOSED) {
            throw new IllegalStateException(
                "CLOSED 티켓은 우선순위를 변경할 수 없습니다.");
        }
        this.priority = next;
    }

    public void assign(String assigneeId) {
        if (this.status == TicketStatus.CLOSED) {
            throw new IllegalStateException(
                "CLOSED 티켓은 담당자 할당이 불가합니다.");
        }
        this.assigneeId = assigneeId;
    }

    /** 생성된 워크플로우 인스턴스 id 연결 (한 번만). */
    public void linkWorkflowInstance(Long workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    /** 워크플로우 종결 시 티켓을 CLOSED 로 전환 (이미 CLOSED 면 no-op). */
    public void closeByWorkflow() {
        if (this.status != TicketStatus.CLOSED) {
            changeStatus(TicketStatus.CLOSED);
        }
    }

    /** ticket_no 한 번만 부여 (이미 set 되어 있으면 IllegalStateException). */
    public void assignTicketNo(String ticketNo) {
        if (this.ticketNo != null && !this.ticketNo.isBlank()) {
            throw new IllegalStateException("ticket_no 는 이미 부여되었습니다: " + this.ticketNo);
        }
        this.ticketNo = ticketNo;
    }

    public void updateContent(String title, String content, String category) {
        if (this.status == TicketStatus.CLOSED) {
            throw new IllegalStateException(
                "CLOSED 티켓은 내용을 수정할 수 없습니다.");
        }
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (category != null) {
            this.category = category;
        }
    }
}
