package com.zhixing.books;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.common.*;
import com.zhixing.config.*;
import com.zhixing.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookSettingsPermissionTest {
  @Test
  void settingsRequireTheBookMenuAndNeverUseTheArticlePermission() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    BookSettings service = mock(BookSettings.class);
    AppProperties properties = new AppProperties();
    when(auth.require(eq("book-session"), eq("english_books")))
        .thenReturn(
            new AdminPrincipal(
                "admin", "editor", "EDITOR", "编辑", Collections.singleton("english_books")));
    when(auth.require(eq("article-session"), anyString()))
        .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有书籍权限"));
    when(service.view()).thenReturn(Collections.singletonMap("databaseReady", true));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new BookSettingsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new AdminSessionFilter(auth, properties))
            .build();
    mvc.perform(get("/api/admin/english-books/settings").header("X-Admin-Token", "book-session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.databaseReady").value(true));
    mvc.perform(get("/api/admin/english-books/settings").header("X-Admin-Token", "article-session"))
        .andExpect(status().isForbidden());
    verify(service, times(1)).view();
  }
}
