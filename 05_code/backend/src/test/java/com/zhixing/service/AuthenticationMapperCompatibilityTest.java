package com.zhixing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.RegistrationMapper;
import com.zhixing.mapper.UserSessionMapper;
import com.zhixing.mapper.type.BinaryHash;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AuthenticationMapperCompatibilityTest {
  @Test
  void binaryHashesBindThroughMybatisPlusAndUppercaseColumnLabelsRemainReadable() {
    EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      jdbc.execute(
          "CREATE TABLE app_user(id VARCHAR(32) PRIMARY KEY,status VARCHAR(16),last_login_at TIMESTAMP,updated_at TIMESTAMP)");
      jdbc.execute(
          "CREATE TABLE auth_session(id VARCHAR(32) PRIMARY KEY,principal_type VARCHAR(16),principal_id VARCHAR(32),token_hash VARBINARY(32),expires_at TIMESTAMP,last_seen_at TIMESTAMP,revoked_at TIMESTAMP,updated_at TIMESTAMP)");
      jdbc.execute(
          "CREATE TABLE invite_code(id VARCHAR(32) PRIMARY KEY,code_hash VARBINARY(32),status VARCHAR(16),expires_at TIMESTAMP)");
      jdbc.update("INSERT INTO app_user(id,status) VALUES('user','active')");
      jdbc.update(
          "INSERT INTO invite_code(id,code_hash,status,expires_at) VALUES('invite',?,'available',?)",
          CryptoUtils.sha256("invite"),
          Timestamp.from(Instant.now().plusSeconds(60)));
      MybatisConfiguration configuration = new MybatisConfiguration();
      configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), db));
      for (String name : new String[] {"UserSessionMapper", "RegistrationMapper"}) {
        String resource = "/mapper/" + name + ".xml";
        new XMLMapperBuilder(
                getClass().getResourceAsStream(resource),
                configuration,
                resource,
                configuration.getSqlFragments())
            .parse();
      }
      try (SqlSession session =
          new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        UserSessionMapper sessions = session.getMapper(UserSessionMapper.class);
        Timestamp now = Timestamp.from(Instant.now());
        BinaryHash hash = new BinaryHash(CryptoUtils.sha256("token"));
        assertThat(
                sessions.insertUserSession(
                    "session", "user", hash, Timestamp.from(Instant.now().plusSeconds(60)), now))
            .isEqualTo(1);
        Map<String, Object> row =
            sessions
                .selectUserSessionByTokenHash(new BinaryHash(CryptoUtils.sha256("token")))
                .get(0);
        assertThat(value(row, "session_id")).isEqualTo("session");
        assertThat(value(row, "user_id")).isEqualTo("user");
        assertThat(value(row, "expires_at")).isNotNull();
        Map<String, Object> invite =
            session
                .getMapper(RegistrationMapper.class)
                .lockInviteByCodeHash(new BinaryHash(CryptoUtils.sha256("invite")))
                .get(0);
        assertThat(value(invite, "status")).isEqualTo("available");
        sessions.revokeSession(now, now, "session");
        assertThat(value(sessions.selectUserSessionByTokenHash(hash).get(0), "revoked_at"))
            .isNotNull();
      }
    } finally {
      db.shutdown();
    }
  }

  private Object value(Map<String, Object> row, String key) {
    return row.containsKey(key) ? row.get(key) : row.get(key.toUpperCase(java.util.Locale.ROOT));
  }
}
