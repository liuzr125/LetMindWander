package com.zhixing.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.service.ReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {
  @Test
  void legacyAndLivenessRemainAvailableWithoutDependencyChecks() throws Exception {
    ReadinessService service = mock(ReadinessService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.module").value("F01"))
        .andExpect(jsonPath("$.modules[1]").value("F02"));
    mvc.perform(get("/api/health/live"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("{\"status\":\"UP\"}", true));
    verifyNoInteractions(service);
  }

  @Test
  void readinessUses503ForUnavailabilityAndReturnsNoInternalDetails() throws Exception {
    ReadinessService service = mock(ReadinessService.class);
    when(service.ready()).thenReturn(false, true);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
    mvc.perform(get("/api/health/ready"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    mvc.perform(get("/api/health/ready"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }
}
