-- ============================================================
-- 知行日课 V3.1 增量脚本：学段字段 + 基础素材(技术资讯/英语单词)
-- 适用：现有 letMindWander 库（V3.0 62 表结构，MySQL 5.7.25）
-- 原则：只 ALTER 加字段 + 插入种子数据，不重建任何表。
-- 可重复执行：ALTER 前判空，INSERT 用 ON DUPLICATE KEY / 固定ID幂等。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

-- ============================================================
-- 第一部分：结构变更（添加字段，不重建表）
-- ============================================================

-- 1. learning_content 增加「学段」字段：区分英语单词的小学/初中/高中类别
--    primary 小学 / junior 初中 / senior 高中；仅 content_type='word' 非空
SET @has_stage := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'learning_content' AND column_name = 'stage');
SET @ddl := IF(@has_stage = 0,
  'ALTER TABLE `learning_content` ADD COLUMN `stage` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''学段分类：primary 小学 / junior 初中 / senior 高中；仅 word 类型非空''',
  'DO 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 学段筛选索引（幂等）
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'learning_content' AND index_name = 'idx_stage');
SET @ddl2 := IF(@has_idx = 0,
  'ALTER TABLE `learning_content` ADD KEY `idx_stage` (`content_type`, `stage`, `state`, `published_at`)',
  'DO 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ============================================================
-- 第二部分：公开内容来源
-- ============================================================
INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('7918934720e8180ff4d0f1610c0e4aab','人教版中小学英语词表','manual',NULL,'依据义务教育及普通高中英语课程标准公开词表整理，仅作学习用途',1) ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('a7b04c72f9cc9be95bb5b862bf5e06cf','公开科技资讯','manual',NULL,'来源公开网络资讯，仅作学习引用，保留原文链接与原作者',1) ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- ============================================================
-- 第三部分：系统主题（大模型 / AI应用）
-- ============================================================
INSERT INTO `learning_topic` (`id`,`scope_key`,`owner_id`,`name`,`normalized_name`,`state`) VALUES
('00000000000000000000000000000013','system',NULL,'大模型','大模型','active') ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
INSERT INTO `learning_topic` (`id`,`scope_key`,`owner_id`,`name`,`normalized_name`,`state`) VALUES
('00000000000000000000000000000014','system',NULL,'AI应用','ai应用','active') ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- ============================================================
-- 第四部分：技术资讯（learning_content + content_version + content_topic）
-- ============================================================
INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('2c1d3fd9a16f81b163906a1354fbfd31','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('3a8643b42ff2141736a9614979e0f40614d5c6e8401c1737df669b3101e57e5a'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','1a664297931d6f40cb66da78eff65860',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('1a664297931d6f40cb66da78eff65860','2c1d3fd9a16f81b163906a1354fbfd31',1,'人工智能大模型发展迈入新阶段','全球大模型市场进入多方制衡、多元竞争的新阶段，ChatGPT 市场份额首次跌破 50%。','2026 年以来大模型产业告别单一能力竞赛，形成「能力为王、成本制胜」的双重竞争范式。谷歌、Anthropic、OpenAI 交替领跑，中国开源模型以高性价比驱动市场重构。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-09-03 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('edd2e1aba6d6cf3f6212298d43bc14c8b265432eaa381920215ffddc9447ecbd'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('942f57387b4a024cca80991290ea9800','1a664297931d6f40cb66da78eff65860','00000000000000000000000000000011') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='1a664297931d6f40cb66da78eff65860' WHERE `id`='2c1d3fd9a16f81b163906a1354fbfd31';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('570ece9315d22c9890936f3f3a3f20eb','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('a6b8936d42a1313169d92d90ac490f7bba2d3676c13cb1471a47146b14a041a4'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','93de908ed8fd5cddef2ffd51db65e132',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('93de908ed8fd5cddef2ffd51db65e132','570ece9315d22c9890936f3f3a3f20eb',1,'腾讯混元 Hy4-preview 混合稀疏大模型','腾讯混元发布 Hy4-preview，770B 总参数、49B 激活参数，上下文窗口达 1M。','Hy4-preview 采用混合稀疏架构，软件工程任务评测表现提升，部分能力开源至腾讯云 TokenHub，主打高性能与长上下文。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('dd41aa2c5fc02e090f249bacb10f9df0a27a28069ef37a5c5bfdb524ba3f22b4'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('cbd645568ddaab94b3921722bee9ea62','93de908ed8fd5cddef2ffd51db65e132','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='93de908ed8fd5cddef2ffd51db65e132' WHERE `id`='570ece9315d22c9890936f3f3a3f20eb';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('714c811d2d422d7863795ba6532faf50','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('9f29ed6a52d5a8445d26fced47bff291a90c1d25284ce5a1e779a03e57b98f2e'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','0f1a18762452881c450698174dd1b212',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0f1a18762452881c450698174dd1b212','714c811d2d422d7863795ba6532faf50',1,'智谱 GLM-5.3 Flash 低成本推理','智谱 AI 上半年营收 9.54 亿元，GLM-5.3 Flash 主打低成本推理。','智谱 AI 已实现 10 万卡级国产芯片规模化推理，GLM-5.3 Flash 以低成本推理定位面向企业级应用。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('34ffa073dfa5010b1f936bd84f9a20b47b65a3701e36af26f971d0222452f0e1'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('f03d2fdefae54e48f874b17434160317','0f1a18762452881c450698174dd1b212','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='0f1a18762452881c450698174dd1b212' WHERE `id`='714c811d2d422d7863795ba6532faf50';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('a49ca730fefb7dbaf85f39b5e8d8b3c8','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('66976d78c6706e186a2d5fcead837d0bef7c71c6f3c137a1810a187b4d403fb0'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','5c9b06126f0a1efed5750dee2c97d5a6',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('5c9b06126f0a1efed5750dee2c97d5a6','a49ca730fefb7dbaf85f39b5e8d8b3c8',1,'阿里通义千问 Agent Teams 多智能体协作','千问升级 Agent Teams 多智能体协作功能，接入 Wan3.0 视频生成模型。','一套工作流即可完成脚本、分镜、生成、剪辑的全链路 AI 内容创作，多智能体协作成为 AI 应用新趋势。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('c8e4410360ce5f5733e8e3f2c3f2dc2d3da900d8f240a80c8063a1cc23e8e236'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('4c04c7fe0b679eccb1f9fcb810dcdc6f','5c9b06126f0a1efed5750dee2c97d5a6','00000000000000000000000000000014') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='5c9b06126f0a1efed5750dee2c97d5a6' WHERE `id`='a49ca730fefb7dbaf85f39b5e8d8b3c8';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('201cd5e95aad2e284de65e9b3932accd','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('8e0127aacee4ae07601312952f411aa136d48ba915912691b510de3597b91832'),UNHEX('be1c1cab0a1620abb1c03dec4b4d8f6c2dd25a777965ead772f4631febcfa733'),'published','a448241132a46d4a0578405b907941a2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a448241132a46d4a0578405b907941a2','201cd5e95aad2e284de65e9b3932accd',1,'OpenAI GPT-5.5 Instant 发布','GPT-5.5 Instant 幻觉率降低 52%，推理速度大幅提升。','新模型标志着大模型进入「快准稳」新阶段，在推理效率与可靠性之间取得新平衡。','intro',180,'https://blog.csdn.net/enheng1238/article/details/164294388','CSDN AI日报','2026-09-02 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('7de301bfe6a9e1d31b4f6f86b93ba630a9c5bf7972866d216ea34e5ad05f67cd'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('d8ba929d2802af4a3e0c1e39077a8ae8','a448241132a46d4a0578405b907941a2','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='a448241132a46d4a0578405b907941a2' WHERE `id`='201cd5e95aad2e284de65e9b3932accd';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('89af0b1539f35b6a3c4e7baa51a945fd','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('081cfd6daf3536d1b2a3f9c7dbff2befc1ff93bca910c0b93042516225780f2c'),UNHEX('71c9804d6f0725e8e320c66c91b2ee2c7d4685fd4edb837f5af83da5893aa360'),'published','0f6b9285296bd86007c56e71f95c6d61',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0f6b9285296bd86007c56e71f95c6d61','89af0b1539f35b6a3c4e7baa51a945fd',1,'DeepSeek V4 开源，推理价格创新低','DeepSeek V4 以超低价格开源，性能逼近顶级闭源模型。','输入价格低至约 0.14 美元/百万 token，引发行业性价比竞争，推动开源模型加速普及。','intro',180,'https://devpress.csdn.net/v1/article/detail/163659168','CSDN AI日报','2026-08-15 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('6aa3069c32e459e9f57c81f84f2bfda38175d54c3e92324bdc47901d5c46ce80'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('23657dc7e2cd5dfcf60d66238327b62f','0f6b9285296bd86007c56e71f95c6d61','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='0f6b9285296bd86007c56e71f95c6d61' WHERE `id`='89af0b1539f35b6a3c4e7baa51a945fd';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('125dffbdcd7e8d16d14f74f2f7be30fb','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('1004645f5fcac3ab34715ab05d2133bd881feaae8f7431dc4cbd799d9ac17673'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','a8d0b0f3e5fe6352690026dc78d5312c',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a8d0b0f3e5fe6352690026dc78d5312c','125dffbdcd7e8d16d14f74f2f7be30fb',1,'美团 LongCat-2.0 万亿参数大模型','美团开源 LongCat-2.0（1.6 万亿参数），全程依托超 5 万张国产算力卡完成训练与推理。','LongCat-2.0 是业界首个纯国产算力万亿级模型，标志国产算力生态迈入新阶段。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-06-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('3830e0dacf24e86aa8b1be810d79abaeb5563857ad5e933479f78a00d405b67e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('470af350e648f4b81cc0b681733ed7e1','a8d0b0f3e5fe6352690026dc78d5312c','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='a8d0b0f3e5fe6352690026dc78d5312c' WHERE `id`='125dffbdcd7e8d16d14f74f2f7be30fb';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('57e72b0e53b66a8b72e11e7c5650757e','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('d1907d899e755fa2d5302a1f601632ec79613a5748e633e9cc4ae004c1bf471c'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','b4ec0ddf5986b325cfbbc88632f39a42',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('b4ec0ddf5986b325cfbbc88632f39a42','57e72b0e53b66a8b72e11e7c5650757e',1,'华为昇腾 384 超节点算力集群','昇腾 384 超节点提供 300PFLOPS 密集 BF16 算力，已用于训练盘古 Ultra MoE 大模型。','华为昇腾 384 超节点性能接近英伟达 GB200 NVL72 两倍，支撑国产大模型训练。','advanced',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-06-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('626dd1c299a2213780c9f9c1e5f5b5c1460ee14f1648d264034624972b25296c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('17d90ecfccd183bd16544753d8df39b2','b4ec0ddf5986b325cfbbc88632f39a42','00000000000000000000000000000014') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='b4ec0ddf5986b325cfbbc88632f39a42' WHERE `id`='57e72b0e53b66a8b72e11e7c5650757e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('29f26bb95462ba13145830c1018ea0a2','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('4b109739bda4e68060523e0f013961bec57b88a42d76c8de1f157b3bd04637d8'),UNHEX('f3fa42d5ae9a4d3dc4dc5448debcd0e9071749bd47a99d3c02e01c4d4b26e0f6'),'published','109b48ce1815f6892c6f4ab41eebc6ea',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('109b48ce1815f6892c6f4ab41eebc6ea','29f26bb95462ba13145830c1018ea0a2',1,'2026 年中国 AI 发展趋势前瞻','AI 企业超 6000 家，核心产业规模预计突破 1.2 万亿元。','大模型从「拼规模」转向「拼密度」，稀疏注意力机制成为提升推理效率的重要技术路径。','intro',180,'https://www.news.cn/20260128/3b2f11906fd74ca397fef9996c805a60/c.html','新华社','2026-01-28 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('86dd0e234e76ee4ef61ac25756e285c5229053e3d64486d635c9be0b1438a8f2'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('e696d6a5f635cbd4615e0b20630734e3','109b48ce1815f6892c6f4ab41eebc6ea','00000000000000000000000000000011') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='109b48ce1815f6892c6f4ab41eebc6ea' WHERE `id`='29f26bb95462ba13145830c1018ea0a2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('96c4f701034514bda87ae14c53985072','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('e99f9d8c42e06f1351e9f1520c95ab711f55f7624f9966853f16df070c8f894a'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','33750d40260baf43a15e99023c23a548',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('33750d40260baf43a15e99023c23a548','96c4f701034514bda87ae14c53985072',1,'Claude Sonnet 5 发布','Anthropic 推出新一代中端主力模型 Claude Sonnet 5，主打代理能力。','官方称其为迄今「最具代理能力」的 Sonnet 模型，强化长文档处理与多工具并行调用。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-07-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('275a1b3afd5a6562238491b97818c6b3413d2e21afa3aa7f9e4f6b6fb567f729'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('9fb52eabaec3a0c3d1d592fe87e996b6','33750d40260baf43a15e99023c23a548','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='33750d40260baf43a15e99023c23a548' WHERE `id`='96c4f701034514bda87ae14c53985072';

-- ============================================================
-- 第五部分：英语单词（小学/初中/高中，learning_content.stage 区分类别）
-- ============================================================
INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('32d0e4b09fa3ddde7dd1fa1be5a45e07','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5876012d5c48963d658d85985f92c1516dca2a448c27d230752b2b5add4f4f08'),UNHEX('3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b'),'published','primary','aa1a0da8a7f040cc6f3f17e4ccf1a6ed',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('aa1a0da8a7f040cc6f3f17e4ccf1a6ed','32d0e4b09fa3ddde7dd1fa1be5a45e07',1,'apple','apple','/ˈæpl/','苹果','I eat an apple every day.','我每天吃一个苹果。','intro',15,'公开英语词表，仅作学习用途',UNHEX('3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('1de06f87ef1148b6f771b9caaa4ee657','aa1a0da8a7f040cc6f3f17e4ccf1a6ed','noun','苹果',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0dc0c8fef86f745f7e04f8df0f1875f6','1de06f87ef1148b6f771b9caaa4ee657','I eat an apple every day.','我每天吃一个苹果。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='aa1a0da8a7f040cc6f3f17e4ccf1a6ed' WHERE `id`='32d0e4b09fa3ddde7dd1fa1be5a45e07';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d12d2a85ea7992417f8af5e102ef62c2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('cb5cd6845de3c596636757d4b9b24384573dcfbd4e9fe1f02cbe7b7bcf74d5bf'),UNHEX('92719fe0cf8cd51592af31ee8a5736d79f7273777fa3f7b70bfe993a4cd32180'),'published','primary','a235ac3b22eca4290ff217db4dd760c5',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a235ac3b22eca4290ff217db4dd760c5','d12d2a85ea7992417f8af5e102ef62c2',1,'book','book','/bʊk/','书','This book is interesting.','这本书很有趣。','intro',15,'公开英语词表，仅作学习用途',UNHEX('92719fe0cf8cd51592af31ee8a5736d79f7273777fa3f7b70bfe993a4cd32180'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('e87000337cd4531009b16038ca486632','a235ac3b22eca4290ff217db4dd760c5','noun','书',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('3be7f1498040a2c1f0d3183280ef3baa','e87000337cd4531009b16038ca486632','This book is interesting.','这本书很有趣。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='a235ac3b22eca4290ff217db4dd760c5' WHERE `id`='d12d2a85ea7992417f8af5e102ef62c2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('eac2bf0ddae12ce6dcd2e4bfe555e775','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('754b4ee0bf1b6d8475b3778fd4dfade5c176d051b12a4575c76a73b8b3ad1a00'),UNHEX('77af778b51abd4a3c51c5ddd97204a9c3ae614ebccb75a606c3b6865aed6744e'),'published','primary','5178ba7f288a4248614000cfde2559d7',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('5178ba7f288a4248614000cfde2559d7','eac2bf0ddae12ce6dcd2e4bfe555e775',1,'cat','cat','/kæt/','猫','The cat is sleeping.','猫在睡觉。','intro',15,'公开英语词表，仅作学习用途',UNHEX('77af778b51abd4a3c51c5ddd97204a9c3ae614ebccb75a606c3b6865aed6744e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('79c594eb23a951ddc480186673e15d58','5178ba7f288a4248614000cfde2559d7','noun','猫',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('13d27ee71f99bbe52f70965f3e038857','79c594eb23a951ddc480186673e15d58','The cat is sleeping.','猫在睡觉。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='5178ba7f288a4248614000cfde2559d7' WHERE `id`='eac2bf0ddae12ce6dcd2e4bfe555e775';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('08241716d13bbdc2bde15db31b1db89e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('6b28e1144d9d722cb1399534b3877c47aae72e0557faebe6e900c5ec3507d55e'),UNHEX('cd6357efdd966de8c0cb2f876cc89ec74ce35f0968e11743987084bd42fb8944'),'published','primary','7f99e9d47af443e20ae351d587ec01dd',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('7f99e9d47af443e20ae351d587ec01dd','08241716d13bbdc2bde15db31b1db89e',1,'dog','dog','/dɒɡ/','狗','My dog is very friendly.','我的狗很友好。','intro',15,'公开英语词表，仅作学习用途',UNHEX('cd6357efdd966de8c0cb2f876cc89ec74ce35f0968e11743987084bd42fb8944'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('722f417bfba31566f59e9387c75ad667','7f99e9d47af443e20ae351d587ec01dd','noun','狗',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('c61be795e7d4d2955bafd312194fa7ea','722f417bfba31566f59e9387c75ad667','My dog is very friendly.','我的狗很友好。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='7f99e9d47af443e20ae351d587ec01dd' WHERE `id`='08241716d13bbdc2bde15db31b1db89e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('6e5ea74a2362d12cf0453d973f70d05e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('82b39b1b0df9f437a5ccc3662d61f1ae49fdc10e744ddefdf5ed910983f59624'),UNHEX('770e607624d689265ca6c44884d0807d9b054d23c473c106c72be9de08b7376c'),'published','primary','e5f182fdc9a55fb6853705f449849b91',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('e5f182fdc9a55fb6853705f449849b91','6e5ea74a2362d12cf0453d973f70d05e',1,'good','good','/ɡʊd/','好的','You did a good job.','你做得很好。','intro',15,'公开英语词表，仅作学习用途',UNHEX('770e607624d689265ca6c44884d0807d9b054d23c473c106c72be9de08b7376c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('122c47ceb4de0f05d4a3832b85190eb3','e5f182fdc9a55fb6853705f449849b91','adjective','好的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('07e35b9c9cbbad50b62dc5545c8f76b6','122c47ceb4de0f05d4a3832b85190eb3','You did a good job.','你做得很好。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='e5f182fdc9a55fb6853705f449849b91' WHERE `id`='6e5ea74a2362d12cf0453d973f70d05e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('623cfdf90b2f81f1e835ee299a0c7f5e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('bb9d5fe5b77e1f343b387faefe0aaf4ee105fee7238258b8ba85aaaf84c66fe0'),UNHEX('acba25512100f80b56fc3ccd14c65be55d94800cda77585c5f41a887e398f9be'),'published','primary','72e72bc4ee4e71679977f23deb91303f',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('72e72bc4ee4e71679977f23deb91303f','623cfdf90b2f81f1e835ee299a0c7f5e',1,'run','run','/rʌn/','跑','I run in the morning.','我早上跑步。','intro',15,'公开英语词表，仅作学习用途',UNHEX('acba25512100f80b56fc3ccd14c65be55d94800cda77585c5f41a887e398f9be'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('28cc009d8b0e34adb6d08e1aa81cce52','72e72bc4ee4e71679977f23deb91303f','verb','跑',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('95e105d0b720e4fb7a3120e76480e1bc','28cc009d8b0e34adb6d08e1aa81cce52','I run in the morning.','我早上跑步。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='72e72bc4ee4e71679977f23deb91303f' WHERE `id`='623cfdf90b2f81f1e835ee299a0c7f5e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('795ab453e5660467f28e3aa2cae4287c','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('0e9cbde8075806789cdbcb3209a88d93788dcd797548541cf2864615bcd873cb'),UNHEX('2a21fe6d592a19b7de898b50eb53c429608de1a66f3e9f62da19714a770553d1'),'published','primary','a869eebd4c03d2eb8f3d2cfa30fc72ce',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a869eebd4c03d2eb8f3d2cfa30fc72ce','795ab453e5660467f28e3aa2cae4287c',1,'big','big','/bɪɡ/','大的','That is a big tree.','那是一棵大树。','intro',15,'公开英语词表，仅作学习用途',UNHEX('2a21fe6d592a19b7de898b50eb53c429608de1a66f3e9f62da19714a770553d1'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('3cee626ae05a17ca080f8904fac0ddb2','a869eebd4c03d2eb8f3d2cfa30fc72ce','adjective','大的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('f62616fc16aeef65647a7dcd69ac3860','3cee626ae05a17ca080f8904fac0ddb2','That is a big tree.','那是一棵大树。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='a869eebd4c03d2eb8f3d2cfa30fc72ce' WHERE `id`='795ab453e5660467f28e3aa2cae4287c';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d7e06b7e0312565f2ce3973b6e1621d2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('7ff42dcc17583a1e150469036bb3654237d89a80d51e6427b62e59704946900e'),UNHEX('489f719cadf919094ddb38e7654de153ac33c02febb5de91e5345cbe372cf4a0'),'published','primary','c257d6e568516935ecc31b428e84c117',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c257d6e568516935ecc31b428e84c117','d7e06b7e0312565f2ce3973b6e1621d2',1,'happy','happy','/ˈhæpi/','快乐的','She looks happy today.','她今天看起来很开心。','intro',15,'公开英语词表，仅作学习用途',UNHEX('489f719cadf919094ddb38e7654de153ac33c02febb5de91e5345cbe372cf4a0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('75a142d1768006bf70b2f8ab8adaed20','c257d6e568516935ecc31b428e84c117','adjective','快乐的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0b1fc7fe1f425e7481306221f82c0aa4','75a142d1768006bf70b2f8ab8adaed20','She looks happy today.','她今天看起来很开心。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c257d6e568516935ecc31b428e84c117' WHERE `id`='d7e06b7e0312565f2ce3973b6e1621d2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('ac7d6602011575e040508df8a08e0f14','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('ff237c9f5d9d9f757571612f93faf9ea43cd24c24784e1ee53bfded561c1a847'),UNHEX('d64debd942d7dc26a851231583b1721f43ea936fa41932b6dad7556e5f8cd24a'),'published','primary','40448c65895a420ca0f93dbee362bbc3',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('40448c65895a420ca0f93dbee362bbc3','ac7d6602011575e040508df8a08e0f14',1,'school','school','/skuːl/','学校','I go to school by bus.','我坐公交车去学校。','intro',15,'公开英语词表，仅作学习用途',UNHEX('d64debd942d7dc26a851231583b1721f43ea936fa41932b6dad7556e5f8cd24a'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('3ce818f09995b4098d1f9ee7e46acd61','40448c65895a420ca0f93dbee362bbc3','noun','学校',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('56926cfcfa5fb4ac3d780a41b6198d64','3ce818f09995b4098d1f9ee7e46acd61','I go to school by bus.','我坐公交车去学校。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='40448c65895a420ca0f93dbee362bbc3' WHERE `id`='ac7d6602011575e040508df8a08e0f14';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('f590aed1f648037e87e42442b67b2219','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('cc09e82f3ce5e7425857ca75474e62982422449a6fdc92e755c6b7148518d692'),UNHEX('0f4168490e38b8447e11ba4bd656aa11b925bd22af30bac464bc153fdb608501'),'published','primary','32a78a687e717e45e116f3ade4d420aa',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('32a78a687e717e45e116f3ade4d420aa','f590aed1f648037e87e42442b67b2219',1,'water','water','/ˈwɔːtə/','水','Please drink some water.','请喝点水。','intro',15,'公开英语词表，仅作学习用途',UNHEX('0f4168490e38b8447e11ba4bd656aa11b925bd22af30bac464bc153fdb608501'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('64e3baded7e98bf43a07c5f38ae314bc','32a78a687e717e45e116f3ade4d420aa','noun','水',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('3c9b7d6b173df1293ebceebf8778e0ee','64e3baded7e98bf43a07c5f38ae314bc','Please drink some water.','请喝点水。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='32a78a687e717e45e116f3ade4d420aa' WHERE `id`='f590aed1f648037e87e42442b67b2219';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('52666718d87f008288ecd24b277be48f','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('765c737b059f5771e1e00e4ca05ec3f12e35ad6485ad2d88fc39a9ab0c169d76'),UNHEX('1c6b6b9e01275af3ac768463d2c751b5c0ee7324ba31723ba694ba4ad48eaf3f'),'published','junior','6e0d2352f815c0254d83a747068e6a26',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('6e0d2352f815c0254d83a747068e6a26','52666718d87f008288ecd24b277be48f',1,'achieve','achieve','/əˈtʃiːv/','实现，达到','She worked hard to achieve her goal.','她努力实现自己的目标。','intro',15,'公开英语词表，仅作学习用途',UNHEX('1c6b6b9e01275af3ac768463d2c751b5c0ee7324ba31723ba694ba4ad48eaf3f'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('6e239951c928e19a0cd2d06f4f64cdeb','6e0d2352f815c0254d83a747068e6a26','verb','实现，达到',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('6dc323bb9bf5406f472ce03a8b6a739c','6e239951c928e19a0cd2d06f4f64cdeb','She worked hard to achieve her goal.','她努力实现自己的目标。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='6e0d2352f815c0254d83a747068e6a26' WHERE `id`='52666718d87f008288ecd24b277be48f';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('0d9a35db20c602a20f4109cd309d7d19','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b94df3527d7c77d79972a3320a83b7db48223b5aa6c2a99afb79383645c776cc'),UNHEX('ba5285161ba6eed0085fb13784ce5c92f70ebc268b94fd66aa1d68a32884204d'),'published','junior','173a593894a862cfc958fc38f06d45a8',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('173a593894a862cfc958fc38f06d45a8','0d9a35db20c602a20f4109cd309d7d19',1,'environment','environment','/ɪnˈvaɪrənmənt/','环境','We should protect the environment.','我们应该保护环境。','intro',15,'公开英语词表，仅作学习用途',UNHEX('ba5285161ba6eed0085fb13784ce5c92f70ebc268b94fd66aa1d68a32884204d'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('cd21e8876cf198976d72648f1910e10f','173a593894a862cfc958fc38f06d45a8','noun','环境',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('1441d24fb4d8c835ccb73ea6ad9ced7d','cd21e8876cf198976d72648f1910e10f','We should protect the environment.','我们应该保护环境。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='173a593894a862cfc958fc38f06d45a8' WHERE `id`='0d9a35db20c602a20f4109cd309d7d19';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('be95d376af2614e73fc0ec0c2b565fd0','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('07395af3bc31da8fb4491b3a4594259b879dad28f089f53b584317066f124eb5'),UNHEX('f1947f79fdfb8046150959ca09cdd05cb53672ad4c0f49a87bbc7cddf5c91293'),'published','junior','b52aa56a0362aa852f636f89eeda6585',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('b52aa56a0362aa852f636f89eeda6585','be95d376af2614e73fc0ec0c2b565fd0',1,'culture','culture','/ˈkʌltʃə/','文化','China has a long history and rich culture.','中国历史悠久、文化丰富。','intro',15,'公开英语词表，仅作学习用途',UNHEX('f1947f79fdfb8046150959ca09cdd05cb53672ad4c0f49a87bbc7cddf5c91293'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('a679f2700abbb69b61f096e336740319','b52aa56a0362aa852f636f89eeda6585','noun','文化',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('6449b7520dace793633408cf42a792d7','a679f2700abbb69b61f096e336740319','China has a long history and rich culture.','中国历史悠久、文化丰富。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='b52aa56a0362aa852f636f89eeda6585' WHERE `id`='be95d376af2614e73fc0ec0c2b565fd0';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('dd4dced045f8db3f96fe0a6d92fe7bd9','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('6d2a06cb59ef9d570acefe6af57030fc115b8dd0628dd77e65e8840168852240'),UNHEX('c52028f34e378d1e07b4d5b8d10e4860e99cd3802218acf9391db8ea64fd2899'),'published','junior','169d6300ccb4ecb737490615492eb6e5',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('169d6300ccb4ecb737490615492eb6e5','dd4dced045f8db3f96fe0a6d92fe7bd9',1,'describe','describe','/dɪˈskraɪb/','描述','Can you describe the picture?','你能描述一下这张图吗？','intro',15,'公开英语词表，仅作学习用途',UNHEX('c52028f34e378d1e07b4d5b8d10e4860e99cd3802218acf9391db8ea64fd2899'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('a154d437e31ab09d5c15e809903cf196','169d6300ccb4ecb737490615492eb6e5','verb','描述',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('cee5a38ad569fd002267b6459059b4d6','a154d437e31ab09d5c15e809903cf196','Can you describe the picture?','你能描述一下这张图吗？',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='169d6300ccb4ecb737490615492eb6e5' WHERE `id`='dd4dced045f8db3f96fe0a6d92fe7bd9';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d9d151ad6d139ac8b43e965212cd6c50','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('2815d193c02c77ba6cb0c74ef8cf0c9482d7821d1ad054ae47a9096b71cac66d'),UNHEX('04b447783afe5bfd4b48e8db067137319526a98e029c37b2559154ceccc1067e'),'published','junior','4d489316ac707ca1aabb187292617cc4',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('4d489316ac707ca1aabb187292617cc4','d9d151ad6d139ac8b43e965212cd6c50',1,'opportunity','opportunity','/ˌɒpəˈtjuːnəti/','机会','This is a good opportunity to learn.','这是一个学习的好机会。','intro',15,'公开英语词表，仅作学习用途',UNHEX('04b447783afe5bfd4b48e8db067137319526a98e029c37b2559154ceccc1067e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('c21dfd5a7601918e5705b6e85ae01549','4d489316ac707ca1aabb187292617cc4','noun','机会',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('1544725cfb68634b0588b28dc5ecf8a6','c21dfd5a7601918e5705b6e85ae01549','This is a good opportunity to learn.','这是一个学习的好机会。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='4d489316ac707ca1aabb187292617cc4' WHERE `id`='d9d151ad6d139ac8b43e965212cd6c50';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('4740a422f6e3e919bbb39a8ec470eae8','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5a3ca37130966d2cd337b06943af95fc10f206d4f2de5493040790361bdfabc7'),UNHEX('e0f895872d65b2528feec97350a3a212b3d4ab88748e25d022a34641d338216b'),'published','junior','6c3a613c186c4bc65b8bc0e5be6058f8',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('6c3a613c186c4bc65b8bc0e5be6058f8','4740a422f6e3e919bbb39a8ec470eae8',1,'knowledge','knowledge','/ˈnɒlɪdʒ/','知识','Knowledge is power.','知识就是力量。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e0f895872d65b2528feec97350a3a212b3d4ab88748e25d022a34641d338216b'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('4f85fee403eec4e4c7162b2650bec637','6c3a613c186c4bc65b8bc0e5be6058f8','noun','知识',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('dd1591c9b350b150e3085803e13d83c3','4f85fee403eec4e4c7162b2650bec637','Knowledge is power.','知识就是力量。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='6c3a613c186c4bc65b8bc0e5be6058f8' WHERE `id`='4740a422f6e3e919bbb39a8ec470eae8';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('5e31799f2d71332bbeb66994e9f36a31','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('58825f59269d9eeb20b5bf3ca7ee8614bc144520e170c6bcd9f8f9c14ddbf357'),UNHEX('c9195f0946897bd7094ab4dc0ffbfade1a43544fed9ea93a5b448971952feed3'),'published','junior','69ecfc204e11cd595f47877a29a176e2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('69ecfc204e11cd595f47877a29a176e2','5e31799f2d71332bbeb66994e9f36a31',1,'responsible','responsible','/rɪˈspɒnsəbl/','负责任的','You should be responsible for your actions.','你应该对自己的行为负责。','intro',15,'公开英语词表，仅作学习用途',UNHEX('c9195f0946897bd7094ab4dc0ffbfade1a43544fed9ea93a5b448971952feed3'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('ab250271f26a9e73d3e6c2f8811f1dee','69ecfc204e11cd595f47877a29a176e2','adjective','负责任的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('909cad88d4c9cbd8fe61faa060843d74','ab250271f26a9e73d3e6c2f8811f1dee','You should be responsible for your actions.','你应该对自己的行为负责。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='69ecfc204e11cd595f47877a29a176e2' WHERE `id`='5e31799f2d71332bbeb66994e9f36a31';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('9de617f01bc402443176cfd7c7b6625f','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('caed91dde26ef3e91d4a832c27cd1ec281800b3d67480be1c0fe36117f0f12a1'),UNHEX('f354ee99e2bc863ce19d80b843353476394ebc3530a51c9290d629065bacc3b3'),'published','junior','c51245276c449b90ae7e79fd7ef8505a',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c51245276c449b90ae7e79fd7ef8505a','9de617f01bc402443176cfd7c7b6625f',1,'community','community','/kəˈmjuːnəti/','社区','We live in a friendly community.','我们住在一个友好的社区。','intro',15,'公开英语词表，仅作学习用途',UNHEX('f354ee99e2bc863ce19d80b843353476394ebc3530a51c9290d629065bacc3b3'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('5fc9c9bd5754fcb7bb259062e12b131d','c51245276c449b90ae7e79fd7ef8505a','noun','社区',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('9d8d1cc61db62d3be2aa11c484e2fe03','5fc9c9bd5754fcb7bb259062e12b131d','We live in a friendly community.','我们住在一个友好的社区。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c51245276c449b90ae7e79fd7ef8505a' WHERE `id`='9de617f01bc402443176cfd7c7b6625f';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2db9c6fe4957ceda70c6b7b207bec803','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('4aecaaf831dae86b94def0a212e81a5c14948d947f6862d6023636ee2dd798f2'),UNHEX('53e5e7c5a884893739c4cf64528572aeb846f8e4ca1986188287475c65900fab'),'published','junior','1f5b44de6df63503c80766707c162cb6',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('1f5b44de6df63503c80766707c162cb6','2db9c6fe4957ceda70c6b7b207bec803',1,'experience','experience','/ɪkˈspɪəriəns/','经验，经历','He has much teaching experience.','他有丰富的教学经验。','intro',15,'公开英语词表，仅作学习用途',UNHEX('53e5e7c5a884893739c4cf64528572aeb846f8e4ca1986188287475c65900fab'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('316f46dd9206801b730bef209c4d6479','1f5b44de6df63503c80766707c162cb6','noun','经验，经历',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('d0a10b07a7f7fdead34a0dfa0a743568','316f46dd9206801b730bef209c4d6479','He has much teaching experience.','他有丰富的教学经验。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='1f5b44de6df63503c80766707c162cb6' WHERE `id`='2db9c6fe4957ceda70c6b7b207bec803';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('99aeae313d7f3a644e8dfe878d6d12d2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('d4fe73cbbd8780bab51a60589a71b81f5dfc6990feb2d9b83149ea0ec2518bf4'),UNHEX('2b35ed6944dd2e8f7462b14096e8969711280dffe1457a680c885a95127e426c'),'published','junior','4fd06461276506369238f259f37a5d01',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('4fd06461276506369238f259f37a5d01','99aeae313d7f3a644e8dfe878d6d12d2',1,'improve','improve','/ɪmˈpruːv/','改善，提高','I want to improve my English.','我想提高我的英语。','intro',15,'公开英语词表，仅作学习用途',UNHEX('2b35ed6944dd2e8f7462b14096e8969711280dffe1457a680c885a95127e426c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('04503b3e83563bb2ac6557ded84fe13d','4fd06461276506369238f259f37a5d01','verb','改善，提高',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('fa407e42c2b1e958faa8fe455d2eee54','04503b3e83563bb2ac6557ded84fe13d','I want to improve my English.','我想提高我的英语。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='4fd06461276506369238f259f37a5d01' WHERE `id`='99aeae313d7f3a644e8dfe878d6d12d2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2f7544ea697c478b6c6545585faa3a7c','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('78c072a9b7a8f3540b0a430448aefdc0abf0980368d7b183100600ebdada2ed6'),UNHEX('3754c68d4b05de4538e0edf5d9fc8bef08397314b5e683a6fecbc9e319bbe3c7'),'published','senior','216cfa804177e1391ac02a6d418d985f',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('216cfa804177e1391ac02a6d418d985f','2f7544ea697c478b6c6545585faa3a7c',1,'phenomenon','phenomenon','/fəˈnɒmɪnən/','现象','This is a common social phenomenon.','这是一种常见的社会现象。','intro',15,'公开英语词表，仅作学习用途',UNHEX('3754c68d4b05de4538e0edf5d9fc8bef08397314b5e683a6fecbc9e319bbe3c7'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('8b47669bdf94823146d1b704087009cf','216cfa804177e1391ac02a6d418d985f','noun','现象',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('d784e8ffd0a0b8f7d6075f51ae0d313d','8b47669bdf94823146d1b704087009cf','This is a common social phenomenon.','这是一种常见的社会现象。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='216cfa804177e1391ac02a6d418d985f' WHERE `id`='2f7544ea697c478b6c6545585faa3a7c';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('3b182632b6512891954c33dc633752bc','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('d44ff8b2b35b74efb1644dc13d44ecc26f09f39a2a249df403ae508172f072c8'),UNHEX('e61db885bd6e7f403a7bc1d80577ecb7e31b18121b69534a04e986cd4d215fa0'),'published','senior','fd056c40ed85c86721430a6b8b458fb1',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('fd056c40ed85c86721430a6b8b458fb1','3b182632b6512891954c33dc633752bc',1,'significant','significant','/sɪɡˈnɪfɪkənt/','重要的，显著的','There is a significant difference between them.','它们之间有显著差异。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e61db885bd6e7f403a7bc1d80577ecb7e31b18121b69534a04e986cd4d215fa0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('b73b18454a699151473b87b07b4f0468','fd056c40ed85c86721430a6b8b458fb1','adjective','重要的，显著的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0085f69c4424002759fbe1352607a609','b73b18454a699151473b87b07b4f0468','There is a significant difference between them.','它们之间有显著差异。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='fd056c40ed85c86721430a6b8b458fb1' WHERE `id`='3b182632b6512891954c33dc633752bc';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('85c46187cc43ae2e65358fb67e95ef32','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('563a798f0eb6c2cc9aac5510d31b58b466b80f94499a1796902d9dfae955af84'),UNHEX('74425421116546fd8872363f511450b62a1853310fedff6c283f55569bcc1ffe'),'published','senior','017c247893cfc8d85cf29af55c6eb4e2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('017c247893cfc8d85cf29af55c6eb4e2','85c46187cc43ae2e65358fb67e95ef32',1,'perspective','perspective','/pəˈspektɪv/','观点，视角','We should look at the problem from a different perspective.','我们应该从不同角度看问题。','intro',15,'公开英语词表，仅作学习用途',UNHEX('74425421116546fd8872363f511450b62a1853310fedff6c283f55569bcc1ffe'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('f48c020ca3214905e65ac9b852d9aaa5','017c247893cfc8d85cf29af55c6eb4e2','noun','观点，视角',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('e10fa4d5c6ecedccda8e1b8e2752469e','f48c020ca3214905e65ac9b852d9aaa5','We should look at the problem from a different perspective.','我们应该从不同角度看问题。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='017c247893cfc8d85cf29af55c6eb4e2' WHERE `id`='85c46187cc43ae2e65358fb67e95ef32';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('4a74a74570d506a5e5d9b5461e923c9b','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b37892bcf239081533f89232552f56e48d88af381b7d36f82b88a1a4c28abf9a'),UNHEX('6611032577e5a7b3a146e7b2111117b71a2a7b0fa4ef2861353816730a4f4a02'),'published','senior','f99c89521fb0b9b3130ad4534299370c',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('f99c89521fb0b9b3130ad4534299370c','4a74a74570d506a5e5d9b5461e923c9b',1,'innovation','innovation','/ˌɪnəˈveɪʃn/','创新','Innovation drives the development of society.','创新推动社会发展。','intro',15,'公开英语词表，仅作学习用途',UNHEX('6611032577e5a7b3a146e7b2111117b71a2a7b0fa4ef2861353816730a4f4a02'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('239eb8507d65d4f85c68c6dece0c4630','f99c89521fb0b9b3130ad4534299370c','noun','创新',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('27b0406c82c54c5430e528d26b2fd9c7','239eb8507d65d4f85c68c6dece0c4630','Innovation drives the development of society.','创新推动社会发展。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='f99c89521fb0b9b3130ad4534299370c' WHERE `id`='4a74a74570d506a5e5d9b5461e923c9b';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2311891a306fe49e5398fda56cae6334','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('abb684ab8e829c242b9cc771e8c7ddda44d0d829a2e6fefb8866d0a6555f11b7'),UNHEX('4994ad9e4f40b39e4773dd86c85e61ce997bb4ae85a39d4a373332a3035a96be'),'published','senior','f0c8dc975bc5d2e1274da64d71693454',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('f0c8dc975bc5d2e1274da64d71693454','2311891a306fe49e5398fda56cae6334',1,'comprehensive','comprehensive','/ˌkɒmprɪˈhensɪv/','全面的','The report gives a comprehensive analysis.','这份报告给出了全面的分析。','intro',15,'公开英语词表，仅作学习用途',UNHEX('4994ad9e4f40b39e4773dd86c85e61ce997bb4ae85a39d4a373332a3035a96be'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('9c7a9b87867c9812c545d4587528a5ae','f0c8dc975bc5d2e1274da64d71693454','adjective','全面的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('eb528eb823d7b925007e2dfe3434b915','9c7a9b87867c9812c545d4587528a5ae','The report gives a comprehensive analysis.','这份报告给出了全面的分析。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='f0c8dc975bc5d2e1274da64d71693454' WHERE `id`='2311891a306fe49e5398fda56cae6334';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('7aec8ec6c458446a80ecaf92f234a16e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('982dcf6a3b7791b08314184b839db244c14a4b6c362f47475d2167a763cdaf4f'),UNHEX('7beb7f4a57251263ca5d7274880aa4ba470a8b32e18885340ca2fd40a588fe23'),'published','senior','0a9ee8a6805a33b57970b91ae2ac6895',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0a9ee8a6805a33b57970b91ae2ac6895','7aec8ec6c458446a80ecaf92f234a16e',1,'hypothesis','hypothesis','/haɪˈpɒθəsɪs/','假设','The experiment supports the hypothesis.','实验支持这个假设。','intro',15,'公开英语词表，仅作学习用途',UNHEX('7beb7f4a57251263ca5d7274880aa4ba470a8b32e18885340ca2fd40a588fe23'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('5fe61dee12e7fec59879c7f7edd44b96','0a9ee8a6805a33b57970b91ae2ac6895','noun','假设',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('37f859e3d763e3dcf5935826445a7f52','5fe61dee12e7fec59879c7f7edd44b96','The experiment supports the hypothesis.','实验支持这个假设。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='0a9ee8a6805a33b57970b91ae2ac6895' WHERE `id`='7aec8ec6c458446a80ecaf92f234a16e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('ad66316f61a3ec14818a39c17ccb183a','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b189ee028c9923e3dc9d8482f45dc1ce056b7cd5d81c5fe6eb8b07958ade4f2f'),UNHEX('84bb160039a7955385c44ea53025a2585c33ca4a40c9d2498f7cfc0784ecbd53'),'published','senior','c0356639d0fa30068df6adf3e7b73fa2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c0356639d0fa30068df6adf3e7b73fa2','ad66316f61a3ec14818a39c17ccb183a',1,'consequence','consequence','/ˈkɒnsɪkwəns/','结果，后果','Every choice has its consequences.','每个选择都有其后果。','intro',15,'公开英语词表，仅作学习用途',UNHEX('84bb160039a7955385c44ea53025a2585c33ca4a40c9d2498f7cfc0784ecbd53'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('0d2988f1392dbb016289f13122a53d33','c0356639d0fa30068df6adf3e7b73fa2','noun','结果，后果',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('a81d3f9cde077b9db94d50266cdfa71a','0d2988f1392dbb016289f13122a53d33','Every choice has its consequences.','每个选择都有其后果。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c0356639d0fa30068df6adf3e7b73fa2' WHERE `id`='ad66316f61a3ec14818a39c17ccb183a';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('1c1182f00cedeeaa526fdb660bd57cb5','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5aac2d6e910986d4ccca9f98365d7c6dc2dd3983e6d3b756df1cbc3aee74ff1e'),UNHEX('a65dd3628de58bb9f3eeac7e3fbc8508cd45e0c550cabd97271bba4e272719d0'),'published','senior','c0ad4a677bde0bf2860fbc1c2fe876b3',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c0ad4a677bde0bf2860fbc1c2fe876b3','1c1182f00cedeeaa526fdb660bd57cb5',1,'demonstrate','demonstrate','/ˈdemənstreɪt/','证明，演示','The teacher demonstrated how to solve the problem.','老师演示了如何解决这个问题。','intro',15,'公开英语词表，仅作学习用途',UNHEX('a65dd3628de58bb9f3eeac7e3fbc8508cd45e0c550cabd97271bba4e272719d0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('f7103e57689e99f91c2aafeed5bbf6f8','c0ad4a677bde0bf2860fbc1c2fe876b3','verb','证明，演示',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('c5efbaceae7273eb5c33741c136a5d76','f7103e57689e99f91c2aafeed5bbf6f8','The teacher demonstrated how to solve the problem.','老师演示了如何解决这个问题。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c0ad4a677bde0bf2860fbc1c2fe876b3' WHERE `id`='1c1182f00cedeeaa526fdb660bd57cb5';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d01e0be6cfac199365571af9d91cf304','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('df5363818e2ea29040dccc9a898c0d7e3766f397f91537433b616ae8f66a72cd'),UNHEX('e45c0ca14614471e97255d365ca87e0f277ff47a3073fd631f5eeaab458ceb6c'),'published','senior','aa17359186f76b1486ef5642fa775cf9',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('aa17359186f76b1486ef5642fa775cf9','d01e0be6cfac199365571af9d91cf304',1,'sophisticated','sophisticated','/səˈfɪstɪkeɪtɪd/','复杂的，精密的','This is a sophisticated machine.','这是一台精密的机器。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e45c0ca14614471e97255d365ca87e0f277ff47a3073fd631f5eeaab458ceb6c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('4c09655ebc27276d7839b8295774e66e','aa17359186f76b1486ef5642fa775cf9','adjective','复杂的，精密的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0315394206bb43d70bcaa3b827e2c8a4','4c09655ebc27276d7839b8295774e66e','This is a sophisticated machine.','这是一台精密的机器。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='aa17359186f76b1486ef5642fa775cf9' WHERE `id`='d01e0be6cfac199365571af9d91cf304';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('fd7baa34641d38c26ad992d0eefb8cf8','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('4d6675c60b71420f74c638aef33c3c3c467c5e718d983b4ee5ec9a96ad7113ba'),UNHEX('711415eb4348502ead97fca1cf869a6ed64c8111ddc748b9d7b7fab48974ad3e'),'published','senior','43faac24125573b9681203dbff55b3fe',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('43faac24125573b9681203dbff55b3fe','fd7baa34641d38c26ad992d0eefb8cf8',1,'sustainable','sustainable','/səˈsteɪnəbl/','可持续的','We need sustainable development.','我们需要可持续发展。','intro',15,'公开英语词表，仅作学习用途',UNHEX('711415eb4348502ead97fca1cf869a6ed64c8111ddc748b9d7b7fab48974ad3e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('eb07d56418fb14c4de50730b1409a4b2','43faac24125573b9681203dbff55b3fe','adjective','可持续的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('8d9b38bf7b22675cccb65adb872bbd0e','eb07d56418fb14c4de50730b1409a4b2','We need sustainable development.','我们需要可持续发展。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='43faac24125573b9681203dbff55b3fe' WHERE `id`='fd7baa34641d38c26ad992d0eefb8cf8';
