package com.zhixing.mapper;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL boundary for reading-book snapshots, durable generation steps and per-user progress. */
@Repository
public class EnglishBookRepository {
  private final JdbcTemplate db;

  public EnglishBookRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> book(String id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM english_reading_book WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
  }

  public int activeJobs(String id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM english_book_job WHERE book_id=? AND state IN ('queued','running','pausing')",
        Integer.class,
        id);
  }

  public void insertBook(String id, String title, String doc, String actor, String difficulty) {
    db.update(
        "INSERT INTO english_reading_book(id,title,draft_json,created_by,updated_by,difficulty) VALUES(?,?,?,?,?,?)",
        id,
        title,
        doc,
        actor,
        actor,
        difficulty);
  }

  public int saveDraft(
      String id,
      String title,
      String doc,
      String actor,
      int expected,
      Timestamp now,
      String difficulty) {
    return db.update(
        "UPDATE english_reading_book SET title=?,draft_json=?,revision=revision+1,updated_by=?,updated_at=?,difficulty=? WHERE id=? AND revision=?",
        title,
        doc,
        actor,
        now,
        difficulty,
        id,
        expected);
  }

  public void jobSave(String id, String title, String doc, Timestamp now, String difficulty) {
    db.update(
        "UPDATE english_reading_book SET title=?,draft_json=?,revision=revision+1,updated_at=?,difficulty=? WHERE id=?",
        title,
        doc,
        now,
        difficulty,
        id);
  }

  public void publish(String id, String actor, Timestamp now) {
    db.update(
        "UPDATE english_reading_book SET state='published',published_title=title,published_difficulty=difficulty,published_json=draft_json,published_revision=revision,updated_by=?,updated_at=? WHERE id=?",
        actor,
        now,
        id);
  }

  public void unpublish(String id, String actor, Timestamp now) {
    db.update(
        "UPDATE english_reading_book SET state='offline',updated_by=?,updated_at=? WHERE id=?",
        actor,
        now,
        id);
  }

  public List<Map<String, Object>> books(
      boolean published, String keyword, int offset, String difficulty) {
    return db.queryForList(
        "SELECT id,state,revision,published_revision,draft_json,published_json FROM english_reading_book WHERE "
            + (published ? "state='published' AND published_title" : "1=1 AND title")
            + " LIKE ? AND (?='' OR "
            + (published ? "published_difficulty" : "difficulty")
            + "=?) ORDER BY updated_at DESC,id LIMIT 21 OFFSET ?",
        keyword,
        difficulty,
        difficulty,
        offset);
  }

  public List<Map<String, Object>> jobs(String id) {
    return db.queryForList(
        "SELECT id,kind,state,message,completed_count,total_count,spent_units,options_json,created_at,updated_at FROM english_book_job WHERE book_id=? ORDER BY created_at DESC,id DESC LIMIT 20",
        id);
  }

  public List<Map<String, Object>> progress(String owner, String book) {
    return db.queryForList(
        "SELECT revision,progress_json FROM english_book_progress WHERE owner_id=? AND book_id=?",
        owner,
        book);
  }

  public void insertProgress(String owner, String book, String json) {
    db.update(
        "INSERT INTO english_book_progress(owner_id,book_id,revision,progress_json) VALUES(?,?,1,?)",
        owner,
        book,
        json);
  }

  public int saveProgress(String owner, String book, int revision, String json, Timestamp now) {
    return db.update(
        "UPDATE english_book_progress SET revision=revision+1,progress_json=?,updated_at=? WHERE owner_id=? AND book_id=? AND revision=?",
        json,
        now,
        owner,
        book,
        revision);
  }

  public List<String> shelf(String owner, String keyword, int offset, String difficulty) {
    return db.queryForList(
        "SELECT p.book_id FROM english_book_progress p JOIN english_reading_book b ON b.id=p.book_id WHERE p.owner_id=? AND b.state='published' AND b.published_title LIKE ? AND (?='' OR b.published_difficulty=?) ORDER BY p.updated_at DESC,p.book_id LIMIT 21 OFFSET ?",
        String.class,
        owner,
        keyword,
        difficulty,
        difficulty,
        offset);
  }

  public boolean hasWordSource(String owner, String book, String paragraph, String content) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM english_book_word_source WHERE owner_id=? AND book_id=? AND paragraph_id=? AND content_id=?",
            Integer.class,
            owner,
            book,
            paragraph,
            content)
        > 0;
  }

  public void wordSource(
      String owner,
      String book,
      String chapter,
      String paragraph,
      String content,
      Object revision,
      String quote) {
    db.update(
        "INSERT INTO english_book_word_source(owner_id,book_id,chapter_id,paragraph_id,content_id,book_revision,quote_text) VALUES(?,?,?,?,?,?,?)",
        owner,
        book,
        chapter,
        paragraph,
        content,
        revision,
        quote);
  }

  public void jobOptions(String id, String options) {
    db.update("UPDATE english_book_job SET options_json=? WHERE id=?", options, id);
  }

  public void insertJob(String id, String book, String kind, String options, String actor) {
    db.update(
        "INSERT INTO english_book_job(id,book_id,kind,state,options_json,created_by) VALUES(?,?,?,'queued',?,?)",
        id,
        book,
        kind,
        options,
        actor);
  }

  public List<Map<String, Object>> job(String id, String book) {
    return db.queryForList("SELECT * FROM english_book_job WHERE id=? AND book_id=?", id, book);
  }

  public void pauseQueued(String id) {
    db.update("UPDATE english_book_job SET state='paused' WHERE id=? AND state='queued'", id);
  }

  public void pauseRunning(String id) {
    db.update("UPDATE english_book_job SET state='pausing' WHERE id=? AND state='running'", id);
  }

  public void cancel(String id, Timestamp now) {
    db.update("UPDATE english_book_job SET state='cancelled',updated_at=? WHERE id=?", now, id);
  }

  public void resume(String id, Timestamp now) {
    db.update(
        "UPDATE english_book_job SET state='queued',message='',updated_at=? WHERE id=?", now, id);
  }

  public void heartbeat(String worker, Timestamp until) {
    db.update(
        "UPDATE english_book_job SET lease_until=? WHERE worker_id=? AND state IN ('running','pausing')",
        until,
        worker);
  }

  public void recover(Timestamp now) {
    db.update(
        "UPDATE english_book_job SET state='paused',message='上次处理未结束，生成结果需核对',updated_at=? WHERE state IN ('running','pausing') AND (lease_until IS NULL OR lease_until<?)",
        now,
        now);
  }

  public List<Map<String, Object>> nextJob() {
    return db.queryForList(
        "SELECT * FROM english_book_job WHERE state='queued' ORDER BY created_at,id LIMIT 1");
  }

  public int claim(String id, String worker, Timestamp until, Timestamp now) {
    return db.update(
        "UPDATE english_book_job SET state='running',worker_id=?,lease_until=?,updated_at=? WHERE id=? AND state='queued'",
        worker,
        until,
        now,
        id);
  }

  public void finish(String id, String worker, Timestamp now) {
    db.update(
        "UPDATE english_book_job SET state='completed',message='处理完成，请预览并核对发布条件',updated_at=? WHERE id=? AND state='running' AND worker_id=?",
        now,
        id,
        worker);
  }

  public void fail(String id, String worker, String message, Timestamp now) {
    db.update(
        "UPDATE english_book_job SET state='failed',message=?,updated_at=? WHERE id=? AND state IN ('running','pausing') AND worker_id=?",
        message,
        now,
        id,
        worker);
  }

  public List<Map<String, Object>> guard(String id) {
    return db.queryForList("SELECT state,worker_id FROM english_book_job WHERE id=?", id);
  }

  public void paused(String id, Timestamp now) {
    db.update(
        "UPDATE english_book_job SET state='paused',updated_at=? WHERE id=? AND state='pausing'",
        now,
        id);
  }

  public void checkpoint(String id, int done, int total, Timestamp now) {
    db.update(
        "UPDATE english_book_job SET completed_count=?,total_count=?,updated_at=? WHERE id=?",
        done,
        total,
        now,
        id);
  }

  public List<Map<String, Object>> unresolved(String book) {
    return db.queryForList(
        "SELECT step_key,state,updated_at FROM english_book_generation WHERE book_id=? AND state='requesting' ORDER BY updated_at",
        book);
  }

  public void resetUnresolved(String key, String book) {
    db.update(
        "DELETE FROM english_book_generation WHERE step_key=? AND book_id=? AND state='requesting'",
        key,
        book);
  }

  public void spend(String job, long units) {
    db.update("UPDATE english_book_job SET spent_units=spent_units+? WHERE id=?", units, job);
  }

  public List<Map<String, Object>> generation(String key, String book) {
    return db.queryForList(
        "SELECT state,asset_id,payload FROM english_book_generation WHERE step_key=? AND book_id=?",
        key,
        book);
  }

  public byte[] payload(String key) {
    return db.queryForObject(
        "SELECT payload FROM english_book_generation WHERE step_key=?", byte[].class, key);
  }

  public void requesting(String key, String book) {
    db.update(
        "INSERT INTO english_book_generation(step_key,book_id,state) VALUES(?,?,'requesting')",
        key,
        book);
  }

  public void forgetRejected(String key) {
    db.update("DELETE FROM english_book_generation WHERE step_key=? AND state='requesting'", key);
  }

  public void generated(String key, byte[] bytes, Timestamp now) {
    db.update(
        "UPDATE english_book_generation SET state='generated',payload=?,updated_at=? WHERE step_key=?",
        bytes,
        now,
        key);
  }

  public List<String> readyAsset(String key, String book) {
    return db.queryForList(
        "SELECT asset_id FROM english_book_generation WHERE step_key=? AND book_id=? AND state='ready'",
        String.class,
        key,
        book);
  }

  public double duration(String key) {
    return db.queryForObject(
        "SELECT duration_seconds FROM english_book_generation WHERE step_key=?", Double.class, key);
  }

  public void ready(String key, String asset, double duration, Timestamp now) {
    db.update(
        "UPDATE english_book_generation SET state='ready',payload=NULL,asset_id=?,duration_seconds=?,updated_at=? WHERE step_key=?",
        asset,
        duration,
        now,
        key);
  }

  public int activeBookJobs() {
    return db.queryForObject(
        "SELECT COUNT(*) FROM english_book_job WHERE state IN ('queued','running','pausing')",
        Integer.class);
  }

  public void lockBookSettingsVersion() {
    db.queryForList(
        "SELECT param_value FROM app_parameter WHERE param_key='books.settings.version' FOR UPDATE");
  }

  public boolean bookMigrationReady() {
    return db.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            c -> {
              Map<String, List<String>> required = new LinkedHashMap<>();
              required.put(
                  "english_reading_book",
                  Arrays.asList(
                      "published_title", "published_json", "difficulty", "published_difficulty"));
              required.put(
                  "english_book_job", Arrays.asList("worker_id", "lease_until", "spent_units"));
              required.put("english_book_generation", Arrays.asList("payload", "duration_seconds"));
              required.put("english_book_progress", Arrays.asList("progress_json"));
              required.put("english_book_word_source", Arrays.asList("quote_text"));
              for (Map.Entry<String, List<String>> table : required.entrySet()) {
                Set<String> columns = new HashSet<>();
                try (java.sql.ResultSet rows =
                    c.getMetaData().getColumns(c.getCatalog(), null, table.getKey(), null)) {
                  while (rows.next())
                    columns.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                }
                if (!columns.containsAll(table.getValue())) return false;
              }
              return true;
            });
  }
}
