package com.zhixing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.common.*;
import com.zhixing.config.*;
import com.zhixing.controller.AdminArticleOptionsController;
import com.zhixing.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminArticleVoiceSettingsTest {
  private final AliyunTtsService tts = mock(AliyunTtsService.class);

  private MockMvc mvc(String menu) {
    AppProperties properties = new AppProperties();
    AdminAuthService auth = mock(AdminAuthService.class);
    AdminPrincipal principal =
        new AdminPrincipal("editor", "editor", "ADMIN", "管理员", Collections.singleton(menu));
    when(auth.require(eq("session"), anyString()))
        .thenAnswer(
            call -> {
              if (!principal.hasMenu(call.getArgument(1)))
                throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有权限");
              return principal;
            });
    return MockMvcBuilders.standaloneSetup(
            new AdminArticleOptionsController(
                mock(AdminContentService.class), mock(AdminVocabularyService.class), tts))
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilters(new AdminSessionFilter(auth, properties))
        .build();
  }

  @Test
  void articleRoleCanReadAndSaveVoiceWithoutAiOrParameterMenu() throws Exception {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("voice", "luca");
    config.put("voices", Collections.singletonList(Collections.singletonMap("code", "luca")));
    when(tts.articleVoiceSettings()).thenReturn(config);
    when(tts.articleAppKeyConfigured()).thenReturn(true);
    when(tts.saveArticleVoice("luca")).thenReturn(config);
    MockMvc mvc = mvc("articles");
    mvc.perform(get("/api/admin/content/articles/tts-status").header("X-Admin-Token", "session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.voice").value("luca"))
        .andExpect(jsonPath("$.appKeyConfigured").value(true))
        .andExpect(jsonPath("$.appKey").doesNotExist())
        .andExpect(jsonPath("$.nlsToken").doesNotExist());
    mvc.perform(
            put("/api/admin/content/articles/tts-settings")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voice\":\"luca\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.voice").value("luca"));
    verify(tts).saveArticleVoice("luca");
    verify(tts, never()).saveConfig(any());
  }

  @Test
  void unrelatedMenuCannotChangeArticleVoice() throws Exception {
    mvc("content")
        .perform(
            put("/api/admin/content/articles/tts-settings")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voice\":\"luca\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(tts);
  }
}
