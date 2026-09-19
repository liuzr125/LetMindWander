package com.zhixing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f20;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F20VocabularyImportIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    private static final String BOOK="f2000000000000000000000000000001";

    @BeforeEach void seed(){
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,word_count,sort_no,state) VALUES(?,?,'导入测试词书','exam',0,1,'active')",BOOK,"f20-book");
    }

    @Test void stagesDatasetBuildsStableTtsPlanAndBlocksPrematurePublish() throws Exception {
        String datasetJson="{\"datasetName\":\"已授权演示词表\",\"providerName\":\"测试提供方\",\"licenseStatus\":\"verified\",\"licenseNote\":\"测试授权\",\"payload\":\"word,pos,meaning,example\\nrecord,noun,记录,Keep a record.\\nobject,noun,物体,,\"}";
        String dataset=mvc.perform(post("/api/admin/vocabulary/datasets").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datasetName").value("已授权演示词表")).andReturn().getResponse().getContentAsString();
        String datasetId=idOf(dataset,"datasetId");
        String batchJson="{\"datasetId\":\""+datasetId+"\",\"targetBookId\":\""+BOOK+"\",\"options\":{\"usVoice\":\"eva\",\"ukVoice\":\"luna\"}}";
        String batch=mvc.perform(post("/api/admin/vocabulary/import-batches").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content(batchJson))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("draft")).andReturn().getResponse().getContentAsString();
        String batchId=idOf(batch,"batchId");
        mvc.perform(post("/api/admin/vocabulary/import-batches/{id}/start",batchId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ready_for_review"))
                .andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.ttsPending").value(6));
        mvc.perform(post("/api/admin/vocabulary/import-batches/{id}/publish",batchId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TTS_GATE_NOT_READY"));
        jdbc.update("UPDATE tts_generation_task SET state='succeeded',asset_id='f2000000000000000000000000000002' WHERE batch_id=?",batchId);
        mvc.perform(post("/api/admin/vocabulary/import-batches/{id}/publish",batchId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("published"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].contentState").value("published"));
    }

    private String idOf(String json,String key){java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\\""+key+"\\\":\\\"([^\\\"]+)\\\"").matcher(json);if(!matcher.find())throw new AssertionError("missing "+key);return matcher.group(1);}
}
