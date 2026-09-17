package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.model.AdminUserView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminUserService {
    private final JdbcTemplate jdbc;
    public AdminUserService(JdbcTemplate jdbc){this.jdbc=jdbc;}

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
        List<AdminUserView> items=jdbc.query("SELECT id,seq_no,short_id,nickname,mobile,status,ai_consent_version,last_login_at,created_at FROM app_user"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",pageArgs.toArray(),(rs,row)->{
            AdminUserView item=new AdminUserView();item.setUserId(rs.getString("id"));Number seq=(Number)rs.getObject("seq_no");item.setSeqNo(seq==null?null:seq.intValue());item.setShortId(rs.getString("short_id"));item.setNickname(rs.getString("nickname"));item.setMobileMasked(mask(rs.getString("mobile")));item.setStatus(rs.getString("status"));item.setAiConsented(rs.getString("ai_consent_version")!=null);Timestamp login=rs.getTimestamp("last_login_at"),created=rs.getTimestamp("created_at");item.setLastLoginAt(login==null?null:login.toInstant());item.setCreatedAt(created==null?null:created.toInstant());return item;
        });
        int count=total==null?0:total,totalPages=count==0?0:(count+pageSize-1)/pageSize;AdminUserPageView result=new AdminUserPageView();result.setTotal(count);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setKeyword(keyword);result.setStatus(status);result.setItems(items);return result;
    }
    private String mask(String mobile){if(mobile==null||mobile.length()<7)return mobile;return mobile.substring(0,3)+"****"+mobile.substring(mobile.length()-4);}
}
