package com.zhixing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic baseline only. Semantic relations and quotations require separate evidence/review. */
@Service
public class WordLearningCardCoverageService {
    public static final String GENERATOR_VERSION="school-cet-card-standard-v3";
    private static final Set<String> TARGET_LEVELS=new LinkedHashSet<String>(Arrays.asList("primary","junior","senior","cet4","cet6"));
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final ObjectMapper json;
    public WordLearningCardCoverageService(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;}

    public Map<String,Object> generate(){return tx.execute(status -> generateInTransaction());}

    private Map<String,Object> generateInTransaction(){
        List<Map<String,Object>> targets=jdbc.queryForList("SELECT DISTINCT cv.id AS version_id,cv.word_term,cv.meaning FROM vocabulary_book vb " +
            "JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id JOIN learning_content lc ON lc.id=vbw.content_id " +
            "JOIN content_version cv ON cv.id=lc.published_version_id WHERE vb.state='active' " +
            "AND vb.level_code IN ('primary','junior','senior','cet4','cet6') AND lc.content_type='word' AND lc.state='published' " +
            "AND lc.withdrawn_at IS NULL AND cv.review_status='approved' ORDER BY cv.word_term,cv.id");
        Map<String,Target> byVersion=new LinkedHashMap<String,Target>();
        for(Map<String,Object> row:targets){Target t=new Target();t.version=text(row,"version_id");t.word=text(row,"word_term");t.meaning=text(row,"meaning");byVersion.put(t.version,t);}

        for(Map<String,Object> row:jdbc.queryForList("SELECT cv.id AS version_id,vb.level_code FROM vocabulary_book vb JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id " +
            "JOIN learning_content lc ON lc.id=vbw.content_id JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE vb.state='active' AND vb.level_code IN ('primary','junior','senior','cet4','cet6') AND lc.state='published' AND lc.withdrawn_at IS NULL AND cv.review_status='approved' ORDER BY vb.sort_no")){
            Target t=byVersion.get(text(row,"version_id"));String level=text(row,"level_code");if(t!=null&&TARGET_LEVELS.contains(level))t.scopes.add(level);
        }
        for(Map<String,Object> row:jdbc.queryForList("SELECT ws.content_version_id AS version_id,ws.part_of_speech,ws.meaning AS sense_meaning,we.id AS example_id " +
            "FROM word_sense ws LEFT JOIN word_example we ON we.sense_id=ws.id WHERE ws.content_version_id IN " + targetSubquery() + " ORDER BY ws.content_version_id,ws.sort_no,we.sort_no,we.id")){
            Target t=byVersion.get(text(row,"version_id"));if(t==null)continue;
            if(t.partOfSpeech==null){t.partOfSpeech=clean(text(row,"part_of_speech"));String sense=clean(text(row,"sense_meaning"));if(sense!=null)t.meaning=sense;}
            if(t.exampleId==null)t.exampleId=clean(text(row,"example_id"));
        }
        Map<String,Set<String>> keys=new HashMap<String,Set<String>>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT c.content_version_id,c.card_key FROM word_learning_card c WHERE c.content_version_id IN "+targetSubquery()))
            keys.computeIfAbsent(text(row,"content_version_id"),k->new HashSet<String>()).add(text(row,"card_key"));

        List<Object[]> cards=new ArrayList<Object[]>();
        for(Target t:byVersion.values()){
            Set<String> existing=keys.computeIfAbsent(t.version,k->new HashSet<String>());
            if(!existing.contains("system-recall-v1"))cards.add(recall(t));
            if(!existing.contains("system-usage-v1"))cards.add(usage(t));
        }
        String cardSql="INSERT INTO word_learning_card(id,content_version_id,card_key,card_type,sense_label,title,body,recall_prompt,example_id,"+
            "source_kind,source_title,source_verified,rights_status,rights_note,generator_version,review_status,reviewed_by,reviewed_at,state,sort_no,del_is) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'published',?,0)";
        if(!cards.isEmpty())jdbc.batchUpdate(cardSql,cards);

        Map<String,CardState> states=new HashMap<String,CardState>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT c.content_version_id,c.card_type,COUNT(*) AS amount FROM word_learning_card c WHERE c.content_version_id IN "+targetSubquery()+
            " AND c.del_is=0 AND c.state='published' AND c.review_status='approved' AND c.reviewed_at IS NOT NULL AND c.rights_status IN ('original','licensed','public_domain','inherited') GROUP BY c.content_version_id,c.card_type")){
            CardState state=states.computeIfAbsent(text(row,"content_version_id"),k->new CardState());String type=text(row,"card_type");int amount=((Number)row.get("amount")).intValue();state.count+=amount;state.counts.put(type,amount);if("mnemonic".equals(type))state.mnemonic=true;if("usage".equals(type))state.usage=true;
        }
        boolean lexicalEvidenceLoaded=jdbc.queryForObject("SELECT COUNT(*) FROM lexical_dataset_snapshot WHERE dataset_code='oewn-2025' AND del_is=0 AND import_status='complete'",Integer.class)>0;
        int complete=0,examples=0;
        List<Object[]> coverage=new ArrayList<Object[]>();
        for(Target t:byVersion.values()){
            CardState state=states.getOrDefault(t.version,new CardState());boolean done=state.mnemonic&&state.usage;if(done)complete++;if(t.exampleId!=null)examples++;
            List<String> issues=new ArrayList<String>();if(!state.mnemonic)issues.add("missing_mnemonic");if(!state.usage)issues.add("missing_usage");if(t.exampleId==null)issues.add("missing_example_reference");
            int relationTypes=0;for(String type:Arrays.asList("synonym","confusable","derivative"))if(state.counts.getOrDefault(type,0)>0)relationTypes++;String relationStatus=relationTypes==3?"complete":(relationTypes>0?"partial":(lexicalEvidenceLoaded?"evidence_not_available":"evidence_required"));
            for(String type:Arrays.asList("synonym","confusable","derivative"))if(state.counts.getOrDefault(type,0)==0)issues.add("missing_"+type);
            coverage.add(new Object[]{t.version,join(t.scopes),state.count,done?"complete":"blocked",t.exampleId==null?"missing":"linked",relationStatus,t.exampleId,GENERATOR_VERSION,toJson(issues)});
        }
        if(!coverage.isEmpty())jdbc.batchUpdate("INSERT INTO word_learning_card_coverage(content_version_id,target_scope_codes,required_card_count,published_card_count,baseline_status,example_status,relation_status,quotation_status,source_example_id,generator_version,issues_json,del_is,generated_at,updated_at) " +
            "VALUES(?,?,2,?,?,?,?,'optional_not_required',?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE target_scope_codes=VALUES(target_scope_codes),published_card_count=VALUES(published_card_count),baseline_status=VALUES(baseline_status),example_status=VALUES(example_status),relation_status=VALUES(relation_status),source_example_id=VALUES(source_example_id),generator_version=VALUES(generator_version),issues_json=VALUES(issues_json),del_is=0,generated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",coverage);
        List<Object[]> typeCoverage=new ArrayList<Object[]>();
        for(Target t:byVersion.values()){
            CardState state=states.getOrDefault(t.version,new CardState());
            for(String type:WordLearningCardPreferenceService.TYPES){int count=state.counts.containsKey(type)?state.counts.get(type):0;String requirement=requirement(type);String coverageStatus=count>0?"available":missingStatus(type,lexicalEvidenceLoaded);typeCoverage.add(new Object[]{t.version,type,requirement,count,coverageStatus,statusNote(type,count,lexicalEvidenceLoaded),GENERATOR_VERSION});}
        }
        if(!typeCoverage.isEmpty())jdbc.batchUpdate("INSERT INTO word_learning_card_type_coverage(content_version_id,card_type,requirement_level,published_count,coverage_status,status_note,generator_version,del_is,generated_at,updated_at) " +
            "VALUES(?,?,?,?,?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE requirement_level=VALUES(requirement_level),published_count=VALUES(published_count),coverage_status=VALUES(coverage_status),status_note=VALUES(status_note),generator_version=VALUES(generator_version),del_is=0,generated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",typeCoverage);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("targetWords",byVersion.size());result.put("insertedCards",cards.size());result.put("completeWords",complete);result.put("wordsWithLinkedExamples",examples);result.put("typeCoverageRows",typeCoverage.size());result.put("generatorVersion",GENERATOR_VERSION);return result;
    }

    private Object[] recall(Target t){
        String meaning=shortText(t.meaning,180,"当前义项");String label=label(t);
        return new Object[]{id(t.version,"system-recall-v1"),t.version,"system-recall-v1","mnemonic",label,"中英双向回想",
            "先遮住英文，只看主要释义“"+meaning+"”，说出并拼写这个单词；再反过来看到 "+t.word+"，说出一个主要意思。答不出时看答案，稍后再试。",
            "不看页面，说出“"+meaning+"”对应的英文，并逐字母拼写。",null,"original","系统规则生成 · 词条双向回想",0,"original",
            "根据当前已审核词条的词头和主要释义生成；不声称词源、近义或派生关系。",GENERATOR_VERSION,"approved",GENERATOR_VERSION,900};
    }
    private Object[] usage(Target t){
        return new Object[]{id(t.version,"system-usage-v1"),t.version,"system-usage-v1","usage",label(t),"把例句变成自己的话",
            t.exampleId==null?"暂时没有可引用的已发布例句。先用这个词口头说一个与你今天有关的短句；不确定时不要自行保存为标准例句。":"先朗读上面的已发布例句，再只替换一个人物、地点、时间或数量，保留 "+t.word+" 的原有用法。最后遮住原句复述。",
            "用 "+t.word+" 说一句与你今天有关的话。",t.exampleId,t.exampleId==null?"original":"content",t.exampleId==null?"系统规则生成 · 口头造句提示":"当前已发布词条例句",t.exampleId==null?0:1,t.exampleId==null?"original":"inherited",
            t.exampleId==null?"原创练习指令，不生成或冒充标准英文例句。":"例句内容通过 example_id 读取当前词条既有审核版本，授权状态继承该词条；卡片不复制、不改写原记录。",GENERATOR_VERSION,"approved",GENERATOR_VERSION,910};
    }
    private String label(Target t){String pos=clean(t.partOfSpeech);return shortText((pos==null?"词条":pos)+" · "+shortText(t.meaning,110,"主要义项"),160,"词条 · 主要义项");}
    private String targetSubquery(){return "(SELECT DISTINCT cv2.id FROM vocabulary_book vb2 JOIN vocabulary_book_word vbw2 ON vbw2.book_id=vb2.id JOIN learning_content lc2 ON lc2.id=vbw2.content_id JOIN content_version cv2 ON cv2.id=lc2.published_version_id WHERE vb2.state='active' AND vb2.level_code IN ('primary','junior','senior','cet4','cet6') AND lc2.content_type='word' AND lc2.state='published' AND lc2.withdrawn_at IS NULL AND cv2.review_status='approved')";}
    private String toJson(List<String> issues){try{return json.writeValueAsString(issues);}catch(Exception e){throw new IllegalStateException(e);}}
    private String id(String version,String key){return UUID.nameUUIDFromBytes(("word-card:"+version+":"+key).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
    private String join(Set<String> values){return String.join(",",values);}
    private String requirement(String type){if("mnemonic".equals(type)||"usage".equals(type))return "required";if("quote".equals(type))return "optional";return "evidence_required";}
    private String missingStatus(String type,boolean evidenceLoaded){if("mnemonic".equals(type)||"usage".equals(type))return "missing_required";if("quote".equals(type))return "optional_not_available";return evidenceLoaded?"evidence_not_available":"evidence_required";}
    private String statusNote(String type,int count,boolean evidenceLoaded){if(count>0)return "已有"+count+"张通过发布门禁的卡片";if("quote".equals(type))return "影视或歌词引用不是每词必填；需真实出处、精确定位与授权核验";if("mnemonic".equals(type)||"usage".equals(type))return "基础训练卡缺失，必须补齐后才算达标";return evidenceLoaded?"已核对固定版本开放词汇数据与本库拼写比较，未找到可安全发布的对应关系":"尚未导入可核验词汇关系证据，不允许按拼写自动编造词义关系";}
    private String shortText(String value,int max,String fallback){String v=clean(value);if(v==null)return fallback;return v.length()<=max?v:v.substring(0,max-1)+"…";}
    private String clean(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    private String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":value.toString();}
    private static class Target{String version,word,meaning,partOfSpeech,exampleId;Set<String> scopes=new LinkedHashSet<String>();}
    private static class CardState{int count;boolean mnemonic,usage;Map<String,Integer> counts=new HashMap<String,Integer>();}
}
