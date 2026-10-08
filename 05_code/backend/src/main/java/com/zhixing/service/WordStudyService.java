package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.mapper.VocabularyBookMapper;
import com.zhixing.model.ContentDetailView;
import com.zhixing.model.DailyTaskView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Server-owned recall queue. Owner lock, version and attempt receipt protect every transition. */
@Service
public class WordStudyService {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final ContentMapper contents;
    private final DailyTaskService tasks;
    private final VocabularyBookMapper books;
    private final BookStudyService history;
    private final WordStudyPreferenceService preferences;
    private final ObjectMapper json;
    public WordStudyService(JdbcTemplate jdbc,ContentMapper contents,DailyTaskService tasks,VocabularyBookMapper books,
                            BookStudyService history,WordStudyPreferenceService preferences,ObjectMapper json){
        this.jdbc=jdbc;this.contents=contents;this.tasks=tasks;this.books=books;this.history=history;this.preferences=preferences;this.json=json;
    }
    LocalDate today(){return LocalDate.now(ZONE);}
    Instant now(){return Instant.now();}
    private void lock(String owner){books.lockOwner(owner);if(jdbc.queryForList("SELECT owner_id FROM word_study_cursor WHERE owner_id=?",owner).isEmpty())jdbc.update("INSERT INTO word_study_cursor(owner_id) VALUES(?)",owner);}
    private long position(String owner){return jdbc.queryForObject("SELECT position_no FROM word_study_cursor WHERE owner_id=?",Long.class,owner);}

    @Transactional
    public Map<String,Object> queue(String owner,String mode,String contentId){
        lock(owner);LocalDate date=today();
        tasks.get(owner,date);
        // Lazy compatibility: old learning records remain pending, understood records remain learned.
        String book=books.selectActiveBookId(owner);
        if(book!=null){
            for(String id:jdbc.queryForList("SELECT lr.content_id FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status='learning'",String.class,owner,book))ensure(owner,id,"new",null,null,1);
        }
        for(Map<String,Object> t:jdbc.queryForList("SELECT t.id,t.content_id FROM daily_task t JOIN daily_package p ON p.id=t.package_id LEFT JOIN learning_record lr ON lr.owner_id=t.owner_id AND lr.content_id=t.content_id WHERE t.owner_id=? AND p.business_date=? AND t.task_type='word' AND t.status NOT IN ('DONE','CANCELLED','SKIPPED') AND (lr.id IS NULL OR lr.learning_status='unlearned')",owner,java.sql.Date.valueOf(date)))
            ensure(owner,str(t,"content_id"),"new",str(t,"id"),null,1);
        for(Map<String,Object> r:jdbc.queryForList("SELECT rs.id,k.source_content_id,rs.stage,rs.due_date FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id JOIN learning_content lc ON lc.id=k.source_content_id AND lc.content_type='word' WHERE rs.owner_id=? AND rs.state='active' AND rs.due_date<=?",owner,java.sql.Date.valueOf(date))){
            String id=str(r,"source_content_id");Map<String,Object> existing=state(owner,id);
            String taskId=reviewTask(owner,str(r,"id"));
            if(existing==null)ensure(owner,id,"review",taskId,str(r,"id"),legacyInterval(num(r,"stage")));
            else if(num(existing,"pending")==0&&!date.equals(day(existing,"last_completed_date")))jdbc.update("UPDATE word_study_state SET pending=1,source_kind='review',task_id=?,schedule_id=?,streak=0,recovery=NULL,business_date=?,eligible_position=0,eligible_at=NULL,version_no=version_no+1 WHERE owner_id=? AND content_id=?",taskId,str(r,"id"),java.sql.Date.valueOf(date),owner,id);
            else if(num(existing,"pending")==1){
                if("new".equals(str(existing,"source_kind")))jdbc.update("UPDATE word_study_state SET origin_task_id=COALESCE(origin_task_id,task_id),source_kind='review',task_id=?,schedule_id=?,recovery=COALESCE(recovery,'unclear'),version_no=version_no+1 WHERE owner_id=? AND content_id=?",taskId,str(r,"id"),owner,id);
                else jdbc.update("UPDATE word_study_state SET task_id=?,schedule_id=? WHERE owner_id=? AND content_id=?",taskId,str(r,"id"),owner,id);
            }
        }
        if(contentId!=null&&!contentId.isEmpty()){
            required(owner,contentId);
            Map<String,Object> old=state(owner,contentId);
            if(old==null){List<Map<String,Object>> schedules=jdbc.queryForList("SELECT rs.id,rs.stage,rs.due_date FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id WHERE rs.owner_id=? AND k.source_content_id=? AND rs.state='active'",owner,contentId);
                List<String> learned=jdbc.queryForList("SELECT learning_status FROM learning_record WHERE owner_id=? AND content_id=? AND learning_status IN ('understood','mastered')",String.class,owner,contentId);
                if(learned.isEmpty()&&schedules.isEmpty())ensure(owner,contentId,"new",null,null,1);
            }
        }
        // Across midnight a partial success must be confirmed afresh; unfinished work survives.
        jdbc.update("UPDATE word_study_state SET streak=0,business_date=?,eligible_position=0,eligible_at=NULL,version_no=version_no+1 WHERE owner_id=? AND pending=1 AND business_date<>?",java.sql.Date.valueOf(date),owner,java.sql.Date.valueOf(date));
        if(contentId!=null&&state(owner,contentId)!=null)jdbc.update("UPDATE word_study_state SET free_study=1 WHERE owner_id=? AND content_id=?",owner,contentId);
        return view(owner,mode,contentId);
    }
    private void ensure(String owner,String id,String kind,String task,String schedule,int interval){
        Map<String,Object> old=state(owner,id);
        if(old!=null){if("new".equals(kind)&&task!=null&&num(old,"exposed")==0)jdbc.update("UPDATE word_study_state SET task_id=?,origin_task_id=? WHERE owner_id=? AND content_id=?",task,task,owner,id);return;}
        ContentDetailView word=required(owner,id);
        jdbc.update("INSERT INTO word_study_state(owner_id,content_id,source_kind,task_id,schedule_id,business_date,first_seen_date,scheduled_interval) VALUES(?,?,?,?,?,?,?,?)",owner,id,kind,task,schedule,java.sql.Date.valueOf(today()),java.sql.Date.valueOf(today()),interval);
        if("new".equals(kind)&&jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE owner_id=? AND content_id=? AND learning_status='learning'",Integer.class,owner,id)>0)jdbc.update("UPDATE word_study_state SET exposed=1 WHERE owner_id=? AND content_id=?",owner,id);
        if("new".equals(kind)&&task!=null)jdbc.update("UPDATE word_study_state SET origin_task_id=? WHERE owner_id=? AND content_id=?",task,owner,id);
        if("review".equals(kind)&&!Boolean.TRUE.equals(word.getUnderstood()))jdbc.update("UPDATE word_study_state SET recovery='unclear' WHERE owner_id=? AND content_id=?",owner,id);

    }
    private Map<String,Object> state(String owner,String id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM word_study_state WHERE owner_id=? AND content_id=?",owner,id);return rows.isEmpty()?null:rows.get(0);}
    private String reviewTask(String owner,String schedule){List<String> rows=jdbc.queryForList("SELECT t.id FROM daily_task t JOIN daily_package p ON p.id=t.package_id JOIN review_schedule rs ON rs.knowledge_id=t.knowledge_id WHERE t.owner_id=? AND rs.id=? AND p.business_date=? AND t.task_type='review' AND t.status NOT IN ('DONE','CANCELLED','SKIPPED')",String.class,owner,schedule,java.sql.Date.valueOf(today()));return rows.isEmpty()?null:rows.get(0);}
    private ContentDetailView required(String owner,String id){ContentDetailView word=contents.selectDetail(owner,id);if(word==null||!"word".equals(word.getContentType()))throw fail("WORD_NOT_AVAILABLE","词条不存在或已下架");return word;}

    private List<Map<String,Object>> pending(String owner,String mode){
        String book=books.selectActiveBookId(owner);
        String filter="review".equals(mode)?" AND s.source_kind='review'":" AND (s.source_kind='review' OR s.free_study=1 OR EXISTS(SELECT 1 FROM daily_task t WHERE t.id=s.task_id AND t.status='DOING') OR EXISTS(SELECT 1 FROM vocabulary_book_word w WHERE w.content_id=s.content_id AND w.book_id=?))";
        List<Object> args=new ArrayList<Object>();args.add(owner);args.add(java.sql.Date.valueOf(today()));args.add(java.sql.Date.valueOf(today()));if(!"review".equals(mode))args.add(book);
        return jdbc.queryForList("SELECT s.*,cv.word_term FROM word_study_state s JOIN learning_content lc ON lc.id=s.content_id AND lc.state='published' JOIN content_version cv ON cv.id=lc.published_version_id AND cv.review_status='approved' WHERE s.owner_id=? AND s.pending=1 AND (s.source_kind<>'review' OR EXISTS(SELECT 1 FROM review_schedule live WHERE live.id=s.schedule_id AND live.owner_id=s.owner_id AND live.state='active' AND live.due_date<=?)) AND (s.exposed=1 OR s.source_kind='review' OR s.free_study=1 OR EXISTS(SELECT 1 FROM daily_task allocated JOIN daily_package dp ON dp.id=allocated.package_id WHERE allocated.id=s.task_id AND allocated.status IN ('TODO','DOING') AND dp.business_date=?))"+filter+" ORDER BY s.eligible_position,s.first_seen_date,s.content_id",args.toArray());
    }
    boolean ready(Map<String,Object> s,long position){return position>=lng(s,"eligible_position")||s.get("eligible_at")==null||!now().isBefore(databaseInstant(s.get("eligible_at")));}
    private Map<String,Object> view(String owner,String mode){return view(owner,mode,null,null);}
    private Map<String,Object> view(String owner,String mode,String preferred){return view(owner,mode,preferred,null);}
    private Map<String,Object> view(String owner,String mode,String preferred,String appliedMode){
        List<Map<String,Object>> rows=pending(owner,mode);long pos=position(owner);Map<String,Object> current=null;int fresh=0,consolidation=0,review=0;long wait=Long.MAX_VALUE;
        for(Map<String,Object> s:rows){boolean isReview="review".equals(str(s,"source_kind"));if(isReview&&s.get("recovery")==null)review++;else if(isReview)consolidation++;else if(num(s,"exposed")==0)fresh++;else consolidation++;
            if(ready(s,pos)&&(current==null||str(s,"content_id").equals(preferred)))current=s;
            if(s.get("eligible_at")!=null)wait=Math.min(wait,Math.max(0,Duration.between(now(),databaseInstant(s.get("eligible_at"))).getSeconds()));
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("businessDate",today().toString());result.put("intervalMode",appliedMode==null?preferences.intervalMode(owner):appliedMode);result.put("newCount",fresh);result.put("consolidationCount",consolidation);result.put("reviewCount",review);result.put("remaining",rows.size());result.put("waitSeconds",current==null&&wait!=Long.MAX_VALUE?wait:0);
        if(current!=null){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("contentId",str(current,"content_id"));item.put("wordTerm",str(current,"word_term"));item.put("version",num(current,"version_no"));item.put("streak",num(current,"streak"));item.put("kind",str(current,"source_kind"));result.put("current",item);}
        return result;
    }

    @Transactional
    public Map<String,Object> feedback(String owner,String id,Map<String,Object> request){
        lock(owner);
        String attempt=String.valueOf(request.getOrDefault("attemptId",""));String feedback=String.valueOf(request.get("feedback"));String mode="review".equals(request.get("mode"))?"review":"all";
        if(attempt.isEmpty()||attempt.length()>96||!Arrays.asList("remember","fuzzy","unclear").contains(feedback))throw fail("WORD_FEEDBACK_INVALID","缺少反馈或请求标识");
        String encoded=encode(new TreeMap<String,Object>(request));
        List<Map<String,Object>> receipts=jdbc.queryForList("SELECT content_id,request_json,result_json FROM word_study_attempt WHERE owner_id=? AND attempt_id=?",owner,attempt);
        if(!receipts.isEmpty()){Map<String,Object> receipt=receipts.get(0);if(!id.equals(str(receipt,"content_id"))||!encoded.equals(str(receipt,"request_json")))throw fail("WORD_ATTEMPT_REUSED","请求标识已用于另一反馈");return decode(str(receipt,"result_json"));}
        Map<String,Object> s=state(owner,id);if(s==null||num(s,"pending")==0)throw fail("WORD_STUDY_REQUIRED","请刷新学习队列");
        if(!today().toString().equals(String.valueOf(request.get("businessDate")))||!today().equals(day(s,"business_date")))throw fail("WORD_DAY_CHANGED","日期已变化，请刷新学习队列");
        if(!(request.get("expectedVersion") instanceof Number)||num(s,"version_no")!=((Number)request.get("expectedVersion")).intValue())throw fail("WORD_VERSION_CONFLICT","学习进度已变化，请刷新");
        long cursor=position(owner);
        if(!ready(s,cursor))throw fail("WORD_RECALL_TOO_SOON","先学习其他词，或稍后再回忆这个词");
        ContentDetailView word=required(owner,id);boolean review="review".equals(str(s,"source_kind"));String recovery=str(s,"recovery");
        if(review&&jdbc.queryForObject("SELECT COUNT(*) FROM review_schedule WHERE owner_id=? AND id=? AND state='active' AND due_date<=?",Integer.class,owner,str(s,"schedule_id"),java.sql.Date.valueOf(today()))==0)throw fail("WORD_REVIEW_CHANGED","复习计划已变化，请刷新队列");
        int streak="remember".equals(feedback)?num(s,"streak")+1:0;
        if(!"remember".equals(feedback))recovery="unclear".equals(feedback)||"unclear".equals(recovery)?"unclear":"fuzzy";
        boolean complete="remember".equals(feedback);
        long position=cursor+1;jdbc.update("UPDATE word_study_cursor SET position_no=? WHERE owner_id=?",position,owner);
        Instant moment=now();
        String studyTask=str(s,"task_id");
        if(!review&&studyTask!=null){List<Map<String,Object>> taskRows=jdbc.queryForList("SELECT status,version_no FROM daily_task WHERE id=? AND owner_id=?",studyTask,owner);if(!taskRows.isEmpty()&&"TODO".equals(str(taskRows.get(0),"status"))){com.zhixing.dto.TaskEventRequest start=new com.zhixing.dto.TaskEventRequest();start.setEventType("start");start.setExpectedVersion(num(taskRows.get(0),"version_no"));tasks.event(owner,studyTask,start);}}

        jdbc.update("UPDATE word_study_state SET exposed=1,streak=?,recovery=?,pending=?,eligible_position=?,eligible_at=?,version_no=version_no+1 WHERE owner_id=? AND content_id=?",streak,recovery,complete?0:1,position+4,Timestamp.from(moment.plusSeconds(120)),owner,id);
        String status=complete?"understood":"learning";int familiarity=complete?85:("fuzzy".equals(feedback)?50:0);
        contents.upsertWordFeedback(CryptoUtils.randomId(),owner,id,"word:"+id,word.getVersionId(),status,familiarity,moment);
        contents.insertLearningEvent(CryptoUtils.randomId(),owner,contents.selectLearningRecordId(owner,id),contents.selectLearningRecordVersion(owner,id),str(s,"task_id"),feedback,today(),moment);
        history.record(owner,id,review?BookStudyService.KIND_REVIEW:BookStudyService.KIND_STUDY,moment);
        String appliedMode=preferences.intervalMode(owner);
        int beforeStage=stage(owner,id);
        if(complete)complete(owner,id,s,recovery,review,moment,appliedMode,word);
        log(owner,id,attempt,feedback,s,complete,streak,beforeStage,moment,appliedMode,word);
        Map<String,Object> result=view(owner,mode,null,appliedMode);result.put("completed",complete);result.put("feedback",feedback);
        jdbc.update("INSERT INTO word_study_attempt(owner_id,attempt_id,content_id,request_json,result_json) VALUES(?,?,?,?,?)",owner,attempt,id,encoded,encode(result));return decode(encode(result));
    }
    private void complete(String owner,String id,Map<String,Object> s,String recovery,boolean review,Instant moment,String mode,ContentDetailView word){
        String knowledge=contents.selectContentKnowledgeId(owner,id);
        if(knowledge==null){knowledge=CryptoUtils.randomId();contents.insertContentKnowledge(knowledge,owner,id,word.getVersionId(),word.getTitle());}
        List<Map<String,Object>> schedules=jdbc.queryForList("SELECT id,stage,due_date FROM review_schedule WHERE owner_id=? AND knowledge_id=?",owner,knowledge);
        int stage=schedules.isEmpty()?0:num(schedules.get(0),"stage");
        java.sql.Date priorDue=schedules.isEmpty()?null:(java.sql.Date)schedules.get(0).get("due_date");
        int oldInterval=num(s,"scheduled_interval");
        int interval=nextInterval(mode,review,recovery,oldInterval);
        if(review&&recovery==null&&"fixed".equals(mode)&&"fixed".equals(str(s,"schedule_mode")))interval=new int[]{1,3,7,14,30,60}[Math.min(5,stage+1)];
        // Fixed stage is inferred from actual interval; flexible -> fixed uses the next larger ladder step.
        int nextStage=review&&recovery==null?("flexible".equals(mode)?floorStage(interval):fixedStage(interval)):("unclear".equals(recovery)||!review?0:stage);
        if(review&&"fuzzy".equals(recovery)&&"fixed".equals(mode)&&"flexible".equals(str(s,"schedule_mode")))nextStage=floorStage(oldInterval);
        LocalDate due=today().plusDays(interval);
        String scheduleId=schedules.isEmpty()?CryptoUtils.randomId():str(schedules.get(0),"id");
        if(schedules.isEmpty())contents.insertFeedbackSchedule(scheduleId,owner,knowledge,nextStage,due);else contents.updateFeedbackSchedule(owner,knowledge,nextStage,due);
        jdbc.update("UPDATE word_study_state SET scheduled_interval=?,schedule_mode=?,last_completed_date=?,schedule_id=? WHERE owner_id=? AND content_id=?",interval,mode,java.sql.Date.valueOf(today()),scheduleId,owner,id);
        if(review){
            String feedbackId=CryptoUtils.randomId();
            // The recall queue permits only one completed review per word/business day.
            jdbc.update("INSERT INTO review_feedback(id,owner_id,knowledge_id,schedule_id,business_date,feedback,before_stage,before_state,before_due_date,after_stage,after_state,after_due_date,revision_no,task_id,effective_at) VALUES(?,?,?,?,?,?,?,'active',?,?,'active',?,1,?,?)",feedbackId,owner,knowledge,scheduleId,java.sql.Date.valueOf(today()),recovery==null?"remember":recovery,stage,priorDue,nextStage,java.sql.Date.valueOf(due),str(s,"task_id"),Timestamp.from(moment));
            jdbc.update("UPDATE review_schedule SET last_feedback_id=? WHERE id=?",feedbackId,scheduleId);
        }
        String origin=str(s,"origin_task_id");
        if(origin!=null&&!origin.equals(str(s,"task_id"))){List<String> originalStatus=jdbc.queryForList("SELECT status FROM daily_task WHERE id=? AND owner_id=?",String.class,origin,owner);if(!originalStatus.isEmpty()&&Arrays.asList("TODO","DOING").contains(originalStatus.get(0)))tasks.completeContentTask(owner,origin,id,null);}
        String task=str(s,"task_id");
        if(task!=null){List<String> statuses=jdbc.queryForList("SELECT status FROM daily_task WHERE id=? AND owner_id=?",String.class,task,owner);if(!statuses.isEmpty()&&Arrays.asList("TODO","DOING").contains(statuses.get(0))){if(review)tasks.completeReviewTask(owner,task,knowledge);else tasks.completeContentTask(owner,task,id,null);}}
    }
    static int nextInterval(String mode,boolean review,String recovery,int old){
        if(!review||"unclear".equals(recovery))return 1;
        if("flexible".equals(mode))return "fuzzy".equals(recovery)?Math.max(1,(old+1)/2):Math.min(60,Math.max(1,old)*2);
        if(recovery!=null)return 1;
        for(int days:new int[]{3,7,14,30,60})if(days>old)return days;
        return 60;
    }
    private static int floorStage(int interval){int stage=0;int[] days={1,3,7,14,30,60};for(int i=0;i<days.length;i++)if(interval>=days[i])stage=i;return stage;}
    private static int fixedStage(int interval){int[] days={1,3,7,14,30,60};for(int i=0;i<days.length;i++)if(interval<=days[i])return i;return 5;}
    private int stage(String owner,String id){List<Integer> stages=jdbc.queryForList("SELECT rs.stage FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id WHERE rs.owner_id=? AND k.source_content_id=?",Integer.class,owner,id);return stages.isEmpty()?0:stages.get(0);}
    private void log(String owner,String id,String attempt,String feedback,Map<String,Object> before,boolean complete,int streak,int beforeStage,Instant moment,String mode,ContentDetailView word){
        Map<String,Object> after=state(owner,id);String event=CryptoUtils.randomId();
        List<Map<String,Object>> schedules=jdbc.queryForList("SELECT rs.stage,rs.due_date FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id WHERE rs.owner_id=? AND k.source_content_id=?",owner,id);
        jdbc.update("INSERT INTO word_study_event(id,owner_id,content_id,attempt_id,feedback,source_kind,word_term,interval_mode,before_stage,after_stage,before_interval,after_interval,next_due,streak,completed,event_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",event,owner,id,attempt,feedback,str(before,"source_kind"),word.getWordTerm(),mode,beforeStage,schedules.isEmpty()?0:num(schedules.get(0),"stage"),num(before,"scheduled_interval"),num(after,"scheduled_interval"),schedules.isEmpty()?null:schedules.get(0).get("due_date"),streak,complete?1:0,Timestamp.from(moment));
        // Capture round identity at the action, never reinterpret historical events after a book switch.
        for(String round:jdbc.queryForList("SELECT r.id FROM vocabulary_book_study_record r WHERE r.owner_id=? AND EXISTS(SELECT 1 FROM vocabulary_book_word w WHERE w.book_id=r.book_id AND w.content_id=?) AND r.round_no=(SELECT MAX(r2.round_no) FROM vocabulary_book_study_record r2 WHERE r2.owner_id=r.owner_id AND r2.book_id=r.book_id)",String.class,owner,id))jdbc.update("INSERT INTO word_study_event_round(event_id,round_id) VALUES(?,?)",event,round);
    }
    private int legacyInterval(int stage){return new int[]{1,3,7,14,30,60}[Math.max(0,Math.min(5,stage))];}
    @Transactional public void reset(String owner,String book){lock(owner);jdbc.update("DELETE FROM word_study_state WHERE owner_id=? AND content_id IN (SELECT content_id FROM vocabulary_book_word WHERE book_id=?)",owner,book);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> decode(String value){try{return json.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private static String str(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:String.valueOf(v);}
    private static int num(Map<String,Object> row,String key){Object v=row.get(key);return v==null?0:((Number)v).intValue();}
    private static long lng(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    // Connector/J returns MySQL DATETIME as LocalDateTime; H2 returns Timestamp.
    // DATETIME is stored in the configured Asia/Shanghai database timezone.
    static Instant databaseInstant(Object value){
        if(value instanceof Timestamp)return ((Timestamp)value).toInstant();
        if(value instanceof LocalDateTime)return ((LocalDateTime)value).atZone(ZONE).toInstant();
        if(value instanceof Instant)return (Instant)value;
        if(value instanceof OffsetDateTime)return ((OffsetDateTime)value).toInstant();
        throw new IllegalArgumentException("Unsupported database time type: "+value.getClass().getName());
    }
    private static LocalDate day(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:v instanceof LocalDate?(LocalDate)v:((java.sql.Date)v).toLocalDate();}
    private ApiException fail(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
