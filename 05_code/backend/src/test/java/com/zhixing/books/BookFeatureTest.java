package com.zhixing.books;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.*;
import com.zhixing.config.*;
import com.zhixing.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookFeatureTest {
  JdbcTemplate db;
  BookStore store;
  BookService books;
  BookJobs jobs;
  MediaService media;
  AliyunTtsService tts;
  BookSources sources;
  AppParameterService params;
  AiService ai;

  @BeforeEach
  void setup() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:books"
                + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    db = new JdbcTemplate(ds);
    String sql =
        new String(
                Files.readAllBytes(Paths.get("sql/V3.30_english_reading_books.sql")),
                StandardCharsets.UTF_8)
            .split("INSERT INTO admin_menu")[0];
    new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)))
        .execute(ds);
    store =
        new BookStore(
            new com.zhixing.mapper.EnglishBookRepository(db),
            new ObjectMapper(),
            new DataSourceTransactionManager(ds));
    media = mock(MediaService.class);
    tts = mock(AliyunTtsService.class);
    params = mock(AppParameterService.class);
    ai = mock(AiService.class);
    sources = mock(BookSources.class);
    when(media.isBookAsset(anyString(), anyString())).thenReturn(true);
    when(media.isBookAsset(anyString(), anyString(), anyBoolean())).thenReturn(true);
    when(media.storeBookAsset(anyString(), anyString(), any(), anyBoolean(), anyString()))
        .thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    when(tts.synthesizeBookSegment(anyString(), anyString())).thenReturn(mp3());
    when(params.optionalStored(eq("books.tts.price_per_thousand_calls"), anyString()))
        .thenReturn("100");
    when(tts.configured()).thenReturn(true);
    books = new BookService(store, media, new BookImporter());
    jobs = new BookJobs(store, books, sources, tts, media, ai, mock(BookRemote.class), params);
    ReflectionTestUtils.setField(jobs, "enabled", true);
    ReflectionTestUtils.setField(jobs, "imageEndpoint", "");
    ReflectionTestUtils.setField(jobs, "imageModel", "");
    ReflectionTestUtils.setField(jobs, "maxCost", new java.math.BigDecimal("30"));
  }

  @AfterEach
  void close() {
    jobs.close();
  }

  static byte[] mp3() {
    byte[] b = new byte[417 * 40];
    for (int i = 0; i < 40; i++) {
      b[i * 417] = (byte) 255;
      b[i * 417 + 1] = (byte) 251;
      b[i * 417 + 2] = (byte) 144;
    }
    return b;
  }

  BookDocument document() {
    BookDocument d = new BookDocument();
    d.title = "An authorized illustrated book";
    d.licenseNote = "Test authorization";
    d.rightsScope = "test region, reading and audio";
    d.rightsConfirmed =
        d.imagesRightsConfirmed = d.audioRightsConfirmed = d.completenessConfirmed = true;
    d.chapters =
        BookText.parse(
            "# Chapter 1\n\nA little fox reads a book.\n\n# Chapter 2\n\nThe fox goes home.");
    for (BookDocument.Chapter c : d.chapters) {
      BookDocument.Picture p = new BookDocument.Picture();
      p.assetId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
      c.pictures.add(p);
    }
    return d;
  }

  String create(BookDocument d) {
    return (String) books.create(d, "admin").get("id");
  }

  int revision(String id) {
    return ((Number) store.row(id, false).get("revision")).intValue();
  }

  @Test
  void fullAudioUploadPublishAndProgressWithSnapshotIsolation() {
    BookDocument d = document();
    String id = create(d);
    assertTrue(store.persistence.bookMigrationReady());
    assertThrows(ApiException.class, () -> books.publish(id, revision(id), "admin"));
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    jobs.runNext();
    assertEquals(
        "completed", db.queryForObject("SELECT state FROM english_book_job", String.class));
    assertTrue(books.checks(id, store.draft(id)).isEmpty());
    books.publish(id, revision(id), "admin");
    String publishedTitle = store.published(id).title;
    BookDocument edit = store.draft(id);
    edit.title = "Changed draft";
    books.save(id, edit, revision(id), "admin");
    assertEquals(publishedTitle, store.published(id).title);
    assertEquals(1, ((List<?>) store.list(true, "", 1, "intro").get("items")).size());
    assertEquals(0, ((List<?>) store.list(true, "", 1, "advanced").get("items")).size());
    assertEquals(1, ((List<?>) store.list(true, "authorized", 1).get("items")).size());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM english_book_generation WHERE state='ready' AND payload IS NOT NULL",
            Integer.class));
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("revision", 0);
    p.put("completeChapterId", d.chapters.get(0).id);
    Map<String, Object> saved = books.saveProgress(id, "alice", p);
    assertEquals(1, saved.get("revision"));
    assertEquals(0, books.progress(id, "bob").get("revision"));
    assertThrows(ApiException.class, () -> books.saveProgress(id, "alice", p));
    p.put("revision", 1);
    books.saveProgress(id, "alice", p);
    assertEquals(1, ((List<?>) books.progress(id, "alice").get("completed")).size());
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    jobs.runNext();
    verify(tts, times(2)).synthesizeBookSegment(anyString(), anyString());
    books.unpublish(id, "admin");
    assertThrows(ApiException.class, () -> store.published(id));
    assertEquals(1, ((List<?>) books.progress(id, "alice").get("completed")).size());
  }

  @Test
  void uploadFailureRetriesSavedBytesWithoutAnotherPaidSynthesis() {
    String id = create(document());
    when(media.storeBookAsset(anyString(), anyString(), any(), anyBoolean(), anyString()))
        .thenThrow(new RuntimeException("upload failed"))
        .thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    jobs.runNext();
    String jid = db.queryForObject("SELECT id FROM english_book_job", String.class);
    assertEquals("failed", db.queryForObject("SELECT state FROM english_book_job", String.class));
    jobs.control(id, jid, "resume");
    jobs.runNext();
    assertEquals(
        "completed", db.queryForObject("SELECT state FROM english_book_job", String.class));
    verify(tts, times(2)).synthesizeBookSegment(anyString(), anyString());
  }

  @Test
  void uncertainProviderResultIsNotAutomaticallyRetried() {
    String id = create(document());
    when(tts.synthesizeBookSegment(anyString(), anyString()))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TTS_TIMEOUT", "timeout"));
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    jobs.runNext();
    String jid = db.queryForObject("SELECT id FROM english_book_job", String.class);
    jobs.control(id, jid, "resume");
    jobs.runNext();
    verify(tts, times(1)).synthesizeBookSegment(anyString(), anyString());
    assertEquals("failed", db.queryForObject("SELECT state FROM english_book_job", String.class));
  }

  @Test
  void textChangeInvalidatesAudioAndFullnessAndCannotMutatePublishedVersion() {
    String id = create(document());
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    jobs.runNext();
    books.publish(id, revision(id), "admin");
    BookDocument edit = store.draft(id);
    edit.chapters.get(0).paragraphs.get(0).text = "Different text.";
    books.save(id, edit, revision(id), "admin");
    assertTrue(store.draft(id).chapters.get(0).audio.isEmpty());
    assertFalse(store.draft(id).completenessConfirmed);
    assertEquals(
        "A little fox reads a book.", store.published(id).chapters.get(0).paragraphs.get(0).text);
    assertThrows(ApiException.class, () -> books.publish(id, revision(id), "admin"));
  }

  @Test
  void jobBlocksEditingAndPauseResumeKeepsDurableQueue() {
    String id = create(document());
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    assertThrows(
        ApiException.class, () -> jobs.enqueue(id, "audio", Collections.emptyMap(), "admin"));
    assertThrows(ApiException.class, () -> books.save(id, document(), revision(id), "admin"));
    String jid = db.queryForObject("SELECT id FROM english_book_job", String.class);
    jobs.control(id, jid, "pause");
    assertEquals("paused", db.queryForObject("SELECT state FROM english_book_job", String.class));
    jobs.control(id, jid, "resume");
    jobs.runNext();
    assertEquals(
        "completed", db.queryForObject("SELECT state FROM english_book_job", String.class));
  }

  @Test
  void missingRightsAndImagesAndCrossBookAssetsBlockPublishing() {
    BookDocument d = document();
    String id = create(d);
    d.imagesRightsConfirmed = false;
    assertFalse(books.checks(id, d).isEmpty());
    when(media.isBookAsset(eq(id), anyString())).thenReturn(false);
    when(media.isBookAsset(eq(id), anyString(), anyBoolean())).thenReturn(false);
    assertThrows(ApiException.class, () -> books.assetUrl(id, "foreign", true));
    assertThrows(ApiException.class, () -> books.save(id, d, revision(id), "admin"));
  }

  @Test
  void adminBookMenuIsIndependentOfArticlePermission() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    AppProperties props = new AppProperties();
    when(auth.require(anyString(), anyString()))
        .thenAnswer(
            call -> {
              if (!"english_books".equals(call.getArgument(1)))
                throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "forbidden");
              return new AdminPrincipal(
                  "admin", "admin", "EDITOR", "editor", Collections.singleton("english_books"));
            });
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new BookAdminController(books, store, jobs, sources))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new AdminSessionFilter(auth, props))
            .build();
    mvc.perform(get("/api/admin/english-books").header("X-Admin-Token", "session"))
        .andExpect(status().isOk());
    verify(auth).require("session", "english_books");
  }

  @Test
  void migrationIsRepeatableAndDoesNotGrantExistingEditorsNewPagePermissions() {
    db.execute(
        "CREATE TABLE admin_menu(id CHAR(32) PRIMARY KEY,code VARCHAR(60) UNIQUE,name VARCHAR(80),path VARCHAR(120),icon VARCHAR(12),sort_order INT,enabled INT,parent_id CHAR(32),description VARCHAR(240))");
    db.execute("CREATE TABLE admin_role(id CHAR(32) PRIMARY KEY,code VARCHAR(60))");
    db.execute(
        "CREATE TABLE admin_role_menu(role_id CHAR(32),menu_id CHAR(32),PRIMARY KEY(role_id,menu_id))");
    db.update("INSERT INTO admin_menu(id,code,name) VALUES('english-group','group_english','英语角')");
    db.update("INSERT INTO admin_role VALUES('super','SUPER_ADMIN'),('editor','CONTENT_EDITOR')");
    org.springframework.jdbc.datasource.init.ResourceDatabasePopulator migration =
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.FileSystemResource(
                "sql/V3.30_english_reading_books.sql"));
    migration.setSqlScriptEncoding("UTF-8");
    migration.execute(db.getDataSource());
    migration.execute(db.getDataSource());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM admin_menu WHERE code='english_books'", Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM admin_role_menu WHERE role_id='super'", Integer.class));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM admin_role_menu WHERE role_id='editor'", Integer.class));
  }

  @Test
  void audioSegmentationPreservesLongUnicodeTextAndDuration() {
    String text = String.join(" ", Collections.nCopies(1000, "The fox reads. 🌍"));
    List<String> parts = BookText.split(text);
    assertTrue(parts.size() > 24);
    assertEquals(text, String.join(" ", parts));
    assertTrue(parts.stream().allMatch(p -> p.codePointCount(0, p.length()) <= 280));
    assertTrue(BookAudio.duration(mp3()) > 1);
    assertThrows(
        ApiException.class,
        () -> BookAudio.duration("ID3invalid".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void billsEachShortTextRequestUsingRoundedNlsCalls() {
    assertEquals(1, BookNlsBilling.calls("hello"));
    assertEquals(1, BookNlsBilling.calls(String.join("", Collections.nCopies(100, "a"))));
    assertEquals(2, BookNlsBilling.calls(String.join("", Collections.nCopies(101, "a"))));
    assertEquals(2, BookNlsBilling.calls(String.join("", Collections.nCopies(51, "字"))));
    String id = create(document());
    jobs.enqueue(id, "audio", Collections.emptyMap(), "admin");
    try {
      Map<String, Object> options =
          store.json.readValue(
              db.queryForObject("SELECT options_json FROM english_book_job", String.class),
              Map.class);
      assertEquals("per_thousand_billing_calls", options.get("priceUnit"));
      assertEquals(
          0,
          new java.math.BigDecimal("0.2")
              .compareTo(new java.math.BigDecimal(String.valueOf(options.get("estimatedCost")))));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void seedreamUsesTwoKWithoutOpenAiOnlyNParameter() {
    ReflectionTestUtils.setField(jobs, "imageModel", "doubao-seedream-5-0-lite-260128");
    Map<String, Object> body = jobs.imageRequest("A fox reading");
    assertEquals("2K", body.get("size"));
    assertEquals("b64_json", body.get("response_format"));
    assertEquals("disabled", body.get("sequential_image_generation"));
    assertFalse(body.containsKey("n"));
  }
}
