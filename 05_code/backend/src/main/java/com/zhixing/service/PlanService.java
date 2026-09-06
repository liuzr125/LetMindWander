package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.CreateTopicRequest;
import com.zhixing.dto.UpdatePlanRequest;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.entity.LearningPlanTopicEntity;
import com.zhixing.entity.LearningTopicEntity;
import com.zhixing.mapper.LearningPlanMapper;
import com.zhixing.model.PlanView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** F03 计划规则；所有读写经 LearningPlanMapper 完成。 */
@Service
public class PlanService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<Integer> BUDGETS = new HashSet<Integer>(Arrays.asList(5, 10, 15, 20, 30));
    private static final Set<String> DIFFICULTIES = new HashSet<String>(Arrays.asList("intro", "advanced"));
    private final LearningPlanMapper plans;
    private final DailyTaskService dailyTasks;

    public PlanService(LearningPlanMapper plans, DailyTaskService dailyTasks) { this.plans = plans; this.dailyTasks = dailyTasks; }

    public PlanView get(String ownerId) {
        LearningPlanEntity plan = plans.selectLatest(ownerId);
        return plan == null ? toView(defaultPlan(ownerId, 1, today()), Collections.<LearningTopicEntity>emptyList())
                : toView(plan, topicsFor(plan));
    }

    /** 用户可追溯此前保存的计划版本；任务包不属于 F03，不能在这里伪造任务列表。 */
    public List<PlanView> history(String ownerId) {
        List<PlanView> result = new ArrayList<PlanView>();
        for (LearningPlanEntity plan : plans.selectHistory(ownerId)) result.add(toView(plan, topicsFor(plan)));
        return result;
    }

    public List<PlanView.TopicView> availableTopics(String ownerId) {
        return topicViews(plans.selectAvailableTopics(ownerId));
    }

    @Transactional
    public PlanView initializeDefault(String ownerId) {
        LearningPlanEntity existing = plans.selectLatestForUpdate(ownerId);
        if (existing != null) return toView(existing, topicsFor(existing));
        LearningPlanEntity plan = defaultPlan(ownerId, 1, today());
        plans.insert(plan);
        plans.updateCurrentPlan(ownerId, plan.getId());
        return toView(plan, Collections.<LearningTopicEntity>emptyList());
    }

    @Transactional
    public PlanView update(String ownerId, UpdatePlanRequest request) {
        LearningPlanEntity latest = plans.selectLatestForUpdate(ownerId);
        if (latest == null) latest = initializeEntity(ownerId);
        if (request.getVersionNo() == null || !request.getVersionNo().equals(latest.getVersionNo())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_VERSION_CONFLICT", "计划已变更，请重新加载后保存");
        }
        LearningPlanEntity next = copyValidated(ownerId, latest, request);
        List<LearningTopicEntity> selectedTopics = validateTopics(ownerId, request.getTopicIds());
        try {
            plans.insert(next);
            for (LearningTopicEntity topic : selectedTopics) {
                LearningPlanTopicEntity relation = new LearningPlanTopicEntity();
                relation.setId(CryptoUtils.randomId()); relation.setPlanId(next.getId()); relation.setTopicId(topic.getId());
                plans.insertPlanTopic(relation);
            }
            plans.updateCurrentPlan(ownerId, next.getId());
            dailyTasks.syncToday(ownerId, next);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_VERSION_CONFLICT", "计划已变更，请重新加载后保存");
        }
        return toView(next, selectedTopics);
    }

    @Transactional
    public PlanView.TopicView createTopic(String ownerId, CreateTopicRequest request) {
        String name = validateTopicName(request == null ? null : request.getName());
        String normalized = normalizeTopic(name);
        LearningTopicEntity existing = plans.selectTopicByScopeAndName(ownerId, normalized);
        if (existing != null) return topicView(existing);
        LearningTopicEntity topic = new LearningTopicEntity();
        topic.setId(CryptoUtils.randomId()); topic.setScopeKey(ownerId); topic.setOwnerId(ownerId);
        topic.setName(name); topic.setNormalizedName(normalized); topic.setState("active");
        try { plans.insertTopic(topic); }
        catch (DuplicateKeyException exception) {
            LearningTopicEntity duplicate = plans.selectTopicByScopeAndName(ownerId, normalized);
            if (duplicate != null) return topicView(duplicate);
            throw exception;
        }
        return topicView(topic);
    }

    private LearningPlanEntity initializeEntity(String ownerId) {
        LearningPlanEntity initial = defaultPlan(ownerId, 1, today());
        plans.insert(initial); plans.updateCurrentPlan(ownerId, initial.getId());
        return initial;
    }

    private LearningPlanEntity defaultPlan(String ownerId, int version, LocalDate effectiveDate) {
        LearningPlanEntity plan = new LearningPlanEntity();
        plan.setId(CryptoUtils.randomId()); plan.setOwnerId(ownerId); plan.setVersionNo(version); plan.setEffectiveDate(effectiveDate);
        plan.setDailyBudgetMin(10); plan.setWeekdaysMask(31); plan.setTopicMask(1); plan.setDifficulty("intro"); plan.setTechCount(1); plan.setNewWordCount(3);
        plan.setJournalEnabled(1); plan.setReviewEnabled(1); plan.setReviewLimit(5); plan.setIsPaused(0);
        return plan;
    }

    private LearningPlanEntity copyValidated(String ownerId, LearningPlanEntity latest, UpdatePlanRequest r) {
        if (r.getDailyBudgetMin() == null || !BUDGETS.contains(r.getDailyBudgetMin())) bad("INVALID_DAILY_BUDGET", "每日时间仅支持 5、10、15、20、30 分钟");
        if (r.getWeekdaysMask() == null || r.getWeekdaysMask() < 0 || r.getWeekdaysMask() > 127) bad("INVALID_WEEKDAYS", "学习日设置不合法");
        if (r.getTechCount() == null || r.getTechCount() < 0 || r.getTechCount() > 2) bad("INVALID_TECH_COUNT", "技术新学数量应为 0 至 2");
        if (r.getNewWordCount() == null || r.getNewWordCount() < 0 || r.getNewWordCount() > 10) bad("INVALID_WORD_COUNT", "英语新词数量应为 0 至 10");
        if (r.getReviewLimit() == null || r.getReviewLimit() < 0 || r.getReviewLimit() > 10) bad("INVALID_REVIEW_LIMIT", "复习上限应为 0 至 10");
        String difficulty = r.getDifficulty() == null ? "" : r.getDifficulty().trim().toLowerCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(difficulty)) bad("INVALID_DIFFICULTY", "难度仅支持入门或进阶");
        if (r.getJournalEnabled() == null || r.getReviewEnabled() == null || r.getPaused() == null) bad("INVALID_PLAN", "计划开关不能为空");
        LocalDate pauseUntil = parsePauseUntil(r.getPaused(), r.getPauseUntil());
        String reason = trim(r.getChangeReason());
        if (reason != null && reason.codePointCount(0, reason.length()) > 200) bad("INVALID_CHANGE_REASON", "调整说明最多 200 个字符");

        LearningPlanEntity plan = new LearningPlanEntity();
        plan.setId(CryptoUtils.randomId()); plan.setOwnerId(ownerId); plan.setVersionNo(latest.getVersionNo() + 1);
        // 未激活今日任务包时，今日页会据此计划即时生成任务；已激活任务包保留原计划快照。
        plan.setEffectiveDate(today()); plan.setDailyBudgetMin(r.getDailyBudgetMin()); plan.setWeekdaysMask(r.getWeekdaysMask());
        plan.setDifficulty(difficulty); plan.setTechCount(r.getTechCount()); plan.setNewWordCount(r.getNewWordCount());
        plan.setJournalEnabled(bool(r.getJournalEnabled())); plan.setReviewEnabled(bool(r.getReviewEnabled())); plan.setReviewLimit(r.getReviewLimit());
        plan.setIsPaused(bool(r.getPaused())); plan.setPauseUntil(pauseUntil);
        return plan;
    }

    private List<LearningTopicEntity> validateTopics(String ownerId, List<String> topicIds) {
        Set<String> ids = new LinkedHashSet<String>();
        if (topicIds != null) for (String id : topicIds) if (id != null && !id.trim().isEmpty()) ids.add(id.trim());
        Map<String, LearningTopicEntity> allowed = new HashMap<String, LearningTopicEntity>();
        for (LearningTopicEntity topic : plans.selectAvailableTopics(ownerId)) allowed.put(topic.getId(), topic);
        List<LearningTopicEntity> result = new ArrayList<LearningTopicEntity>();
        for (String id : ids) {
            LearningTopicEntity topic = allowed.get(id);
            if (topic == null) bad("INVALID_TOPIC", "主题不存在或无权使用");
            result.add(topic);
        }
        return result;
    }

    private LocalDate parsePauseUntil(Boolean paused, String value) {
        if (!paused.booleanValue()) return null;
        String trimmed = trim(value);
        if (trimmed == null) return null;
        try {
            LocalDate date = LocalDate.parse(trimmed);
            if (date.isBefore(today())) bad("INVALID_PAUSE_UNTIL", "暂停截止日期不能早于今天");
            return date;
        } catch (ApiException exception) { throw exception; }
        catch (Exception exception) { bad("INVALID_PAUSE_UNTIL", "暂停截止日期格式应为 YYYY-MM-DD"); return null; }
    }

    private PlanView toView(LearningPlanEntity p, List<LearningTopicEntity> topics) {
        PlanView view = new PlanView();
        view.setId(p.getId()); view.setVersionNo(p.getVersionNo()); view.setEffectiveDate(p.getEffectiveDate()); view.setDailyBudgetMin(p.getDailyBudgetMin());
        view.setWeekdaysMask(p.getWeekdaysMask()); view.setDifficulty(p.getDifficulty()); view.setTechCount(p.getTechCount()); view.setNewWordCount(p.getNewWordCount());
        view.setJournalEnabled(p.getJournalEnabled() != null && p.getJournalEnabled() == 1); view.setReviewEnabled(p.getReviewEnabled() != null && p.getReviewEnabled() == 1);
        view.setReviewLimit(p.getReviewLimit()); view.setPaused(p.getIsPaused() != null && p.getIsPaused() == 1); view.setPauseUntil(p.getPauseUntil()); view.setTopics(topicViews(topics));
        LocalDate nextDay = tomorrow();
        view.setActiveTomorrow(!view.getPaused() && (p.getPauseUntil() == null || p.getPauseUntil().isBefore(nextDay)) && (p.getWeekdaysMask() & (1 << (nextDay.getDayOfWeek().getValue() - 1))) != 0);
        return view;
    }

    /** 老版本没有关联表数据时，仅将旧位图 bit0 映射为 AI 系统主题供用户确认并保存。 */
    private List<LearningTopicEntity> topicsFor(LearningPlanEntity plan) {
        List<LearningTopicEntity> topics = plans.selectPlanTopics(plan.getId());
        if (!topics.isEmpty() || plan.getTopicMask() == null || (plan.getTopicMask() & 1) == 0) return topics;
        LearningTopicEntity legacyAi = plans.selectLegacyAiTopic();
        return legacyAi == null ? topics : Collections.singletonList(legacyAi);
    }

    private List<PlanView.TopicView> topicViews(List<LearningTopicEntity> topics) { List<PlanView.TopicView> result = new ArrayList<PlanView.TopicView>(); for (LearningTopicEntity t : topics) result.add(topicView(t)); return result; }
    private PlanView.TopicView topicView(LearningTopicEntity t) { PlanView.TopicView v = new PlanView.TopicView(); v.setId(t.getId()); v.setName(t.getName()); v.setSystem("system".equals(t.getScopeKey())); return v; }
    private String validateTopicName(String value) { String name = trim(value); if (name == null || name.codePointCount(0, name.length()) > 50 || name.matches(".*[\\p{Cntrl}].*")) bad("INVALID_TOPIC_NAME", "主题名称需为 1 至 50 个字符"); return name; }
    private String normalizeTopic(String value) { return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    private String trim(String value) { if (value == null) return null; String result = value.trim(); return result.isEmpty() ? null : result; }
    private int bool(Boolean value) { return value.booleanValue() ? 1 : 0; }
    private LocalDate tomorrow() { return LocalDate.now(BUSINESS_ZONE).plusDays(1); }
    private LocalDate today() { return LocalDate.now(BUSINESS_ZONE); }
    private void bad(String code, String message) { throw new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}
