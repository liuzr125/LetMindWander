# 在线词书与词条快照（V3.16.4 / V3.16.6）

入口：Web 管理端 → 词库批次 → 在线词书目录。

## 本次范围与边界

- 核心目录与正式词书保持一致：小学、初中、高中、CET-4、CET-6、考研、IELTS、TOEFL、GRE、Oxford 3000、Oxford 5000、计算机英语。
- 初中、高中、CET-4、CET-6、考研、IELTS、TOEFL、GRE 提供锁定版本的下载候选；小学、Oxford 与计算机英语仅登记书目，待核验具体版本和授权。
- 热门考研教材书目：红宝书、词汇闪过、恋练有词、词根+联想记忆法（绿宝书）。只是额外书目候选，不是销量排行榜。
- 下载来源：kajweb/dict，固定提交 3992bcb94c800a2fd38a9fd6ff95b2353e755363（2024-04-05 仓库提交），不声称是当年考纲或对应热门教材新版。
- 候选来源的条目数来自锁定仓库目录，不等于当前考试大纲或教材收词数；超过 5000 行的 TOEFL / GRE 仍受 15000 行硬上限保护。
- 总计 10943 个“词书成员记录”，不是不重复单词总数。正序和乱序词表也不是完全等价快照，例句覆盖不同。
- 来源未提供明确数据再发布授权。快照以 unknown 保存，热门教材仅 metadata_only；不自动核验许可，不自动发布，不自动调用付费音频。
- 不抓取收费教材全文，不把通用词表冒充具体书籍，不安装或执行下载仓库代码。

## 表与契约

新增 vocabulary_online_book（目录）、vocabulary_online_word（逐词不可变快照），均含 del_is。
复用 vocabulary_dataset_catalog 作为快照身份与授权记录，在线快照 source_payload 为 NULL；导入器从逐词 normalized_json 读取，校验数量。
保留 raw_json 全部来源证据、英美音标、多个义项和全部例句。

word-entry-v1 示例（自有内容）：

```json
{
  "schemaVersion": "word-entry-v1",
  "word": "test",
  "phoneticUs": "/test/",
  "phoneticUk": "/test/",
  "phoneticStatus": "source_unverified",
  "requiresContentReview": false,
  "senses": [{
    "partOfSpeech": "noun",
    "meaning": "测试",
    "examples": [{"sentence": "This is a test.", "translation": "这是一次测试。"}]
  }],
  "sourceExamples": [],
  "qualityFlags": []
}
```

来源未提供例句与义项映射时保留 sourceExamples，标记 requiresContentReview=true，禁止直接通过审核。
缺音标不猜测；来源符号不自动声明为已审核 IPA。普通文字 TTS 无法保证同形异音词每个义项的读法，需另行发音审核。
既有词只复用，不自动覆盖既有释义、例句、音频或学习记录。新增来源信息仍保留在快照，合并需要独立审核。
目前没有批量人工修订页面：需要修订的结构化词条可通过既有“录入数据源”入口创建新的 JSON 快照；不要直接改不可变来源快照。

## 音频与发布

tts_generation_task 增加 example_no；按义项、例句、英美口音建立稳定 object_key。
每次“生成 / 重试音频”至多处理 10 条，未完成项需继续触发；本次没有后台自动批量收费任务。
授权未核验时禁止合成与发布。已有声音使用既有 MediaService → OSS → media_asset → pronunciation 链路。
暂停恢复保留成功任务，不删除已有资产；重复发布同一批次不重复创建单词。

## 本地迁移与同步

已经在用户指定本地 letMindWander 库执行迁移并同步 4 份词表。该工具只允许 localhost:3306/letMindWander。
新环境依次执行 sql/V3.16.4_online_vocabulary_catalog.sql 与 sql/V3.16.6_vocabulary_catalog_expansion.sql；已有 V3.16.4 环境备份后只执行 V3.16.6，再重启后端。命令行执行迁移必须指定 `--default-character-set=utf8mb4`，避免新增中文书名乱码。
脚本不修改 MySQL max_allowed_packet、不重启数据库、不运行 TTS。

```sh
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/catalog-classpath.txt
mkdir -p target/catalog-tools
task_catalog_cp="target/classes:$(< target/catalog-classpath.txt)"
javac -encoding UTF-8 -cp "$task_catalog_cp" -d target/catalog-tools tools/SyncOnlineVocabulary.java
java -Dfile.encoding=UTF-8 -cp "target/catalog-tools:$task_catalog_cp" SyncOnlineVocabulary --apply-local ChuZhong_2 GaoZhong_2 CET4_2 CET6_2 KaoYan_2 IELTS_2 TOEFL_2 GRE_2
```

脚本使用 application-dev.yml 的数据库配置及其中的环境变量，不打印密钥。重复迁移和相同来源同步可重入。
新目录定义要同时更新资源 JSON 和迁移，下载白名单另需代码审核，不能从请求传入任意 URL。

## 实际质量检查（2026-09-20）

| 数据集 | 行数 | 缺至少一种音标 | 无例句 | 待确认义项映射 |
|---|---:|---:|---:|---:|
| KaoYan_2 | 4533 | 31 | 100 | 2405 |
| KaoYan_3 | 3728 | 29 | 84 | 903 |
| KaoYan_1 | 1341 | 9 | 21 | 244 |
| KaoYanluan_1 | 1341 | 9 | 5 | 275 |

## 验证

OnlineVocabularyCatalogTest 六项隔离测试：管理员鉴权、元数据书籍不能下载、快照幂等与软删除、行数和书目校验、多义项保护、结构化发布与暂停恢复、下载白名单与路径限制。
F20VocabularyImportIntegrationTest：旧的平面 CSV/JSON 导入继续兼容。
本轮完整后端测试和前端构建通过；真实 MySQL 同步、重复同步，以及 Safari 页面目录／词条预览均已验证。
