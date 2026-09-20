import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;

/** Explicit local-dev migration/sync utility. No web server, scheduling or TTS is started. */
public class SyncOnlineVocabulary {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if(args.length==0||!"--apply-local".equals(args[0]))throw new IllegalArgumentException("Require --apply-local [bookCode ...]");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        Map<String,Object> config;
        try(java.io.InputStream in=Files.newInputStream(Paths.get("src/main/resources/application-dev.yml"))){config=new Yaml().load(in);}
        Map<String,Object> datasource=(Map<String,Object>)((Map<String,Object>)config.get("spring")).get("datasource");
        String url=resolve(datasource.get("url"));
        if(!url.startsWith("jdbc:mysql://localhost:3306/letMindWander?"))throw new IllegalStateException("Only the named local development database is allowed");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,resolve(datasource.get("username")),resolve(datasource.get("password")));
        // DDL is additive. Snapshot writes are transactional and never touch published content.
        ResourceDatabasePopulator migration=new ResourceDatabasePopulator(new FileSystemResource("sql/V3.16.4_online_vocabulary_catalog.sql"));migration.setSqlScriptEncoding("UTF-8");migration.execute(ds);
        OnlineVocabularyCatalogService service=new OnlineVocabularyCatalogService(new JdbcTemplate(ds),new ObjectMapper(),new OnlineVocabularyDownloader(),new DataSourceTransactionManager(ds));
        System.out.println("Catalogue entries: "+service.books().size());
        for(int i=1;i<args.length;i++)System.out.println(args[i]+": "+new ObjectMapper().writeValueAsString(service.sync(args[i],"00000000000000000000000000000002")));
    }
    private static String resolve(Object value){
        String s=String.valueOf(value);
        if(s.startsWith("${")&&s.endsWith("}")){
            String inner=s.substring(2,s.length()-1);int split=inner.indexOf(':');
            String name=split<0?inner:inner.substring(0,split);String env=System.getenv(name);
            if(env!=null)return env;if(split>=0)return inner.substring(split+1);
            throw new IllegalStateException("Missing environment variable: "+name);
        }
        return s;
    }
}
