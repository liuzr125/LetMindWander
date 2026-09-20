package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.service.*;
import com.zhixing.common.*;
import com.zhixing.config.AppProperties;
import com.zhixing.controller.AdminOnlineVocabularyController;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OnlineVocabularyCatalogTest {
    @BeforeAll static void quietJdbc(){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc")).setLevel(ch.qos.logback.classic.Level.WARN);}
    JdbcTemplate jdbc; ObjectMapper json=new ObjectMapper(); OnlineVocabularyDownloader download;
    OnlineVocabularyCatalogService catalog; VocabularyImportService imports; AliyunTtsService tts;
    String admin="00000000000000000000000000000002";
    @BeforeEach void setup(){
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:online"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        ResourceDatabasePopulator schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));schema.setSqlScriptEncoding("UTF-8");schema.execute(ds);
        jdbc=new JdbcTemplate(ds);download=mock(OnlineVocabularyDownloader.class);
        catalog=new OnlineVocabularyCatalogService(jdbc,json,download,new DataSourceTransactionManager(ds));catalog.initializeCatalogue();
        tts=mock(AliyunTtsService.class);when(tts.vocabularyVoice(any(),anyString(),anyString())).thenAnswer(i->i.getArgument(2));
        imports=new VocabularyImportService(jdbc,json,tts);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,word_count,sort_no,state) VALUES('test-book','test-book','考研测试','exam',0,1,'active')");
    }
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    String source(String word,String meanings){
        return "{\"bookId\":\"KaoYan_2\",\"headWord\":\""+word+"\",\"content\":{\"word\":{\"wordId\":\"test-"+word+"\",\"content\":{\"usphone\":\"test-us\",\"ukphone\":\"test-uk\",\"trans\":"+meanings+",\"sentence\":{\"sentences\":[{\"sContent\":\"A test example.\",\"sCn\":\"测试例句。\"},{\"sContent\":\"Another example.\",\"sCn\":\"另一例句。\"}]}}}}}";
    }
    void prepare(String raw,int expected){
        jdbc.update("UPDATE vocabulary_online_book SET expected_count=? WHERE book_code='KaoYan_2'",expected);
        when(download.download(anyString(),anyString())).thenReturn(new byte[]{1});
        when(download.unpack(any(),eq("KaoYan_2"))).thenReturn(raw);
    }
    @Test void catalogueSeparatesMetadataAndDataAndEnforcesAdmin() throws Exception {
        List<Map<String,Object>> books=catalog.books();
        assertEquals(19,books.size());assertEquals(0,count("vocabulary_online_word"));
        assertEquals(Arrays.asList("小学英语","初中英语","高中英语","大学英语四级","大学英语六级","考研英语","IELTS 雅思","TOEFL 托福","GRE","Oxford 3000","Oxford 5000","计算机英语"),
                books.stream().filter(b->"core".equals(b.get("category"))).map(b->b.get("bookName").toString()).collect(java.util.stream.Collectors.toList()));
        assertEquals(8,books.stream().filter(b->"core".equals(b.get("category"))&&Boolean.TRUE.equals(b.get("downloadable"))).count());
        assertThrows(ApiException.class,()->catalog.sync("POPULAR_HONGBAO_2027",admin));verifyNoInteractions(download);
        MockMvcBuilders.standaloneSetup(new AdminOnlineVocabularyController(catalog,new AppProperties())).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/api/admin/vocabulary/online-books")).andExpect(status().isUnauthorized());
    }
    @Test void syncIsIdempotentKeepsAllExamplesAndNeverPublishes() {
        prepare(source("test","[{\"pos\":\"n\",\"tranCn\":\"测试\"}]"),1);
        Map<String,Object> result=catalog.sync("KaoYan_2",admin);String id=result.get("datasetId").toString();
        assertEquals(1,count("vocabulary_online_word"));assertEquals(1,count("vocabulary_dataset_catalog"));
        assertEquals(0,count("learning_content"));assertEquals(0,count("tts_generation_task"));
        assertEquals("unknown",jdbc.queryForObject("SELECT license_status FROM vocabulary_dataset_catalog WHERE id=?",String.class,id));
        assertEquals(true,catalog.sync("KaoYan_2",admin).get("reusedSnapshot"));
        assertEquals(1,count("vocabulary_online_word"));
        String normalized=jdbc.queryForObject("SELECT normalized_json FROM vocabulary_online_word",String.class);
        assertTrue(normalized.contains("Another example."));assertTrue(normalized.contains("test-us"));
        Map<String,Object> page=catalog.words("KaoYan_2",1,20,"test");assertEquals(1,page.get("total"));
        assertEquals(0,catalog.words("KaoYan_2",1,20,"%").get("total"));
        jdbc.update("UPDATE vocabulary_online_book SET del_is=1 WHERE book_code='KaoYan_2'");
        catalog.initializeCatalogue();assertEquals(18,catalog.books().size());assertThrows(ApiException.class,()->catalog.sync("KaoYan_2",admin));
    }
    @Test void incompleteOrWrongBookCannotReplaceSnapshot(){
        prepare(source("test","[{\"pos\":\"n\",\"tranCn\":\"测试\"}]"),2);
        assertThrows(ApiException.class,()->catalog.sync("KaoYan_2",admin));assertEquals(0,count("vocabulary_dataset_catalog"));
        assertThrows(ApiException.class,()->catalog.normalize(source("test","[]"),"KaoYan_3"));
    }
    @Test void multiSenseUnmappedExamplesAreBlockedWithoutLoss() {
        prepare(source("record","[{\"pos\":\"n\",\"tranCn\":\"记录\"},{\"pos\":\"v\",\"tranCn\":\"记下\"}]"),1);
        String dataset=catalog.sync("KaoYan_2",admin).get("datasetId").toString();
        String batch=imports.createBatch(dataset,"test-book",new HashMap<String,Object>(),admin).get("batchId").toString();
        Map<String,Object> result=imports.start(batch,admin);
        assertEquals(1,result.get("reviewCount"));assertEquals(0,count("tts_generation_task"));
        String item=jdbc.queryForObject("SELECT id FROM vocabulary_import_item",String.class);
        assertThrows(ApiException.class,()->imports.review(batch,Arrays.asList(item),"approve",admin));
        assertThrows(ApiException.class,()->imports.publish(batch,admin));
        assertThrows(ApiException.class,()->imports.retryTts(batch,admin));
        verify(tts,never()).configured();
    }
    @Test void structuredPublishPreservesSensesExamplesPhoneticsAndResume() throws Exception {
        String payload="[{\"word\":\"record\",\"phoneticUs\":\"us-source\",\"phoneticUk\":\"uk-source\",\"senses\":[{\"partOfSpeech\":\"noun\",\"meaning\":\"记录\",\"examples\":[{\"sentence\":\"Keep a record.\",\"translation\":\"保留记录。\"},{\"sentence\":\"Read the record.\",\"translation\":\"阅读记录。\"}]},{\"partOfSpeech\":\"verb\",\"meaning\":\"记录下来\",\"examples\":[{\"sentence\":\"Record the result.\",\"translation\":\"记下结果。\"}]}]}]";
        String dataset=imports.createDataset("原创测试","测试","verified","自有内容",payload).get("datasetId").toString();
        String batch=imports.createBatch(dataset,"test-book",new HashMap<String,Object>(),admin).get("batchId").toString();
        assertThrows(ApiException.class,()->imports.publish(batch,admin));
        imports.start(batch,admin);assertEquals(10,count("tts_generation_task"));
        jdbc.update("UPDATE tts_generation_task SET state='succeeded',asset_id='test-audio' WHERE batch_id=?",batch);
        imports.pause(batch,admin);imports.start(batch,admin);
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM tts_generation_task WHERE state='succeeded'",Integer.class));
        imports.publish(batch,admin);
        assertEquals(2,count("word_sense"));assertEquals(3,count("word_example"));assertEquals(10,count("pronunciation"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM pronunciation WHERE phonetic='uk-source'",Integer.class));
        imports.publish(batch,admin);assertEquals(1,count("learning_content"));
        assertEquals(1,jdbc.queryForObject("SELECT word_count FROM vocabulary_book WHERE id='test-book'",Integer.class));
    }
    @Test void archiveCannotExtractArbitraryPathsAndDownloaderRejectsUrls() throws Exception {
        OnlineVocabularyDownloader real=new OnlineVocabularyDownloader();
        assertThrows(ApiException.class,()->real.download("master","https://localhost/secrets"));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("../KaoYan_2.json"));zip.write("{}".getBytes("UTF-8"));zip.closeEntry();}
        assertThrows(ApiException.class,()->real.unpack(out.toByteArray(),"KaoYan_2"));
        assertThrows(IOException.class,()->OnlineVocabularyDownloader.bounded(new ByteArrayInputStream(new byte[100]),10));
    }
}
