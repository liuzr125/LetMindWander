package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;

/** Supplemental material only: reading a card never writes mastery or learning events. */
@Service
public class WordLearningCardService {
    private final JdbcTemplate jdbc;
    public WordLearningCardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> cards(String contentId) {
        return cards(contentId,new LinkedHashSet<String>(WordLearningCardPreferenceService.TYPES));
    }

    public List<Map<String,Object>> cards(String contentId,Set<String> enabledTypes) {
        List<String> versions = jdbc.queryForList("SELECT cv.id FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE lc.id=? AND lc.content_type='word' AND lc.state='published' AND lc.withdrawn_at IS NULL AND cv.review_status='approved'", String.class, contentId);
        if (versions.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"WORD_NOT_FOUND","词条未发布或不存在");
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        // Per-type limits prevent one verbose section from starving all other sections.
        for (String type : Arrays.asList("mnemonic","synonym","confusable","derivative","usage","quote")) {
            if(enabledTypes==null||!enabledTypes.contains(type))continue;
            List<Map<String,Object>> rows = jdbc.queryForList("SELECT c.*,CASE WHEN ws.id IS NOT NULL THEN we.id END AS linked_example_id," +
                "CASE WHEN ws.id IS NOT NULL THEN we.sentence END AS linked_example_text,CASE WHEN ws.id IS NOT NULL THEN we.translation END AS linked_example_translation " +
                "FROM word_learning_card c LEFT JOIN word_example we ON we.id=c.example_id LEFT JOIN word_sense ws ON ws.id=we.sense_id AND ws.content_version_id=c.content_version_id " +
                "WHERE c.content_version_id=? AND c.card_type=? " +
                "AND c.del_is=0 AND c.state='published' AND c.review_status='approved' AND c.reviewed_at IS NOT NULL " +
                "AND c.rights_status IN ('original','licensed','public_domain','inherited') ORDER BY c.sort_no,c.id LIMIT 30", versions.get(0), type);
            int included=0;
            for (Map<String,Object> row : rows) {
                if (!publishable(row)) continue;
                Map<String,Object> view = new LinkedHashMap<String,Object>();
                for (String field : Arrays.asList("id","card_type","sense_label","title","related_term","body","example_text","example_translation","recall_prompt","source_kind","source_title","source_locator","rights_status","rights_note"))
                    view.put(camel(field), row.get(field));
                if(value(row,"example_text").isEmpty()&&!value(row,"linked_example_id").isEmpty()){
                    view.put("exampleText",row.get("linked_example_text"));view.put("exampleTranslation",row.get("linked_example_translation"));
                }
                view.put("sourceUrl", safeUrl(value(row,"source_url")));
                result.add(view);
                if (++included == 6) break;
            }
        }
        return result;
    }

    private boolean publishable(Map<String,Object> row) {
        String kind=value(row,"source_kind"), rights=value(row,"rights_status");
        if (value(row,"source_title").isEmpty() || value(row,"reviewed_by").isEmpty()) return false;
        if (!Arrays.asList("original","reference","content","film","lyric").contains(kind)) return false;
        boolean quotation="quote".equals(value(row,"card_type"));
        if (quotation != Arrays.asList("film","lyric").contains(kind)) return false;
        if("content".equals(kind)){
            if(!"inherited".equals(rights)||!"1".equals(value(row,"source_verified"))||value(row,"linked_example_id").isEmpty())return false;
        }else if (!"original".equals(kind) && (!"1".equals(value(row,"source_verified")) || safeUrl(value(row,"source_url"))==null)) return false;
        if (quotation && ("original".equals(rights) || value(row,"source_locator").isEmpty())) return false;
        return !value(row,"rights_note").isEmpty();
    }
    private String value(Map<String,Object> row,String key) { Object v=row.get(key);return v==null?"":v.toString().trim(); }
    static String safeUrl(String url) {
        try { URI uri=URI.create(url);return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null ? uri.toASCIIString() : null; }
        catch(Exception ignored){return null;}
    }
    private String camel(String field) { StringBuilder b=new StringBuilder();boolean upper=false;for(char c:field.toCharArray()){if(c=='_')upper=true;else{b.append(upper?Character.toUpperCase(c):c);upper=false;}}return b.toString(); }
}
