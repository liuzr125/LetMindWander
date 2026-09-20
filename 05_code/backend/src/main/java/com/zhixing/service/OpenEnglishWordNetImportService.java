package com.zhixing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Imports only lexical relations needed by the published school/CET vocabulary. */
public class OpenEnglishWordNetImportService {
    public static final String DATASET_CODE="oewn-2025";
    public static final String DATASET_NAME="Open English WordNet";
    public static final String DATASET_VERSION="2025";
    public static final String SOURCE_URL="https://en-word.net/static/english-wordnet-2025-json.zip";
    public static final String LICENSE_URL="https://github.com/globalwordnet/english-wordnet/blob/main/LICENSE.md";
    public static final String EXPECTED_SHA256="7d749f6e2c39e6970e4997839dcf6e42fd281f3c2fae0171d2192bae8cfa4b51";
    public static final String ATTRIBUTION="Open English WordNet 2025, derived from Princeton WordNet; OEWN additions are licensed under CC BY 4.0. See the dataset license for complete attribution.";
    private static final Set<String> TARGET_LEVELS=new LinkedHashSet<String>(Arrays.asList("primary","junior","senior","cet4","cet6"));
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final ObjectMapper json;

    public OpenEnglishWordNetImportService(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json){
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;
    }

    public Map<String,Object> importZip(Path zipPath) throws Exception {
        if(zipPath==null||!Files.isRegularFile(zipPath))throw new IllegalArgumentException("OEWN zip file does not exist: "+zipPath);
        String checksum=sha256(zipPath);
        if(!EXPECTED_SHA256.equalsIgnoreCase(checksum))throw new IllegalArgumentException("OEWN 2025 checksum mismatch: "+checksum);
        final ImportData data=parse(zipPath);
        return tx.execute(status -> persist(data,checksum));
    }

    private ImportData parse(Path zipPath) throws Exception {
        ImportData data=new ImportData();
        for(String word:jdbc.queryForList("SELECT DISTINCT LOWER(TRIM(cv.word_term)) FROM vocabulary_book vb " +
            "JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id JOIN learning_content lc ON lc.id=vbw.content_id " +
            "JOIN content_version cv ON cv.id=lc.published_version_id WHERE vb.state='active' AND vb.level_code IN ('primary','junior','senior','cet4','cet6') " +
            "AND lc.content_type='word' AND lc.state='published' AND lc.withdrawn_at IS NULL AND cv.review_status='approved'",String.class)){
            String normalized=normalize(word);if(normalized!=null)data.targets.put(normalized,new TargetLexeme(normalized));
        }
        try(ZipFile zip=new ZipFile(zipPath.toFile())){
            List<? extends ZipEntry> entries=Collections.list(zip.entries());
            for(ZipEntry entry:entries)if(isEntryFile(entry.getName()))parseEntryFile(zip,entry,data);
            for(TargetLexeme target:data.targets.values())for(Sense sense:target.senses)for(int relationRank=0;relationRank<sense.derivations.size();relationRank++){
                String relatedSense=sense.derivations.get(relationRank),related=data.senseToLemma.get(relatedSense);if(related!=null)add(data,target.word,"derivation",related,sense.pos,sense.id,sense.synset,sense.rank,relationRank,"sense:"+sense.id+" -> "+relatedSense,"OEWN 2025 lexical derivation relation");
            }
            Set<String> targetSynsets=new HashSet<String>();for(TargetLexeme target:data.targets.values())for(Sense sense:target.senses)if(sense.synset!=null)targetSynsets.add(sense.synset);
            Map<String,Synset> selected=new HashMap<String,Synset>();Set<String> relatedIds=new HashSet<String>();
            for(ZipEntry entry:entries)if(isSynsetFile(entry.getName()))parseSelectedSynsets(zip,entry,targetSynsets,selected,relatedIds);
            Map<String,List<String>> relatedMembers=new HashMap<String,List<String>>();
            for(ZipEntry entry:entries)if(isSynsetFile(entry.getName()))parseRelatedMembers(zip,entry,relatedIds,relatedMembers);
            for(TargetLexeme target:data.targets.values())for(Sense sense:target.senses){
                Synset synset=selected.get(sense.synset);if(synset==null)continue;
                for(int relationRank=0;relationRank<synset.members.size();relationRank++){String member=synset.members.get(relationRank);if(!same(target.word,member))add(data,target.word,"synonym",member,sense.pos,sense.id,sense.synset,sense.rank,relationRank,"synset:"+sense.synset,"OEWN 2025 same-synset member");}
                addRelatedSynsets(data,target,sense,synset,"similar",relatedMembers,"similar");
                addRelatedSynsets(data,target,sense,synset,"also",relatedMembers,"similar");
                addRelatedSynsets(data,target,sense,synset,"hypernym",relatedMembers,"hypernym");
            }
        }
        return data;
    }

    private void parseEntryFile(ZipFile zip,ZipEntry entry,ImportData data)throws Exception{
        JsonNode root=read(zip,entry);Iterator<Map.Entry<String,JsonNode>> lemmas=root.fields();
        while(lemmas.hasNext()){
            Map.Entry<String,JsonNode> lemmaEntry=lemmas.next();String lemma=normalize(lemmaEntry.getKey());if(lemma==null)continue;
            TargetLexeme target=data.targets.get(lemma);Iterator<Map.Entry<String,JsonNode>> poses=lemmaEntry.getValue().fields();
            while(poses.hasNext()){
                Map.Entry<String,JsonNode> posEntry=poses.next();String pos=posEntry.getKey();JsonNode detail=posEntry.getValue();
                JsonNode senses=detail.path("sense");int senseRank=0;if(senses.isArray())for(JsonNode node:senses){
                    String id=clean(node.path("id").asText());if(id==null)continue;data.senseToLemma.put(id,lemma);
                    if(target!=null){Sense sense=new Sense(id,clean(node.path("synset").asText()),pos,senseRank);for(JsonNode rel:node.path("derivation"))sense.derivations.add(rel.asText());target.senses.add(sense);}senseRank++;
                }
                if(target!=null){int relationRank=0;for(JsonNode form:detail.path("form")){String related=normalize(form.asText());if(related!=null&&!same(lemma,related))add(data,lemma,"inflection",related,pos,null,null,0,relationRank,"entry:"+lemma+"#"+pos,"OEWN 2025 listed word form; this is an inflection, not necessarily a derivative");relationRank++;}}
            }
        }
    }

    private void parseSelectedSynsets(ZipFile zip,ZipEntry entry,Set<String> wanted,Map<String,Synset> selected,Set<String> relatedIds)throws Exception{
        JsonNode root=read(zip,entry);Iterator<Map.Entry<String,JsonNode>> fields=root.fields();
        while(fields.hasNext()){
            Map.Entry<String,JsonNode> field=fields.next();if(!wanted.contains(field.getKey()))continue;JsonNode node=field.getValue();Synset synset=new Synset(field.getKey());
            for(JsonNode member:node.path("members")){String value=normalize(member.asText());if(value!=null)synset.members.add(value);}
            for(String relation:Arrays.asList("similar","also","hypernym"))for(JsonNode ref:node.path(relation)){String id=clean(ref.asText());if(id!=null){synset.links.computeIfAbsent(relation,k->new ArrayList<String>()).add(id);relatedIds.add(id);}}
            selected.put(synset.id,synset);
        }
    }

    private void parseRelatedMembers(ZipFile zip,ZipEntry entry,Set<String> wanted,Map<String,List<String>> result)throws Exception{
        JsonNode root=read(zip,entry);Iterator<Map.Entry<String,JsonNode>> fields=root.fields();
        while(fields.hasNext()){
            Map.Entry<String,JsonNode> field=fields.next();if(!wanted.contains(field.getKey()))continue;List<String> members=new ArrayList<String>();
            for(JsonNode member:field.getValue().path("members")){String value=normalize(member.asText());if(value!=null)members.add(value);}result.put(field.getKey(),members);
        }
    }

    private void addRelatedSynsets(ImportData data,TargetLexeme target,Sense sense,Synset synset,String linkName,Map<String,List<String>> members,String relationType){
        List<String> links=synset.links.get(linkName);if(links==null)return;
        int relationRank=0;for(String link:links){List<String> values=members.get(link);if(values==null)continue;for(String related:values){if(!same(target.word,related))
            add(data,target.word,relationType,related,sense.pos,sense.id,sense.synset,sense.rank,relationRank,"synset:"+sense.synset+" -> "+link,"OEWN 2025 "+linkName+" relation");relationRank++;}}
    }

    private Map<String,Object> persist(ImportData data,String checksum){
        jdbc.update("INSERT INTO lexical_dataset_snapshot(dataset_code,dataset_name,dataset_version,source_url,license_code,license_url,attribution,checksum_sha256,import_status,relation_count,del_is) " +
            "VALUES(?,?,?,?,?,?,?,?, 'importing',0,0) ON DUPLICATE KEY UPDATE dataset_name=VALUES(dataset_name),dataset_version=VALUES(dataset_version),source_url=VALUES(source_url),license_code=VALUES(license_code),license_url=VALUES(license_url),attribution=VALUES(attribution),checksum_sha256=VALUES(checksum_sha256),import_status='importing',del_is=0,updated_at=CURRENT_TIMESTAMP",
            DATASET_CODE,DATASET_NAME,DATASET_VERSION,SOURCE_URL,"CC-BY-4.0",LICENSE_URL,ATTRIBUTION,checksum);
        jdbc.update("UPDATE word_lexical_relation_evidence SET del_is=1,updated_at=CURRENT_TIMESTAMP WHERE dataset_code=? AND del_is=0",DATASET_CODE);
        String sql="INSERT INTO word_lexical_relation_evidence(id,dataset_code,headword_norm,relation_type,related_term,part_of_speech,source_sense_id,source_synset_id,sense_rank,relation_rank,source_locator,evidence_note,del_is) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE source_sense_id=VALUES(source_sense_id),source_synset_id=VALUES(source_synset_id),sense_rank=VALUES(sense_rank),relation_rank=VALUES(relation_rank),source_locator=VALUES(source_locator),evidence_note=VALUES(evidence_note),del_is=0,updated_at=CURRENT_TIMESTAMP";
        List<Relation> relations=new ArrayList<Relation>(data.relations.values());
        for(int start=0;start<relations.size();start+=1000){List<Object[]> batch=new ArrayList<Object[]>();for(Relation r:relations.subList(start,Math.min(start+1000,relations.size())))batch.add(new Object[]{r.id,DATASET_CODE,r.headword,r.type,r.related,r.pos,r.sense,r.synset,r.rank,r.relationRank,r.locator,r.note});jdbc.batchUpdate(sql,batch);}
        jdbc.update("UPDATE lexical_dataset_snapshot SET import_status='complete',relation_count=?,imported_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE dataset_code=?",relations.size(),DATASET_CODE);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("dataset",DATASET_CODE);result.put("targetWords",data.targets.size());result.put("relations",relations.size());
        Map<String,Integer> counts=new TreeMap<String,Integer>();for(Relation r:relations)counts.put(r.type,counts.getOrDefault(r.type,0)+1);result.put("relationTypes",counts);result.put("sha256",checksum);return result;
    }

    private JsonNode read(ZipFile zip,ZipEntry entry)throws Exception{try(InputStream in=zip.getInputStream(entry)){return json.readTree(in);}}
    private boolean isEntryFile(String name){return name.startsWith("entries-")&&name.endsWith(".json");}
    private boolean isSynsetFile(String name){return name.endsWith(".json")&&!isEntryFile(name)&&!"frames.json".equals(name);}
    private void add(ImportData data,String headword,String type,String related,String pos,String sense,String synset,int rank,int relationRank,String locator,String note){
        headword=normalize(headword);related=normalize(related);pos=clean(pos);if(headword==null||related==null||same(headword,related)||headword.length()>160||related.length()>160)return;
        String key=headword+"|"+type+"|"+related+"|"+(pos==null?"":pos);Relation existing=data.relations.get(key);if(existing!=null&&(existing.rank<rank||(existing.rank==rank&&existing.relationRank<=relationRank)))return;Relation r=new Relation();r.headword=headword;r.type=type;r.related=related;r.pos=pos==null?"":pos;r.sense=limit(sense,200);r.synset=limit(synset,32);r.rank=rank;r.relationRank=relationRank;r.locator=limit(locator,240);r.note=limit(note,500);r.id=id(DATASET_CODE+"|"+key);data.relations.put(key,r);
    }
    private String normalize(String value){String v=clean(value);if(v==null)return null;return v.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ");}
    private boolean same(String a,String b){return normalize(a)!=null&&normalize(a).equals(normalize(b));}
    private String clean(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    private String limit(String value,int max){String v=clean(value);return v==null?null:(v.length()<=max?v:v.substring(0,max));}
    private String id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
    private String sha256(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];try(InputStream in=Files.newInputStream(path)){for(int n;(n=in.read(buffer))>=0;)if(n>0)digest.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format("%02x",b&255));return out.toString();}

    private static class ImportData{Map<String,TargetLexeme> targets=new LinkedHashMap<String,TargetLexeme>();Map<String,String> senseToLemma=new HashMap<String,String>();Map<String,Relation> relations=new LinkedHashMap<String,Relation>();}
    private static class TargetLexeme{String word;List<Sense>senses=new ArrayList<Sense>();TargetLexeme(String word){this.word=word;}}
    private static class Sense{String id,synset,pos;int rank;List<String>derivations=new ArrayList<String>();Sense(String id,String synset,String pos,int rank){this.id=id;this.synset=synset;this.pos=pos;this.rank=rank;}}
    private static class Synset{String id;List<String>members=new ArrayList<String>();Map<String,List<String>>links=new HashMap<String,List<String>>();Synset(String id){this.id=id;}}
    private static class Relation{String id,headword,type,related,pos,sense,synset,locator,note;int rank,relationRank;}
}
