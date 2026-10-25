package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.GlobalExceptionHandler;
import com.whut.training.service.TrainingDashboardService;
import com.whut.training.service.TrainingExcelExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrainingExportControllerTest {
    private final TrainingExcelExportService service = mock(TrainingExcelExportService.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        UserContext.clear();
        mvc = MockMvcBuilders.standaloneSetup(new AdminTrainingDashboardController(mock(TrainingDashboardService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    @Test void anonymousAndNormalUsersCannotTriggerAnExport() throws Exception {
        mvc.perform(post("/api/admin/training-dashboard/export").contentType("application/json")
                .content("{\"range\":\"WEEK\",\"includeDaily\":true}")).andExpect(status().isUnauthorized());
        UserContext.setCurrentUser(new User(7L, "member", null, "hash", UserRole.USER));
        mvc.perform(post("/api/admin/training-dashboard/export").contentType("application/json")
                .content("{\"range\":\"WEEK\",\"includeDaily\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void administratorsKeepTheXlsxDownloadHeadersAndContent() throws Exception {
        var request = new TrainingExportRequest("WEEK", true, false, false);
        byte[] body = {80, 75, 3, 4};
        when(service.export(request)).thenReturn(new TrainingExcelExportService.ExportResult("训练 data.xlsx", body));
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.SUPER_ADMIN}) {
            UserContext.setCurrentUser(new User(7L, "admin", null, "hash", role));
            mvc.perform(post("/api/admin/training-dashboard/export").contentType("application/json")
                            .content("{\"range\":\"WEEK\",\"includeDaily\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''%E8%AE%AD%E7%BB%83%20data.xlsx"))
                    .andExpect(header().longValue("Content-Length", 4))
                    .andExpect(content().bytes(body));
        }
    }
}
