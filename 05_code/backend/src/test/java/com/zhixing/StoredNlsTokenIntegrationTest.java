package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:savedttstoken;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.sql.init.mode=always", "app.ai.credential-encryption-key=test-only-credential-encryption-key",
    "app.ai.pricing-sync-enabled=false", "app.tts.mock-enabled=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class StoredNlsTokenIntegrationTest {
    private static final String KEY = AppParameterService.NLS_TEMPORARY_TOKEN;
    private static final String TOKEN = "test-only-stored-nls-token-value";
    private static final String OWNER = "00000000000000000000000000000001";
    private static final String PATH = "/api/learning/contents/glossary:working/speech";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppParameterService parameters;
    @Autowired AliyunTtsService tts;
    @Autowired AppProperties properties;
    @Autowired RestTemplate http;
    @MockBean SessionService sessions;
    @MockBean MediaService media;
    private MockRestServiceServer nls;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM app_parameter WHERE param_key=?", KEY);
        jdbc.update("UPDATE article_word_glossary SET audio_asset_id=NULL,audio_voice=NULL,audio_generated_at=NULL WHERE term IN ('working','reading')");
        for (String term : Arrays.asList("working", "reading")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM article_word_glossary WHERE term=?", Integer.class, term) == 0)
                jdbc.update("INSERT INTO article_word_glossary(id,term,phonetic,meaning) VALUES(?,?,?,?)", CryptoUtils.randomId(), term, "/test/", "Test");
        }
        parameters.saveSecret("ALIYUN_NLS_APP_KEY", "test-project-app-key");
        when(sessions.requireUser("Bearer test-user")).thenReturn(new AuthenticatedSession("test-session", OWNER));
        when(sessions.requireUser(null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in required"));
        when(media.referenceUrl(anyString())).thenAnswer(call -> "/api/media/" + call.getArgument(0));
        when(media.storePublicAudio(anyString(), anyString(), any(byte[].class), anyString(), anyString(), anyString()))
            .thenAnswer(call -> {
                String id = CryptoUtils.randomId();
                jdbc.update("INSERT INTO media_asset(id,purpose,object_key,mime_type,byte_size,sha256,state) VALUES(?,'word_audio',?,'audio/mpeg',10,?,'ready')",
                        id, "tts/glossary_word/" + id + ".mp3", CryptoUtils.sha256(id));
                return id;
            });
        nls = MockRestServiceServer.bindTo(http).build();
    }
    @AfterEach void verifyCalls() { nls.verify(); }

    private String saveToken(String value, boolean create) throws Exception {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("paramKey", KEY); input.put("paramValue", value); input.put("isSecret", false);
        input.put("description", "Temporary speech token"); input.put("state", "active");
        String path = "/api/admin/parameters";
        if (!create) path += "/" + jdbc.queryForObject("SELECT id FROM app_parameter WHERE param_key=?", String.class, KEY);
        String response = mvc.perform((create ? post(path) : put(path)).header("X-Admin-Token", "dev-admin-token")
                .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSecret").value(true))
                .andExpect(jsonPath("$.paramValue").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(TOKEN);
        return jdbc.queryForObject("SELECT id FROM app_parameter WHERE param_key=?", String.class, KEY);
    }
    private void expectSpeech(String term, String credential) {
        nls.expect(requestTo("https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts"))
                .andExpect(content().json("{\"token\":\"" + credential + "\",\"text\":\"" + term + "\"}"))
                .andRespond(withSuccess(new byte[]{73,68,51,4,0,0,0,0,0,0}, MediaType.valueOf("audio/mpeg")));
    }

    @Test void parameterIsEncryptedAndMiniProgramPersistsAudioWithoutReceivingToken() throws Exception {
        String id = saveToken(TOKEN, true);
        String stored = jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE id=?", String.class, id);
        assertThat(stored).startsWith("enc:v1:").doesNotContain(TOKEN);
        assertThat(parameters.requiredStoredSecret(KEY)).isEqualTo(TOKEN);
        expectSpeech("working", TOKEN);
        String response = mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audioUrl").exists()).andExpect(jsonPath("$.cached").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(TOKEN).doesNotContain(stored);
        String asset = jdbc.queryForObject("SELECT audio_asset_id FROM article_word_glossary WHERE term='working'", String.class);
        assertThat(asset).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT object_key FROM media_asset WHERE id=?", String.class, asset)).endsWith(asset + ".mp3");
        // Removing the token must not break already-saved audio.
        mvc.perform(delete("/api/admin/parameters/" + id).header("X-Admin-Token", "dev-admin-token")).andExpect(status().isOk());
        mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        assertThat(jdbc.queryForObject("SELECT del_is FROM app_parameter WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void updatingTokenIsImmediateAndSurvivesServiceRecreation() throws Exception {
        saveToken(TOKEN, true);
        saveToken("replacement-temporary-token-value", false);
        expectSpeech("working", "replacement-temporary-token-value");
        AliyunTtsService recreated = new AliyunTtsService(jdbc, parameters, media, http, properties);
        assertThat(recreated.speech(OWNER, "glossary:working").get("audioUrl")).isNotNull();
    }

    @Test void missingInactiveAndDeletedTokensNeverFallBackToAccessKey() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_NOT_CONFIGURED"));
        String id = saveToken(TOKEN, true);
        jdbc.update("UPDATE app_parameter SET state='inactive' WHERE id=?", id);
        mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_NOT_CONFIGURED"));
        jdbc.update("UPDATE app_parameter SET state='active',del_is=1 WHERE id=?", id);
        mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_NOT_CONFIGURED"));
        verify(media, never()).storePublicAudio(anyString(), anyString(), any(byte[].class), anyString(), anyString(), anyString());
    }

    @Test void expiredTokenIsReportedWithoutPersistingAnAudioAssociation() throws Exception {
        saveToken(TOKEN, true);
        nls.expect(requestTo("https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts"))
                .andRespond(withSuccess("{\"status_text\":\"Token expired\"}", MediaType.APPLICATION_JSON));
        mvc.perform(post(PATH).header("Authorization", "Bearer test-user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_REJECTED"));
        assertThat(jdbc.queryForObject("SELECT audio_asset_id FROM article_word_glossary WHERE term='working'", String.class)).isNull();
        verify(media, never()).storePublicAudio(anyString(), anyString(), any(byte[].class), anyString(), anyString(), anyString());
    }

    @Test void parameterApisNeverReturnTokenAndRequireAdmin() throws Exception {
        String id = saveToken(TOKEN, true);
        mvc.perform(get("/api/admin/parameters")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/parameters/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        String response = mvc.perform(get("/api/admin/parameters").param("keyword", KEY).header("X-Admin-Token", "dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].paramValue").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(TOKEN);
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
    }

    @Test void aiSaveEndpointPersistsTokenAndDisableSoftDeletesIt() throws Exception {
        mvc.perform(post("/api/admin/ai/tts/temporary-token-production").header("X-Admin-Token", "dev-admin-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nlsToken\":\"" + TOKEN + "\"}")).andExpect(status().isOk());
        assertThat(parameters.requiredStoredSecret(KEY)).isEqualTo(TOKEN);
        mvc.perform(delete("/api/admin/ai/tts/temporary-token-production").header("X-Admin-Token", "dev-admin-token")).andExpect(status().isOk());
        assertThat(parameters.storedSecretConfigured(KEY)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_parameter WHERE param_key=? AND del_is=1", Integer.class, KEY)).isEqualTo(1);
    }

    @Test void parameterWriteLogsOmitSensitiveValuesIncludingMalformedRequests() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("API_ACCESS");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> capture = new ch.qos.logback.core.read.ListAppender<>();
        capture.start(); logger.addAppender(capture);
        try {
            saveToken(TOKEN, true);
            mvc.perform(post("/api/admin/parameters").header("X-Admin-Token", "dev-admin-token")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"paramValue\":\"" + TOKEN))
                    .andExpect(status().isBadRequest());
            assertThat(capture.list).hasSize(2);
            for (ch.qos.logback.classic.spi.ILoggingEvent event : capture.list)
                assertThat(event.getFormattedMessage()).contains("[system parameter body omitted]").doesNotContain(TOKEN);
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
}
