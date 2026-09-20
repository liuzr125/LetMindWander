package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Account-level defaults for entering a new-word page and replaying pronunciation. */
@Service
public class WordStudyPreferenceService {
    private final JdbcTemplate jdbc;
    public WordStudyPreferenceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Map<String,Object> preferences(String userId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT default_answer_mode,auto_play_enabled,auto_play_accent,auto_play_count,auto_play_interval_ms,row_version FROM user_word_study_preference WHERE owner_id=? AND del_is=0",userId);
        if(rows.isEmpty())return defaults();
        Map<String,Object> row=rows.get(0);Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("defaultAnswerMode",String.valueOf(row.get("default_answer_mode")));
        result.put("autoPlayEnabled",number(row.get("auto_play_enabled"))!=0);
        result.put("autoPlayAccent",String.valueOf(row.get("auto_play_accent")));
        result.put("autoPlayCount",number(row.get("auto_play_count")));
        result.put("autoPlayIntervalMs",number(row.get("auto_play_interval_ms")));
        result.put("rowVersion",number(row.get("row_version")));
        return result;
    }

    @Transactional
    public Map<String,Object> update(String userId,Map<String,Object> request){
        Map<String,Object> current=preferences(userId);
        String mode=text(request,"defaultAnswerMode",String.valueOf(current.get("defaultAnswerMode")));
        String accent=text(request,"autoPlayAccent",String.valueOf(current.get("autoPlayAccent")));
        boolean enabled=bool(request,"autoPlayEnabled",Boolean.TRUE.equals(current.get("autoPlayEnabled")));
        int count=integer(request,"autoPlayCount",number(current.get("autoPlayCount")));
        int interval=integer(request,"autoPlayIntervalMs",number(current.get("autoPlayIntervalMs")));
        if(!Arrays.asList("visible","hidden").contains(mode))throw invalid("defaultAnswerMode 仅支持 visible 或 hidden");
        if(!Arrays.asList("uk","us").contains(accent))throw invalid("autoPlayAccent 仅支持 uk 或 us");
        if(count<1||count>5)throw invalid("自动播放次数必须为 1–5 次");
        if(interval<1000||interval>2000)throw invalid("播放间隔必须为 1000–2000 毫秒");
        int changed=jdbc.update("UPDATE user_word_study_preference SET default_answer_mode=?,auto_play_enabled=?,auto_play_accent=?,auto_play_count=?,auto_play_interval_ms=?,row_version=row_version+1,del_is=0,updated_at=CURRENT_TIMESTAMP WHERE owner_id=?",mode,enabled?1:0,accent,count,interval,userId);
        if(changed==0)jdbc.update("INSERT INTO user_word_study_preference(owner_id,default_answer_mode,auto_play_enabled,auto_play_accent,auto_play_count,auto_play_interval_ms,row_version,del_is) VALUES(?,?,?,?,?,?,1,0)",userId,mode,enabled?1:0,accent,count,interval);
        return preferences(userId);
    }

    public static Map<String,Object> defaults(){Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("defaultAnswerMode","visible");result.put("autoPlayEnabled",true);result.put("autoPlayAccent","uk");result.put("autoPlayCount",3);result.put("autoPlayIntervalMs",1500);result.put("rowVersion",0);return result;}
    private ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"WORD_STUDY_PREFERENCE_INVALID",message);}
    private String text(Map<String,Object> request,String key,String fallback){Object value=request==null?null:request.get(key);return value==null?fallback:String.valueOf(value).trim().toLowerCase(Locale.ROOT);}
    private boolean bool(Map<String,Object> request,String key,boolean fallback){Object value=request==null?null:request.get(key);if(value==null)return fallback;if(value instanceof Boolean)return (Boolean)value;if("true".equalsIgnoreCase(String.valueOf(value)))return true;if("false".equalsIgnoreCase(String.valueOf(value)))return false;throw invalid(key+" 必须为布尔值");}
    private int integer(Map<String,Object> request,String key,int fallback){Object value=request==null?null:request.get(key);if(value==null)return fallback;try{return number(value);}catch(Exception e){throw invalid(key+" 必须为整数");}}
    private int number(Object value){return value instanceof Number?((Number)value).intValue():Integer.parseInt(String.valueOf(value));}
}
