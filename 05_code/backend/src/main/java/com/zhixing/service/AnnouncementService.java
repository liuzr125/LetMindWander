package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AnnouncementService {
    private final JdbcTemplate jdbc;
    public AnnouncementService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> list(boolean admin,int page,int size){
        if(page<1||size<1||size>100||page>100000)throw bad("分页参数无效");
        return jdbc.query("SELECT id,title,state,publish_at,updated_at FROM announcement "+(admin?"":"WHERE state='published' AND publish_at<=? ")+"ORDER BY publish_at DESC,updated_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->view(rs,false),admin?new Object[]{size,(page-1)*size}:new Object[]{Timestamp.from(Instant.now()),size,(page-1)*size});
    }
    public Map<String,Object> detail(String id,boolean admin){
        List<Map<String,Object>> rows=jdbc.query("SELECT * FROM announcement WHERE id=?"+(admin?"":" AND state='published' AND publish_at<=?"),(rs,n)->view(rs,true),admin?new Object[]{id}:new Object[]{id,Timestamp.from(Instant.now())});
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ANNOUNCEMENT_NOT_FOUND","公告不存在或尚未发布");return rows.get(0);
    }
    @Transactional public Map<String,Object> save(String actor,String id,Map<String,String> body){
        String title=clean(body.get("title")),content=clean(body.get("body")),state=body.get("state");
        if(title.isEmpty()||title.length()>120||content.isEmpty()||content.length()>20000)throw bad("标题需 1–120 字，正文需 1–20000 字");
        if(!Arrays.asList("draft","published","withdrawn").contains(state))throw bad("公告状态无效");
        Instant publish=null;String input=clean(body.get("publishAt"));
        try {if(!input.isEmpty())publish=Instant.parse(input);}catch(DateTimeException e){throw bad("发布时间需为包含时区的 ISO 时间");}
        if("published".equals(state)&&publish==null)publish=Instant.now();
        Timestamp time=Timestamp.from(Instant.now()),scheduled=publish==null?null:Timestamp.from(publish);
        if(id==null){id=CryptoUtils.randomId();jdbc.update("INSERT INTO announcement(id,title,body,state,publish_at,created_by,updated_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",id,title,content,state,scheduled,actor,actor,time,time);}
        else {detail(id,true);jdbc.update("UPDATE announcement SET title=?,body=?,state=?,publish_at=?,updated_by=?,updated_at=? WHERE id=?",title,content,state,scheduled,actor,time,id);}
        jdbc.update("INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(?,?,'announcement_save','announcement',?,'succeeded',?,?)",CryptoUtils.randomId(),actor,id,"{\"state\":\""+state+"\"}",Timestamp.from(Instant.now().plus(Duration.ofDays(180))));
        return detail(id,true);
    }
    private Map<String,Object> view(java.sql.ResultSet rs,boolean full)throws java.sql.SQLException{
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",rs.getString("id"));out.put("title",rs.getString("title"));out.put("state",rs.getString("state"));Timestamp p=rs.getTimestamp("publish_at");out.put("publishAt",p==null?null:p.toInstant());out.put("updatedAt",rs.getTimestamp("updated_at").toInstant());if(full)out.put("body",rs.getString("body"));return out;
    }
    private String clean(String value){return value==null?"":value.trim();}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANNOUNCEMENT",message);}
}
