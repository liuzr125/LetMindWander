import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.service.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Explicit local-only lexical import and card backfill; it never starts the web server or paid services. */
public class InstallWordLearningCardEnrichment {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        if(args.length<2||!"--apply-local".equals(args[0]))throw new IllegalArgumentException("Use --apply-local --oewn-zip <file> or --apply-local --download-official");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        Path zip;
        if("--oewn-zip".equals(args[1])&&args.length==3)zip=Paths.get(args[2]);
        else if("--download-official".equals(args[1])&&args.length==2){zip=Paths.get("target/lexical-data/english-wordnet-2025-json.zip");download(zip);}
        else throw new IllegalArgumentException("Use --apply-local --oewn-zip <file> or --apply-local --download-official");
        Map<String,Object> config;try(InputStream in=Files.newInputStream(Paths.get("src/main/resources/application-dev.yml"))){config=new Yaml().load(in);}
        Map<String,Object> datasource=(Map<String,Object>)((Map<String,Object>)config.get("spring")).get("datasource");String url=resolve(datasource.get("url"));
        if(!url.startsWith("jdbc:mysql://localhost:3306/letMindWander?"))throw new IllegalStateException("Only the named local development database is allowed");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,resolve(datasource.get("username")),resolve(datasource.get("password")));
        for(String path:Arrays.asList("sql/V3.16.5_word_learning_cards.sql","sql/V3.16.7_word_learning_card_coverage.sql","sql/V3.16.8_word_learning_card_preferences.sql","sql/V3.16.9_word_study_preferences.sql","sql/V3.16.10_word_lexical_evidence.sql")){
            ResourceDatabasePopulator migration=new ResourceDatabasePopulator(new FileSystemResource(path));migration.setSqlScriptEncoding("UTF-8");migration.execute(ds);
        }
        JdbcTemplate jdbc=new JdbcTemplate(ds);DataSourceTransactionManager manager=new DataSourceTransactionManager(ds);ObjectMapper json=new ObjectMapper();
        Map<String,Object> imported=new OpenEnglishWordNetImportService(jdbc,manager,json).importZip(zip);
        Map<String,Object> baseline=new WordLearningCardCoverageService(jdbc,manager,json).generate();
        Map<String,Object> enriched=new WordLearningCardEnrichmentService(jdbc,manager).generate();
        Map<String,Object> coverage=new WordLearningCardCoverageService(jdbc,manager,json).generate();
        System.out.println(json.writeValueAsString(imported));System.out.println(json.writeValueAsString(baseline));System.out.println(json.writeValueAsString(enriched));System.out.println(json.writeValueAsString(coverage));
        System.out.println(jdbc.queryForList("SELECT card_type,coverage_status,COUNT(*) AS words,SUM(published_count) AS cards FROM word_learning_card_type_coverage WHERE del_is=0 GROUP BY card_type,coverage_status ORDER BY card_type,coverage_status"));
        System.out.println(jdbc.queryForList("SELECT relation_status,COUNT(*) AS words FROM word_learning_card_coverage WHERE del_is=0 GROUP BY relation_status ORDER BY relation_status"));
    }
    private static void download(Path target)throws Exception{
        Files.createDirectories(target.getParent());if(Files.isRegularFile(target))return;Path partial=target.resolveSibling(target.getFileName()+".part");
        URLConnection connection=new URL(OpenEnglishWordNetImportService.SOURCE_URL).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(120000);connection.setRequestProperty("User-Agent","LetMindWander lexical importer/1.0");
        try(InputStream in=connection.getInputStream();OutputStream out=Files.newOutputStream(partial,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)if(n>0)out.write(buffer,0,n);}
        Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static String resolve(Object value){String s=String.valueOf(value);if(!s.startsWith("${")||!s.endsWith("}"))return s;String inner=s.substring(2,s.length()-1);int split=inner.indexOf(':');String name=split<0?inner:inner.substring(0,split),env=System.getenv(name);if(env!=null)return env;if(split>=0)return inner.substring(split+1);throw new IllegalStateException("Missing environment variable: "+name);}
}
