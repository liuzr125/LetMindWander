package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminTopicService {
    private final JdbcTemplate jdbc;
    public AdminTopicService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<Map<String,Object>> list(){
        return jdbc.queryForList("SELECT t.id,t.name,t.state,t.topic_type topicType,"+
            "(SELECT COUNT(DISTINCT lc.id) FROM content_topic ct JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id WHERE ct.topic_id=t.id) contentCount,"+
            "(SELECT COUNT(DISTINCT lc.id) FROM content_topic ct JOIN learning_content lc ON lc.published_version_id=ct.content_version_id WHERE ct.topic_id=t.id AND lc.content_type='tech' AND lc.state='published') publishedCount,"+
            "(SELECT COUNT(*) FROM learning_plan_topic pt WHERE pt.topic_id=t.id) planCount "+
            "FROM learning_topic t WHERE t.scope_key='system' AND t.del_is=0 ORDER BY t.name,t.id");
    }

    @Transactional
    public void save(String actor,String id,String rawName){
        save(actor,id,rawName,null);
    }

    @Transactional
    public void save(String actor,String id,String rawName,String rawType){
        String name=rawName==null?"":rawName.trim().replaceAll("\\s+"," ");
        if(name.isEmpty()||name.length()>50)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TOPIC_NAME","主题名称为 1 至 50 个字符");
        String normalized=name.toLowerCase(Locale.ROOT);
        if(id!=null)lock(id);
        String type=rawType==null ? (id==null ? "technical" : jdbc.queryForObject("SELECT topic_type FROM learning_topic WHERE id=?",String.class,id)) : rawType;
        if(!"technical".equals(type)&&!"vocabulary".equals(type))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TOPIC_TYPE","请选择技术知识主题或词书标签");
        if(id!=null&&"vocabulary".equals(type)){
            Integer technical=jdbc.queryForObject("SELECT COUNT(*) FROM content_topic ct JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id WHERE ct.topic_id=? AND lc.content_type<>'word'",Integer.class,id);
            Integer plans=jdbc.queryForObject("SELECT COUNT(*) FROM learning_plan_topic WHERE topic_id=?",Integer.class,id);
            if(technical>0||plans>0)throw new ApiException(HttpStatus.CONFLICT,"TOPIC_TYPE_IN_USE","主题有非单词内容或学习计划引用，不能改为词书标签");
        }
        Integer duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM learning_topic WHERE scope_key='system' AND (LOWER(name)=? OR normalized_name=?) AND id<>?",Integer.class,normalized,normalized,id==null?"":id);
        if(duplicate!=null&&duplicate>0)throw new ApiException(HttpStatus.CONFLICT,"TOPIC_NAME_EXISTS","该主题名称已存在");
        try{
            if(id==null){id=CryptoUtils.randomId();jdbc.update("INSERT INTO learning_topic(id,scope_key,name,normalized_name,state,topic_type) VALUES(?,'system',?,?,'active',?)",id,name,normalized,type);}
            else jdbc.update("UPDATE learning_topic SET name=?,topic_type=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND scope_key='system'",name,type,id);
        }catch(DuplicateKeyException exception){throw new ApiException(HttpStatus.CONFLICT,"TOPIC_NAME_EXISTS","该主题名称已存在");}
        audit(actor,"topic_save",id);
    }

    @Transactional
    public void delete(String actor,String id){
        lock(id);
        Integer published=jdbc.queryForObject("SELECT COUNT(*) FROM content_topic ct JOIN learning_content lc ON lc.published_version_id=ct.content_version_id WHERE ct.topic_id=? AND lc.content_type='tech' AND lc.state='published'",Integer.class,id);
        Integer plans=jdbc.queryForObject("SELECT COUNT(*) FROM learning_plan_topic WHERE topic_id=?",Integer.class,id);
        if((published!=null&&published>0)||(plans!=null&&plans>0))throw new ApiException(HttpStatus.CONFLICT,"TOPIC_IN_USE","主题有已发布技术知识或学习计划引用，不能删除");
        jdbc.update("UPDATE learning_topic SET del_is=1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND scope_key='system' AND del_is=0",id);
        audit(actor,"topic_delete",id);
    }

    private void lock(String id){
        if(jdbc.queryForList("SELECT id FROM learning_topic WHERE id=? AND scope_key='system' AND del_is=0 FOR UPDATE",id).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"TOPIC_NOT_FOUND","系统主题不存在");
    }
    private void audit(String actor,String action,String id){
        jdbc.update("INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(?,?,?,'learning_topic',?,'succeeded','{}',?)",CryptoUtils.randomId(),actor,action,id,java.sql.Timestamp.from(java.time.Instant.now().plus(java.time.Duration.ofDays(180))));
    }
}
