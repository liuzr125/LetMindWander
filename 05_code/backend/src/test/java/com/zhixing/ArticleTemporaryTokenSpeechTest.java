package com.zhixing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.common.GlobalExceptionHandler;
import com.zhixing.config.ApiAccessLoggingFilter;
import com.zhixing.config.AppProperties;
import com.zhixing.controller.AdminContentController;
import com.zhixing.service.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ArticleTemporaryTokenSpeechTest {
    private static final String CONTENT = "31000000000000000000000000000001";
    private static final String VERSION = "31000000000000000000000000000002";
    private static final String SOURCE = "31000000000000000000000000000003";
    private static final String TOKEN = "test-nls-request-token-not-a-real-secret";
    private static final String ENDPOINT = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts";
    private static final String PATH = "/api/admin/content/articles/" + CONTENT + "/speech";
    private static final byte[] AUDIO = new byte[]{73, 68, 51, 4, 0, 0, 0, 0, 0, 0};
    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private OSS oss;
    private AppParameterService parameters;
    private MediaService media;
    private AppProperties properties;
    private RestTemplate http;
    private MockRestServiceServer nls;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("tts" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        jdbc = new JdbcTemplate(db);
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note,enabled) VALUES(?, 'Test', 'original','Test',1)", SOURCE);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)",
                CONTENT, SOURCE, CryptoUtils.sha256(CONTENT), VERSION, VERSION);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'Test article','Hello, this is a test.','Test',?,'approved','00000000000000000000000000000002')",
                VERSION, CONTENT, CryptoUtils.sha256(VERSION));
        parameters = mock(AppParameterService.class);
        when(parameters.optional(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(parameters.configured("ALIYUN_NLS_APP_KEY")).thenReturn(true);
        when(parameters.required("ALIYUN_NLS_APP_KEY")).thenReturn("test-project-app-key");
        oss = mock(OSS.class);
        media = new MediaService(jdbc, parameters) {
            @Override protected OSS buildClient() { return oss; }
        };
        ReflectionTestUtils.setField(media, "ossBucket", "test-bucket");
        ReflectionTestUtils.setField(media, "publicBaseUrl", "/api/media");
        properties = new AppProperties();
        properties.setAdminToken("test-admin");
        properties.getTts().setMockEnabled(false);
        http = new RestTemplate();
        nls = MockRestServiceServer.bindTo(http).build();
        createMvc();
    }

    private void createMvc() {
        AliyunTtsService tts = new AliyunTtsService(jdbc, parameters, media, http, properties);
        AdminContentService contents = mock(AdminContentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminContentController(contents, properties, tts))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new ApiAccessLoggingFilter()).build();
    }

    @AfterEach void tearDown() { db.shutdown(); }

    private ResultActions generate(String body) throws Exception {
        return mvc.perform(post(PATH).header("X-Admin-Token", "test-admin")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private String body() { return "{\"nlsToken\":\"" + TOKEN + "\"}"; }
    private void expectSynthesis() {
        nls.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"token\":\"" + TOKEN + "\",\"appkey\":\"test-project-app-key\",\"text\":\"Hello, this is a test.\",\"format\":\"mp3\"}"))
                .andRespond(withSuccess(AUDIO, MediaType.valueOf("audio/mpeg")));
    }
    private void assertNoSavedAudio() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT article_audio_asset_id FROM content_version WHERE id=?", String.class, VERSION)).isNull();
    }
    private void assertNoAccessKeyFallback() {
        verify(parameters, never()).required("ALIYUN_AK_ID");
        verify(parameters, never()).required("ALIYUN_AK_SECRET");
    }

    @Test void explicitTokenUploadsBytesAndPersistsObjectKeyAndVersionAssociation() throws Exception {
        expectSynthesis();
        when(oss.putObject(eq("test-bucket"), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenAnswer(call -> {
                    // Storage must finish before publishing any media/short-article association.
                    assertNoSavedAudio();
                    InputStream stream = call.getArgument(2);
                    byte[] uploaded = new byte[AUDIO.length];
                    assertThat(stream.read(uploaded)).isEqualTo(AUDIO.length);
                    assertThat(uploaded).containsExactly(AUDIO);
                    ObjectMetadata metadata = call.getArgument(3);
                    assertThat(metadata.getContentType()).isEqualTo("audio/mpeg");
                    return null;
                });
        String response = generate(body()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false)).andReturn().getResponse().getContentAsString();
        Map<?,?> result = new ObjectMapper().readValue(response, Map.class);
        String assetId = String.valueOf(result.get("assetId"));
        assertThat(result.get("audioUrl")).isEqualTo("/api/media/" + assetId);
        assertThat(jdbc.queryForObject("SELECT object_key FROM media_asset WHERE id=?", String.class, assetId))
                .isEqualTo("tts/english_article/" + assetId + ".mp3");
        assertThat(jdbc.queryForObject("SELECT article_audio_asset_id FROM content_version WHERE id=?", String.class, VERSION)).isEqualTo(assetId);
        assertThat(jdbc.queryForObject("SELECT article_audio_generated_at FROM content_version WHERE id=?", java.sql.Timestamp.class, VERSION)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT asset_id FROM tts_usage_log WHERE state='succeeded'", String.class)).isEqualTo(assetId);
        // Fresh service simulates losing all temporary session memory: persisted audio still works.
        createMvc();
        generate(body()).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.assetId").value(assetId));
        verify(oss, times(1)).putObject(eq("test-bucket"), anyString(), any(InputStream.class), any(ObjectMetadata.class));
        verify(oss).shutdown();
        assertNoAccessKeyFallback();
        nls.verify();
    }

    @Test void expiredTokenDoesNotFallBackToAccessKeyOrStoreAudio() throws Exception {
        nls.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"status_text\":\"Token expired\"}"));
        generate(body()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_REJECTED"));
        assertNoSavedAudio();
        verifyNoInteractions(oss);
        assertNoAccessKeyFallback();
        nls.verify();
    }

    @Test void successfulHttpWithJsonErrorIsNotStoredAsMp3() throws Exception {
        nls.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{\"status_text\":\"Token invalid\"}", MediaType.APPLICATION_JSON));
        generate(body()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("TTS_TEMPORARY_TOKEN_REJECTED"));
        assertNoSavedAudio();
        verifyNoInteractions(oss);
        nls.verify();
    }

    @Test void ossFailureDoesNotPublishAnAudioAddress() throws Exception {
        expectSynthesis();
        when(oss.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new IllegalStateException("Simulated OSS failure"));
        generate(body()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("MEDIA_STORE_FAILED"));
        assertNoSavedAudio();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tts_usage_log WHERE state='failed' AND error_code='MEDIA_STORE_FAILED'", Integer.class)).isEqualTo(1);
        nls.verify();
    }

    @Test void missingOrBlankTokenIsRejectedWithoutAnyProviderCall() throws Exception {
        for (String invalid : new String[]{"", "{}", "{\"nlsToken\":\"   \"}", "{\"nlsToken\":\"short\"}"}) {
            generate(invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NLS_TEMPORARY_TOKEN"));
        }
        assertNoSavedAudio();
        verifyNoInteractions(oss);
        assertNoAccessKeyFallback();
        nls.verify();
    }

    @Test void administratorAuthorizationIsRequired() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
        verifyNoInteractions(oss);
        nls.verify();
    }

    @Test void requestLogsOmitTokenEvenForMalformedJson() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("API_ACCESS");
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start(); logger.addAppender(capture);
        try {
            generate("{\"nlsToken\":\"" + TOKEN).andExpect(status().isBadRequest());
            assertThat(capture.list).isNotEmpty();
            for (ILoggingEvent event : capture.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(TOKEN).contains("[TTS credentials omitted]");
            }
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
}
