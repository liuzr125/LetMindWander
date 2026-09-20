package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.model.ContentDetailView;
import com.zhixing.service.ContentService;
import com.zhixing.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f19;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev")
class F19WordPronunciationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentService contents;
    @MockBean MediaService media;

    @BeforeEach void seed() {
        when(media.signedUrl(anyString(),nullable(String.class))).thenAnswer(call -> "signed:" + call.getArgument(0));
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note,enabled) VALUES(?,?,'manual',?,1)",SOURCE,"多义词测试","测试许可");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,'senior','published',?,?,CURRENT_TIMESTAMP)",CONTENT,"word",SOURCE,CryptoUtils.sha256(CONTENT),CryptoUtils.sha256("resume"),VERSION,VERSION);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'resume','resume','advanced',30,'resume','继续；简历','测试许可',?,'approved','00000000000000000000000000000002')",VERSION,CONTENT,CryptoUtils.sha256("resume"));
        jdbc.update("INSERT INTO word_sense(id,content_version_id,part_of_speech,meaning,sort_no) VALUES(?,?,'verb','继续',1),(?,?,'noun','简历',2)",SENSE_VERB,VERSION,SENSE_NOUN,VERSION);
        jdbc.update("INSERT INTO word_example(id,sense_id,sentence,translation,sort_no) VALUES(?,?,'We resume work.','我们继续工作。',1),(?,?,'Send your resume.','发送你的简历。',1)",EXAMPLE_VERB,SENSE_VERB,EXAMPLE_NOUN,SENSE_NOUN);
        insertPronunciation("19000000000000000000000000000008",SENSE_VERB,null,"sense:"+SENSE_VERB,"uk","/rɪˈzjuːm/","19000000000000000000000000000018");
        insertPronunciation("19000000000000000000000000000009",SENSE_VERB,null,"sense:"+SENSE_VERB,"us","/rɪˈzuːm/","19000000000000000000000000000019");
        insertPronunciation("19000000000000000000000000000010",SENSE_NOUN,null,"sense:"+SENSE_NOUN,"uk","/ˈrezjʊmeɪ/","19000000000000000000000000000020");
        insertPronunciation("19000000000000000000000000000011",SENSE_NOUN,null,"sense:"+SENSE_NOUN,"us","/ˈrezəmeɪ/","19000000000000000000000000000021");
        insertPronunciation("19000000000000000000000000000012",null,EXAMPLE_VERB,"example:"+EXAMPLE_VERB,"uk",null,"19000000000000000000000000000022");
        insertPronunciation("19000000000000000000000000000013",null,EXAMPLE_VERB,"example:"+EXAMPLE_VERB,"us",null,"19000000000000000000000000000023");
        insertPronunciation("19000000000000000000000000000014",null,EXAMPLE_NOUN,"example:"+EXAMPLE_NOUN,"uk",null,"19000000000000000000000000000024");
        insertPronunciation("19000000000000000000000000000015",null,EXAMPLE_NOUN,"example:"+EXAMPLE_NOUN,"us",null,"19000000000000000000000000000025");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,'senior','published',?,?,CURRENT_TIMESTAMP)",ARTICLE,"english_article",SOURCE,CryptoUtils.sha256(ARTICLE),ARTICLE_VERSION,ARTICLE_VERSION);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,license_snapshot,body_hash,review_status,article_blocks,created_by) VALUES(?,?,1,'Resume article','Resume article','We resume work.','advanced',60,'测试许可',?,'approved',?,'00000000000000000000000000000002')",ARTICLE_VERSION,ARTICLE,CryptoUtils.sha256("We resume work."),"[{\"paragraph_id\":\"p1\",\"text\":\"We resume work.\",\"translation\":\"我们继续工作。\"}]");
    }

    @Test void keepsSenseAndExampleAudioBoundToTheirOwnMeaningAndAccent() {
        ContentDetailView detail=contents.get("00000000000000000000000000000001",CONTENT);
        assertThat(detail.getPhonetic()).isEqualTo("/rɪˈzjuːm/");
        assertThat(detail.getPronunciations()).isEmpty();
        assertThat(detail.getSenses()).hasSize(2);
        assertThat(detail.getSenses().get(0).getPartOfSpeech()).isEqualTo("verb");
        assertThat(detail.getSenses().get(0).getPronunciations()).extracting("accent").containsExactly("uk","us");
        assertThat(detail.getSenses().get(0).getPronunciations()).extracting("phonetic").containsExactly("/rɪˈzjuːm/","/rɪˈzuːm/");
        assertThat(detail.getSenses().get(1).getPartOfSpeech()).isEqualTo("noun");
        assertThat(detail.getSenses().get(1).getPronunciations()).extracting("phonetic").containsExactly("/ˈrezjʊmeɪ/","/ˈrezəmeɪ/");
        assertThat(detail.getSenses().get(0).getExamples().get(0).getPronunciations()).extracting("accent").containsExactly("uk","us");
        assertThat(detail.getSenses().get(1).getExamples().get(0).getPronunciations()).extracting("accent").containsExactly("uk","us");
        assertThat(detail.getSenses().get(0).getPronunciations().get(0).getAudioUrl()).isEqualTo("signed:19000000000000000000000000000018");
        assertThat(detail.getSenses().get(1).getPronunciations().get(0).getAudioUrl()).isEqualTo("signed:19000000000000000000000000000020");

        insertMedia("19000000000000000000000000000022");
        ContentDetailView.ArticleTokenView withoutWordAudio=resumeToken(contents.get("00000000000000000000000000000001",ARTICLE));
        assertThat(withoutWordAudio.getContentId()).isEqualTo(CONTENT);
        assertThat(withoutWordAudio.getAudioUrl()).as("例句音频不能冒充单词发音").isNull();

        insertMedia("19000000000000000000000000000018");
        insertMedia("19000000000000000000000000000019");
        ContentDetailView.ArticleTokenView withWordAudio=resumeToken(contents.get("00000000000000000000000000000001",ARTICLE));
        assertThat(withWordAudio.getAudioUrl()).isEqualTo("signed:19000000000000000000000000000018");
    }

    private ContentDetailView.ArticleTokenView resumeToken(ContentDetailView detail) {
        return detail.getArticleBlocks().get(0).getTokens().stream()
                .filter(token -> "resume".equalsIgnoreCase(token.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("短文中缺少 resume 点词 token"));
    }

    private void insertMedia(String assetId) {
        jdbc.update("INSERT INTO media_asset(id,purpose,object_key,mime_type,byte_size,sha256,state) VALUES(?,'word_audio',?,'audio/mpeg',1,?,'ready')",
                assetId,"test/"+assetId,CryptoUtils.sha256(assetId));
    }

    private void insertPronunciation(String id,String senseId,String exampleId,String targetKey,String accent,String phonetic,String assetId) {
        jdbc.update("INSERT INTO pronunciation(id,content_version_id,sense_id,example_id,target_key,accent,phonetic,asset_id,state) VALUES(?,?,?,?,?,?,?,?, 'ready')",
                id,VERSION,senseId,exampleId,targetKey,accent,phonetic,assetId);
    }

    private static final String SOURCE="19000000000000000000000000000001";
    private static final String CONTENT="19000000000000000000000000000002";
    private static final String VERSION="19000000000000000000000000000003";
    private static final String SENSE_VERB="19000000000000000000000000000004";
    private static final String SENSE_NOUN="19000000000000000000000000000005";
    private static final String EXAMPLE_VERB="19000000000000000000000000000006";
    private static final String EXAMPLE_NOUN="19000000000000000000000000000007";
    private static final String ARTICLE="19000000000000000000000000000026";
    private static final String ARTICLE_VERSION="19000000000000000000000000000027";
}
