# 单词学习卡片 V3.16.10

## 设计边界

- 卡片不等于掌握。页面的“遮住答案”是口头自练，不写入成绩；“认识／不认识”继续走原自评接口。客观训练仍使用原 word_memory_session / attempt，复习仍使用原计划。
- 近义／英美表达、易混词、派生／词形／复合表达分开。每组初始只展示一条，易混词与词族默认折叠，最多六条。缺资料不自动按字母猜词根、关系或音标。
- 所有卡片绑定 content_version_id。换词条版本后旧卡片不迁移，需重新核对义项。自编联想明确不是词源；原句和引用不得混淆。
- 首批人工／AI 辅助样例覆盖 candy（7）、beautiful（4）、learn（4）、apple（3），共18张。V3.16.7 另对正式小学、初中、高中、四级、六级词书中的每个去重已发布词条生成两张确定性基础卡：中英双向回想、例句迁移练习。已有卡片优先展示，系统基础卡排在后面。
- 系统基础卡只使用当前已审核词头、主要义项以及通过 example_id 关联的现有已发布例句；不会生成新释义、英文句子、音标或音频。V3.16.10 另从固定校验值的 Open English WordNet 2025 导入词汇关系证据：只采用与当前首义词性兼容的第一义项，保留 sense/synset 定位、官方候选顺序和 CC BY 4.0 署名。易混卡只说明本库词头拼写接近，不声称词义、词源或派生关系。
- V3.16.8 为每个目标词建立六类逐项覆盖记录；用户可以按账号分别开关六类卡片。展示偏好只过滤返回与页面显示，不删除卡片、不改变审核状态，也不影响掌握度。
- V3.16.9 增加账号级新词页设置：默认展示或遮住答案、自动播放开关、英／美口音、1–5 次播放次数和 1／1.5／2 秒间隔。默认展示答案、开启英式 3 次与 1.5 秒间隔；下一遍只在上一遍 ended 后计时，离页或手动播放会取消剩余连播。
- V3.16.10 增加 lexical_dataset_snapshot 与 word_lexical_relation_evidence。当前本地审计覆盖 6,766 个目标词条版本：近义／相近 5,951、拼写易混 6,315、派生／词形 3,045；三类关系全具备 2,830、部分具备 3,846、无安全候选 90。没有证据的类别标记 evidence_not_available，不用占位事实凑数。
- 原创示例由 AI 辅助编写，明确标注。知识点参考仅用于核对，未复制词典原文。不预置电影台词或歌词；显示出处不等于得到复制授权。

## 部署

依次执行 `sql/V3.16.5_word_learning_cards.sql`、`sql/V3.16.7_word_learning_card_coverage.sql`、`sql/V3.16.8_word_learning_card_preferences.sql`、`sql/V3.16.9_word_study_preferences.sql`、`sql/V3.16.10_word_lexical_evidence.sql`，再部署后端。没有修改 pronunciation / word_sense / word_example 或音频任务格式，没有调用付费服务。

本地开发库（工具拒绝非 localhost:3306/letMindWander 地址）：

```sh
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/catalog-classpath.txt
javac -encoding UTF-8 -source 8 -target 8 -cp "target/classes:$(< target/catalog-classpath.txt)" -d target/catalog-tools tools/InstallWordLearningCards.java
java -cp "target/catalog-tools:target/classes:$(< target/catalog-classpath.txt)" InstallWordLearningCards --apply-local
javac -encoding UTF-8 -source 8 -target 8 -cp "target/classes:$(< target/catalog-classpath.txt)" -d target/catalog-tools tools/InstallWordLearningCardCoverage.java
java -cp "target/catalog-tools:target/classes:$(< target/catalog-classpath.txt)" InstallWordLearningCardCoverage --apply-local
javac -encoding UTF-8 -source 8 -target 8 -cp "target/classes:$(< target/catalog-classpath.txt)" -d target/catalog-tools tools/InstallWordLearningCardEnrichment.java
java -Xmx768m -cp "target/catalog-tools:target/classes:$(< target/catalog-classpath.txt)" InstallWordLearningCardEnrichment --apply-local --download-official
```

随后重启后端，微信开发者工具重新编译。官方下载固定为 `https://en-word.net/static/english-wordnet-2025-json.zip`，SHA-256 必须为 `7d749f6e2c39e6970e4997839dcf6e42fd281f3c2fae0171d2192bae8cfa4b51`；不匹配即停止。导入与生成幂等；生成器升级时旧系统卡只软删除。正式词书范围以 level_code=primary/junior/senior/cet4/cet6 为准；跨词书及历史内容别名共享同一 published_version_id 时只处理一次。已有释义、音频、学习记录不变。

## 维护与发布

新增材料在 word_learning_card 中先保持 review_status/state=draft、rights_status=unknown。由内容维护者核实义项、用法、例句与权利信息后，填写 reviewed_by、reviewed_at，再置 approved/published。当前没有新增后台卡片编辑器；维护通过审阅种子文件／受控 SQL 完成。AI 生成材料不能自动批量批准为已核实引用。

card_type：mnemonic、synonym、confusable、derivative、usage、quote。
source_kind：original（原创）、content（关联当前已发布词条例句）、reference（知识点参考）、film、lyric。
rights_status：unknown（禁发）、original、inherited（继承词条授权状态）、licensed、public_domain。

影视或歌词：card_type=quote，source_kind=film/lyric；必须有真实作品名 source_title、HTTPS 来源 source_url、定位 source_locator（年份、版本、时间点／段落）、source_verified=1，及 licensed/public_domain 和 rights_note 中的核验依据。不存在“字数短就自动授权”的规则。服务端不自行判定许可真伪；审核人负责核实使用范围。正文 body 存引用原文，example_text 只存明确可用的关联表达，不伪造出处。

删除只执行指定记录 `UPDATE word_learning_card SET del_is=1,updated_at=CURRENT_TIMESTAMP WHERE id=?`。撤稿使用 state=withdrawn。不要 DELETE。

接口：GET `/api/learning/contents/{id}/learning-cards`，要求有效用户会话；仅返回当前账号启用且已发布、已审核词条的对应版本材料，过滤软删除、草稿、未知许可、不完整引用。不返回内部审核人。GET/PUT `/api/learning/card-preferences` 读取或保存六类账号级展示开关；首次使用默认全开，允许全部关闭。来源按钮展示原出处与使用说明，复制链接到剪贴板，不在小程序里绕过业务域名限制。

GET/PUT `/api/learning/word-study-preferences` 读取或保存新词进入设置。autoPlayCount 允许 1–5；autoPlayIntervalMs 必须位于 1000–2000；默认 answerMode=visible、accent=uk、count=3、interval=1500。客户端不能通过多个固定定时器重叠播放，必须在音频 ended 后才开始计算下一遍间隔。

word_learning_card_coverage 每个目标 content_version_id 一行：required_card_count=2；baseline_status 只有同时存在 mnemonic 和 usage 才是 complete；example_status=linked/missing；relation_status 使用 complete、partial、evidence_not_available、evidence_required 区分三类关系覆盖；quotation_status=optional_not_required 表示名句不是每个词的强制项。issues_json 保存机器可读缺口。该表用于验收覆盖，不参与掌握度计算。

word_learning_card_type_coverage 每个目标词固定六行，分别记录 card_type、requirement_level、published_count 与 coverage_status：基础两类缺失为 missing_required；尚未导入关系源为 evidence_required；已核验固定源仍无安全候选为 evidence_not_available；引用类缺失为 optional_not_available。user_word_learning_card_preference 每个用户每类一行，使用 enabled 与 del_is；更新采用 upsert，不物理删除。

## 验证

```sh
mvn -q -Dtest=WordLearningCardTest test
mvn -q -Dtest=WordLearningCardCoverageTest test
mvn -q -Dtest=WordLearningCardEnrichmentTest test
node --test ../miniprogram/tests/word-learning-cards.test.js
```

研究依据（设计方向，不承诺个体记忆效果）：[提取与间隔学习研究](https://www.psychologicalscience.org/journals/psychological-science/0956797615617778/)。词族与相近词的默认折叠、数量上限是本产品的设计选择，并非研究给出的精确最优值。
