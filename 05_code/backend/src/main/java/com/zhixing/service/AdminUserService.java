package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.entity.AdminUserAccountEntity;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.AdminUserMapper;
import com.zhixing.model.AdminUserAccountDetailView;
import com.zhixing.model.AdminUserLearningView;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.model.AdminUserView;
import com.zhixing.model.LearningPageView;
import com.zhixing.model.PlanView;
import com.zhixing.model.VocabularyBookProgressView;
import com.zhixing.model.WordNotebookSummaryView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AdminUserService.class);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final AdminUserMapper mapper;
  private final AdminAuditMapper audit;
  private final PlanService plans;
  private final VocabularyBookService vocabularyBooks;
  private final LearningService learning;

  public AdminUserService(
      AdminUserMapper mapper,
      AdminAuditMapper audit,
      PlanService plans,
      VocabularyBookService vocabularyBooks,
      LearningService learning) {
    this.mapper = mapper;
    this.audit = audit;
    this.plans = plans;
    this.vocabularyBooks = vocabularyBooks;
    this.learning = learning;
  }

  public AdminUserPageView page(
      Integer rawPage, Integer rawPageSize, String rawKeyword, String rawStatus) {
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 个账号");
    String keyword = rawKeyword == null ? "" : rawKeyword.trim(),
        status = rawStatus == null ? "all" : rawStatus.trim();
    if (keyword.length() > 80)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD", "搜索词最多 80 个字符");
    if (!"all".equals(status) && !"active".equals(status) && !"disabled".equals(status))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_STATUS", "账号状态不正确");
    int total = mapper.countPage(keyword, status);
    List<AdminUserView> items = new ArrayList<>();
    for (AdminUserAccountEntity row :
        mapper.selectPageAccounts(keyword, status, pageSize, (page - 1) * pageSize))
      items.add(mapUser(row));
    int count = total, totalPages = count == 0 ? 0 : (count + pageSize - 1) / pageSize;
    AdminUserPageView result = new AdminUserPageView();
    result.setTotal(count);
    result.setPage(page);
    result.setPageSize(pageSize);
    result.setTotalPages(totalPages);
    result.setKeyword(keyword);
    result.setStatus(status);
    result.setItems(items);
    return result;
  }

  public AdminUserLearningView learning(
      String rawUserId, Integer rawNotebookPage, Integer rawNotebookPageSize) {
    String userId = rawUserId == null ? "" : rawUserId.trim();
    int notebookPage = rawNotebookPage == null ? 1 : rawNotebookPage,
        notebookPageSize = rawNotebookPageSize == null ? 20 : rawNotebookPageSize;
    if (userId.isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户 ID 不能为空");
    if (notebookPage < 1 || notebookPageSize < 1 || notebookPageSize > 50)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "生词本页码从 1 开始，每页可显示 1 至 50 条");
    AdminUserAccountEntity account = required(userId);
    AdminUserLearningView result = new AdminUserLearningView();
    result.setAccount(mapUser(account));
    Integer planCount = mapper.countLearningPlans(userId);
    PlanView plan = planCount != null && planCount > 0 ? plans.get(userId) : null;
    result.setPlan(plan);
    VocabularyBookProgressView vocabulary = null;
    if (vocabularyBooks.current(userId) != null) {
      vocabulary = vocabularyBooks.progress(userId, "all", 1, 1);
      vocabulary.setItems(Collections.emptyList());
      vocabulary.setHasMore(false);
    }
    result.setVocabulary(vocabulary);
    WordNotebookSummaryView notebook = learning.notebookSummary(userId);
    result.setNotebook(notebook);
    LearningPageView notebookItems =
        learning.page(userId, "word", "", "", "", true, "", "", notebookPage, notebookPageSize);
    result.setNotebookItems(notebookItems);
    result.setToday(today(userId));
    return result;
  }

  @Transactional
  public AdminUserAccountDetailView accountDetail(String rawUserId, String adminId) {
    String userId = rawUserId == null ? "" : rawUserId.trim();
    if (userId.isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户 ID 不能为空");
    AdminUserAccountEntity row = required(userId);
    AdminUserAccountDetailView item = mapUser(row, new AdminUserAccountDetailView());
    // 完整手机号只进入单独的账号详情视图，列表与学习详情继续使用脱敏字段。
    item.setMobile(row.getMobile());
    audit.insertAudit(
        CryptoUtils.randomId(),
        adminId,
        "user_account_detail_view",
        "app_user",
        userId,
        "succeeded",
        "{}",
        Instant.now().plusSeconds(180L * 24 * 3600));
    LOG.info("Administrator account-detail access audited");
    return item;
  }

  @Transactional
  public AdminUserView setAiAuthorization(String rawUserId, boolean enabled, String adminId) {
    String userId = rawUserId == null ? "" : rawUserId.trim();
    if (userId.isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户 ID 不能为空");
    Instant now = Instant.now();
    int updated =
        mapper.updateAiAuthorization(
            enabled ? "ADMIN_AI_ACCESS_V1" : null, enabled ? now : null, userId);
    if (updated == 0) throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "用户不存在");
    audit.insertAudit(
        CryptoUtils.randomId(),
        adminId,
        "ai_authorization_update",
        "app_user",
        userId,
        "succeeded",
        "{\"enabled\":" + enabled + "}",
        now.plusSeconds(180L * 24 * 3600));
    LOG.info("Administrator AI-authorization change audited");
    return mapUser(required(userId));
  }

  private AdminUserAccountEntity required(String userId) {
    AdminUserAccountEntity account = mapper.selectById(userId);
    if (account == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "用户不存在");
    return account;
  }

  private AdminUserLearningView.TodayProgressView today(String userId) {
    AdminUserLearningView.TodayProgressView item =
        mapper.selectTodayProgress(userId, LocalDate.now(BUSINESS_ZONE));
    if (item == null) {
      item = new AdminUserLearningView.TodayProgressView();
      item.setTotalCount(0);
      item.setCompletedCount(0);
      item.setDoingCount(0);
    }
    int total = item.getTotalCount(), done = item.getCompletedCount();
    item.setCompletionRate(total == 0 ? 0.0 : Math.round(done * 1000.0 / total) / 10.0);
    return item;
  }

  private AdminUserView mapUser(AdminUserAccountEntity row) {
    return mapUser(row, new AdminUserView());
  }

  private <T extends AdminUserView> T mapUser(AdminUserAccountEntity row, T item) {
    item.setUserId(row.getId());
    item.setSeqNo(row.getSeqNo());
    item.setShortId(row.getShortId());
    item.setNickname(row.getNickname());
    item.setMobileMasked(mask(row.getMobile()));
    item.setStatus(row.getStatus());
    item.setAiConsented(row.getAiConsentVersion() != null);
    item.setLastLoginAt(row.getLastLoginAt());
    item.setCreatedAt(row.getCreatedAt());
    return item;
  }

  private String mask(String mobile) {
    if (mobile == null || mobile.length() < 7) return mobile;
    return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
  }
}
