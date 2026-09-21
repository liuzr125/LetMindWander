package com.zhixing.controller;

import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ContentDetailView;
import com.zhixing.service.ArticleTranslationService;
import com.zhixing.service.ContentService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit repair for previously published articles with missing translations. */
@RestController
@RequestMapping("/api/learning/contents")
public class ArticleTranslationController {
    private final SessionService sessions;
    private final ContentService contents;
    private final ArticleTranslationService translations;

    public ArticleTranslationController(SessionService sessions, ContentService contents, ArticleTranslationService translations) {
        this.sessions = sessions; this.contents = contents; this.translations = translations;
    }

    @PostMapping("/{id}/translation")
    public ContentDetailView translate(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        AuthenticatedSession session = sessions.requireUser(auth);
        ContentDetailView detail = contents.get(session.getUserId(), id);
        if (!"english_article".equals(detail.getContentType())) throw new com.zhixing.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "NOT_ARTICLE", "仅英语短文支持译文");
        translations.ensureTranslation(id);
        return contents.get(session.getUserId(), id);
    }

    @PostMapping("/{id}/title-translation")
    public ContentDetailView translateTitle(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        AuthenticatedSession session = sessions.requireUser(auth);
        ContentDetailView detail = contents.get(session.getUserId(), id);
        if (!"english_article".equals(detail.getContentType())) throw new com.zhixing.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "NOT_ARTICLE", "仅英语短文支持标题译文");
        translations.ensureTitleTranslation(id);
        return contents.get(session.getUserId(), id);
    }
}
