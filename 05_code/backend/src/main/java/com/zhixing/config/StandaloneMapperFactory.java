package com.zhixing.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Data access bootstrap for explicitly invoked maintenance tools and isolated mapper tests. No
 * application startup, migration, network call, or generated data is performed here.
 */
public final class StandaloneMapperFactory {
  private StandaloneMapperFactory() {}

  public static SqlSessionTemplate create(DataSource dataSource) {
    try {
      MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
      MybatisConfiguration configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      PersistenceMappingConfig.configureProjectionMappings(configuration);
      factory.setConfiguration(configuration);
      com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig =
          new com.baomidou.mybatisplus.core.config.GlobalConfig();
      globalConfig.setBanner(false);
      factory.setGlobalConfig(globalConfig);
      factory.setDataSource(dataSource);
      factory.setMapperLocations(
          new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
      return new SqlSessionTemplate(factory.getObject());
    } catch (Exception error) {
      throw new IllegalStateException("无法初始化维护工具 Mapper", error);
    }
  }
}
