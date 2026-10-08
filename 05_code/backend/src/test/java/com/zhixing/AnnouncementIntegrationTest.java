package com.zhixing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.service.AnnouncementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:announcements;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class AnnouncementIntegrationTest {
 @Autowired com.zhixing.service.SessionService sessions;
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired AnnouncementService service;@Autowired JdbcTemplate jdbc;
 @Test void publicationVisibilityWithdrawalValidationAndAudit() throws Exception {
  String user="00000000000000000000000000ea";
  jdbc.update("INSERT INTO app_user(id,wx_app_id,wx_open_id,nickname) VALUES(?,?,?,?)",user,"test-announcements","reader","reader");
  String token=sessions.createForUser(user).getAccessToken();
  String draft=create("draft",null),future=create("published",Instant.now().plusSeconds(3600).toString()),published=create("published",Instant.now().minusSeconds(120).toString());
  mvc.perform(get("/api/announcements").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/announcements/"+draft).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
  mvc.perform(get("/api/announcements/"+future).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
  mvc.perform(post("/api/admin/announcements").header("X-Admin-Token",token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  assertEquals(1,service.list(false,1,20).size());assertEquals(published,service.list(false,1,20).get(0).get("id"));
  assertThrows(com.zhixing.common.ApiException.class,()->service.detail(draft,false));assertThrows(com.zhixing.common.ApiException.class,()->service.detail(future,false));
  assertEquals("正文\n升级说明",service.detail(published,false).get("body"));
  mvc.perform(put("/api/admin/announcements/"+published).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"升级\",\"body\":\"撤回说明\",\"state\":\"withdrawn\"}" )).andExpect(status().isOk());
  assertTrue(service.list(false,1,20).isEmpty());assertThrows(com.zhixing.common.ApiException.class,()->service.detail(published,false));
  assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='announcement_save'",Integer.class));
  mvc.perform(post("/api/admin/announcements").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \" ,\"body\":\"内容\",\"state\":\"draft\"}")).andExpect(status().isBadRequest());
  assertEquals(3,service.list(true,1,20).size());
  mvc.perform(get("/api/admin/announcements")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/admin/announcements").header("Authorization","Bearer ordinary-user").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
 }
 private String create(String state,String time)throws Exception{
  Map<String,String> body=new HashMap<>();body.put("title","升级公告");body.put("body","正文\n升级说明");body.put("state",state);body.put("publishAt",time);
  JsonNode result=json.readTree(mvc.perform(post("/api/admin/announcements").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));return result.path("id").asText();
 }
}
