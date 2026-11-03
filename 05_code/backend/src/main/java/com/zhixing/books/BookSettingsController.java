package com.zhixing.books;

import com.zhixing.config.AdminActors;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/english-books/settings")
public class BookSettingsController {
  private final BookSettings settings;

  public BookSettingsController(BookSettings settings) {
    this.settings = settings;
  }

  @GetMapping
  public Map<String, Object> read() {
    return settings.view();
  }

  @PutMapping
  public Map<String, Object> save(
      @RequestBody BookSettings.Request body, HttpServletRequest request) {
    return settings.save(body, AdminActors.requireId(request));
  }
}
