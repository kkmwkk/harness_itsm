package com.nkia.itg.dashboard.service;

import com.nkia.itg.dashboard.dto.AdminStats;
import com.nkia.itg.dashboard.dto.CountByKey;
import com.nkia.itg.dashboard.dto.DashboardSummary;
import com.nkia.itg.dashboard.dto.MyWorkflowStep;
import com.nkia.itg.dashboard.dto.RecentActivity;
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
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.entity.PageMeta;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.system.menu.repository.MenuRepository;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 대시보드 집계 서비스. 기존 ticket·asset·workflow·meta 데이터를 읽어 현재 사용자 기준 요약을 만든다.
 *
 * <p>집계는 단일 인스턴스·MVP 규모의 샘플 데이터를 가정해 in-memory 스트림으로 수행한다 — 도메인
 * 전반에 group-by 쿼리를 흩뿌리지 않고 한 곳에서 가공해 단위 테스트(Mockito)로 검증 가능하게 한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final int RECENT_LIMIT = 8;
    private static final int TREND_WEEKS = 8;

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final AssetLifecycleEventRepository lifecycleEventRepository;
    private final WorkflowInstanceStepRepository stepRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final UserRepository userRepository;
    private final MenuRepository menuRepository;
    private final MetaRepository metaRepository;

    @Transactional(readOnly = true)
    public DashboardSummary summary(String username, Set<String> roles) {
        Set<String> safeRoles = roles == null ? Set.of() : roles;
        LocalDateTime now = LocalDateTime.now();

        List<Ticket> tickets = ticketRepository.findAll();
        long openTickets = tickets.stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED)
                .count();
        long myOpenTickets = tickets.stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED)
                .filter(t -> username != null && username.equals(t.getAssigneeId()))
                .count();
        List<CountByKey> ticketsByPriority = countByFixedKeys(
                tickets, t -> t.getPriority().name(), enumNames(Priority.values()));
        List<CountByKey> ticketsByStatus = countByFixedKeys(
                tickets, t -> t.getStatus().name(), enumNames(TicketStatus.values()));

        long totalAssets = assetRepository.count();
        List<Asset> assets = assetRepository.findAll();
        List<CountByKey> assetsByCategory = countByDiscoveredKeys(assets, this::categoryKey);

        List<AssetLifecycleEvent> events = lifecycleEventRepository.findAll();
        List<Long> lifecycleTrend = weeklyTrend(events, now.toLocalDate(), TREND_WEEKS);

        List<WorkflowInstanceStep> openSteps = stepRepository.findByCompletedAtIsNull();
        long slaBreachCount = openSteps.stream().filter(s -> s.isOverdue(now)).count();
        List<MyWorkflowStep> myWorkflowSteps = myWorkflowSteps(openSteps, username, safeRoles, now);

        List<RecentActivity> recentActivities = recentActivities(tickets, events);

        AdminStats adminStats = safeRoles.contains(ROLE_ADMIN) ? adminStats() : null;

        return new DashboardSummary(
                openTickets, totalAssets, slaBreachCount, myOpenTickets,
                ticketsByPriority, ticketsByStatus, assetsByCategory,
                lifecycleTrend, recentActivities, myWorkflowSteps, adminStats);
    }

    private List<MyWorkflowStep> myWorkflowSteps(
            List<WorkflowInstanceStep> openSteps, String username, Set<String> roles, LocalDateTime now) {
        Long myUserId = username == null
                ? null
                : userRepository.findByUsername(username).map(UserAccount::getId).orElse(null);

        List<WorkflowInstanceStep> mine = openSteps.stream()
                .filter(s -> isMine(s, myUserId, roles))
                .sorted(Comparator.comparing(WorkflowInstanceStep::getStartedAt))
                .toList();
        if (mine.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> instanceToTicket = instanceRepository
                .findAllById(mine.stream().map(WorkflowInstanceStep::getInstanceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(WorkflowInstance::getId, WorkflowInstance::getTicketId));

        return mine.stream()
                .map(s -> new MyWorkflowStep(
                        s.getInstanceId(),
                        instanceToTicket.get(s.getInstanceId()),
                        s.getStepIndex(),
                        s.getStepName(),
                        s.getAssigneeRole(),
                        s.getStartedAt(),
                        s.getSlaDueAt(),
                        s.isOverdue(now)))
                .toList();
    }

    private boolean isMine(WorkflowInstanceStep step, Long myUserId, Set<String> roles) {
        if (myUserId != null && myUserId.equals(step.getAssignedToUserId())) {
            return true;
        }
        return step.getAssigneeRole() != null && roles.contains(step.getAssigneeRole());
    }

    private List<RecentActivity> recentActivities(List<Ticket> tickets, List<AssetLifecycleEvent> events) {
        List<RecentActivity> all = new ArrayList<>();
        for (Ticket t : tickets) {
            all.add(new RecentActivity(
                    "TICKET",
                    t.getTitle(),
                    "ITSM · " + (t.getTicketNo() == null ? "신규" : t.getTicketNo()),
                    t.getCreatedAt()));
        }
        for (AssetLifecycleEvent e : events) {
            all.add(new RecentActivity(
                    "ASSET",
                    eventLabel(e.getEventType().name()) + " · 자산 #" + e.getAssetId(),
                    "ITAM · " + e.getEventDate(),
                    e.getEventDate().atStartOfDay()));
        }
        return all.stream()
                .filter(a -> a.at() != null)
                .sorted(Comparator.comparing(RecentActivity::at).reversed())
                .limit(RECENT_LIMIT)
                .toList();
    }

    private AdminStats adminStats() {
        long metaGroups = metaRepository.findAll().stream()
                .filter(p -> p.getMetaStatus() == MetaStatus.PUBLISHED && p.isActive())
                .map(PageMeta::getGroupId)
                .distinct()
                .count();
        return new AdminStats(userRepository.count(), menuRepository.count(), metaGroups);
    }

    /** 분류 키 — categoryCode 우선, 없으면 category, 둘 다 없으면 '미분류'. */
    private String categoryKey(Asset a) {
        if (a.getCategoryCode() != null && !a.getCategoryCode().isBlank()) {
            return a.getCategoryCode();
        }
        if (a.getCategory() != null && !a.getCategory().isBlank()) {
            return a.getCategory();
        }
        return "미분류";
    }

    private static String eventLabel(String eventType) {
        return switch (eventType) {
            case "ACQUIRED" -> "취득";
            case "TRANSFERRED" -> "이관";
            case "REPAIRED" -> "수리";
            case "DISPOSED" -> "폐기";
            case "RENEWED" -> "갱신";
            default -> eventType;
        };
    }

    private static List<String> enumNames(Enum<?>[] values) {
        List<String> names = new ArrayList<>(values.length);
        for (Enum<?> v : values) {
            names.add(v.name());
        }
        return names;
    }

    /** 고정 키 집합으로 집계 — 0건 키도 빠짐없이 포함(차트가 전체 범주를 표시). */
    private static <T> List<CountByKey> countByFixedKeys(
            List<T> items, Function<T, String> keyFn, List<String> keys) {
        Map<String, Long> counts = items.stream()
                .collect(Collectors.groupingBy(keyFn, Collectors.counting()));
        return keys.stream()
                .map(k -> new CountByKey(k, counts.getOrDefault(k, 0L)))
                .toList();
    }

    /** 데이터에서 발견된 키로 집계 — 건수 내림차순. */
    private static <T> List<CountByKey> countByDiscoveredKeys(List<T> items, Function<T, String> keyFn) {
        return items.stream()
                .collect(Collectors.groupingBy(keyFn, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new CountByKey(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CountByKey::count).reversed()
                        .thenComparing(CountByKey::key))
                .toList();
    }

    /** 최근 weeks 주 버킷별 이벤트 수. 마지막 원소 = 이번 주. */
    private static List<Long> weeklyTrend(List<AssetLifecycleEvent> events, LocalDate today, int weeks) {
        long[] buckets = new long[weeks];
        for (AssetLifecycleEvent e : events) {
            if (e.getEventDate() == null) {
                continue;
            }
            long weeksAgo = ChronoUnit.WEEKS.between(e.getEventDate(), today);
            if (weeksAgo >= 0 && weeksAgo < weeks) {
                buckets[weeks - 1 - (int) weeksAgo]++;
            }
        }
        List<Long> trend = new ArrayList<>(weeks);
        for (long b : buckets) {
            trend.add(b);
        }
        return trend;
    }
}
