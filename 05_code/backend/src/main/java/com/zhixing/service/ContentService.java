package com.zhixing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ContentActionRequest;
import com.zhixing.dto.FamiliarityRequest;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.model.ContentDetailView;
import com.zhixing.model.PronunciationRow;
import com.zhixing.model.PronunciationView;
import com.zhixing.model.ArticleWordLookupRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern ARTICLE_TOKEN=Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?|[^A-Za-z]+");
    private final ContentMapper contents;
    private final DailyTaskService dailyTasks;
    private final MediaService media;
    private final ObjectMapper json;
    public ContentService(ContentMapper contents, DailyTaskService dailyTasks, MediaService media,ObjectMapper json) { this.contents=contents; this.dailyTasks=dailyTasks; this.media=media; this.json=json; }

    public ContentDetailView get(String ownerId, String contentId) {
        ContentDetailView view = required(ownerId, contentId);
        view.setTopics(contents.selectTopics(view.getVersionId()));
        if ("word".equals(view.getContentType())) {
            view.setSenses(contents.selectSenses(view.getVersionId()));
            for (ContentDetailView.WordSenseView sense : view.getSenses()) sense.setExamples(contents.selectExamples(sense.getId()));
            attachPronunciations(view);
        }
        if ("english_article".equals(view.getContentType())) {attachArticleBlocks(view);if(view.getArticleAudioAssetId()!=null)view.setArticleAudioUrl(media.signedUrl(view.getArticleAudioAssetId(),ownerId));}
        return view;
    }

    private void attachArticleBlocks(ContentDetailView view) {
        String raw=view.getArticleBlocksJson();
        if(raw==null||raw.trim().isEmpty()){
            String body=view.getBody();if(body==null||body.trim().isEmpty())return;
            int index=1;for(String text:body.split("\\n\\s*\\n")){if(text.trim().isEmpty())continue;ContentDetailView.ArticleBlockView block=new ContentDetailView.ArticleBlockView();block.setParagraphId("p"+(index++));block.setText(text.trim());view.getArticleBlocks().add(block);}attachArticleTokens(view);return;
        }
        try{
            JsonNode root=json.readTree(raw);if(!root.isArray())throw new IllegalArgumentException("not array");
            int index=1;for(JsonNode node:root){ContentDetailView.ArticleBlockView block=new ContentDetailView.ArticleBlockView();
                block.setParagraphId(text(node,"paragraph_id","paragraphId"));if(block.getParagraphId()==null)block.setParagraphId("p"+(index++));
                block.setText(text(node,"text","original","english"));block.setTranslation(text(node,"translation","chinese"));
                JsonNode words=node.path("words");if(words.isArray())for(JsonNode word:words){ContentDetailView.ArticleWordView item=new ContentDetailView.ArticleWordView();item.setContentId(text(word,"content_id","contentId"));item.setTerm(text(word,"term","word"));item.setMeaning(text(word,"meaning","translation"));if(item.getContentId()!=null)block.getWords().add(item);}
                if(block.getText()!=null)view.getArticleBlocks().add(block);
            }
        }catch(Exception ignored){String body=view.getBody();if(body!=null&&!body.trim().isEmpty()){ContentDetailView.ArticleBlockView block=new ContentDetailView.ArticleBlockView();block.setParagraphId("p1");block.setText(body);view.getArticleBlocks().add(block);}}
        attachArticleTokens(view);
    }

    private void attachArticleTokens(ContentDetailView view){Set<String> terms=new LinkedHashSet<String>();for(ContentDetailView.ArticleBlockView block:view.getArticleBlocks()){Matcher matcher=ARTICLE_TOKEN.matcher(block.getText());while(matcher.find()){String token=matcher.group();if(token.matches("[A-Za-z]+(?:'[A-Za-z]+)?"))terms.add(token.toLowerCase(Locale.ROOT));}}Map<String,ArticleWordLookupRow> words=new HashMap<String,ArticleWordLookupRow>();if(!terms.isEmpty())for(ArticleWordLookupRow row:contents.selectWordsByTerms(new ArrayList<String>(terms)))if(row.getTerm()!=null)words.put(row.getTerm().toLowerCase(Locale.ROOT),row);for(ContentDetailView.ArticleBlockView block:view.getArticleBlocks()){Matcher matcher=ARTICLE_TOKEN.matcher(block.getText());while(matcher.find()){String text=matcher.group();ContentDetailView.ArticleTokenView token=new ContentDetailView.ArticleTokenView();token.setText(text);boolean isWord=text.matches("[A-Za-z]+(?:'[A-Za-z]+)?");token.setWord(isWord);if(isWord){ArticleWordLookupRow row=words.get(text.toLowerCase(Locale.ROOT));if(row!=null){token.setKnown(true);token.setContentId(row.getContentId());token.setSpeechKey(row.getSpeechKey());token.setPhonetic(row.getPhonetic());token.setMeaning(row.getMeaning());if(row.getAudioAssetId()!=null)token.setAudioUrl(media.signedUrl(row.getAudioAssetId(),null));}}block.getTokens().add(token);}}}

    private String text(JsonNode node,String...names){for(String name:names){JsonNode value=node.get(name);if(value!=null&&!value.isNull()&&!value.asText().trim().isEmpty())return value.asText();}return null;}

    private void attachPronunciations(ContentDetailView view) {
        List<PronunciationRow> rows = contents.selectPronunciations(view.getVersionId());
        if (rows == null || rows.isEmpty()) return;
        for (PronunciationRow row : rows) {
            String url = row.getAssetId() == null ? null : media.signedUrl(row.getAssetId(), null);
            PronunciationView p = new PronunciationView();
            p.setAssetId(row.getAssetId());
            p.setAccent(row.getAccent());
            p.setPhonetic(row.getPhonetic());
            p.setAudioUrl(url);
            if (row.getSenseId() != null) {
                for (ContentDetailView.WordSenseView sense : view.getSenses()) {
                    if (row.getSenseId().equals(sense.getId())) { sense.getPronunciations().add(p); break; }
                }
            } else if (row.getExampleId() != null) {
                for (ContentDetailView.WordSenseView sense : view.getSenses()) {
                    for (ContentDetailView.WordExampleView ex : sense.getExamples()) {
                        if (row.getExampleId().equals(ex.getId())) { ex.getPronunciations().add(p); break; }
                    }
                }
            } else view.getPronunciations().add(p);
        }
    }

    @Transactional
    public ContentDetailView understood(String ownerId, String contentId, ContentActionRequest request) {
        ContentDetailView view = required(ownerId, contentId);
        Instant now = Instant.now();
        contents.markUnderstood(CryptoUtils.randomId(), ownerId, contentId, learningKey(view, contentId), view.getVersionId(), now);
        appendLearningEvent(ownerId, contentId, request == null ? null : request.getTaskId(), "understood", now);
        if (request != null && request.getTaskId() != null) {
            dailyTasks.completeContentTask(ownerId, request.getTaskId(), contentId, request.getExpectedVersion());
        }
        return get(ownerId, contentId);
    }

    /** 词条即时反馈只完成本次学习暴露；根据反馈重新安排后续巩固，不把“不认识/模糊”记成答对。 */
    @Transactional
    public ContentDetailView wordFeedback(String ownerId,String contentId,ContentActionRequest request) {
        ContentDetailView view=required(ownerId,contentId);
        if(!"word".equals(view.getContentType()))throw new ApiException(HttpStatus.BAD_REQUEST,"NOT_WORD","仅英语词条支持记忆反馈");
        String feedback=request==null?null:request.getFeedback();
        FeedbackRule rule=feedbackRule(feedback);
        Instant now=Instant.now();
        contents.upsertWordFeedback(CryptoUtils.randomId(),ownerId,contentId,learningKey(view,contentId),view.getVersionId(),
                rule.learningStatus,rule.familiarity,now);
        appendLearningEvent(ownerId,contentId,request.getTaskId(),feedback,now);
        scheduleFeedback(ownerId,contentId,view,rule);
        if(request.getTaskId()!=null)dailyTasks.completeContentTask(ownerId,request.getTaskId(),contentId,request.getExpectedVersion());
        return get(ownerId,contentId);
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

    @Transactional
    public ContentDetailView familiarity(String ownerId,String contentId,FamiliarityRequest request){
        if(request==null||request.getFamiliarityPercent()==null||request.getFamiliarityPercent()<0||request.getFamiliarityPercent()>100)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FAMILIARITY","熟悉度必须是 0 到 100 的整数");
        ContentDetailView view=required(ownerId,contentId);if(!"word".equals(view.getContentType()))throw new ApiException(HttpStatus.BAD_REQUEST,"NOT_WORD","仅英语词条可设置熟悉度");
        int expected=request.getExpectedVersion()==null?0:request.getExpectedVersion();if(view.getRecordVersion()!=null&&view.getRecordVersion()!=expected)
            throw new ApiException(HttpStatus.CONFLICT,"LEARNING_VERSION_CONFLICT","学习状态已更新，请重新加载后再保存熟悉度");
        Instant now=Instant.now();int changed;
        if(expected==0){try{changed=contents.insertFamiliarity(CryptoUtils.randomId(),ownerId,contentId,request.getFamiliarityPercent(),now);}catch(DuplicateKeyException e){changed=0;}}
        else changed=contents.updateFamiliarity(ownerId,contentId,request.getFamiliarityPercent(),expected,now);
        if(changed==0)throw new ApiException(HttpStatus.CONFLICT,"LEARNING_VERSION_CONFLICT","学习状态已更新，请重新加载后再保存熟悉度");
        return get(ownerId,contentId);
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

    private String learningKey(ContentDetailView view, String contentId) {
        return "word".equals(view.getContentType()) ? "word:" + contentId
                : "english_article".equals(view.getContentType()) ? "article:" + contentId : "tech:" + contentId;
    }

    private FeedbackRule feedbackRule(String feedback) {
        if("unclear".equals(feedback))return new FeedbackRule("learning",0,0,1);
        if("fuzzy".equals(feedback))return new FeedbackRule("learning",50,0,1);
        if("remember".equals(feedback))return new FeedbackRule("understood",85,1,3);
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WORD_FEEDBACK","词条反馈仅支持 unclear、fuzzy 或 remember");
    }

    private void scheduleFeedback(String ownerId,String contentId,ContentDetailView view,FeedbackRule rule) {
        String knowledgeId=contents.selectContentKnowledgeId(ownerId,contentId);
        if(knowledgeId==null){knowledgeId=CryptoUtils.randomId();try{contents.insertContentKnowledge(knowledgeId,ownerId,contentId,view.getVersionId(),view.getTitle());}
            catch(DuplicateKeyException ignored){knowledgeId=contents.selectContentKnowledgeId(ownerId,contentId);}}
        LocalDate due=LocalDate.now(BUSINESS_ZONE).plusDays(rule.afterDays);
        if(contents.updateFeedbackSchedule(ownerId,knowledgeId,rule.stage,due)==0){try{contents.insertFeedbackSchedule(CryptoUtils.randomId(),ownerId,knowledgeId,rule.stage,due);}
            catch(DuplicateKeyException ignored){contents.updateFeedbackSchedule(ownerId,knowledgeId,rule.stage,due);}}
    }

    private void appendLearningEvent(String ownerId, String contentId, String taskId, String feedback, Instant now) {
        String recordId = contents.selectLearningRecordId(ownerId, contentId);
        Integer recordVersion = contents.selectLearningRecordVersion(ownerId, contentId);
        if (recordId == null || recordVersion == null) throw new IllegalStateException("学习状态写入后无法读取");
        contents.insertLearningEvent(CryptoUtils.randomId(), ownerId, recordId, recordVersion, taskId,
                feedback, LocalDate.now(BUSINESS_ZONE), now);
    }

    private static class FeedbackRule {
        final String learningStatus; final int familiarity,stage,afterDays;
        FeedbackRule(String learningStatus,int familiarity,int stage,int afterDays){this.learningStatus=learningStatus;this.familiarity=familiarity;this.stage=stage;this.afterDays=afterDays;}
    }
}
