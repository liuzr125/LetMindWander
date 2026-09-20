import com.zhixing.service.WordLearningCardSeedImporter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;

/** Restricted local development deployment; no secret values or web sessions are printed. */
public class InstallWordLearningCards {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        if(args.length!=1 || !("--apply-local".equals(args[0])||"--audit-local".equals(args[0])))throw new IllegalArgumentException("Require --apply-local or --audit-local");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        Map<String,Object> config;
        try(java.io.InputStream in=Files.newInputStream(Paths.get("src/main/resources/application-dev.yml"))){config=new Yaml().load(in);}
        Map<String,Object> datasource=(Map<String,Object>)((Map<String,Object>)config.get("spring")).get("datasource");
        String url=resolve(datasource.get("url"));
        if(!url.startsWith("jdbc:mysql://localhost:3306/letMindWander?"))throw new IllegalStateException("Only the named local development database is allowed");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,resolve(datasource.get("username")),resolve(datasource.get("password")));
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        if("--audit-local".equals(args[0])){
            System.out.println(jdbc.queryForList("SELECT vb.book_code,vb.book_name,vb.word_count,COUNT(DISTINCT vbw.content_id) AS actual_words,"+
                "COUNT(DISTINCT CASE WHEN lc.state='published' AND cv.review_status='approved' THEN vbw.content_id END) AS published_words,"+
                "COUNT(DISTINCT CASE WHEN we.id IS NOT NULL THEN vbw.content_id END) AS words_with_examples,"+
                "COUNT(DISTINCT CASE WHEN wlc.id IS NOT NULL AND wlc.del_is=0 THEN vbw.content_id END) AS words_with_cards " +
                "FROM vocabulary_book vb LEFT JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id LEFT JOIN learning_content lc ON lc.id=vbw.content_id " +
                "LEFT JOIN content_version cv ON cv.id=lc.published_version_id LEFT JOIN word_sense ws ON ws.content_version_id=cv.id LEFT JOIN word_example we ON we.sense_id=ws.id " +
                "LEFT JOIN word_learning_card wlc ON wlc.content_version_id=cv.id WHERE vb.level_code IN ('primary','junior','senior','cet4','cet6') " +
                "GROUP BY vb.id,vb.book_code,vb.book_name,vb.word_count ORDER BY vb.sort_no"));
            System.out.println(jdbc.queryForList("SELECT COUNT(DISTINCT vbw.content_id) AS target_union,COUNT(DISTINCT CASE WHEN lc.state='published' AND cv.review_status='approved' THEN vbw.content_id END) AS published_union " +
                "FROM vocabulary_book vb JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id LEFT JOIN learning_content lc ON lc.id=vbw.content_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id " +
                "WHERE vb.level_code IN ('primary','junior','senior','cet4','cet6')"));
            System.out.println(jdbc.queryForList("SELECT " +
                "COUNT(DISTINCT CASE WHEN lc.id IS NULL THEN vbw.content_id END) AS missing_content,"+
                "COUNT(DISTINCT CASE WHEN lc.content_type<>'word' THEN vbw.content_id END) AS wrong_type,"+
                "COUNT(DISTINCT CASE WHEN lc.state<>'published' THEN vbw.content_id END) AS not_published,"+
                "COUNT(DISTINCT CASE WHEN lc.withdrawn_at IS NOT NULL THEN vbw.content_id END) AS withdrawn,"+
                "COUNT(DISTINCT CASE WHEN cv.id IS NULL THEN vbw.content_id END) AS missing_version,"+
                "COUNT(DISTINCT CASE WHEN cv.content_id<>lc.id THEN vbw.content_id END) AS version_owner_mismatch,"+
                "COUNT(DISTINCT CASE WHEN cv.review_status<>'approved' THEN vbw.content_id END) AS not_approved " +
                "FROM vocabulary_book vb JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id LEFT JOIN learning_content lc ON lc.id=vbw.content_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id " +
                "WHERE vb.level_code IN ('primary','junior','senior','cet4','cet6')"));
            System.out.println(jdbc.queryForList("SELECT DISTINCT lc.id AS member_content_id,cv.content_id AS canonical_content_id,cv.word_term,lc.state,cv.review_status " +
                "FROM vocabulary_book vb JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id JOIN learning_content lc ON lc.id=vbw.content_id JOIN content_version cv ON cv.id=lc.published_version_id " +
                "WHERE vb.level_code IN ('primary','junior','senior','cet4','cet6') AND cv.content_id<>lc.id ORDER BY cv.word_term LIMIT 20"));return;
        }
        ResourceDatabasePopulator migration=new ResourceDatabasePopulator(new FileSystemResource("sql/V3.16.5_word_learning_cards.sql"));migration.setSqlScriptEncoding("UTF-8");migration.execute(ds);
        int added=new WordLearningCardSeedImporter(jdbc,new DataSourceTransactionManager(ds)).seed();
        System.out.println("Added cards: "+added);
        System.out.println(jdbc.queryForList("SELECT cv.word_term,COUNT(*) AS cards FROM word_learning_card c JOIN content_version cv ON cv.id=c.content_version_id WHERE c.del_is=0 GROUP BY cv.word_term"));
    }
    private static String resolve(Object value){
        String s=String.valueOf(value);if(!s.startsWith("${")||!s.endsWith("}"))return s;
        String inner=s.substring(2,s.length()-1);int split=inner.indexOf(':');String name=split<0?inner:inner.substring(0,split),env=System.getenv(name);
        if(env!=null)return env;if(split>=0)return inner.substring(split+1);throw new IllegalStateException("Missing environment variable: "+name);
    }
}
