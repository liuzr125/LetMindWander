package com.zhixing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhixing.mapper.OpenEnglishWordNetImportPersistenceMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class OpenEnglishWordNetPersistenceTest {
  @Test
  void reimportUpdatesEvidenceAndRestoresRetiredRowsWithoutDuplicates() {
    EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("wordnet-mapper-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .addScript("schema.sql")
            .build();
    try {
      OpenEnglishWordNetImportPersistenceMapper mapper =
          MapperTestSupport.mapper(db, OpenEnglishWordNetImportPersistenceMapper.class);
      JdbcTemplate jdbc = new JdbcTemplate(db);
      mapper.upsertDatasetSnapshot(
          "test",
          "Test",
          "2025",
          "https://example.test",
          "CC-BY-4.0",
          "https://example.test/license",
          "Test",
          String.join("", Collections.nCopies(64, "a")));
      Map<String, Object> first =
          evidence(
              "00000000000000000000000000000001", "learn", "study", "sense1", "synset1", "source1");
      Map<String, Object> second =
          evidence(
              "00000000000000000000000000000002", "read", "peruse", "sense2", "synset2", "source2");
      mapper.upsertEvidenceBatch(Arrays.asList(first, second));
      assertEquals(2, mapper.retireDatasetEvidence("test"));
      first.put("evidenceNote", "corrected");
      mapper.upsertEvidenceBatch(Collections.singletonList(first));
      mapper.completeDatasetImport(1, "test");
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM word_lexical_relation_evidence", Integer.class));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM word_lexical_relation_evidence WHERE del_is=0", Integer.class));
      assertEquals(
          "corrected",
          jdbc.queryForObject(
              "SELECT evidence_note FROM word_lexical_relation_evidence WHERE del_is=0",
              String.class));
      assertEquals(
          "complete",
          jdbc.queryForObject(
              "SELECT import_status FROM lexical_dataset_snapshot WHERE dataset_code='test'",
              String.class));
    } finally {
      db.shutdown();
    }
  }

  private Map<String, Object> evidence(
      String id, String headword, String related, String sense, String synset, String locator) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("id", id);
    row.put("datasetCode", "test");
    row.put("headword", headword);
    row.put("relationType", "synonym");
    row.put("relatedTerm", related);
    row.put("partOfSpeech", "v");
    row.put("senseId", sense);
    row.put("synsetId", synset);
    row.put("senseRank", 1);
    row.put("relationRank", 1);
    row.put("sourceLocator", locator);
    row.put("evidenceNote", "original");
    return row;
  }
}
