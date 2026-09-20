package com.zhixing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Builds auditable relation cards from imported evidence and conservative spelling comparisons. */
public class WordLearningCardEnrichmentService {
    public static final String GENERATOR_VERSION="oewn-card-enrichment-v4";
    private static final List<String> PREVIOUS_GENERATOR_VERSIONS=Arrays.asList("oewn-card-enrichment-v1","oewn-card-enrichment-v2","oewn-card-enrichment-v3");
    private static final String OEWN_TITLE="Open English WordNet 2025（CC BY 4.0）";
    private static final String OEWN_URL="https://en-word.net/";
    private static final String OEWN_RIGHTS="Open English WordNet 2025；源数据衍生自 Princeton WordNet，OEWN 新增内容采用 CC BY 4.0。页面仅陈述关系并保留数据集名称、版本、定位和许可链接。";
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    public WordLearningCardEnrichmentService(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}

    public Map<String,Object> generate(){return tx.execute(status -> generateInTransaction());}

    private Map<String,Object> generateInTransaction(){
        int retired=jdbc.update("UPDATE word_learning_card SET del_is=1,updated_at=CURRENT_TIMESTAMP WHERE generator_version IN (?,?,?) AND del_is=0",PREVIOUS_GENERATOR_VERSIONS.get(0),PREVIOUS_GENERATOR_VERSIONS.get(1),PREVIOUS_GENERATOR_VERSIONS.get(2));
        Map<String,Target> targets=new LinkedHashMap<String,Target>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT DISTINCT cv.id AS version_id,LOWER(TRIM(cv.word_term)) AS word_term,cv.meaning FROM vocabulary_book vb " +
            "JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id JOIN learning_content lc ON lc.id=vbw.content_id JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE vb.state='active' AND vb.level_code IN ('primary','junior','senior','cet4','cet6') AND lc.content_type='word' AND lc.state='published' " +
            "AND lc.withdrawn_at IS NULL AND cv.review_status='approved' ORDER BY word_term,version_id")){
            Target t=new Target();t.version=text(row,"version_id");t.word=normalize(text(row,"word_term"));t.meaning=clean(text(row,"meaning"));targets.put(t.version,t);
        }
        for(Map<String,Object> row:jdbc.queryForList("SELECT ws.content_version_id AS version_id,ws.part_of_speech,ws.meaning FROM word_sense ws WHERE ws.content_version_id IN "+targetSubquery()+" ORDER BY ws.content_version_id,ws.sort_no")){
            Target t=targets.get(text(row,"version_id"));if(t!=null&&t.pos==null){t.pos=clean(text(row,"part_of_speech"));String meaning=clean(text(row,"meaning"));if(meaning!=null)t.meaning=meaning;}
        }
        Map<String,List<Evidence>> evidence=new HashMap<String,List<Evidence>>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT * FROM word_lexical_relation_evidence WHERE dataset_code=? AND del_is=0 ORDER BY headword_norm,relation_type,related_term",OpenEnglishWordNetImportService.DATASET_CODE)){
            Evidence e=new Evidence();e.type=text(row,"relation_type");e.related=text(row,"related_term");e.pos=text(row,"part_of_speech");e.rank=((Number)row.get("sense_rank")).intValue();e.relationRank=((Number)row.get("relation_rank")).intValue();e.locator=text(row,"source_locator");evidence.computeIfAbsent(text(row,"headword_norm"),k->new ArrayList<Evidence>()).add(e);
        }
        for(Map<String,Object> row:jdbc.queryForList("SELECT content_version_id,card_key,card_type,del_is,state,review_status,reviewed_at,rights_status FROM word_learning_card WHERE content_version_id IN "+targetSubquery())){
            Target t=targets.get(text(row,"content_version_id"));if(t==null)continue;t.keys.add(text(row,"card_key"));
            if("0".equals(text(row,"del_is"))&&"published".equals(text(row,"state"))&&"approved".equals(text(row,"review_status"))&&row.get("reviewed_at")!=null&&Arrays.asList("original","licensed","public_domain","inherited").contains(text(row,"rights_status")))t.covered.add(text(row,"card_type"));
        }
        List<Term> dictionary=uniqueTerms(targets.values());Map<Integer,List<Term>> buckets=new HashMap<Integer,List<Term>>();for(Term term:dictionary)buckets.computeIfAbsent(term.word.length(),k->new ArrayList<Term>()).add(term);
        List<Object[]> cards=new ArrayList<Object[]>();Map<String,Integer> insertedByType=new TreeMap<String,Integer>();
        for(Target target:targets.values()){
            if(target.word==null)continue;
            List<Evidence> candidates=evidence.getOrDefault(target.word,Collections.<Evidence>emptyList());
            if(!target.covered.contains("synonym")&&!target.keys.contains("system-oewn-related-v4")){Evidence chosen=choose(candidates,target.pos,"synonym","similar","hypernym");if(chosen!=null)add(cards,insertedByType,relatedCard(target,chosen));}
            if(!target.covered.contains("derivative")&&!target.keys.contains("system-oewn-family-v4")){Evidence chosen=choose(candidates,target.pos,"derivation","inflection");if(chosen!=null)add(cards,insertedByType,familyCard(target,chosen));}
            if(!target.covered.contains("confusable")&&!target.keys.contains("system-spelling-compare-v4")){Term related=nearest(target.word,buckets);if(related!=null)add(cards,insertedByType,confusableCard(target,related));}
        }
        if(!cards.isEmpty())jdbc.batchUpdate("INSERT INTO word_learning_card(id,content_version_id,card_key,card_type,sense_label,title,related_term,body,recall_prompt,source_kind,source_title,source_url,source_locator,source_verified,rights_status,rights_note,generator_version,review_status,reviewed_by,reviewed_at,state,sort_no,del_is) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'approved',?,CURRENT_TIMESTAMP,'published',?,0)",cards);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("targetWords",targets.size());result.put("evidenceHeadwords",evidence.size());result.put("retiredPreviousCards",retired);result.put("insertedCards",cards.size());result.put("insertedByType",insertedByType);result.put("generatorVersion",GENERATOR_VERSION);return result;
    }

    private Object[] relatedCard(Target target,Evidence evidence){
        String relationLabel,title,body;
        if("synonym".equals(evidence.type)){relationLabel="同一义项组";title=target.word+" 与 "+evidence.related;body="Open English WordNet 2025 将 "+target.word+" 与 "+evidence.related+" 列在同一同义词集中。这里只表示对应义项接近；换句前仍要核对搭配、语体和词性。";}
        else if("similar".equals(evidence.type)){relationLabel="相近表达";title=target.word+" 与 "+evidence.related;body="Open English WordNet 2025 将 "+evidence.related+" 标为与 "+target.word+" 相关或相近的表达。它们不是所有语境都可直接互换，先看当前句子的词性和搭配。";}
        else{relationLabel="上位概念";title=target.word+" → "+evidence.related;body=evidence.related+" 是 Open English WordNet 2025 为 "+target.word+" 当前义项连接的更上位概念。这是类别关系，不是严格同义词，也不能直接在句中替换。";}
        return row(target,"system-oewn-related-v4","synonym",relationLabel+" · "+label(target),title,evidence.related,body,"先说出两者的共同点，再说一个不能直接替换的场景。","reference",OEWN_TITLE,OEWN_URL,evidence.locator,1,"licensed",OEWN_RIGHTS,500);
    }
    private Object[] familyCard(Target target,Evidence evidence){
        boolean inflection="inflection".equals(evidence.type);String title=target.word+" → "+evidence.related;
        String body=inflection?"Open English WordNet 2025 将 "+evidence.related+" 列为 "+target.word+" 的词形。它属于屈折变化，不等同于派生出一个新词；重点观察拼写和句法位置。":"Open English WordNet 2025 标记 "+target.word+" 与 "+evidence.related+" 存在词法派生关系。先比较词性和拼写变化；有派生关系不代表两者能在同一句法位置互换。";
        return row(target,"system-oewn-family-v4","derivative",(inflection?"屈折词形":"派生关系")+" · "+label(target),title,evidence.related,body,"不看页面，写出这两个词，并说出它们的词形或词性差别。","reference",OEWN_TITLE,OEWN_URL,evidence.locator,1,"licensed",OEWN_RIGHTS,520);
    }
    private Object[] confusableCard(Target target,Term related){
        String body=target.word+" 与 "+related.word+" 只因拼写相近放在一起，不表示近义、词源或派生关系。逐字母比较“"+target.word+" / "+related.word+"”，重点看长度和不同位置，再分别回到各自词条核对意思。";
        return row(target,"system-spelling-compare-v4","confusable","拼写易混 · 不是近义词",target.word+" ≠ "+related.word,related.word,body,"遮住页面，分别拼写这两个词，并圈出不同的字母。","original","系统词表拼写对比",null,"algorithm:damerau-levenshtein-v1",0,"original","由当前已审核词表的词头进行保守拼写距离比较；不推断词义、词源或派生关系。",510);
    }
    private Object[] row(Target t,String key,String type,String sense,String title,String related,String body,String prompt,String sourceKind,String sourceTitle,String sourceUrl,String locator,int verified,String rights,String rightsNote,int sort){
        return new Object[]{id(t.version,key),t.version,key,type,shortText(sense,160,"词条"),shortText(title,160,t.word),shortText(related,80,null),shortText(body,1200,""),shortText(prompt,300,null),sourceKind,sourceTitle,sourceUrl,shortText(locator,200,null),verified,rights,rightsNote,GENERATOR_VERSION,GENERATOR_VERSION,sort};
    }
    private void add(List<Object[]> cards,Map<String,Integer> counts,Object[] row){cards.add(row);String type=String.valueOf(row[3]);counts.put(type,counts.getOrDefault(type,0)+1);}
    private Evidence choose(List<Evidence> values,String targetPos,String...priorities){for(String priority:priorities){Evidence best=null;for(Evidence value:values)if(value.rank==0&&priority.equals(value.type)&&isUsableRelated(value.related)&&posCompatible(targetPos,value.pos)){if(best==null||value.relationRank<best.relationRank||(value.relationRank==best.relationRank&&value.related.compareTo(best.related)<0))best=value;}if(best!=null)return best;}return null;}
    private boolean posCompatible(String targetPos,String evidencePos){String target=clean(targetPos),evidence=clean(evidencePos);if(target==null)return false;target=target.toLowerCase(Locale.ROOT);if(Arrays.asList("noun","n","名词").contains(target))return "n".equals(evidence);if(Arrays.asList("verb","v","动词").contains(target))return "v".equals(evidence);if(Arrays.asList("adjective","adj","a","形容词").contains(target))return "a".equals(evidence)||"s".equals(evidence);if(Arrays.asList("adverb","adv","r","副词").contains(target))return "r".equals(evidence);return false;}
    private boolean isUsableRelated(String word){return word!=null&&word.length()<=80&&word.matches("[a-z][a-z '\\-]*");}
    private List<Term> uniqueTerms(Collection<Target> targets){Map<String,Term> unique=new LinkedHashMap<String,Term>();for(Target target:targets)if(target.word!=null&&target.word.matches("[a-z][a-z'\\-]*")&&target.word.length()>=2&&target.word.length()<=30)unique.putIfAbsent(target.word,new Term(target.word));return new ArrayList<Term>(unique.values());}
    private Term nearest(String word,Map<Integer,List<Term>> buckets){
        if(!word.matches("[a-z][a-z'\\-]*")||word.length()<2||word.length()>30)return null;int max=word.length()<=3?1:(word.length()<=6?2:3);Term best=null;int bestDistance=max+1;
        for(int length=Math.max(2,word.length()-max);length<=word.length()+max;length++)for(Term candidate:buckets.getOrDefault(length,Collections.<Term>emptyList())){
            if(word.equals(candidate.word)||morphVariant(word,candidate.word))continue;int distance=distance(word,candidate.word,max);if(distance<bestDistance||(distance==bestDistance&&best!=null&&candidate.word.compareTo(best.word)<0)){best=candidate;bestDistance=distance;}
        }
        return bestDistance<=max?best:null;
    }
    private boolean morphVariant(String a,String b){String shorter=a.length()<=b.length()?a:b,longer=a.length()<=b.length()?b:a;if(longer.equals(shorter+"s")||longer.equals(shorter+"es")||longer.equals(shorter+"ed")||longer.equals(shorter+"er")||longer.equals(shorter+"ly"))return true;if(shorter.endsWith("y")&&longer.equals(shorter.substring(0,shorter.length()-1)+"ies"))return true;return shorter.length()>3&&longer.equals(shorter+"ing");}
    private int distance(String a,String b,int cutoff){int[] previous=new int[b.length()+1],current=new int[b.length()+1],beforePrevious=null;for(int j=0;j<=b.length();j++)previous[j]=j;for(int i=1;i<=a.length();i++){current[0]=i;int rowMin=i;for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;int value=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+cost);if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1)&&beforePrevious!=null)value=Math.min(value,beforePrevious[j-2]+1);current[j]=value;rowMin=Math.min(rowMin,value);}if(rowMin>cutoff)return cutoff+1;beforePrevious=previous;previous=current;current=new int[b.length()+1];}return previous[b.length()];}
    private String label(Target t){return (t.pos==null?"词条":t.pos)+" · "+shortText(t.meaning,110,"主要义项");}
    private String targetSubquery(){return "(SELECT DISTINCT cv2.id FROM vocabulary_book vb2 JOIN vocabulary_book_word vbw2 ON vbw2.book_id=vb2.id JOIN learning_content lc2 ON lc2.id=vbw2.content_id JOIN content_version cv2 ON cv2.id=lc2.published_version_id WHERE vb2.state='active' AND vb2.level_code IN ('primary','junior','senior','cet4','cet6') AND lc2.content_type='word' AND lc2.state='published' AND lc2.withdrawn_at IS NULL AND cv2.review_status='approved')";}
    private String id(String version,String key){return UUID.nameUUIDFromBytes(("word-card:"+version+":"+key).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
    private String normalize(String value){String v=clean(value);return v==null?null:v.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private String clean(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    private String shortText(String value,int max,String fallback){String v=clean(value);if(v==null)return fallback;return v.length()<=max?v:v.substring(0,max-1)+"…";}
    private String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":value.toString();}
    private static class Target{String version,word,meaning,pos;Set<String>keys=new HashSet<String>();Set<String>covered=new HashSet<String>();}
    private static class Evidence{String type,related,pos,locator;int rank,relationRank;}
    private static class Term{String word;Term(String word){this.word=word;}}
}
