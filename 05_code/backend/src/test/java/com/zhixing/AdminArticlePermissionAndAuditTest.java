package com.zhixing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.common.*;
import com.zhixing.config.*;
import com.zhixing.controller.*;
import com.zhixing.model.*;
import com.zhixing.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminArticlePermissionAndAuditTest {
  private static final String ACTOR = "real-admin-account";
  private final AppProperties properties = new AppProperties();
  private final AdminContentService content = mock(AdminContentService.class);
  private final AdminVocabularyService books = mock(AdminVocabularyService.class);
  private final AliyunTtsService tts = mock(AliyunTtsService.class);

  private MockMvc mvc(String menu, Object... controllers) {
    AdminAuthService auth = mock(AdminAuthService.class);
    AdminPrincipal principal =
        new AdminPrincipal(ACTOR, "editor", "ADMIN", "管理员", Collections.singleton(menu));
    when(auth.require(eq("session"), anyString()))
        .thenAnswer(
            call -> {
              if (!principal.hasMenu(call.getArgument(1)))
                throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有权限");
              return principal;
            });
    return MockMvcBuilders.standaloneSetup(controllers)
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilters(new AdminSessionFilter(auth, properties))
        .build();
  }

  @Test
  void articleOnlyRoleCanLoadItsDependenciesWithoutOtherMenus() throws Exception {
    AdminVocabularyBookView book = new AdminVocabularyBookView();
    book.setBookId("book");
    book.setBookName("小学英语");
    book.setMemberCount(500);
    when(books.books()).thenReturn(Collections.singletonList(book));
    when(content.articleCoverage()).thenReturn(Collections.emptyList());
    when(content.articles(null, null, null, null, 1, 20))
        .thenReturn(new AdminTechnicalContentPageView());
    when(tts.articleAppKeyConfigured()).thenReturn(true);
    MockMvc mvc =
        mvc(
            "articles",
            new AdminArticleOptionsController(content, books, tts),
            new AdminContentController(content, properties, tts));
    mvc.perform(get("/api/admin/content/articles/options").header("X-Admin-Token", "session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books[0].bookName").value("小学英语"))
        .andExpect(jsonPath("$.books[0].memberCount").doesNotExist())
        .andExpect(jsonPath("$.technicalTopics").doesNotExist());
    mvc.perform(get("/api/admin/content/articles").header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/content/articles/tts-status").header("X-Admin-Token", "session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appKeyConfigured").value(true));
    for (String path :
        Arrays.asList(
            "/api/admin/content/coverage", "/api/admin/vocabulary-books", "/api/admin/ai/tts"))
      mvc.perform(get(path).header("X-Admin-Token", "session")).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/admin/collection/article-generation/fill")
                .header("X-Admin-Token", "session"))
        .andExpect(status().isForbidden());
    verify(content, never()).coverage();
    verify(tts, never()).adminConfig();
  }

  @Test
  void contentMenuCannotReadOrModifyArticles() throws Exception {
    MockMvc mvc =
        mvc(
            "content",
            new AdminContentController(content, properties, tts),
            new AdminArticleOptionsController(content, books, tts));
    for (String path :
        Arrays.asList(
            "/api/admin/content/articles",
            "/api/admin/content/articles/options",
            "/api/admin/content/articles/tts-status",
            "/api/admin/content/articles/id"))
      mvc.perform(get(path).header("X-Admin-Token", "session")).andExpect(status().isForbidden());
    mvc.perform(
            put("/api/admin/content/articles/id/metadata")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(content, books, tts);
  }

  @Test
  void categoryMetadataAndSpeechUseAuthenticatedActor() throws Exception {
    MockMvc mvc = mvc("articles", new AdminContentController(content, properties, tts));
    mvc.perform(
            put("/api/admin/content/articles/id/category")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\"book\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/content/articles/id/metadata")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\"book\",\"difficulty\":\"intro\",\"estimatedMinutes\":2}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/admin/content/articles/id/speech")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nlsToken\":\"temporary\"}"))
        .andExpect(status().isOk());
    verify(content).updateArticleCategory(ACTOR, "id", "book");
    verify(content).updateArticleMetadata(ACTOR, "id", "book", "intro", 2);
    verify(tts).speechWithTemporaryToken(ACTOR, "id", "temporary");
  }

  @Test
  void feedbackAndVocabularyOperationsUseAuthenticatedActor() throws Exception {
    AdminFeedbackService feedback = mock(AdminFeedbackService.class);
    mvc("feedback", new AdminFeedbackController(feedback, properties))
        .perform(
            put("/api/admin/feedback/id/state")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"resolved\"}"))
        .andExpect(status().isOk());
    verify(feedback).setState("id", "resolved", ACTOR);
    VocabularyImportService imports = mock(VocabularyImportService.class);
    MockMvc mvc = mvc("imports", new AdminVocabularyImportController(imports, properties, tts));
    mvc.perform(delete("/api/admin/vocabulary/datasets/id").header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/admin/vocabulary/import-batches/id").header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/admin/vocabulary/import-batches/id/start")
                .header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    verify(imports).deleteDataset("id", ACTOR);
    verify(imports).deleteBatch("id", ACTOR);
    verify(imports).start("id", ACTOR);
    OnlineVocabularyCatalogService online = mock(OnlineVocabularyCatalogService.class);
    mvc("imports", new AdminOnlineVocabularyController(online, properties))
        .perform(
            post("/api/admin/vocabulary/online-books/book/sync").header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    verify(online).sync("book", ACTOR);
  }

  @Test
  void aiBudgetAndReconciliationUseAuthenticatedActor() throws Exception {
    AiService ai = mock(AiService.class);
    MockMvc mvc =
        mvc(
            "ai",
            new AdminAiController(
                ai,
                mock(AiOfficialPricingService.class),
                tts,
                properties,
                mock(AdminAiUsageService.class)));
    mvc.perform(
            put("/api/admin/ai/budget")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitAmount\":10,\"currency\":\"CNY\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/ai/attempts/id/reconcile")
                .header("X-Admin-Token", "session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settledAmount\":1}"))
        .andExpect(status().isOk());
    verify(ai).saveBudget(new BigDecimal("10"), "CNY", ACTOR);
    verify(ai).reconcileAttempt("id", new BigDecimal("1"), ACTOR);
  }

  @Test
  void missingPrincipalNeverFallsBackToConfiguredAdministrator() {
    org.junit.jupiter.api.Assertions.assertThrows(
        ApiException.class,
        () -> AdminActors.requireId(new org.springframework.mock.web.MockHttpServletRequest()));
  }
}
