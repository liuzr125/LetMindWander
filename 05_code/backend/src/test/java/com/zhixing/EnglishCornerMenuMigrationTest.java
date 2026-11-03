package com.zhixing;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class EnglishCornerMenuMigrationTest {
  @Test
  void upgradeIsIdempotentAndOnlyGrantsContainerToExistingEnglishRoles() {
    EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
        .setName("english-menu-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE").build();
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      jdbc.execute("CREATE TABLE admin_menu(id VARCHAR(32) PRIMARY KEY,code VARCHAR(32) UNIQUE,name VARCHAR(80),path VARCHAR(120),icon VARCHAR(12),sort_order SMALLINT,enabled TINYINT,parent_id VARCHAR(32),description VARCHAR(200))");
      jdbc.execute("CREATE TABLE admin_role(id VARCHAR(32) PRIMARY KEY,code VARCHAR(32))");
      jdbc.execute("CREATE TABLE admin_role_menu(role_id VARCHAR(32),menu_id VARCHAR(32),PRIMARY KEY(role_id,menu_id))");
      jdbc.update("INSERT INTO admin_role VALUES('super','SUPER_ADMIN'),('english','ENGLISH_EDITOR'),('users','USER_EDITOR')");
      for (String code : Arrays.asList("group_content", "group_users", "words", "imports", "articles", "study_records", "users")) {
        jdbc.update("INSERT INTO admin_menu(id,code,name,path,enabled,parent_id) VALUES(?,?,?,?,1,?)", code, code, code, code.startsWith("group_") ? "" : "/" + code, code.equals("users") || code.equals("study_records") ? "group_users" : code.startsWith("group_") ? null : "group_content");
      }
      jdbc.update("INSERT INTO admin_role_menu VALUES('english','articles'),('users','users')");
      List<Map<String, Object>> before = jdbc.queryForList("SELECT role_id,menu_id FROM admin_role_menu ORDER BY role_id,menu_id");
      ResourceDatabasePopulator migration = new ResourceDatabasePopulator(new FileSystemResource("sql/V3.28_english_corner_menu.sql"));
      migration.setSqlScriptEncoding("UTF-8");
      migration.execute(db);
      migration.execute(db);
      String groupId = jdbc.queryForObject("SELECT id FROM admin_menu WHERE code='group_english'", String.class);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_menu WHERE code='group_english'", Integer.class)).isEqualTo(1);
      assertThat(jdbc.queryForList("SELECT DISTINCT parent_id FROM admin_menu WHERE code IN ('words','imports','articles','study_records')", String.class)).containsExactly(groupId);
      assertThat(jdbc.queryForList("SELECT menu_id FROM admin_role_menu WHERE role_id='english'", String.class)).containsExactlyInAnyOrder("articles", groupId);
      assertThat(jdbc.queryForList("SELECT menu_id FROM admin_role_menu WHERE role_id='users'", String.class)).containsExactly("users");
      assertThat(jdbc.queryForList("SELECT menu_id FROM admin_role_menu WHERE role_id='super'", String.class)).containsExactly(groupId);
      assertThat(jdbc.queryForList("SELECT role_id,menu_id FROM admin_role_menu WHERE menu_id<>? ORDER BY role_id,menu_id", groupId)).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT path FROM admin_menu WHERE code='articles'", String.class)).isEqualTo("/articles");
    } finally { db.shutdown(); }
  }

  @Test
  void freshDatabaseSeedsEnglishGroupIncludingReadingBooks() {
    EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
        .setName("english-seed-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
        .addScript("schema.sql").addScript("data.sql").build();
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      assertThat(jdbc.queryForList("SELECT child.code FROM admin_menu child JOIN admin_menu parent ON child.parent_id=parent.id WHERE parent.code='group_english' ORDER BY child.sort_order", String.class))
          .containsExactly("words", "imports", "articles", "english_books", "study_records");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id WHERE m.code='group_english'", Integer.class)).isEqualTo(2);
    } finally { db.shutdown(); }
  }
}
