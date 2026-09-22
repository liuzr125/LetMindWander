package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.model.AdminStudyDayView;
import com.zhixing.model.AdminStudyRecordDetailView;
import com.zhixing.model.AdminStudyRecordPageView;
import com.zhixing.model.AdminStudyRecordView;
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
 * 管理端「词书学习记录」：查看每个用户每本英语词书的学习记录、每日明细与已学词条。
 * 数据来源：vocabulary_book_study_record / vocabulary_book_study_daily（学习时写入）
 *          + learning_record × vocabulary_book_word（已学/掌握/学习中词数、首次学会时间，均实时统计）。
 */
@Service
public class AdminStudyRecordService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final List<String> STATUSES=Arrays.asList("all","learned","mastered","learning");
    private static final String SELECT="SELECT r.owner_id,u.nickname,u.short_id,u.mobile,r.book_id,b.book_name,b.book_type,b.level_code," +
            "r.first_studied_at,r.last_studied_at,r.study_count,r.reviewed_count,r.study_day_count," +
            "(SELECT COUNT(*) FROM vocabulary_book_word w WHERE w.book_id=r.book_id) total_words," +
            "(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status IN ('understood','mastered')) learned_words," +
            "(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status='mastered') mastered_words," +
            "(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
            " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status='learning') learning_words," +
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
        if(from!=null){where.append(" AND r.last_studied_at>=?");args.add(Timestamp.valueOf(from.atStartOfDay()));}
        if(to!=null){where.append(" AND r.last_studied_at<?");args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));}
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id"+where,Integer.class,args.toArray());
        List<Object> pageArgs=new ArrayList<Object>(args);pageArgs.add((page-1)*pageSize);pageArgs.add(pageSize);
        List<AdminStudyRecordView> items=new ArrayList<AdminStudyRecordView>();
        for(Map<String,Object> row:jdbc.queryForList(SELECT+where+" ORDER BY r.last_studied_at DESC,r.owner_id,r.book_id LIMIT ?,?",pageArgs.toArray()))items.add(view(row));
        AdminStudyRecordPageView out=new AdminStudyRecordPageView();
        out.setTotal(total);out.setPage(page);out.setPageSize(pageSize);out.setTotalPages(total==0?0:(total+pageSize-1)/pageSize);
        out.setKeyword(keyword);out.setBookId(bookId);out.setDateFrom(from==null?null:from.toString());out.setDateTo(to==null?null:to.toString());
        out.setItems(items);
        Map<String,Object> summary=new LinkedHashMap<String,Object>();
        String summaryFrom=" FROM vocabulary_book_study_record r JOIN app_user u ON u.id=r.owner_id JOIN vocabulary_book b ON b.id=r.book_id"+where;
        summary.put("records",total);
        summary.put("users",scalar("SELECT COUNT(DISTINCT r.owner_id)"+summaryFrom,args));
        summary.put("books",scalar("SELECT COUNT(DISTINCT r.book_id)"+summaryFrom,args));
        summary.put("todayStudied",scalar("SELECT COUNT(*) FROM vocabulary_book_study_record r WHERE r.last_studied_at>=?",Arrays.<Object>asList(Timestamp.valueOf(LocalDate.now(BUSINESS_ZONE).atStartOfDay()))));
        out.setSummary(summary);
        return out;
    }

    public AdminStudyRecordDetailView detail(String rawOwnerId,String rawBookId){
        String ownerId=clean(rawOwnerId),bookId=clean(rawBookId);
        if(ownerId==null||bookId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_RECORD_KEY","用户与词书都必须指定");
        List<Map<String,Object>> rows=jdbc.queryForList(SELECT+" WHERE r.owner_id=? AND r.book_id=?",ownerId,bookId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ADMIN_STUDY_RECORD_NOT_FOUND","该用户在这本词书上还没有学习记录");
        AdminStudyRecordDetailView out=new AdminStudyRecordDetailView();
        AdminStudyRecordView record=view(rows.get(0));
        out.setRecord(record);out.setActiveDays(record.getStudyDayCount());
        out.setDays(days(ownerId,bookId));
        return out;
    }

    public AdminStudyWordPageView words(String rawOwnerId,String rawBookId,Integer rawPage,Integer rawPageSize,String rawStatus){
        String ownerId=clean(rawOwnerId),bookId=clean(rawBookId);
        if(ownerId==null||bookId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_RECORD_KEY","用户与词书都必须指定");
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条词条");
        String status=rawStatus==null?"all":rawStatus.trim();
        if(!STATUSES.contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDY_WORD_STATUS","词条状态仅支持 all、learned、mastered 或 learning");
        String condition;
        if("learned".equals(status))condition=" AND lr.learning_status IN ('understood','mastered')";
        else if("mastered".equals(status))condition=" AND lr.learning_status='mastered'";
        else if("learning".equals(status))condition=" AND lr.learning_status='learning'";
        else condition=" AND lr.learning_status<>'unlearned'";
        String where=" WHERE lr.owner_id=? AND w.book_id=?"+condition;
        List<Object> args=new ArrayList<Object>();args.add(ownerId);args.add(bookId);
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id AND w.book_id=? WHERE lr.owner_id=?"+condition,Integer.class,bookId,ownerId);
        List<Object> pageArgs=new ArrayList<Object>(args);pageArgs.add((page-1)*pageSize);pageArgs.add(pageSize);
        List<AdminStudyWordView> items=new ArrayList<AdminStudyWordView>();
        for(Map<String,Object> row:jdbc.queryForList(WORD_SELECT+where+" ORDER BY lr.first_completed_at DESC,lr.content_id LIMIT ?,?",pageArgs.toArray()))items.add(word(row));
        AdminStudyWordPageView out=new AdminStudyWordPageView();
        out.setTotal(total);out.setPage(page);out.setPageSize(pageSize);out.setTotalPages(total==0?0:(total+pageSize-1)/pageSize);
        out.setStatus(status);out.setItems(items);
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

    private List<AdminStudyDayView> days(String ownerId,String bookId){
        // day 在 H2 里是保留字，统一用 business_day 作为派生列名
        String sql="SELECT business_day,SUM(new_words) new_words,SUM(study_count) study_count,SUM(reviewed_count) reviewed_count FROM (" +
                "SELECT DATE(COALESCE(lr.first_completed_at,lr.created_at)) business_day,COUNT(DISTINCT lr.content_id) new_words,0 study_count,0 reviewed_count " +
                "  FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                " WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status IN ('understood','mastered') " +
                " GROUP BY DATE(COALESCE(lr.first_completed_at,lr.created_at)) " +
                "UNION ALL SELECT d.business_date business_day,0 new_words,d.study_count,d.reviewed_count FROM vocabulary_book_study_daily d WHERE d.owner_id=? AND d.book_id=?" +
                ") t GROUP BY business_day ORDER BY business_day DESC LIMIT 90";
        List<AdminStudyDayView> out=new ArrayList<AdminStudyDayView>();
        for(Map<String,Object> row:jdbc.queryForList(sql,ownerId,bookId,ownerId,bookId)){
            AdminStudyDayView item=new AdminStudyDayView();
            item.setBusinessDate(String.valueOf(raw(row,"business_day")));
            item.setNewWordCount(number(row,"new_words"));item.setStudyCount(number(row,"study_count"));item.setReviewedCount(number(row,"reviewed_count"));
            out.add(item);
        }
        return out;
    }

    private AdminStudyRecordView view(Map<String,Object> row){
        AdminStudyRecordView out=new AdminStudyRecordView();
        out.setOwnerId(text(row,"owner_id"));out.setNickname(text(row,"nickname"));out.setShortId(text(row,"short_id"));
        out.setMobile(maskMobile(text(row,"mobile")));
        out.setBookId(text(row,"book_id"));out.setBookName(text(row,"book_name"));out.setBookType(text(row,"book_type"));
        String level=text(row,"level_code");out.setLevelCode(level);out.setLevelLabel(levelLabel(level));
        int total=number(row,"total_words")==null?0:number(row,"total_words");
        int learned=number(row,"learned_words")==null?0:number(row,"learned_words");
        int mastered=number(row,"mastered_words")==null?0:number(row,"mastered_words");
        int learning=number(row,"learning_words")==null?0:number(row,"learning_words");
        out.setTotalWords(total);out.setLearnedWords(learned);out.setMasteredWords(mastered);out.setLearningWords(learning);
        out.setCompletionPercent(total==0?0:(int)Math.round(learned*100.0/total));
        out.setStudyDayCount(number(row,"study_day_count"));out.setStudyCount(number(row,"study_count"));out.setReviewedCount(number(row,"reviewed_count"));
        out.setCurrentBook(Boolean.TRUE.equals(bool(row,"current_book")));
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
    private Integer number(Map<String,Object> row,String key){Object raw=raw(row,key);if(raw==null)return null;return raw instanceof Number?((Number)raw).intValue():Integer.valueOf(String.valueOf(raw));}
    private Boolean bool(Map<String,Object> row,String key){Object raw=raw(row,key);if(raw==null)return null;if(raw instanceof Boolean)return (Boolean)raw;if(raw instanceof Number)return ((Number)raw).intValue()!=0;return Boolean.valueOf(String.valueOf(raw));}
    private String text(Map<String,Object> row,String key){Object raw=raw(row,key);return raw==null?null:String.valueOf(raw);}
    private Instant instant(Map<String,Object> row,String key){Object raw=raw(row,key);if(raw instanceof Timestamp)return ((Timestamp)raw).toInstant();
        if(raw instanceof java.time.LocalDateTime)return ((java.time.LocalDateTime)raw).atZone(BUSINESS_ZONE).toInstant();
        if(raw instanceof java.time.LocalDate)return ((java.time.LocalDate)raw).atStartOfDay(BUSINESS_ZONE).toInstant();
        return raw instanceof Instant?(Instant)raw:null;}
    private Object raw(Map<String,Object> row,String key){Object value=row.get(key);if(value==null)for(Map.Entry<String,Object> entry:row.entrySet())if(entry.getKey().equalsIgnoreCase(key))value=entry.getValue();return value;}
    private String clean(String raw){String value=raw==null?null:raw.trim();return value==null||value.isEmpty()?null:value;}
}
