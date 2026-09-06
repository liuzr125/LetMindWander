package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ContentActionRequest;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.model.ContentDetailView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ContentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ContentMapper contents;
    private final DailyTaskService dailyTasks;
    public ContentService(ContentMapper contents, DailyTaskService dailyTasks) { this.contents=contents; this.dailyTasks=dailyTasks; }

    public ContentDetailView get(String ownerId, String contentId) {
        ContentDetailView view = required(ownerId, contentId);
        view.setTopics(contents.selectTopics(view.getVersionId()));
        if ("word".equals(view.getContentType())) {
            view.setSenses(contents.selectSenses(view.getVersionId()));
            for (ContentDetailView.WordSenseView sense : view.getSenses()) sense.setExamples(contents.selectExamples(sense.getId()));
        }
        return view;
    }

    @Transactional
    public ContentDetailView understood(String ownerId, String contentId, ContentActionRequest request) {
        ContentDetailView view = required(ownerId, contentId);
        String key = "word".equals(view.getContentType()) ? "word:" + contentId : "tech:" + contentId;
        contents.markUnderstood(CryptoUtils.randomId(), ownerId, contentId, key, view.getVersionId(), Instant.now());
        if (request != null && request.getTaskId() != null) {
            dailyTasks.completeContentTask(ownerId, request.getTaskId(), contentId, request.getExpectedVersion());
        }
        return get(ownerId, contentId);
    }

    @Transactional
    public ContentDetailView favorite(String ownerId, String contentId, ContentActionRequest request) {
        ContentDetailView view = required(ownerId, contentId);
        boolean active = request == null || !Boolean.FALSE.equals(request.getActive());
        contents.upsertFavorite(CryptoUtils.randomId(), ownerId, favoriteTargetType(view.getContentType()), contentId,
                active ? "active" : "removed", view.getTitle(), Instant.now());
        return get(ownerId, contentId);
    }

    @Transactional
    public ContentDetailView review(String ownerId, String contentId, ContentActionRequest request) {
        ContentDetailView view = required(ownerId, contentId);
        boolean active = request == null || !Boolean.FALSE.equals(request.getActive());
        String knowledgeId = contents.selectContentKnowledgeId(ownerId, contentId);
        if (knowledgeId == null) {
            knowledgeId = CryptoUtils.randomId();
            try { contents.insertContentKnowledge(knowledgeId, ownerId, contentId, view.getVersionId(), view.getTitle()); }
            catch (DuplicateKeyException ignored) { knowledgeId = contents.selectContentKnowledgeId(ownerId, contentId); }
        }
        String state = active ? "active" : "paused";
        LocalDate due = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        if (contents.updateReview(ownerId, knowledgeId, state, due) == 0 && active) {
            try { contents.insertReview(CryptoUtils.randomId(), ownerId, knowledgeId, due); }
            catch (DuplicateKeyException ignored) { contents.updateReview(ownerId, knowledgeId, state, due); }
        }
        return get(ownerId, contentId);
    }

    @Transactional
    public ContentDetailView wordBook(String ownerId, String contentId, ContentActionRequest request) {
        ContentDetailView view = required(ownerId, contentId);
        if (!"word".equals(view.getContentType())) throw new ApiException(HttpStatus.BAD_REQUEST,"NOT_WORD","仅英语词条可加入生词本");
        boolean active = request == null || !Boolean.FALSE.equals(request.getActive());
        if (contents.upsertWordNotebook(CryptoUtils.randomId(), ownerId, contentId, active ? "active" : "removed", Instant.now()) == 0)
            throw new ApiException(HttpStatus.CONFLICT,"WORD_KEY_MISSING","词条缺少规范键，暂不能加入生词本");
        return get(ownerId, contentId);
    }

    private ContentDetailView required(String ownerId, String contentId) {
        ContentDetailView view = contents.selectDetail(ownerId, contentId);
        if (view == null) throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","内容不存在、已下架或无权访问");
        return view;
    }

    private String favoriteTargetType(String contentType) {
        if ("tech".equals(contentType)) return "content";
        if ("english_article".equals(contentType)) return "article";
        return contentType;
    }
}
