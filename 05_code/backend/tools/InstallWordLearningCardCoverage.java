import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.service.WordLearningCardCoverageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;

/** Explicit local-only migration/generator. It never starts TTS, AI, schedulers or the web server. */
public class InstallWordLearningCardCoverage {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        if(args.length!=1||!"--apply-local".equals(args[0]))throw new IllegalArgumentException("Require --apply-local");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        Map<String,Object> config;
        try(java.io.InputStream in=Files.newInputStream(Paths.get("src/main/resources/application-dev.yml"))){config=new Yaml().load(in);}
        Map<String,Object> datasource=(Map<String,Object>)((Map<String,Object>)config.get("spring")).get("datasource");
        String url=resolve(datasource.get("url"));
        if(!url.startsWith("jdbc:mysql://localhost:3306/letMindWander?"))throw new IllegalStateException("Only the named local development database is allowed");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,resolve(datasource.get("username")),resolve(datasource.get("password")));
        for(String path:Arrays.asList("sql/V3.16.5_word_learning_cards.sql","sql/V3.16.7_word_learning_card_coverage.sql","sql/V3.16.8_word_learning_card_preferences.sql","sql/V3.16.9_word_study_preferences.sql","sql/V3.16.10_word_lexical_evidence.sql")){
            ResourceDatabasePopulator migration=new ResourceDatabasePopulator(new FileSystemResource(path));migration.setSqlScriptEncoding("UTF-8");migration.execute(ds);
        }
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        Map<String,Object> result=new WordLearningCardCoverageService(jdbc,new DataSourceTransactionManager(ds),new ObjectMapper()).generate();
        System.out.println(new ObjectMapper().writeValueAsString(result));
        System.out.println(jdbc.queryForList("SELECT target_scope_codes,COUNT(*) AS words,SUM(published_card_count) AS cards,SUM(CASE WHEN baseline_status='complete' THEN 1 ELSE 0 END) AS complete FROM word_learning_card_coverage WHERE del_is=0 GROUP BY target_scope_codes ORDER BY target_scope_codes"));
        System.out.println(jdbc.queryForList("SELECT baseline_status,example_status,relation_status,quotation_status,COUNT(*) AS words FROM word_learning_card_coverage WHERE del_is=0 GROUP BY baseline_status,example_status,relation_status,quotation_status"));
        System.out.println(jdbc.queryForList("SELECT card_type,requirement_level,coverage_status,COUNT(*) AS words,SUM(published_count) AS cards FROM word_learning_card_type_coverage WHERE del_is=0 GROUP BY card_type,requirement_level,coverage_status ORDER BY card_type,coverage_status"));
    }
    private static String resolve(Object value){String s=String.valueOf(value);if(!s.startsWith("${")||!s.endsWith("}"))return s;String inner=s.substring(2,s.length()-1);int split=inner.indexOf(':');String name=split<0?inner:inner.substring(0,split),env=System.getenv(name);if(env!=null)return env;if(split>=0)return inner.substring(split+1);throw new IllegalStateException("Missing environment variable: "+name);}
}
