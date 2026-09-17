package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.*;
import com.zhixing.mapper.WordMemoryMapper;
import com.zhixing.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class WordMemoryService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final Set<String> DIMENSIONS=new LinkedHashSet<String>(Arrays.asList("meaning","spelling"));
    private static final Set<String> SOURCES=new HashSet<String>(Arrays.asList("word_detail","learning","today","review","free"));
    private static final Set<String> HINT_TYPES=new HashSet<String>(Arrays.asList("first_letter","clue","answer"));
    private static final int MAX_ATTEMPTS=3;
    private final WordMemoryMapper memory;
    private final ContentService contents;
    private final ObjectMapper json;
    private final AppProperties properties;

    public WordMemoryService(WordMemoryMapper memory,ContentService contents,ObjectMapper json,AppProperties properties){this.memory=memory;this.contents=contents;this.json=json;this.properties=properties;}

    public Map<String,Boolean> config(){Map<String,Boolean> result=new LinkedHashMap<String,Boolean>();AppProperties.WordMemory flags=properties.getWordMemory();
        result.put("enabled",flags.isEnabled());result.put("listeningEnabled",flags.isListeningEnabled());result.put("aiEnabled",flags.isAiEnabled());result.put("stableLabelEnabled",flags.isStableLabelEnabled());return result;}

    public List<WordMemoryHintView> hints(String contentId,String senseId){
        ensureEnabled();
        String id=requiredText(contentId,"INVALID_CONTENT","缺少词条ID",32);
        return memory.selectHints(id,clean(senseId));
    }

    @Transactional
    public WordMemorySessionView create(String ownerId,String idempotencyKey,CreateWordMemorySessionRequest request){
        ensureEnabled();
        String key=requiredText(idempotencyKey,"IDEMPOTENCY_KEY_REQUIRED","创建训练会话必须提供 Idempotency-Key",100);
        WordMemorySessionRow prior=memory.selectSessionByIdempotency(ownerId,key);
        if(prior!=null)return view(prior);
        if(request==null||request.getContentIds()==null)throw bad("CONTENT_REQUIRED","请选择要训练的单词");
        LinkedHashSet<String> contentSet=new LinkedHashSet<String>();
        for(String raw:request.getContentIds()){String id=clean(raw);if(id!=null)contentSet.add(id);}
        if(contentSet.isEmpty()||contentSet.size()>5)throw bad("INVALID_CONTENT_COUNT","每组需选择 1 到 5 个单词");
        List<String> dimensions=dimensions(request.getDimensions());
        String source=clean(request.getSource());if(source==null)source="word_detail";
        if(!SOURCES.contains(source))throw bad("INVALID_MEMORY_SOURCE","训练来源不正确");
        String returnTo=clean(request.getReturnTo());
        if(returnTo!=null&&(!returnTo.startsWith("/pages/")||returnTo.length()>500))throw bad("INVALID_RETURN_TO","返回地址必须是小程序内部页面");
        String taskId=clean(request.getTaskId());
        if(taskId!=null&&(contentSet.size()!=1||memory.countOwnedWordTask(ownerId,taskId,contentSet.iterator().next())!=1))
            throw bad("TASK_TARGET_MISMATCH","今日任务与训练词条不匹配");
        List<WordMemoryEpisodeRow> questions=new ArrayList<WordMemoryEpisodeRow>();
        for(String contentId:contentSet)for(String dimension:dimensions){
            WordMemoryEpisodeRow question=memory.selectQuestion(contentId,dimension);
            if(question==null)throw new ApiException(HttpStatus.CONFLICT,"MEMORY_QUESTION_MISSING","所选词条缺少已审核的"+dimensionLabel(dimension)+"题目");
            questions.add(question);
        }
        Instant now=Instant.now();WordMemorySessionRow session=new WordMemorySessionRow();session.setId(CryptoUtils.randomId());session.setOwnerId(ownerId);
        session.setSourceType(source);session.setReturnTo(returnTo);session.setTaskId(taskId);session.setBusinessDate(LocalDate.now(BUSINESS_ZONE));
        session.setTargetCount(contentSet.size());session.setRequiredDimensions(join(dimensions));session.setAddToReview(Boolean.TRUE.equals(request.getAddToReview())?1:0);
        session.setState("active");session.setVersionNo(1);session.setIdempotencyKey(key);session.setCreatedAt(now);session.setUpdatedAt(now);
        try{memory.insertSession(session);}catch(DuplicateKeyException e){WordMemorySessionRow concurrent=memory.selectSessionByIdempotency(ownerId,key);if(concurrent!=null)return view(concurrent);throw e;}
        int position=1;for(WordMemoryEpisodeRow question:questions){question.setId(CryptoUtils.randomId());question.setSessionId(session.getId());question.setPositionNo(position++);memory.insertEpisode(question);}
        return view(session);
    }

    public WordMemorySessionView get(String ownerId,String sessionId){ensureEnabled();return view(required(ownerId,sessionId));}

    @Transactional
    public WordMemoryHintRevealView revealHint(String ownerId,String sessionId,String episodeId,WordMemoryHintRequest request){
        ensureEnabled();
        if(request==null||request.getExpectedVersion()==null)throw bad("EXPECTED_VERSION_REQUIRED","缺少会话版本");
        String type=clean(request.getHintType());if(!HINT_TYPES.contains(type))throw bad("INVALID_HINT_TYPE","提示类型不正确");
        WordMemorySessionRow session=requiredForUpdate(ownerId,sessionId);ensureMutable(session);checkVersion(session,request.getExpectedVersion());
        WordMemoryEpisodeRow episode=memory.selectEpisodeForUpdate(sessionId,episodeId);if(episode==null)throw notFound("MEMORY_EPISODE_NOT_FOUND","训练题不存在");
        if("answered".equals(episode.getState()))throw new ApiException(HttpStatus.CONFLICT,"MEMORY_EPISODE_CLOSED","本题已结束，不能在作答后补记提示");
        String content;if("answer".equals(type))content=episode.getExpectedAnswer();else if("clue".equals(type))content=clean(episode.getHintText());else content=firstLetter(episode.getExpectedAnswer());
        if(content==null)throw new ApiException(HttpStatus.CONFLICT,"MEMORY_HINT_UNAVAILABLE","当前题目没有这种提示");
        Instant now=Instant.now();int revealed="answer".equals(type)?1:0;memory.markHint(sessionId,episodeId,revealed,now);
        memory.insertHintEvent(CryptoUtils.randomId(),ownerId,sessionId,episodeId,type,revealed,now);
        if(memory.advanceSession(ownerId,sessionId,session.getVersionNo(),session.getState(),session.getCompletedAt(),now)!=1)throw conflict();
        WordMemoryHintRevealView result=new WordMemoryHintRevealView();result.setEpisodeId(episodeId);result.setHintType(type);result.setContent(content);
        result.setAnswerRevealed("answer".equals(type));result.setSessionVersion(session.getVersionNo()+1);return result;
    }

    @Transactional
    public WordMemoryAttemptView attempt(String ownerId,String sessionId,String episodeId,String idempotencyKey,WordMemoryAttemptRequest request){
        ensureEnabled();
        String key=requiredText(idempotencyKey,"IDEMPOTENCY_KEY_REQUIRED","提交答案必须提供 Idempotency-Key",100);
        WordMemoryAttemptRow prior=memory.selectAttemptByIdempotency(ownerId,key);
        if(prior!=null){if(!sessionId.equals(prior.getSessionId())||!episodeId.equals(prior.getEpisodeId()))throw bad("IDEMPOTENCY_KEY_REUSED","Idempotency-Key 已用于其他作答");return attemptView(prior,required(ownerId,sessionId));}
        if(request==null||request.getExpectedVersion()==null)throw bad("EXPECTED_VERSION_REQUIRED","缺少会话版本");
        String answer=request.getAnswer()==null?"":request.getAnswer().trim();if(answer.length()>1000)throw bad("ANSWER_TOO_LONG","答案最多 1000 个字符");
        if(request.getDurationMs()!=null&&(request.getDurationMs()<0||request.getDurationMs()>3600000))throw bad("INVALID_DURATION","作答耗时不正确");
        WordMemorySessionRow session=requiredForUpdate(ownerId,sessionId);ensureMutable(session);checkVersion(session,request.getExpectedVersion());
        WordMemoryEpisodeRow episode=memory.selectEpisodeForUpdate(sessionId,episodeId);if(episode==null)throw notFound("MEMORY_EPISODE_NOT_FOUND","训练题不存在");
        if("answered".equals(episode.getState())||episode.getAttemptCount()!=null&&episode.getAttemptCount()>=MAX_ATTEMPTS)
            throw new ApiException(HttpStatus.CONFLICT,"MEMORY_EPISODE_CLOSED","本题已结束，不能继续提交");
        int attemptNo=(episode.getAttemptCount()==null?0:episode.getAttemptCount())+1;boolean correct=matches(episode,answer);
        boolean first=attemptNo==1;boolean hinted=Integer.valueOf(1).equals(episode.getHintUsed());
        String resultType=correct?(first?(hinted?"hinted_correct":"independent_correct"):"retry_correct"):"incorrect";
        String state=correct||attemptNo>=MAX_ATTEMPTS?"answered":"retry_pending";Instant now=Instant.now();String attemptId=CryptoUtils.randomId();
        memory.insertAttempt(attemptId,ownerId,sessionId,episodeId,attemptNo,answer,correct?"correct":"incorrect",resultType,hinted?1:0,first?1:0,request.getDurationMs(),key,now);
        memory.updateEpisodeAfterAttempt(sessionId,episodeId,state,attemptNo,resultType,now);
        WordMemoryEpisodeRow next=memory.selectNextEpisode(sessionId);String sessionState=next==null?"ready_to_finish":"active";
        if(memory.advanceSession(ownerId,sessionId,session.getVersionNo(),sessionState,null,now)!=1)throw conflict();
        WordMemoryAttemptRow row=new WordMemoryAttemptRow();row.setId(attemptId);row.setOwnerId(ownerId);row.setSessionId(sessionId);row.setEpisodeId(episodeId);
        row.setAttemptNo(attemptNo);row.setVerdict(correct?"correct":"incorrect");row.setResultType(resultType);row.setFirstAttempt(first?1:0);row.setHintUsed(hinted?1:0);
        WordMemorySessionRow updated=session;updated.setVersionNo(session.getVersionNo()+1);updated.setState(sessionState);
        WordMemoryAttemptView result=attemptView(row,updated);result.setCanRetry(!correct&&attemptNo<MAX_ATTEMPTS);result.setNextEpisodeAvailable(next!=null);return result;
    }

    @Transactional
    public WordMemoryResultView finish(String ownerId,String sessionId,FinishWordMemorySessionRequest request){
        ensureEnabled();
        WordMemorySessionRow session=requiredForUpdate(ownerId,sessionId);
        if("completed".equals(session.getState())||"partial".equals(session.getState()))return result(ownerId,session);
        if(request==null||request.getExpectedVersion()==null)throw bad("EXPECTED_VERSION_REQUIRED","缺少会话版本");checkVersion(session,request.getExpectedVersion());
        List<WordMemoryEpisodeRow> episodes=memory.selectEpisodes(sessionId);boolean incomplete=false;for(WordMemoryEpisodeRow e:episodes)if(e.getFirstResult()==null)incomplete=true;
        boolean partial=Boolean.TRUE.equals(request.getPartial());if(incomplete&&!partial)throw new ApiException(HttpStatus.CONFLICT,"MEMORY_SESSION_INCOMPLETE","必测题尚未完成，可继续训练或明确结束为部分完成");
        Instant now=Instant.now();String state=incomplete?"partial":"completed";
        if(memory.advanceSession(ownerId,sessionId,session.getVersionNo(),state,now,now)!=1)throw conflict();
        LinkedHashSet<String> practiced=new LinkedHashSet<String>();
        for(WordMemoryEpisodeRow e:episodes)if(e.getFirstResult()!=null){practiced.add(e.getContentId());memory.insertEvidence(CryptoUtils.randomId(),e.getId(),"word-memory-v1");}
        if(Integer.valueOf(1).equals(session.getAddToReview()))for(String contentId:practiced){ContentActionRequest action=new ContentActionRequest();action.setActive(true);contents.review(ownerId,contentId,action);}
        session.setState(state);session.setVersionNo(session.getVersionNo()+1);session.setCompletedAt(now);return result(ownerId,session);
    }

    public WordMemoryResultView result(String ownerId,String sessionId){ensureEnabled();return result(ownerId,required(ownerId,sessionId));}

    private WordMemoryResultView result(String ownerId,WordMemorySessionRow session){
        List<WordMemoryEpisodeRow> episodes=memory.selectEpisodes(session.getId());Set<String> practiced=new HashSet<String>(),weak=new HashSet<String>();
        int completed=0,valid=0,independent=0,hinted=0,retry=0,firstIncorrect=0,untested=0;
        for(WordMemoryEpisodeRow e:episodes){if(e.getFirstResult()==null){untested++;continue;}valid++;practiced.add(e.getContentId());
            if("answered".equals(e.getState()))completed++;if("independent_correct".equals(e.getFirstResult()))independent++;
            if("hinted_correct".equals(e.getFirstResult()))hinted++;if("incorrect".equals(e.getFirstResult()))firstIncorrect++;
            if("retry_correct".equals(e.getFinalResult()))retry++;if(!isCorrectResult(e.getFinalResult())||"incorrect".equals(e.getFirstResult()))weak.add(e.getContentId());}
        WordMemoryResultView v=new WordMemoryResultView();v.setSessionId(session.getId());v.setState(session.getState());v.setReturnTo(session.getReturnTo());
        v.setBusinessDate(session.getBusinessDate());v.setTargetWordCount(session.getTargetCount());v.setPracticedWordCount(practiced.size());v.setTotalEpisodes(episodes.size());
        v.setCompletedEpisodes(completed);v.setValidObjectiveCount(valid);v.setIndependentCorrectCount(independent);v.setHintedCorrectCount(hinted);
        v.setRetryCorrectCount(retry);v.setFirstIncorrectCount(firstIncorrect);v.setUntestedCount(untested);v.setWeakWordCount(weak.size());v.setSessionVersion(session.getVersionNo());
        v.setReviewPlans(memory.selectReviewPlans(ownerId,session.getId()));return v;
    }

    private WordMemorySessionView view(WordMemorySessionRow row){WordMemorySessionView v=new WordMemorySessionView();v.setSessionId(row.getId());v.setSource(row.getSourceType());
        v.setReturnTo(row.getReturnTo());v.setTaskId(row.getTaskId());v.setBusinessDate(row.getBusinessDate());v.setTargetCount(row.getTargetCount());
        v.setRequiredDimensions(split(row.getRequiredDimensions()));v.setAddToReview(Integer.valueOf(1).equals(row.getAddToReview()));v.setState(row.getState());v.setVersion(row.getVersionNo());
        if(!"completed".equals(row.getState())&&!"partial".equals(row.getState()))v.setCurrentEpisode(episodeView(memory.selectNextEpisode(row.getId())));return v;}

    private WordMemoryEpisodeView episodeView(WordMemoryEpisodeRow row){if(row==null)return null;WordMemoryEpisodeView v=new WordMemoryEpisodeView();v.setId(row.getId());v.setContentId(row.getContentId());
        v.setDimension(row.getDimension());v.setPrompt(row.getPromptText());v.setState(row.getState());v.setPosition(row.getPositionNo());v.setAttemptCount(row.getAttemptCount());
        v.setHintUsed(Integer.valueOf(1).equals(row.getHintUsed()));v.setAnswerRevealed(Integer.valueOf(1).equals(row.getAnswerRevealed()));v.setFirstResult(row.getFirstResult());v.setFinalResult(row.getFinalResult());
        if("meaning".equals(row.getDimension()))v.setWordTerm(row.getWordTerm());else v.setMeaning(row.getMeaning());
        List<String> hints=new ArrayList<String>();hints.add("first_letter");if(clean(row.getHintText())!=null)hints.add("clue");hints.add("answer");v.setAvailableHints(hints);return v;}

    private WordMemoryAttemptView attemptView(WordMemoryAttemptRow row,WordMemorySessionRow session){WordMemoryAttemptView v=new WordMemoryAttemptView();v.setAttemptId(row.getId());
        v.setEpisodeId(row.getEpisodeId());v.setAttemptNo(row.getAttemptNo());v.setResult(row.getResultType());v.setCorrect("correct".equals(row.getVerdict()));
        v.setFirstAttempt(Integer.valueOf(1).equals(row.getFirstAttempt()));v.setSessionVersion(session.getVersionNo());v.setCanRetry(!Boolean.TRUE.equals(v.getCorrect())&&row.getAttemptNo()<MAX_ATTEMPTS);
        v.setNextEpisodeAvailable(memory.selectNextEpisode(session.getId())!=null);return v;}

    private boolean matches(WordMemoryEpisodeRow episode,String answer){List<String> accepted=new ArrayList<String>();accepted.add(episode.getExpectedAnswer());
        String raw=clean(episode.getAcceptedAnswersJson());if(raw!=null)try{accepted.addAll(json.readValue(raw,new TypeReference<List<String>>(){}));}catch(Exception e){throw new ApiException(HttpStatus.CONFLICT,"MEMORY_ANSWER_CONFIG_INVALID","题目答案配置异常，请联系管理员");}
        boolean insensitive="case_insensitive".equals(episode.getAnswerPolicy());String actual=answer.trim();for(String expected:accepted)if(expected!=null&&(insensitive?expected.trim().equalsIgnoreCase(actual):expected.trim().equals(actual)))return true;return false;}
    private List<String> dimensions(List<String> raw){LinkedHashSet<String> result=new LinkedHashSet<String>();if(raw==null||raw.isEmpty())result.addAll(DIMENSIONS);else for(String item:raw){String value=clean(item);if(!DIMENSIONS.contains(value))throw bad("INVALID_MEMORY_DIMENSION","首版仅支持认义和拼写训练");result.add(value);}if(result.isEmpty())throw bad("MEMORY_DIMENSION_REQUIRED","至少选择一个训练维度");return new ArrayList<String>(result);}
    private WordMemorySessionRow required(String ownerId,String id){WordMemorySessionRow row=memory.selectSession(ownerId,id);if(row==null)throw notFound("MEMORY_SESSION_NOT_FOUND","训练会话不存在或无权访问");return row;}
    private WordMemorySessionRow requiredForUpdate(String ownerId,String id){WordMemorySessionRow row=memory.selectSessionForUpdate(ownerId,id);if(row==null)throw notFound("MEMORY_SESSION_NOT_FOUND","训练会话不存在或无权访问");return row;}
    private void ensureMutable(WordMemorySessionRow row){if("completed".equals(row.getState())||"partial".equals(row.getState()))throw new ApiException(HttpStatus.CONFLICT,"MEMORY_SESSION_CLOSED","训练会话已经结束");}
    private void checkVersion(WordMemorySessionRow row,Integer expected){if(!expected.equals(row.getVersionNo()))throw conflict();}
    private ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"MEMORY_SESSION_VERSION_CONFLICT","训练会话已在其他设备更新，请恢复最新进度");}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    private ApiException notFound(String code,String message){return new ApiException(HttpStatus.NOT_FOUND,code,message);}
    private String requiredText(String value,String code,String message,int max){String clean=clean(value);if(clean==null||clean.length()>max)throw bad(code,message);return clean;}
    private String clean(String value){if(value==null)return null;String result=value.trim();return result.isEmpty()?null:result;}
    private String join(List<String> values){return String.join(",",values);} private List<String> split(String value){return value==null||value.isEmpty()?Collections.<String>emptyList():Arrays.asList(value.split(","));}
    private String firstLetter(String answer){String value=clean(answer);if(value==null)return null;if(value.length()==1)return value;StringBuilder b=new StringBuilder();b.append(value.charAt(0));for(int i=1;i<value.length();i++)b.append(Character.isWhitespace(value.charAt(i))?' ':'_');return b.toString();}
    private String dimensionLabel(String dimension){return "meaning".equals(dimension)?"认义":"拼写";}
    private boolean isCorrectResult(String result){return "independent_correct".equals(result)||"hinted_correct".equals(result)||"retry_correct".equals(result);}
    private void ensureEnabled(){if(!properties.getWordMemory().isEnabled())throw new ApiException(HttpStatus.NOT_FOUND,"MEMORY_FEATURE_DISABLED","记忆训练暂未开放，请使用原词条与自评流程");}
}
