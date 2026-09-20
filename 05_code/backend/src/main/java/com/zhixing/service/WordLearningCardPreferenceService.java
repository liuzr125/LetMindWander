package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Account-level display choices only; preferences never change cards or mastery. */
@Service
public class WordLearningCardPreferenceService {
    public static final List<String> TYPES=Collections.unmodifiableList(Arrays.asList("mnemonic","synonym","confusable","derivative","usage","quote"));
    private static final Map<String,String> TITLES=titles();
    private final JdbcTemplate jdbc;
    public WordLearningCardPreferenceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Set<String> enabledTypes(String userId){
        Map<String,Boolean> values=values(userId);Set<String> enabled=new LinkedHashSet<String>();
        for(String type:TYPES)if(values.get(type))enabled.add(type);
        return enabled;
    }

    public Map<String,Object> preferences(String userId){
        Map<String,Boolean> values=values(userId);List<Map<String,Object>> types=new ArrayList<Map<String,Object>>();
        for(String type:TYPES){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("type",type);item.put("title",TITLES.get(type));item.put("enabled",values.get(type));types.add(item);}
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("types",types);result.put("enabledTypes",enabledTypes(values));return result;
    }

    @Transactional
    public Map<String,Object> update(String userId,Map<String,Object> request){
        Object raw=request==null?null:request.get("enabledTypes");
        if(!(raw instanceof Collection))throw new ApiException(HttpStatus.BAD_REQUEST,"CARD_PREFERENCE_INVALID","请选择要展示的卡片类型");
        Set<String> enabled=new LinkedHashSet<String>();
        for(Object item:(Collection<?>)raw){String type=item==null?"":item.toString().trim();if(!TYPES.contains(type))throw new ApiException(HttpStatus.BAD_REQUEST,"CARD_TYPE_INVALID","存在不支持的卡片类型");enabled.add(type);}
        for(String type:TYPES){int value=enabled.contains(type)?1:0;int changed=jdbc.update("UPDATE user_word_learning_card_preference SET enabled=?,row_version=row_version+1,del_is=0,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND card_type=?",value,userId,type);
            if(changed==0)jdbc.update("INSERT INTO user_word_learning_card_preference(owner_id,card_type,enabled,row_version,del_is) VALUES(?,?,?,1,0)",userId,type,value);
        }
        return preferences(userId);
    }

    private Map<String,Boolean> values(String userId){
        Map<String,Boolean> values=new LinkedHashMap<String,Boolean>();for(String type:TYPES)values.put(type,true);
        for(Map<String,Object> row:jdbc.queryForList("SELECT card_type,enabled FROM user_word_learning_card_preference WHERE owner_id=? AND del_is=0",userId)){
            String type=String.valueOf(row.get("card_type"));if(TYPES.contains(type))values.put(type,number(row.get("enabled"))!=0);
        }
        return values;
    }
    private List<String> enabledTypes(Map<String,Boolean> values){List<String> result=new ArrayList<String>();for(String type:TYPES)if(values.get(type))result.add(type);return result;}
    private int number(Object value){return value instanceof Number?((Number)value).intValue():Integer.parseInt(String.valueOf(value));}
    private static Map<String,String> titles(){Map<String,String> m=new LinkedHashMap<String,String>();m.put("mnemonic","这样记");m.put("synonym","近义与相近表达");m.put("confusable","易混词辨析");m.put("derivative","派生词与词族");m.put("usage","日常这样说");m.put("quote","有出处的表达");return Collections.unmodifiableMap(m);}
}
