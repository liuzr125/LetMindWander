package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.model.AdminStudyDayView;
import com.zhixing.model.AdminStudyRecordDetailView;
import com.zhixing.model.AdminStudyRecordPageView;
import com.zhixing.model.AdminStudyRecordView;
import com.zhixing.model.AdminStudyResetView;
import com.zhixing.model.AdminStudyWordPageView;
import com.zhixing.model.AdminStudyWordView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端「词书学习记录」：按「用户 × 词书 × 轮次」查看学习记录、每日明细与词条。
 * 一轮 = 一次选定这本词书：切走时冻结（结束时间 + 已学快照），切回会新开一轮并把上一轮的已学/未学带过来。
 * 数据来源：vocabulary_book_study_record（轮次）/ vocabulary_book_study_daily（每日动作）
 *          + learning_record × vocabulary_book_word（已学/掌握/学习中词数、本轮新学词数，实时统计）。
 */
@Service
public class AdminStudyRecordService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final List<String> STATUSES=Arrays.asList("all","learned","mastered","learning");
    private static final List<String> SCOPES=Arrays.asList("round","book");
    /** 已学词数：进行中的轮次看实时，已结束的轮次看结束快照（老记录不再被改写） */
    private static final String LIVE_LEARNED="(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status IN ('understood','mastered'))";
    private static final String LIVE_MASTERED="(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status='mastered')";
    private static final String LIVE_LEARNING="(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status='learning')";
    private static final String SELECT="SELECT r.id,r.owner_id,u.nickname,u.short_id,u.mobile,r.book_id,b.book_name,b.book_type,b.level_code," +
            "r.round_no,r.selected_at,r.ended_at,r.carried_learned_count,r.final_learned_count,r.first_studied_at,r.last_studied_at," +
            "r.study_count,r.reviewed_count,r.study_day_count," +
            "(SELECT COUNT(*) FROM vocabulary_book_word w WHERE w.book_id=r.book_id) total_words," +
            "CASE WHEN r.ended_at IS NULL THEN "+LIVE_LEARNED+" ELSE COALESCE(r.final_learned_count,"+LIVE_LEARNED+") END learned_words," +
            "CASE WHEN r.ended_at IS NULL THEN "+LIVE_MASTERED+" ELSE 0 END mastered_words," +
            "CASE WHEN r.ended_at IS NULL THEN "+LIVE_LEARNING+" ELSE 0 END learning_words," +
            "(SELECT COUNT(*) FROM vocabulary_book_reset_log l WHERE l.round_id=r.id) reset_times," +
            "(SELECT COALESCE(SUM(l.reset_count),0) FROM vocabulary_book_reset_log l WHERE l.round_id=r.id) reset_word_total," +
            "(SELECT MAX(l.reset_at) FROM vocabulary_book_reset_log l WHERE l.round_id=r.id) last_reset_at," +
            "EXISTS(SELECT 1 FROM user_vocabulary_book ub WHERE ub.owner_id=r.owner_id AND ub.book_id=r.book_id AND ub.state='active') current_book " +
            "FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id ";
    private final JdbcTemplate jdbc;
    public AdminStudyRecordService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public AdminStudyRecordPageView page(Integer rawPage,Integer rawPageSize,String rawKeyword,String rawBookId,String rawDateFrom,String rawDateTo){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条记录");
        String keyword=rawKeyword==null?"":rawKeyword.trim(),bookId=clean(rawBookId);
        if(keyword.length()>60)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_KEYWORD","搜索词最多 60 个字符");
        LocalDate from=date(rawDateFrom),to=date(rawDateTo);
        if(from!=null&&to!=null&&from.isAfter(to))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<Object>();
        if(bookId!=null){where.append(" AND r.book_id=?");args.add(bookId);}
        if(!keyword.isEmpty()){where.append(" AND (u.nickname LIKE ? OR u.short_id LIKE ? OR u.mobile LIKE ? OR b.book_name LIKE ?)");String like="%"+keyword+"%";for(int i=0;i<4;i++)args.add(like);}
        if(from!=null){where.append(" AND r.selected_at>=?");args.add(Timestamp.valueOf(from.atStartOfDay()));}
        if(to!=null){where.append(" AND r.selected_at<?");args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));}
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id"+where,Integer.class,args.toArray());
        List<Object> pageArgs=new ArrayList<Object>(args);pageArgs.add((page-1)*pageSize);pageArgs.add(pageSize);
        List<AdminStudyRecordView> items=new ArrayList<AdminStudyRecordView>();
        for(Map<String,Object> row:jdbc.queryForList(SELECT+where+" ORDER BY r.selected_at DESC,r.owner_id,r.book_id,r.round_no DESC LIMIT ?,?",pageArgs.toArray()))items.add(view(row));
        AdminStudyRecordPageView out=new AdminStudyRecordPageView();
        out.setTotal(total);out.setPage(page);out.setPageSize(pageSize);out.setTotalPages(total==0?0:(total+pageSize-1)/pageSize);
        out.setKeyword(keyword);out.setBookId(bookId);out.setDateFrom(from==null?null:from.toString());out.setDateTo(to==null?null:to.toString());
        out.setItems(items);
        Map<String,Object> summary=new LinkedHashMap<String,Object>();
        String summaryFrom=" FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id"+where;
        summary.put("records",total);
        summary.put("users",scalar("SELECT COUNT(DISTINCT r.owner_id)"+summaryFrom,args));
        summary.put("books",scalar("SELECT COUNT(DISTINCT r.book_id)"+summaryFrom,args));
        summary.put("ongoing",scalar("SELECT COUNT(*) FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id"+where+" AND r.ended_at IS NULL",args));
        summary.put("todayStudied",scalar("SELECT COUNT(*) FROM vocabulary_book_study_record r WHERE r.last_studied_at>=?",Arrays.<Object>asList(Timestamp.valueOf(LocalDate.now(BUSINESS_ZONE).atStartOfDay()))));
        out.setSummary(summary);
        return out;
    }

    public AdminStudyRecordDetailView detail(String rawRecordId){
        String recordId=clean(rawRecordId);
        if(recordId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_RECORD_ID","学习记录 ID 不能为空");
        List<Map<String,Object>> rows=jdbc.queryForList(SELECT+" WHERE r.id=?",recordId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ADMIN_STUDY_RECORD_NOT_FOUND","学习记录不存在或已被删除");
        AdminStudyRecordDetailView out=new AdminStudyRecordDetailView();
        AdminStudyRecordView record=view(rows.get(0));
        out.setRecord(record);out.setActiveDays(record.getStudyDayCount());
        out.setDays(days(record));
        out.setRoundNewWords(roundNewWords(record));
        out.setResets(resets(record.getId()));
        return out;
    }

    public AdminStudyWordPageView words(String rawRecordId,Integer rawPage,Integer rawPageSize,String rawStatus,String rawScope){
        String recordId=clean(rawRecordId);
        if(recordId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_RECORD_ID","学习记录 ID 不能为空");
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条词条");
        String status=rawStatus==null?"all":rawStatus.trim(),scope=rawScope==null?"round":rawScope.trim();
        if(!STATUSES.contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_WORD_STATUS","词条状态仅支持 all、learned、mastered 或 learning");
        if(!SCOPES.contains(scope))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_WORD_SCOPE","词条范围仅支持 round（本轮新学）或 book（这本书全部）");
        List<Map<String,Object>> rounds=jdbc.queryForList(SELECT+" WHERE r.id=?",recordId);
        if(rounds.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ADMIN_STUDY_RECORD_NOT_FOUND","学习记录不存在或已被删除");
        AdminStudyRecordView record=view(rounds.get(0));
        StringBuilder where=new StringBuilder(" WHERE lr.owner_id=? AND w.book_id=?");
        List<Object> args=new ArrayList<Object>();args.add(record.getOwnerId());args.add(record.getBookId());
        if("round".equals(scope)&&record.getSelectedAt()!=null){
            where.append(" AND COALESCE(lr.first_completed_at,lr.created_at)>=?");
            args.add(Timestamp.from(record.getSelectedAt()));
            if(record.getEndedAt()!=null){where.append(" AND COALESCE(lr.first_completed_at,lr.created_at)<?");args.add(Timestamp.from(record.getEndedAt()));}
        }
        String condition;
        if("learned".equals(status))condition=" AND lr.learning_status IN ('understood','mastered')";
        else if("mastered".equals(status))condition=" AND lr.learning_status='mastered'";
        else if("learning".equals(status))condition=" AND lr.learning_status='learning'";
        else condition=" AND lr.learning_status<>'unlearned'";
        where.append(condition);
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id"+where,Integer.class,args.toArray());
        List<Object> pageArgs=new ArrayList<Object>(args);pageArgs.add((page-1)*pageSize);pageArgs.add(pageSize);
        List<AdminStudyWordView> items=new ArrayList<AdminStudyWordView>();
        for(Map<String,Object> row:jdbc.queryForList(WORD_SELECT+where+" ORDER BY lr.first_completed_at DESC,lr.content_id LIMIT ?,?",pageArgs.toArray()))items.add(word(row));
        AdminStudyWordPageView out=new AdminStudyWordPageView();
        out.setTotal(total);out.setPage(page);out.setPageSize(pageSize);out.setTotalPages(total==0?0:(total+pageSize-1)/pageSize);
        out.setStatus(status);out.setScope(scope);out.setItems(items);
        return out;
    }

    /** 过滤下拉用的词书列表（只给本书记录页用，避免依赖「英语单词」菜单权限）。 */
    public List<Map<String,Object>> books(){
        List<Map<String,Object>> out=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT id,book_name,level_code FROM vocabulary_book WHERE state='active' ORDER BY sort_no,book_name")){
            Map<String,Object> item=new LinkedHashMap<String,Object>();
            item.put("id",text(row,"id"));item.put("name",text(row,"book_name"));item.put("levelLabel",levelLabel(text(row,"level_code")));
            out.add(item);
        }
        return out;
    }

    private static final String WORD_SELECT="SELECT lr.content_id,lr.learning_status,lr.familiarity_percent,lr.first_completed_at,lr.last_feedback_at," +
            "COALESCE(cv.word_term,cv.title) word,cv.meaning,cv.phonetic,cv.difficulty," +
            "CASE WHEN wn.id IS NULL THEN 0 ELSE 1 END in_notebook " +
            "FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            "JOIN learning_content c ON c.id=lr.content_id " +
            "LEFT JOIN content_version cv ON cv.id=COALESCE(lr.last_version_id,c.published_version_id) " +
            "LEFT JOIN word_notebook wn ON wn.owner_id=lr.owner_id AND wn.content_id=lr.content_id AND wn.state='active' ";

    /** 本轮新学词数：学习记录完成时间落在本轮窗口内的词条数。 */
    private Integer roundNewWords(AdminStudyRecordView record){
        if(record.getSelectedAt()==null)return 0;
        String sql="SELECT COUNT(*) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                "WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status IN ('understood','mastered') AND COALESCE(lr.first_completed_at,lr.created_at)>=?" +
                (record.getEndedAt()==null?"":" AND COALESCE(lr.first_completed_at,lr.created_at)<?");
        List<Object> args=new ArrayList<Object>();
        args.add(record.getOwnerId());args.add(record.getBookId());args.add(Timestamp.from(record.getSelectedAt()));
        if(record.getEndedAt()!=null)args.add(Timestamp.from(record.getEndedAt()));
        return jdbc.queryForObject(sql,Integer.class,args.toArray());
    }

    /** 「重新学习」记录：这一轮里每次重置的时间、词数与取消的复习排期数。 */
    private List<AdminStudyResetView> resets(String roundId){
        List<AdminStudyResetView> out=new ArrayList<AdminStudyResetView>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT reset_at,reset_count,paused_review_count FROM vocabulary_book_reset_log WHERE round_id=? ORDER BY reset_at DESC",roundId)){
            AdminStudyResetView item=new AdminStudyResetView();
            item.setResetAt(instant(row,"reset_at"));
            item.setResetCount(number(row,"reset_count"));item.setPausedReviewCount(number(row,"paused_review_count"));
            out.add(item);
        }
        return out;
    }

    /** 每日明细：本轮窗口内的「当天新学」（按学习记录实时统计）+ 记录下来的学习/复习动作次数。 */
    private List<AdminStudyDayView> days(AdminStudyRecordView record){
        List<AdminStudyDayView> out=new ArrayList<AdminStudyDayView>();
        Map<String,AdminStudyDayView> merged=new LinkedHashMap<String,AdminStudyDayView>();
        String newWordsSql="SELECT DATE(COALESCE(lr.first_completed_at,lr.created_at)) business_day,COUNT(DISTINCT lr.content_id) new_words " +
                "FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                "WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status IN ('understood','mastered') " +
                "AND COALESCE(lr.first_completed_at,lr.created_at)>=?" + (record.getEndedAt()==null?"":" AND COALESCE(lr.first_completed_at,lr.created_at)<?") +
                " GROUP BY DATE(COALESCE(lr.first_completed_at,lr.created_at))";
        List<Object> newArgs=new ArrayList<Object>();
        newArgs.add(record.getOwnerId());newArgs.add(record.getBookId());newArgs.add(Timestamp.from(record.getSelectedAt()==null?Instant.now():record.getSelectedAt()));
        if(record.getEndedAt()!=null)newArgs.add(Timestamp.from(record.getEndedAt()));
        for(Map<String,Object> row:jdbc.queryForList(newWordsSql,newArgs.toArray())){
            String day=String.valueOf(raw(row,"business_day"));
            AdminStudyDayView item=new AdminStudyDayView();
            item.setBusinessDate(day);item.setNewWordCount(number(row,"new_words"));item.setStudyCount(0);item.setReviewedCount(0);
            merged.put(day,item);
        }
        for(Map<String,Object> row:jdbc.queryForList("SELECT business_date,study_count,reviewed_count FROM vocabulary_book_study_daily WHERE round_id=?",record.getId())){
            String day=String.valueOf(raw(row,"business_date"));
            AdminStudyDayView item=merged.get(day);
            if(item==null){item=new AdminStudyDayView();item.setBusinessDate(day);item.setNewWordCount(0);merged.put(day,item);}
            item.setStudyCount(number(row,"study_count"));item.setReviewedCount(number(row,"reviewed_count"));
        }
        List<String> keys=new ArrayList<String>(merged.keySet());
        keys.sort((a,b)->b.compareTo(a));
        int limit=Math.min(keys.size(),90);
        for(int i=0;i<limit;i++)out.add(merged.get(keys.get(i)));
        return out;
    }

    private AdminStudyRecordView view(Map<String,Object> row){
        AdminStudyRecordView out=new AdminStudyRecordView();
        out.setId(text(row,"id"));
        out.setOwnerId(text(row,"owner_id"));out.setNickname(text(row,"nickname"));out.setShortId(text(row,"short_id"));
        out.setMobile(maskMobile(text(row,"mobile")));
        out.setBookId(text(row,"book_id"));out.setBookName(text(row,"book_name"));out.setBookType(text(row,"book_type"));
        String level=text(row,"level_code");out.setLevelCode(level);out.setLevelLabel(levelLabel(level));
        out.setRoundNo(number(row,"round_no"));out.setSelectedAt(instant(row,"selected_at"));out.setEndedAt(instant(row,"ended_at"));
        out.setCarriedLearnedCount(number(row,"carried_learned_count"));
        out.setResetTimes(number(row,"reset_times"));out.setResetWordTotal(number(row,"reset_word_total"));out.setLastResetAt(instant(row,"last_reset_at"));
        int total=number(row,"total_words")==null?0:number(row,"total_words");
        int learned=number(row,"learned_words")==null?0:number(row,"learned_words");
        out.setTotalWords(total);out.setLearnedWords(learned);
        out.setMasteredWords(number(row,"mastered_words"));out.setLearningWords(number(row,"learning_words"));
        out.setCompletionPercent(total==0?0:(int)Math.round(learned*100.0/total));
        out.setStudyDayCount(number(row,"study_day_count"));out.setStudyCount(number(row,"study_count"));out.setReviewedCount(number(row,"reviewed_count"));
        out.setOngoing(Boolean.TRUE.equals(bool(row,"current_book")));
        out.setCurrentBook(Boolean.TRUE.equals(bool(row,"current_book")));
        out.setCurrentRound(Boolean.TRUE.equals(bool(row,"current_book"))&&out.getEndedAt()==null);
        out.setFirstStudiedAt(instant(row,"first_studied_at"));out.setLastStudiedAt(instant(row,"last_studied_at"));
        return out;
    }

    private AdminStudyWordView word(Map<String,Object> row){
        AdminStudyWordView out=new AdminStudyWordView();
        out.setContentId(text(row,"content_id"));out.setWord(text(row,"word"));out.setMeaning(text(row,"meaning"));out.setPhonetic(text(row,"phonetic"));
        String difficulty=text(row,"difficulty");out.setDifficulty(difficulty);out.setDifficultyLabel("advanced".equals(difficulty)?"进阶":"入门");
        String status=text(row,"learning_status");out.setLearningStatus(status);out.setStatusLabel(statusLabel(status));
        out.setFamiliarityPercent(number(row,"familiarity_percent"));
        out.setInNotebook(Boolean.TRUE.equals(bool(row,"in_notebook")));
        out.setFirstCompletedAt(instant(row,"first_completed_at"));out.setLastFeedbackAt(instant(row,"last_feedback_at"));
        return out;
    }

    private String statusLabel(String status){
        if("mastered".equals(status))return "已掌握";
        if("understood".equals(status))return "已学会";
        if("learning".equals(status))return "学习中";
        return "未学习";
    }
    private String levelLabel(String level){
        if(level==null)return "未分级";
        if("primary".equals(level))return "小学";
        if("junior".equals(level))return "初中";
        if("senior".equals(level))return "高中";
        if("cet4".equals(level))return "大学英语四级";
        if("cet6".equals(level))return "大学英语六级";
        if("kaoyan".equals(level)||"postgraduate".equals(level)||"postgrad".equals(level))return "考研";
        if("ielts".equals(level))return "雅思";
        if("toefl".equals(level))return "托福";
        if("gre".equals(level))return "GRE";
        if("oxford3000".equals(level))return "Oxford 3000";
        if("oxford5000".equals(level))return "Oxford 5000";
        if("computer".equals(level))return "计算机英语";
        if("unclassified".equals(level))return "未分级";
        return level;
    }
    private Integer scalar(String sql,List<Object> args){return jdbc.queryForObject(sql,Integer.class,args.toArray());}
    private LocalDate date(String raw){String value=clean(raw);if(value==null)return null;try{return LocalDate.parse(value);}catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE","日期格式必须为 YYYY-MM-DD");}}
    private String maskMobile(String mobile){ if(mobile==null||mobile.isEmpty())return null; if(mobile.length()<7)return "***"; return mobile.substring(0,3)+"****"+mobile.substring(mobile.length()-4); }
    private Integer number(Map<String,Object> row,String key){Object value=raw(row,key);if(value==null)return null;return value instanceof Number?((Number)value).intValue():Integer.valueOf(String.valueOf(value));}
    private Boolean bool(Map<String,Object> row,String key){Object value=raw(row,key);if(value==null)return null;if(value instanceof Boolean)return (Boolean)value;if(value instanceof Number)return ((Number)value).intValue()!=0;return Boolean.valueOf(String.valueOf(value));}
    private String text(Map<String,Object> row,String key){Object value=raw(row,key);return value==null?null:String.valueOf(value);}
    private Instant instant(Map<String,Object> row,String key){Object value=raw(row,key);if(value instanceof Timestamp)return ((Timestamp)value).toInstant();
        if(value instanceof java.time.LocalDateTime)return ((java.time.LocalDateTime)value).atZone(BUSINESS_ZONE).toInstant();
        if(value instanceof java.time.LocalDate)return ((java.time.LocalDate)value).atStartOfDay(BUSINESS_ZONE).toInstant();
        return value instanceof Instant?(Instant)value:null;}
    private Object raw(Map<String,Object> row,String key){Object value=row.get(key);if(value==null)for(Map.Entry<String,Object> entry:row.entrySet())if(entry.getKey().equalsIgnoreCase(key))value=entry.getValue();return value;}
    private String clean(String raw){String value=raw==null?null:raw.trim();return value==null||value.isEmpty()?null:value;}
}
