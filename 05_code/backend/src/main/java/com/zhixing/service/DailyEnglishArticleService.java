package com.zhixing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.model.ArticleGenerationRunView;
import com.zhixing.model.ArticleGenerationStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * 每日分词书英语短文。官方 RSS 只提供当日选题索引，不抓取或复制原文；
 * 模型按正式词书中的目标词原创生成短文，结构校验后才发布。
 */
@Service
public class DailyEnglishArticleService {
    private static final Logger LOGGER= LoggerFactory.getLogger(DailyEnglishArticleService.class);
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final String SOURCE_ID="e0000000000000000000000000000017";
    private static final String COUNT_PARAMETER="article_generation.per_book_count";
    private static final String ENABLED_PARAMETER="article_generation.enabled";
    private static final String LICENSE="系统仅参考官方 RSS 标题选题；正文按词书目标词原创生成，不复制第三方正文。";
    private static final String SYSTEM_PROMPT="You create original graded English reading passages for Chinese learners. Return strict JSON only. Never copy source wording, never invent factual claims, and keep all content age-appropriate. Online headlines are topic inspiration only.";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiService ai;
    private final AppParameterService parameters;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    public DailyEnglishArticleService(JdbcTemplate jdbc,ObjectMapper json,AiService ai,AppParameterService parameters,AppProperties properties,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;this.ai=ai;this.parameters=parameters;this.properties=properties;this.transactions=new TransactionTemplate(manager);
    }

    @Scheduled(cron="${app.article-generation.cron:0 30 22 * * *}",zone="Asia/Shanghai")
    public void scheduledGeneration(){
        if(!enabled())return;
        try{generate(LocalDate.now(BUSINESS_ZONE),"daily");}
        catch(Exception exception){LOGGER.error("每日分词书英语短文任务失败",exception);}
    }

    public Map<String,Object> generate(LocalDate date,String triggerType){
        int perBook=perBookCount();
        String triggerKey=(triggerType==null?"manual":triggerType)+":"+date;
        String runId=CryptoUtils.randomId();Instant started=Instant.now();
        try{jdbc.update("INSERT INTO english_article_generation_run(id,trigger_key,run_date,state,started_at) VALUES(?,?,?,'running',?)",runId,triggerKey,date,Timestamp.from(started));}
        catch(DuplicateKeyException duplicate){return runByTrigger(triggerKey);}
        int books=0,generated=0,skipped=0,failed=0;List<String> errors=new ArrayList<String>();
        try{
            List<Map<String,Object>> topics=latestOnlineTopics();
            if(topics.isEmpty())throw new IllegalStateException("没有可用的官方 RSS 选题；请先执行每日内容采集");
            for(Map<String,Object> book:eligibleBooks()){
                books++;
                String bookId=text(book,"id"),bookCode=text(book,"book_code"),bookName=text(book,"book_name"),level=text(book,"level_code");
                int existing=count("SELECT COUNT(*) FROM english_article_book WHERE book_id=? AND generated_date=?",bookId,date);
                if(existing>=perBook){skipped+=perBook;continue;}
                try{
                    List<Map<String,Object>> words=targetWords(bookId,date,25);
                    if(words.size()<10)throw new IllegalStateException("词书可用词条不足 10 个");
                    int bookGenerated=0;
                    while(existing+bookGenerated<perBook){
                        int batchSize=Math.min(5,perBook-existing-bookGenerated),firstSlot=existing+bookGenerated+1;
                        List<GeneratedArticle> batch=ask(bookName,level,date,words,topics,batchSize,runId+":"+firstSlot);
                        transactions.executeWithoutResult(status->{for(int i=0;i<batch.size();i++)persist(bookId,bookCode,level,date,firstSlot+i,batch.get(i),words,topics.get((firstSlot+i-1)%topics.size()));});
                        bookGenerated+=batch.size();generated+=batch.size();
                    }
                }catch(Exception exception){int current=count("SELECT COUNT(*) FROM english_article_book WHERE book_id=? AND generated_date=?",bookId,date);failed+=Math.max(0,perBook-current);errors.add(bookName+"："+shorten(exception.getMessage()));}
            }
        }catch(Exception exception){errors.add(shorten(exception.getMessage()));failed++;}
        String state=failed==0?"success":generated>0?"partial":"failed";String error=errors.isEmpty()?null:shorten(String.join("；",errors));
        jdbc.update("UPDATE english_article_generation_run SET state=?,book_count=?,generated_count=?,skipped_count=?,failed_count=?,error_message=?,finished_at=? WHERE id=?",state,books,generated,skipped,failed,error,Timestamp.from(Instant.now()),runId);
        return runById(runId);
    }

    public ArticleGenerationRunView runManually(){
        if(!enabled())throw new IllegalStateException("每日分词书英语短文任务当前已禁用；请先启用任务");
        Map<String,Object> result=generate(LocalDate.now(BUSINESS_ZONE),"manual:"+CryptoUtils.randomId());
        return toRun(result);
    }

    public ArticleGenerationStatusView status(){
        ArticleGenerationStatusView result=new ArticleGenerationStatusView();
        result.setEnabled(enabled());
        result.setCronExpression(properties.getArticleGeneration().getCron());
        result.setTimezone(BUSINESS_ZONE.getId());
        result.setPerBookCount(perBookCount());
        result.setEligibleBookCount(eligibleBooks().size());
        result.setAvailableTopicCount(latestOnlineTopics().size());
        result.setNextRunAt(result.isEnabled()?nextRun(Instant.now()):null);
        return result;
    }

    public ArticleGenerationStatusView updatePerBookCount(int value){
        if(value<1||value>20)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ARTICLE_COUNT","每本词书每天可生成 1 至 20 篇短文");
        parameters.saveValue(COUNT_PARAMETER,String.valueOf(value),"每日分词书英语短文：每本有效词书每天的目标篇数（1-20）");
        return status();
    }

    public ArticleGenerationStatusView updateEnabled(boolean value){
        parameters.saveValue(ENABLED_PARAMETER,String.valueOf(value),"每日分词书英语短文定时任务开关；false 时到点不执行且不调用模型");
        return status();
    }

    private int perBookCount(){
        String fallback=String.valueOf(properties.getArticleGeneration().getPerBookCount());
        try{return Math.max(1,Math.min(20,Integer.parseInt(parameters.optionalStored(COUNT_PARAMETER,fallback).trim())));}
        catch(Exception exception){LOGGER.warn("英语短文每日篇数配置无效，使用服务默认值 {}",fallback);return Math.max(1,Math.min(20,properties.getArticleGeneration().getPerBookCount()));}
    }

    private boolean enabled(){
        String fallback=String.valueOf(properties.getArticleGeneration().isEnabled());
        String configured=parameters.optionalStored(ENABLED_PARAMETER,fallback);
        if("true".equalsIgnoreCase(configured)||"1".equals(configured))return true;
        if("false".equalsIgnoreCase(configured)||"0".equals(configured))return false;
        LOGGER.warn("英语短文定时任务开关配置无效，使用服务默认值 {}",fallback);return properties.getArticleGeneration().isEnabled();
    }

    public List<ArticleGenerationRunView> runs(int limit){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM english_article_generation_run ORDER BY created_at DESC,id DESC LIMIT ?",Math.max(1,Math.min(50,limit)));
        List<ArticleGenerationRunView> result=new ArrayList<ArticleGenerationRunView>();
        for(Map<String,Object> row:rows)result.add(toRun(row));
        return result;
    }

    public Map<String,Object> runDetail(String runId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM english_article_generation_run WHERE id=?",runId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ARTICLE_GENERATION_RUN_NOT_FOUND","英语短文生成运行记录不存在");
        ArticleGenerationRunView run=toRun(rows.get(0));Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("kind","article_generation");result.put("run",run);result.put("taskCode","daily_english_article_generation");result.put("sqlScript","V3.16.7_daily_graded_articles.sql");result.put("status",status());
        result.put("tables",Arrays.asList("english_article_generation_run：任务运行结果","english_article_book：短文与词书、日期和槽位映射","learning_content / content_version：已校验发布的原创短文","app_parameter：任务开关和每日篇数"));
        result.put("tools",Arrays.asList("Spring @Scheduled（Asia/Shanghai）","官方 RSS 选题索引","AI 全局配额、并发和月度预算门禁","结构、长度、目标词覆盖与重复标题校验"));
        result.put("steps",Arrays.asList("读取持久化任务开关；禁用时不执行","筛选至少含 10 个已审核词条的启用词书","按词书抽取目标词并以最多 5 篇一批生成","校验合格后按日期固定槽位事务入库","记录生成、已存在和失败数量"));
        result.put("principles",Arrays.asList("定时运行按日期幂等，手动运行仅补齐缺少槽位","官方来源只提供选题，不复制第三方正文","数量增加会提高模型调用次数与费用","失败批次不写入，已成功批次保留并可再次补齐"));
        return result;
    }

    private List<Map<String,Object>> eligibleBooks(){
        return jdbc.queryForList("SELECT vb.id,vb.book_code,vb.book_name,vb.level_code,COUNT(vbw.id) word_total FROM vocabulary_book vb JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id JOIN learning_content lc ON lc.id=vbw.content_id AND lc.content_type='word' AND lc.state='published' JOIN content_version cv ON cv.id=lc.published_version_id AND cv.review_status='approved' WHERE vb.state='active' GROUP BY vb.id,vb.book_code,vb.book_name,vb.level_code HAVING COUNT(vbw.id)>=10 ORDER BY vb.sort_no,vb.book_name");
    }

    private List<Map<String,Object>> latestOnlineTopics(){
        return jdbc.queryForList("SELECT cv.title,cv.origin_url,cv.origin_published_at,cs.name source_name FROM learning_content lc JOIN content_version cv ON cv.id=lc.current_version_id JOIN content_source cs ON cs.id=lc.source_id WHERE lc.content_type='tech' AND cs.source_type='rss' AND cv.origin_url IS NOT NULL ORDER BY cv.created_at DESC,cv.id DESC LIMIT 20");
    }

    private List<Map<String,Object>> targetWords(String bookId,LocalDate date,int limit){
        int offset=Math.floorMod(date.getDayOfYear()*31+bookId.hashCode(),Math.max(1,count("SELECT COUNT(*) FROM vocabulary_book_word WHERE book_id=?",bookId)-limit+1));
        return jdbc.queryForList("SELECT LOWER(TRIM(cv.word_term)) word_term,cv.meaning FROM vocabulary_book_word vbw JOIN learning_content lc ON lc.id=vbw.content_id AND lc.state='published' JOIN content_version cv ON cv.id=lc.published_version_id AND cv.review_status='approved' WHERE vbw.book_id=? ORDER BY vbw.sort_no,vbw.importance DESC,lc.id LIMIT ?,?",bookId,offset,limit);
    }

    private List<GeneratedArticle> ask(String bookName,String level,LocalDate date,List<Map<String,Object>> words,List<Map<String,Object>> topics,int count,String runId)throws Exception{
        boolean advanced=!Arrays.asList("primary","junior","senior").contains(level);int minWords=advanced?75:45,maxWords=advanced?120:80;
        List<String> wordList=new ArrayList<String>();for(Map<String,Object> row:words)wordList.add(text(row,"word_term"));
        List<Map<String,String>> topicList=new ArrayList<Map<String,String>>();for(Map<String,Object> row:topics){Map<String,String> topic=new LinkedHashMap<String,String>();topic.put("title",text(row,"title"));topic.put("source",text(row,"source_name"));topic.put("url",text(row,"origin_url"));topicList.add(topic);}
        String prompt="Create "+count+" different original English mini-passages for vocabulary book '"+bookName+"' (level "+level+") on "+date+". " +
                "Each body must contain "+minWords+"-"+maxWords+" English words and naturally use at least "+(advanced?4:3)+" words from targetWords. " +
                "Use the onlineTopics only as broad inspiration; do not quote them and do not state unverified news facts. Return exactly this JSON shape: {\"articles\":[{\"title\":\"English title\",\"summaryZh\":\"中文摘要\",\"body\":\"English body\"}]}. " +
                "targetWords="+json.writeValueAsString(wordList)+" onlineTopics="+json.writeValueAsString(topicList);
        String raw=ai.generateSystemContent(SYSTEM_PROMPT,prompt,"article:"+date+":"+bookName+":"+runId);
        JsonNode array=json.readTree(stripFence(raw)).path("articles");if(!array.isArray()||array.size()!=count)throw new IllegalStateException("AI 未返回恰好 "+count+" 篇短文");
        List<GeneratedArticle> result=new ArrayList<GeneratedArticle>();Set<String> titles=new HashSet<String>();
        for(JsonNode node:array){String title=clean(node.path("title").asText()),summary=clean(node.path("summaryZh").asText()),body=clean(node.path("body").asText());validate(title,summary,body,wordList,advanced);if(!titles.add(title.toLowerCase(Locale.ROOT)))throw new IllegalStateException("AI 返回了重复标题");result.add(new GeneratedArticle(title,summary,body));}
        return result;
    }

    private void validate(String title,String summary,String body,List<String> targetWords,boolean advanced){
        if(title.length()<3||title.length()>100||summary.length()<2||summary.length()>500||body.length()<120||body.length()>1800)throw new IllegalStateException("短文结构或长度不合格");
        String lower=body.toLowerCase(Locale.ROOT);int used=0;for(String word:targetWords)if(word.matches("[a-z][a-z'-]*")&&lower.matches("(?s).*\\b"+java.util.regex.Pattern.quote(word)+"\\b.*"))used++;
        if(used<(advanced?4:3))throw new IllegalStateException("短文未覆盖足够的目标词汇");
    }

    private void persist(String bookId,String bookCode,String level,LocalDate date,int slot,GeneratedArticle article,List<Map<String,Object>> words,Map<String,Object> topic){
        String contentId=CryptoUtils.randomId(),versionId=CryptoUtils.randomId();String difficulty=Arrays.asList("primary","junior","senior").contains(level)?"intro":"advanced";
        String topicUrl=text(topic,"origin_url");String targetJson=write(words);String topicJson=write(topic);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,origin_url_hash,stage,state,current_version_id,published_version_id,published_at,row_version) VALUES(?,'english_article',?, ?,?,?, 'published',?,?,CURRENT_TIMESTAMP,1)",contentId,SOURCE_ID,CryptoUtils.sha256(bookId+"|"+date+"|"+slot+"|"+article.body),CryptoUtils.sha256(topicUrl),level,versionId,versionId);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,reviewed_at,created_by,article_blocks) VALUES(?,?,1,?,?,?,?,?,?,?,?,?,?, 'approved',CURRENT_TIMESTAMP,?,?)",versionId,contentId,article.title,article.summary,article.body,difficulty,Math.max(60,article.body.split("\\s+").length),topicUrl,"知行日课原创生成",value(topic,"origin_published_at"),LICENSE,CryptoUtils.sha256(article.body),properties.getAdminPrincipalId(),write(Collections.singletonList(Collections.singletonMap("text",article.body))));
        jdbc.update("INSERT INTO english_article_book(id,content_id,book_id,generated_date,slot_no,target_words_json,topic_snapshot_json) VALUES(?,?,?,?,?,?,?)",CryptoUtils.randomId(),contentId,bookId,date,slot,targetJson,topicJson);
    }

    private Map<String,Object> runByTrigger(String key){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM english_article_generation_run WHERE trigger_key=?",key);return rows.isEmpty()?Collections.<String,Object>emptyMap():rows.get(0);}
    private Map<String,Object> runById(String id){return jdbc.queryForMap("SELECT * FROM english_article_generation_run WHERE id=?",id);}
    private ArticleGenerationRunView toRun(Map<String,Object> row){
        ArticleGenerationRunView run=new ArticleGenerationRunView();
        run.setId(text(row,"id"));run.setTriggerKey(text(row,"trigger_key"));run.setState(text(row,"state"));run.setErrorMessage(nullableText(row,"error_message"));
        Object date=value(row,"run_date");if(date instanceof java.sql.Date)run.setRunDate(((java.sql.Date)date).toLocalDate());else if(date!=null)run.setRunDate(LocalDate.parse(String.valueOf(date)));
        run.setBookCount(number(row,"book_count"));run.setGeneratedCount(number(row,"generated_count"));run.setSkippedCount(number(row,"skipped_count"));run.setFailedCount(number(row,"failed_count"));
        run.setStartedAt(instant(row,"started_at"));run.setFinishedAt(instant(row,"finished_at"));run.setCreatedAt(instant(row,"created_at"));return run;
    }
    private int number(Map<String,Object> row,String key){Object result=value(row,key);return result instanceof Number?((Number)result).intValue():0;}
    private String nullableText(Map<String,Object> row,String key){Object result=value(row,key);return result==null?null:String.valueOf(result);}
    private Instant instant(Map<String,Object> row,String key){Object result=value(row,key);if(result instanceof Timestamp)return ((Timestamp)result).toInstant();if(result instanceof LocalDateTime)return ((LocalDateTime)result).atZone(ZoneOffset.UTC).toInstant();if(result instanceof OffsetDateTime)return ((OffsetDateTime)result).toInstant();if(result instanceof java.util.Date)return ((java.util.Date)result).toInstant();return result==null?null:Instant.parse(String.valueOf(result));}
    private Instant nextRun(Instant from){
        try{ZonedDateTime next=CronExpression.parse(properties.getArticleGeneration().getCron()).next(from.atZone(BUSINESS_ZONE));return next==null?null:next.toInstant();}
        catch(IllegalArgumentException exception){LOGGER.warn("英语短文生成 cron 配置无效，状态页使用默认 22:30",exception);ZonedDateTime now=from.atZone(BUSINESS_ZONE);ZonedDateTime next=now.withHour(22).withMinute(30).withSecond(0).withNano(0);if(!next.isAfter(now))next=next.plusDays(1);return next.toInstant();}
    }
    private int count(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private Object value(Map<String,Object> row,String key){Object result=row.get(key);return result==null?row.get(key.toUpperCase(Locale.ROOT)):result;}
    private String text(Map<String,Object> row,String key){Object result=value(row,key);return result==null?"":String.valueOf(result);}
    private String clean(String value){return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private String shorten(String value){String safe=value==null?"未知错误":value.trim();return safe.length()<=900?safe:safe.substring(0,899)+"…";}
    private String stripFence(String value){String safe=value==null?"":value.trim();if(safe.startsWith("```")){safe=safe.replaceFirst("^```(?:json)?\\s*","");safe=safe.replaceFirst("\\s*```$","");}return safe;}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception exception){throw new IllegalStateException(exception);}}
    private static final class GeneratedArticle{final String title,summary,body;GeneratedArticle(String title,String summary,String body){this.title=title;this.summary=summary;this.body=body;}}
}
