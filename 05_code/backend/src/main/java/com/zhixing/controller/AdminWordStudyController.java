package com.zhixing.controller;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.WordStudyPreferenceService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/admin/study-records/word-study")
public class AdminWordStudyController {
 private final JdbcTemplate jdbc;private final WordStudyPreferenceService preferences;
 public AdminWordStudyController(JdbcTemplate jdbc,WordStudyPreferenceService preferences){this.jdbc=jdbc;this.preferences=preferences;}
 @GetMapping("/mode") public Map<String,Object> mode(){return Collections.<String,Object>singletonMap("intervalMode",preferences.intervalMode(null));}
 @PutMapping("/mode") @Transactional public Map<String,Object> save(@RequestBody Map<String,Object> body,HttpServletRequest request){
  AdminPrincipal actor=(AdminPrincipal)request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);
  if(actor==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","需要管理员会话");
  String next=String.valueOf(body.get("intervalMode"));if(!Arrays.asList("fixed","flexible").contains(next))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_INTERVAL_MODE","仅支持固定或灵活间隔");
  jdbc.update("INSERT INTO word_study_mode(setting_id,interval_mode) VALUES(1,'fixed') ON DUPLICATE KEY UPDATE setting_id=1");
  String before=jdbc.queryForObject("SELECT interval_mode FROM word_study_mode WHERE setting_id=1 FOR UPDATE",String.class);
  jdbc.update("UPDATE word_study_mode SET interval_mode=? WHERE setting_id=1",next);
  if(!before.equals(next))jdbc.update("INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(?,?,?,'word_study_mode','global','succeeded',?,?)",CryptoUtils.randomId(),actor.getAccountId(),"word_study_mode_update","{\"before\":\""+before+"\",\"after\":\""+next+"\"}",Timestamp.from(Instant.now().plusSeconds(180L*86400)));
  return mode();
 }
 @GetMapping("/events") public Map<String,Object> events(@RequestParam(required=false)String roundId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){
  if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","每页 1 至 100 条");
  String from=" FROM word_study_event e JOIN app_user u ON u.id=e.owner_id JOIN learning_content lc ON lc.id=e.content_id JOIN content_version cv ON cv.id=lc.published_version_id";
  List<Object> args=new ArrayList<Object>();String where="";if(roundId!=null){where=" WHERE EXISTS(SELECT 1 FROM word_study_event_round er WHERE er.event_id=e.id AND er.round_id=?)";args.add(roundId);}
  int total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Integer.class,args.toArray());args.add(pageSize);args.add((page-1)*pageSize);
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT e.*,u.nickname,u.short_id"+from+where+" ORDER BY e.event_at DESC,e.id LIMIT ? OFFSET ?",args.toArray());
  Map<String,List<Map<String,Object>>> rounds=new HashMap<String,List<Map<String,Object>>>();
  if(!rows.isEmpty()){
   List<Object> eventIds=new ArrayList<Object>();for(Map<String,Object> row:rows)eventIds.add(row.get("id"));
   String placeholders=String.join(",",Collections.nCopies(eventIds.size(),"?"));
   for(Map<String,Object> round:jdbc.queryForList("SELECT er.event_id,r.id,r.round_no,b.book_name FROM word_study_event_round er JOIN vocabulary_book_study_record r ON r.id=er.round_id JOIN vocabulary_book b ON b.id=r.book_id WHERE er.event_id IN ("+placeholders+") ORDER BY er.event_id,r.id",eventIds.toArray())){
    String eventId=String.valueOf(round.remove("event_id"));rounds.computeIfAbsent(eventId,k->new ArrayList<Map<String,Object>>()).add(round);
   }
  }
  for(Map<String,Object> row:rows)row.put("rounds",rounds.getOrDefault(String.valueOf(row.get("id")),Collections.<Map<String,Object>>emptyList()));
  Map<String,Object> out=new LinkedHashMap<String,Object>();out.put("items",rows);out.put("total",total);out.put("page",page);out.put("pageSize",pageSize);return out;
 }
}
