package com.zhixing.service;

import com.fasterxml.jackson.databind.*;
import com.zhixing.mapper.WordLearningCardSeedMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in only. No startup hook, no network, no AI or audio calls. */
public class WordLearningCardSeedImporter {
  private final WordLearningCardSeedMapper mapper;
  private final TransactionTemplate tx;

  public WordLearningCardSeedImporter(
      WordLearningCardSeedMapper mapper, PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.tx = new TransactionTemplate(manager);
  }

  public int seed() throws Exception {
    final JsonNode seeds;
    try (InputStream in = new ClassPathResource("word-learning-cards.json").getInputStream()) {
      seeds = new ObjectMapper().readTree(in);
    }
    return tx.execute(
        status -> {
          int inserted = 0;
          for (JsonNode word : seeds) {
            List<String> versions =
                mapper.selectSeedVersions(
                    word.path("word").asText(),
                    word.path("meaningContains").asText(),
                    word.path("meaningContains").asText());
            for (String version : versions) {
              int order = 0;
              for (JsonNode card : word.path("cards")) {
                String key = card.path("key").asText(), url = optional(card, "sourceUrl");
                // 稳定键检查包含软删除卡片，重复导入不会复活已撤回数据。
                if (mapper.countCardKey(version, key) > 0) continue;
                String id =
                    UUID.nameUUIDFromBytes(
                            ("word-card:" + version + ":" + key).getBytes(StandardCharsets.UTF_8))
                        .toString()
                        .replace("-", "");
                inserted +=
                    mapper.insertSeedCard(
                        id,
                        version,
                        key,
                        card.path("type").asText(),
                        card.path("sense").asText(),
                        card.path("title").asText(),
                        optional(card, "related"),
                        card.path("body").asText(),
                        optional(card, "example"),
                        optional(card, "translation"),
                        optional(card, "prompt"),
                        url == null ? "original" : "reference",
                        url == null ? "本应用原创 · AI辅助编写" : card.path("sourceTitle").asText(),
                        url,
                        url == null ? 0 : 1,
                        "本应用原创辅助讲解及例句（AI辅助编写），非词典原文、影视台词或歌词。参考链接仅用于核对知识点，不代表取得该站内容的复制授权。",
                        order++);
              }
            }
          }
          return inserted;
        });
  }

  private String optional(JsonNode node, String key) {
    String value = node.path(key).asText("").trim();
    return value.isEmpty() ? null : value;
  }
}
