package com.zhixing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhixing.mapper.type.BinaryHash;
import java.sql.Timestamp;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class PersistenceProjectionContractTest {
  interface ProjectionMapper {
    @Insert(
        "INSERT INTO projection_contract(id,body,digest,created_at) VALUES(#{id},#{body},#{digest.bytes,jdbcType=BINARY},#{createdAt})")
    int insert(
        @Param("id") String id,
        @Param("body") String body,
        @Param("digest") BinaryHash digest,
        @Param("createdAt") Timestamp createdAt);

    @Select("SELECT ID,BODY,DIGEST,CREATED_AT FROM projection_contract WHERE id=#{id}")
    Map<String, Object> select(@Param("id") String id);
  }

  @Test
  void binaryDigestAndClobMapPreserveJdbcContractWithUppercaseColumnLabels() {
    EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("projection" + System.nanoTime())
            .build();
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      jdbc.execute(
          "CREATE TABLE projection_contract(id VARCHAR(32) PRIMARY KEY,body CLOB,digest VARBINARY(32),created_at TIMESTAMP)");
      ProjectionMapper mapper = MapperTestSupport.mapper(db, ProjectionMapper.class);
      byte[] digest = new byte[] {0, 1, -128, -1};
      Timestamp createdAt = Timestamp.valueOf("2026-10-08 10:12:13");
      assertThat(mapper.insert("projection", "中文 explanation", new BinaryHash(digest), createdAt))
          .isEqualTo(1);
      Map<String, Object> row = mapper.select("projection");
      assertThat(row.get("id")).isEqualTo("projection");
      assertThat(row.get("body")).isEqualTo("中文 explanation");
      assertThat(row.get("digest")).isEqualTo(digest);
      assertThat(row.get("created_at")).isEqualTo(createdAt);
      assertThat(row.keySet()).contains("ID", "BODY", "DIGEST", "CREATED_AT");
    } finally {
      db.shutdown();
    }
  }
}
