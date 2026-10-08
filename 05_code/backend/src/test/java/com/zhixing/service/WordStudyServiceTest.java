package com.zhixing.service;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.MaterialMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:wordstudy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc @Transactional
class WordStudyServiceTest {
 @Autowired JdbcTemplate jdbc;@SpyBean WordStudyService study;@SpyBean WordStudyPreferenceService prefs;@SpyBean com.zhixing.mapper.ContentMapper contents;@Autowired MaterialMapper materials;@Autowired com.zhixing.mapper.VocabularyBookMapper books;@Autowired MockMvc mvc;@Autowired SessionService sessions;@Autowired DailyTaskService daily;
 final String owner="wsuser00000000000000000000000001", book="wsbook00000000000000000000000001", source="wssrc000000000000000000000000001";
 LocalDate date=LocalDate.of(2026,10,8);Instant instant;
 @BeforeEach void setup(){
  clock(LocalDate.of(2026,10,8));
  jdbc.update("INSERT INTO app_user(id,wx_app_id,wx_open_id,nickname) VALUES(?,?,?,?)",owner,"wordstudy",owner,"词汇测试");
  jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",source,"test","test");
  jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,state) VALUES(?,?,?,'k12','primary',8,'active')",book,book,"test book");
  jdbc.update("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state,daily_new_limit) VALUES(?,?,?,'active',5)",CryptoUtils.randomId(),owner,book);
  for(int n=1;n<=8;n++){String id=id(n),version=String.format("wsvers%026d",n),term="word"+n;
   jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'word',?,?,?,'published',?,?,CURRENT_TIMESTAMP)",id,source,CryptoUtils.sha256(id),CryptoUtils.sha256(term),version,version);
   jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,'intro',30,?,?,'test',?,'approved',?)",version,id,term,term,term,"释义",CryptoUtils.sha256(term),owner);
   jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no) VALUES(?,?,?,?)",CryptoUtils.randomId(),book,id,n);
  }
 }
 void clock(LocalDate day){date=day;instant=day.atTime(10,0).atZone(WordStudyService.ZONE).toInstant();doReturn(day).when(study).today();doReturn(instant).when(study).now();}
 String id(int n){return String.format("wsword%026d",n);}
 Map<String,Object> queue(int n){return study.queue(owner,"all",id(n));}
 Map<String,Object> request(int n,String f){Map<String,Object> b=new LinkedHashMap<>();b.put("attemptId",UUID.randomUUID().toString());b.put("feedback",f);b.put("expectedVersion",jdbc.queryForObject("SELECT version_no FROM word_study_state WHERE owner_id=? AND content_id=?",Integer.class,owner,id(n)));b.put("businessDate",date.toString());return b;}
 Map<String,Object> feedback(int n,String f){return study.feedback(owner,id(n),request(n,f));}
 void ready(int n){jdbc.update("UPDATE word_study_state SET eligible_at=? WHERE owner_id=? AND content_id=?",Timestamp.from(instant.minusSeconds(1)),owner,id(n));}
 void master(int n){queue(n);assertEquals(true,feedback(n,"remember").get("completed"));}
 Map<String,Object> schedule(int n){return jdbc.queryForMap("SELECT rs.* FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id WHERE rs.owner_id=? AND k.source_content_id=?",owner,id(n));}
 LocalDate due(int n){return ((java.sql.Date)schedule(n).get("due_date")).toLocalDate();}
 void dueQueue(int n){clock(due(n));study.queue(owner,"review",null);}
 @Test void progressCountsOnlyPublishedApprovedDistinctWords(){
  master(1);master(2);master(3);
  jdbc.update("UPDATE content_version SET review_status='pending' WHERE content_id=?",id(2));
  jdbc.update("UPDATE learning_content SET state='withdrawn' WHERE id=?",id(3));
  com.zhixing.model.VocabularyBookProgressView progress=books.selectCurrentProgress(owner);
  assertEquals(6,progress.getTotalCount());assertEquals(1,progress.getLearnedCount());
 }
 @Test void feedbackReusesContentAndModeReads(){
  queue(1);org.mockito.Mockito.clearInvocations(contents,prefs);
  feedback(1,"remember");
  org.mockito.Mockito.verify(contents,org.mockito.Mockito.times(1)).selectDetail(owner,id(1));
  org.mockito.Mockito.verify(prefs,org.mockito.Mockito.times(1)).intervalMode(owner);
 }
 @Test void oneRememberCompletesAndReceiptIsIdempotent(){queue(1);Map<String,Object> b=request(1,"remember"),response=study.feedback(owner,id(1),b);assertEquals(true,response.get("completed"));assertEquals(response,study.feedback(owner,id(1),b));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM word_study_event WHERE owner_id=?",Integer.class,owner));assertThrows(ApiException.class,()->feedback(1,"remember"));assertEquals(date.plusDays(1),due(1));assertEquals(0,study.queue(owner,"all",id(1)).get("consolidationCount"));}
 @Test void fiveDistinctRemembersCountFiveCompletedWords(){String pack=pack(date);for(int n=1;n<=5;n++)jdbc.update("INSERT INTO daily_task(id,owner_id,package_id,task_type,title_snapshot,target_key,content_id,status,estimated_seconds,sort_no) VALUES(?,?,?,'word','word',?,?,'TODO',30,?)",CryptoUtils.randomId(),owner,pack,"word:"+id(n),id(n),n);for(int n=1;n<=5;n++){queue(n);assertEquals(true,feedback(n,"remember").get("completed"));}assertEquals(5,jdbc.queryForObject("SELECT COUNT(DISTINCT content_id) FROM learning_record WHERE owner_id=? AND learning_status='understood'",Integer.class,owner));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM word_study_state WHERE owner_id=? AND pending=1",Integer.class,owner));assertEquals(0,study.queue(owner,"review",null).get("remaining"));assertEquals(5,books.selectCurrentProgress(owner).getLearnedCount());assertEquals(5,daily.get(owner,date).getDoneCount());}
 @Test void failedWordSurvivesMidnightAndOneRememberCompletes(){queue(1);feedback(1,"unclear");Map<String,Object> stale=request(1,"remember");clock(date.plusDays(1));assertThrows(ApiException.class,()->study.feedback(owner,id(1),stale));queue(1);assertEquals(0,jdbc.queryForObject("SELECT streak FROM word_study_state WHERE owner_id=?",Integer.class,owner));assertEquals(true,feedback(1,"remember").get("completed"));}
 @Test void fixedCalendarAndPermanentMaintenance(){LocalDate start=date;master(1);int[] intervals={3,7,14,30,60,60},cumulative={4,11,25,55,115,175};for(int i=0;i<intervals.length;i++){dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(intervals[i]),due(1));assertEquals(start.plusDays(cumulative[i]),due(1));assertEquals("active",schedule(1).get("state"));}}
 @Test void fixedFuzzyPreservesStageAndUnclearResets(){master(1);dueQueue(1);feedback(1,"remember");dueQueue(1);int stage=((Number)schedule(1).get("stage")).intValue();feedback(1,"fuzzy");ready(1);feedback(1,"remember");assertEquals(stage,((Number)schedule(1).get("stage")).intValue());assertEquals(date.plusDays(1),due(1));dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(7),due(1));dueQueue(1);feedback(1,"unclear");ready(1);feedback(1,"remember");assertEquals(0,((Number)schedule(1).get("stage")).intValue());assertEquals(date.plusDays(1),due(1));}
 @Test void flexibleAndModeSwitchUseActualInterval(){master(1);LocalDate before=due(1);jdbc.update("INSERT INTO word_study_mode(setting_id,interval_mode) VALUES(1,'flexible')");assertEquals(before,due(1));dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(2),due(1));dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(4),due(1));dueQueue(1);feedback(1,"fuzzy");ready(1);feedback(1,"remember");assertEquals(date.plusDays(2),due(1));jdbc.update("UPDATE word_study_mode SET interval_mode='fixed'");dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(3),due(1));assertEquals(60,WordStudyService.nextInterval("flexible",true,null,40));assertEquals(3,WordStudyService.nextInterval("flexible",true,"fuzzy",5));}
 @Test void overdueAndSameDayReplay(){master(1);clock(date.plusDays(10));study.queue(owner,"review",null);Map<String,Object> b=request(1,"remember");study.feedback(owner,id(1),b);assertEquals(date.plusDays(3),due(1));study.feedback(owner,id(1),b);study.queue(owner,"review",null);assertThrows(ApiException.class,()->feedback(1,"remember"));}
 @Test void pendingAndMasteredNeverConsumeNewQuota(){queue(1);feedback(1,"unclear");master(2);assertFalse(materials.selectWordContent(owner,book,"intro",8,date.plusDays(1)).stream().anyMatch(w->id(1).equals(w.getId())||id(2).equals(w.getId())));}
 @Test void administratorOnlyGlobalModeAndAudit() throws Exception {assertEquals("fixed",prefs.intervalMode(owner));assertThrows(ApiException.class,()->prefs.update(owner,Collections.<String,Object>singletonMap("intervalMode","flexible")));mvc.perform(put("/api/admin/study-records/word-study/mode").contentType(MediaType.APPLICATION_JSON).content("{\"intervalMode\":\"flexible\"}")).andExpect(status().isUnauthorized());mvc.perform(put("/api/admin/study-records/word-study/mode").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"intervalMode\":\"flexible\"}")).andExpect(status().isOk());assertEquals("flexible",prefs.intervalMode("other-user"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='word_study_mode_update'",Integer.class));mvc.perform(get("/api/admin/study-records/word-study/events")).andExpect(status().isUnauthorized());}
 @Test void switchingKeepsPendingAndDue() throws Exception {queue(1);feedback(1,"fuzzy");master(2);LocalDate before=due(2);mvc.perform(put("/api/admin/study-records/word-study/mode").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"intervalMode\":\"flexible\"}")).andExpect(status().isOk());assertEquals(before,due(2));assertEquals(0,jdbc.queryForObject("SELECT streak FROM word_study_state WHERE owner_id=? AND content_id=?",Integer.class,owner,id(1)));ready(1);feedback(1,"remember");assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM word_study_event WHERE owner_id=? AND content_id=? AND interval_mode='flexible'",Integer.class,owner,id(1)));}
 @Test void publicApiCannotBypassRecallOrWriteMode() throws Exception {
  String token=sessions.createForUser(owner).getAccessToken();
  mvc.perform(post("/api/learning/contents/{id}/understood",id(1)).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORD_STUDY_REQUIRED"));
  mvc.perform(put("/api/learning/word-study-preferences").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"intervalMode\":\"flexible\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/study-records/word-study/mode").header("X-Admin-Token",token).contentType(MediaType.APPLICATION_JSON).content("{\"intervalMode\":\"flexible\"}")).andExpect(status().isUnauthorized());
 }
 @Test void pastDoneReviewDoesNotSuppressLaterDueTask() {
  master(1);String knowledge=String.valueOf(schedule(1).get("knowledge_id")), pack=CryptoUtils.randomId();
  jdbc.update("INSERT INTO daily_package(id,owner_id,business_date,plan_id,budget_seconds) VALUES(?,?,?,?,600)",pack,owner,java.sql.Date.valueOf(date),CryptoUtils.randomId());
  jdbc.update("INSERT INTO daily_task(id,owner_id,package_id,task_type,title_snapshot,target_key,knowledge_id,status,estimated_seconds,sort_no) VALUES(?,?,?,'review','word',?,?,'DONE',30,1)",CryptoUtils.randomId(),owner,pack,"review:"+knowledge,knowledge);
  assertTrue(materials.selectDueReviews(owner,date,5).isEmpty());assertEquals(1,materials.selectDueReviews(owner,due(1),5).size());
 }
 @Test void hiddenFirstStateIsResumedAcrossBookSwitchAndFreeBrowsingWorks() {
  queue(1);feedback(1,"fuzzy");String other=CryptoUtils.randomId();jdbc.update("UPDATE user_vocabulary_book SET state='paused' WHERE owner_id=?",owner);
  jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,state) VALUES(?,?,?,'k12','primary',0,'active')",other,other,"other");
  jdbc.update("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state,daily_new_limit) VALUES(?,?,?,'active',5)",CryptoUtils.randomId(),owner,other);
  Map<String,Object> resumed=study.queue(owner,"all",null);assertEquals(1,resumed.get("consolidationCount"));assertEquals(0,jdbc.queryForObject("SELECT streak FROM word_study_state WHERE owner_id=?",Integer.class,owner));
  jdbc.update("UPDATE user_vocabulary_book SET state='paused' WHERE owner_id=?",owner);Map<String,Object> free=queue(2);assertNotNull(free.get("current"));
 }

 @Test void pausedReviewIsHiddenAndCannotBeSubmitted() {
  master(1);dueQueue(1);jdbc.update("UPDATE review_schedule SET state='paused' WHERE owner_id=?",owner);
  assertEquals(0,study.queue(owner,"review",null).get("remaining"));assertThrows(ApiException.class,()->feedback(1,"remember"));
 }

 String pack(LocalDate day){String id=CryptoUtils.randomId();jdbc.update("INSERT INTO daily_package(id,owner_id,business_date,plan_id,budget_seconds) VALUES(?,?,?,?,600)",id,owner,java.sql.Date.valueOf(day),CryptoUtils.randomId());return id;}
 String reviewTask(String pack,String knowledge){String task=CryptoUtils.randomId();jdbc.update("INSERT INTO daily_task(id,owner_id,package_id,task_type,title_snapshot,target_key,knowledge_id,status,estimated_seconds,sort_no) VALUES(?,?,?,'review','word',?,?,'TODO',30,1)",task,owner,pack,"review:"+knowledge,knowledge);return task;}
 @Test void manuallyScheduledUnlearnedWordIsReviewOnlyAndRequiresConsolidation(){
  String knowledge=CryptoUtils.randomId(),schedule=CryptoUtils.randomId();
  jdbc.update("INSERT INTO knowledge_item(id,owner_id,item_type,title,body,search_text,source_content_id,version_no,visibility,state) VALUES(?,?,'content_ref','word','','word',?,1,'private','active')",knowledge,owner,id(1));
  jdbc.update("INSERT INTO review_schedule(id,owner_id,knowledge_id,state,stage,due_date) VALUES(?,?,?,'active',0,?)",schedule,owner,knowledge,java.sql.Date.valueOf(date));
  assertFalse(materials.selectWordContent(owner,book,"intro",8,date).stream().anyMatch(w->id(1).equals(w.getId())));
  Map<String,Object> q=study.queue(owner,"review",null);assertEquals(1,q.get("remaining"));assertEquals(true,feedback(1,"remember").get("completed"));
 }
 @Test void pendingReviewBindsLaterTaskAndDirectCompletionIsRejected(){
  master(1);dueQueue(1);String task=reviewTask(pack(date),String.valueOf(schedule(1).get("knowledge_id")));
  com.zhixing.dto.TaskEventRequest start=new com.zhixing.dto.TaskEventRequest();start.setEventType("start");start.setExpectedVersion(1);daily.event(owner,task,start);
  com.zhixing.dto.TaskEventRequest complete=new com.zhixing.dto.TaskEventRequest();complete.setEventType("complete");complete.setExpectedVersion(2);assertThrows(ApiException.class,()->daily.event(owner,task,complete));
  study.queue(owner,"review",null);assertEquals(task,jdbc.queryForObject("SELECT task_id FROM word_study_state WHERE owner_id=?",String.class,owner));feedback(1,"remember");assertEquals("DONE",jdbc.queryForObject("SELECT status FROM daily_task WHERE id=?",String.class,task));
 }
 @Test void overdueAuditKeepsOriginalDueDate(){master(1);LocalDate original=due(1);clock(original.plusDays(5));study.queue(owner,"review",null);feedback(1,"remember");assertEquals(original,jdbc.queryForObject("SELECT before_due_date FROM review_feedback WHERE owner_id=?",java.sql.Date.class,owner).toLocalDate());}

 @Test void flexibleToFixedFuzzyMapsActualIntervalWithoutSkippingNextStep(){
  master(1);jdbc.update("INSERT INTO word_study_mode(setting_id,interval_mode) VALUES(1,'flexible')");dueQueue(1);feedback(1,"remember");dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(4),due(1));
  jdbc.update("UPDATE word_study_mode SET interval_mode='fixed'");dueQueue(1);feedback(1,"fuzzy");ready(1);feedback(1,"remember");assertEquals(date.plusDays(1),due(1));dueQueue(1);feedback(1,"remember");assertEquals(date.plusDays(7),due(1));
 }

 @Test void mysqlDatetimeAndH2TimestampHaveSameSpacingBoundary(){
  Instant eligible=instant.plusSeconds(120);
  Object[] databaseValues={Timestamp.from(eligible),LocalDateTime.ofInstant(eligible,WordStudyService.ZONE)};
  for(Object databaseValue:databaseValues){
   Map<String,Object> row=new HashMap<>();row.put("eligible_position",4L);row.put("eligible_at",databaseValue);
   assertEquals(eligible,WordStudyService.databaseInstant(databaseValue));
   assertFalse(study.ready(row,0));assertTrue(study.ready(row,4));
   doReturn(eligible).when(study).now();assertTrue(study.ready(row,0));
   doReturn(instant).when(study).now();
  }
 }
}
