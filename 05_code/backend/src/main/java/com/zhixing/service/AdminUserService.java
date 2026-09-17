package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.model.AdminUserView;
import com.zhixing.model.AdminUserLearningView;
import com.zhixing.model.LearningPageView;
import com.zhixing.model.PlanView;
import com.zhixing.model.VocabularyBookProgressView;
import com.zhixing.model.WordNotebookSummaryView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AdminUserService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final PlanService plans;
    private final VocabularyBookService vocabularyBooks;
    private final LearningService learning;
    public AdminUserService(JdbcTemplate jdbc,PlanService plans,VocabularyBookService vocabularyBooks,LearningService learning){this.jdbc=jdbc;this.plans=plans;this.vocabularyBooks=vocabularyBooks;this.learning=learning;}

    public AdminUserPageView page(Integer rawPage,Integer rawPageSize,String rawKeyword,String rawStatus){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 个账号");
        String keyword=rawKeyword==null?"":rawKeyword.trim(),status=rawStatus==null?"all":rawStatus.trim();
        if(keyword.length()>80)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_KEYWORD","搜索词最多 80 个字符");
        if(!"all".equals(status)&&!"active".equals(status)&&!"disabled".equals(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_USER_STATUS","账号状态不正确");
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<Object>();
        if(!"all".equals(status)){where.append(" AND status=?");args.add(status);}
        if(!keyword.isEmpty()){where.append(" AND (LOWER(nickname) LIKE CONCAT('%',LOWER(?),'%') OR LOWER(COALESCE(short_id,'')) LIKE CONCAT('%',LOWER(?),'%') OR COALESCE(mobile,'') LIKE CONCAT('%',?,'%'))");args.add(keyword);args.add(keyword);args.add(keyword);}
        Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM app_user"+where,args.toArray(),Integer.class);
        List<Object> pageArgs=new ArrayList<Object>(args);pageArgs.add(pageSize);pageArgs.add((page-1)*pageSize);
        List<AdminUserView> items=jdbc.query("SELECT id,seq_no,short_id,nickname,mobile,status,ai_consent_version,last_login_at,created_at FROM app_user"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",pageArgs.toArray(),(rs,row)->mapUser(rs));
        int count=total==null?0:total,totalPages=count==0?0:(count+pageSize-1)/pageSize;AdminUserPageView result=new AdminUserPageView();result.setTotal(count);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setKeyword(keyword);result.setStatus(status);result.setItems(items);return result;
    }
    public AdminUserLearningView learning(String rawUserId,Integer rawNotebookPage,Integer rawNotebookPageSize){
        String userId=rawUserId==null?"":rawUserId.trim();
        int notebookPage=rawNotebookPage==null?1:rawNotebookPage,notebookPageSize=rawNotebookPageSize==null?20:rawNotebookPageSize;
        if(userId.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_USER_ID","用户 ID 不能为空");
        if(notebookPage<1||notebookPageSize<1||notebookPageSize>50)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","生词本页码从 1 开始，每页可显示 1 至 50 条");
        List<AdminUserView> rows=jdbc.query("SELECT id,seq_no,short_id,nickname,mobile,status,ai_consent_version,last_login_at,created_at FROM app_user WHERE id=?",(rs,row)->mapUser(rs),userId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ADMIN_USER_NOT_FOUND","用户不存在");
        AdminUserLearningView result=new AdminUserLearningView();result.setAccount(rows.get(0));
        Integer planCount=jdbc.queryForObject("SELECT COUNT(*) FROM learning_plan WHERE owner_id=?",Integer.class,userId);
        PlanView plan=planCount!=null&&planCount>0?plans.get(userId):null;result.setPlan(plan);
        VocabularyBookProgressView vocabulary=null;
        if(vocabularyBooks.current(userId)!=null){vocabulary=vocabularyBooks.progress(userId,"all",1,1);vocabulary.setItems(Collections.emptyList());vocabulary.setHasMore(false);}
        result.setVocabulary(vocabulary);
        WordNotebookSummaryView notebook=learning.notebookSummary(userId);result.setNotebook(notebook);
        LearningPageView notebookItems=learning.page(userId,"word","","","",true,"","",notebookPage,notebookPageSize);result.setNotebookItems(notebookItems);
        result.setToday(today(userId));return result;
    }
    private AdminUserLearningView.TodayProgressView today(String userId){
        LocalDate date=LocalDate.now(BUSINESS_ZONE);
        List<AdminUserLearningView.TodayProgressView> rows=jdbc.query("SELECT COUNT(*) total_count,SUM(CASE WHEN dt.status='DONE' THEN 1 ELSE 0 END) completed_count,SUM(CASE WHEN dt.status='DOING' THEN 1 ELSE 0 END) doing_count FROM daily_task dt JOIN daily_package dp ON dp.id=dt.package_id WHERE dt.owner_id=? AND dp.business_date=?",(rs,row)->{AdminUserLearningView.TodayProgressView item=new AdminUserLearningView.TodayProgressView();int total=rs.getInt("total_count"),done=rs.getInt("completed_count");item.setTotalCount(total);item.setCompletedCount(done);item.setDoingCount(rs.getInt("doing_count"));item.setCompletionRate(total==0?0.0:Math.round(done*1000.0/total)/10.0);return item;},userId,date);
        if(!rows.isEmpty())return rows.get(0);AdminUserLearningView.TodayProgressView empty=new AdminUserLearningView.TodayProgressView();empty.setTotalCount(0);empty.setCompletedCount(0);empty.setDoingCount(0);empty.setCompletionRate(0.0);return empty;
    }
    private AdminUserView mapUser(java.sql.ResultSet rs)throws java.sql.SQLException{AdminUserView item=new AdminUserView();item.setUserId(rs.getString("id"));Number seq=(Number)rs.getObject("seq_no");item.setSeqNo(seq==null?null:seq.intValue());item.setShortId(rs.getString("short_id"));item.setNickname(rs.getString("nickname"));item.setMobileMasked(mask(rs.getString("mobile")));item.setStatus(rs.getString("status"));item.setAiConsented(rs.getString("ai_consent_version")!=null);Timestamp login=rs.getTimestamp("last_login_at"),created=rs.getTimestamp("created_at");item.setLastLoginAt(login==null?null:login.toInstant());item.setCreatedAt(created==null?null:created.toInstant());return item;}
    private String mask(String mobile){if(mobile==null||mobile.length()<7)return mobile;return mobile.substring(0,3)+"****"+mobile.substring(mobile.length()-4);}
}
