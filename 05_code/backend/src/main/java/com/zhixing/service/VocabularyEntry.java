package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Shared staged-word contract; flat uploads remain compatible. */
public final class VocabularyEntry {
    public final String word,phoneticUs,phoneticUk;
    public final List<Map<String,Object>> senses=new ArrayList<Map<String,Object>>();
    public VocabularyEntry(Map<String,Object> raw) {
        word=text(raw.get("word"),text(raw.get("term"),""));
        phoneticUs=text(raw.get("phoneticUs"),text(raw.get("phonetic"),""));
        phoneticUk=text(raw.get("phoneticUk"),text(raw.get("phonetic"),""));
        Object nested=raw.get("senses");
        if(nested instanceof List){
            for(Object item:(List<?>)nested){if(!(item instanceof Map))throw invalid();senses.add(map(item));}
        }else{
            Map<String,Object> sense=new LinkedHashMap<String,Object>();
            sense.put("partOfSpeech",text(raw.get("pos"),"unknown"));sense.put("meaning",text(raw.get("meaning"),""));
            List<Map<String,Object>> examples=new ArrayList<Map<String,Object>>();
            if(!text(raw.get("example"),"").isEmpty()){
                Map<String,Object> ex=new LinkedHashMap<String,Object>();ex.put("sentence",raw.get("example"));ex.put("translation",text(raw.get("translation"),""));examples.add(ex);
            }
            sense.put("examples",examples);senses.add(sense);
        }
    }
    public void validate() {
        if(senses.isEmpty()||senses.size()>30||phoneticUs.length()>200||phoneticUk.length()>200)throw invalid();
        for(int i=1;i<=senses.size();i++){
            Map<String,Object> s=senses.get(i-1);
            String meaning=text(s.get("meaning"),""),pos=text(s.get("partOfSpeech"),"unknown");
            if(meaning.isEmpty()||meaning.length()>1000||pos.length()>32||examples(i).size()>50)throw invalid();
            for(Map<String,Object> e:examples(i)){
                if(text(e.get("sentence"),"").isEmpty()||text(e.get("sentence"),"").length()>1000||text(e.get("translation"),"").length()>1000)throw invalid();
            }
        }
    }
    public List<Map<String,Object>> examples(int senseNo) {
        Object x=senses.get(senseNo-1).get("examples");
        if(x==null)return Collections.emptyList();if(!(x instanceof List))throw invalid();
        List<Map<String,Object>> out=new ArrayList<Map<String,Object>>();for(Object e:(List<?>)x){if(!(e instanceof Map))throw invalid();out.add(map(e));}return out;
    }
    public String sentence(int senseNo,int exampleNo){return text(examples(senseNo).get(exampleNo-1).get("sentence"),"");}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    public static String text(Object o,String fallback){return o==null||o.toString().trim().isEmpty()?fallback:o.toString().trim();}
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"WORD_STRUCTURE_INVALID","词条义项、音标或例句结构不完整或超长，请修正数据源");}
}
