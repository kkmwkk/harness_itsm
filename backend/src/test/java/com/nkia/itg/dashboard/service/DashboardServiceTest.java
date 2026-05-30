package com.nkia.itg.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.nkia.itg.dashboard.dto.CountByKey;
import com.nkia.itg.dashboard.dto.DashboardSummary;
import com.nkia.itg.itam.asset.domain.AssetLifecycleEventType;
import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.entity.Asset;
import com.nkia.itg.itam.asset.entity.AssetLifecycleEvent;
import com.nkia.itg.itam.asset.repository.AssetLifecycleEventRepository;
import com.nkia.itg.itam.asset.repository.AssetRepository;
import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstance;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceStepRepository;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.system.menu.repository.MenuRepository;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService 단위 테스트 (Mockito)")
class DashboardServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetLifecycleEventRepository lifecycleEventRepository;
    @Mock private WorkflowInstanceStepRepository stepRepository;
    @Mock private WorkflowInstanceRepository instanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private MetaRepository metaRepository;

    private DashboardService service() {
        return new DashboardService(
                ticketRepository, assetRepository, lifecycleEventRepository,
                stepRepository, instanceRepository, userRepository, menuRepository, metaRepository);
    }

    private Ticket ticket(Long id, TicketStatus status, Priority priority, String assigneeId) {
        return Ticket.builder()
                .id(id)
                .ticketNo("ITSM-%05d".formatted(id))
                .title("샘플 티켓 " + id)
                .priority(priority)
                .status(status)
                .assigneeId(assigneeId)
                .createdAt(LocalDateTime.now().minusHours(id))
                .build();
    }

    private Asset asset(Long id, String categoryCode) {
        return Asset.builder()
                .id(id)
                .name("샘플 자산 " + id)
                .assetType(AssetType.HARDWARE)
                .status(AssetStatus.ACTIVE)
                .categoryCode(categoryCode)
                .pageMetaIdAtRegistration("itg-asset-v1-1")
                .build();
    }

    private WorkflowInstanceStep openStep(Long id, Long instanceId, String role, Long userId, LocalDateTime due) {
        return WorkflowInstanceStep.builder()
                .id(id)
                .instanceId(instanceId)
                .stepIndex(1)
                .stepName("1차 검토")
                .assigneeRole(role)
                .assignedToUserId(userId)
                .startedAt(LocalDateTime.now().minusHours(2))
                .slaDueAt(due)
                .build();
    }

    @Test
    @DisplayName("KPI·분포 집계 — openTickets/myOpenTickets·우선순위·상태·분류")
    void KPI_분포_집계() {
        given(ticketRepository.findAll()).willReturn(List.of(
                ticket(1L, TicketStatus.OPEN, Priority.HIGH, "it-support"),
                ticket(2L, TicketStatus.IN_PROGRESS, Priority.HIGH, "it-support"),
                ticket(3L, TicketStatus.CLOSED, Priority.LOW, "it-support")
        ));
        given(assetRepository.count()).willReturn(127L);
        given(assetRepository.findAll()).willReturn(List.of(
                asset(1L, "HW_LAPTOP"), asset(2L, "HW_LAPTOP"), asset(3L, "SW_LICENSE")));
        given(lifecycleEventRepository.findAll()).willReturn(List.of());
        given(stepRepository.findByCompletedAtIsNull()).willReturn(List.of());

        DashboardSummary s = service().summary("it-support", Set.of("ROLE_IT_SUPPORT"));

        assertThat(s.openTickets()).isEqualTo(2);          // CLOSED 제외
        assertThat(s.myOpenTickets()).isEqualTo(2);        // it-support 의 열린 티켓
        assertThat(s.totalAssets()).isEqualTo(127);
        // 우선순위: 고정 키 4종 모두 포함(0건 포함)
        assertThat(s.ticketsByPriority()).hasSize(4);
        assertThat(byKey(s.ticketsByPriority(), "HIGH")).isEqualTo(2);
        assertThat(byKey(s.ticketsByPriority(), "CRITICAL")).isZero();
        // 상태: OPEN/IN_PROGRESS/RESOLVED/CLOSED
        assertThat(byKey(s.ticketsByStatus(), "CLOSED")).isEqualTo(1);
        // 분류: 발견된 키만, 건수 내림차순
        assertThat(s.assetsByCategory().get(0).key()).isEqualTo("HW_LAPTOP");
        assertThat(s.assetsByCategory().get(0).count()).isEqualTo(2);
        // lifecycleTrend 는 항상 8 버킷
        assertThat(s.lifecycleTrend()).hasSize(8);
        // 비관리자는 adminStats null
        assertThat(s.adminStats()).isNull();
    }

    @Test
    @DisplayName("SLA 초과 단계 카운트 + 내 워크플로우 단계(역할 매칭)")
    void SLA_초과_및_내_워크플로우_단계() {
        given(ticketRepository.findAll()).willReturn(List.of());
        given(assetRepository.count()).willReturn(0L);
        given(assetRepository.findAll()).willReturn(List.of());
        given(lifecycleEventRepository.findAll()).willReturn(List.of());
        LocalDateTime now = LocalDateTime.now();
        WorkflowInstanceStep overdue = openStep(10L, 100L, "ROLE_TEAM_LEAD", null, now.minusHours(1));
        WorkflowInstanceStep future = openStep(11L, 101L, "ROLE_IT_SUPPORT", null, now.plusHours(5));
        given(stepRepository.findByCompletedAtIsNull()).willReturn(List.of(overdue, future));
        given(userRepository.findByUsername("team-lead")).willReturn(Optional.empty());
        given(instanceRepository.findAllById(List.of(100L))).willReturn(List.of(
                WorkflowInstance.builder().id(100L).workflowDefCode("WF").ticketId(55L).build()));

        DashboardSummary s = service().summary("team-lead", Set.of("ROLE_TEAM_LEAD"));

        assertThat(s.slaBreachCount()).isEqualTo(1);       // overdue 만
        // 내 역할(ROLE_TEAM_LEAD) 단계만 노출
        assertThat(s.myWorkflowSteps()).hasSize(1);
        assertThat(s.myWorkflowSteps().get(0).instanceId()).isEqualTo(100L);
        assertThat(s.myWorkflowSteps().get(0).ticketId()).isEqualTo(55L);
        assertThat(s.myWorkflowSteps().get(0).overdue()).isTrue();
    }

    @Test
    @DisplayName("최근 활동 — 티켓·자산 이벤트를 시간순 통합")
    void 최근_활동_통합() {
        given(ticketRepository.findAll()).willReturn(List.of(
                ticket(1L, TicketStatus.OPEN, Priority.HIGH, "u")));
        given(assetRepository.count()).willReturn(0L);
        given(assetRepository.findAll()).willReturn(List.of());
        given(lifecycleEventRepository.findAll()).willReturn(List.of(
                AssetLifecycleEvent.builder()
                        .id(1L).assetId(7L)
                        .eventType(AssetLifecycleEventType.ACQUIRED)
                        .eventDate(LocalDate.now())
                        .createdAt(LocalDateTime.now())
                        .build()));
        given(stepRepository.findByCompletedAtIsNull()).willReturn(List.of());

        DashboardSummary s = service().summary("u", Set.of("ROLE_USER"));

        assertThat(s.recentActivities()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(s.recentActivities()).extracting(a -> a.type()).contains("TICKET", "ASSET");
    }

    @Test
    @DisplayName("ROLE_ADMIN 은 adminStats(사용자·메뉴·메타 그룹 수) 포함")
    void 관리자_adminStats_포함() {
        given(ticketRepository.findAll()).willReturn(List.of());
        given(assetRepository.count()).willReturn(0L);
        given(assetRepository.findAll()).willReturn(List.of());
        given(lifecycleEventRepository.findAll()).willReturn(List.of());
        given(stepRepository.findByCompletedAtIsNull()).willReturn(List.of());
        given(userRepository.findByUsername("admin")).willReturn(
                Optional.of(UserAccount.builder().id(1L).username("admin").name("관리자").build()));
        given(userRepository.count()).willReturn(4L);
        given(menuRepository.count()).willReturn(12L);
        given(metaRepository.findAll()).willReturn(List.of());

        DashboardSummary s = service().summary("admin", Set.of("ROLE_ADMIN"));

        assertThat(s.adminStats()).isNotNull();
        assertThat(s.adminStats().userCount()).isEqualTo(4);
        assertThat(s.adminStats().menuCount()).isEqualTo(12);
        assertThat(s.adminStats().metaGroupCount()).isZero();
    }

    private static long byKey(List<CountByKey> list, String key) {
        return list.stream().filter(c -> c.key().equals(key)).map(CountByKey::count).findFirst().orElse(-1L);
    }
}
