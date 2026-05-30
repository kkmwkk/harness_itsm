package com.nkia.itg;

import com.nkia.itg.itam.asset.repository.AssetLifecycleEventRepository;
import com.nkia.itg.itam.asset.repository.AssetRepository;
import com.nkia.itg.itam.category.repository.AssetCategoryRepository;
import com.nkia.itg.itsm.requesttype.repository.TicketRequestTypeRepository;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.pms.project.repository.ProjectRepository;
import com.nkia.itg.pms.project.repository.TaskRepository;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import com.nkia.itg.system.menu.repository.MenuRepository;
import com.nkia.itg.system.notification.repository.NotificationRepository;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import com.nkia.itg.system.role.repository.RoleRepository;
import com.nkia.itg.system.user.repository.UserRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowDefinitionRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class ApplicationContextLoadTest {

    @MockitoBean
    private MetaRepository metaRepository;

    @MockitoBean
    private TicketRepository ticketRepository;

    @MockitoBean
    private AssetRepository assetRepository;

    @MockitoBean
    private AssetCategoryRepository assetCategoryRepository;

    @MockitoBean
    private AssetLifecycleEventRepository assetLifecycleEventRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @MockitoBean
    private MenuRepository menuRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @MockitoBean
    private WorkflowInstanceRepository workflowInstanceRepository;

    @MockitoBean
    private WorkflowInstanceStepRepository workflowInstanceStepRepository;

    @MockitoBean
    private TicketRequestTypeRepository ticketRequestTypeRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private TaskRepository taskRepository;

    @Test
    void contextLoads() {}
}
