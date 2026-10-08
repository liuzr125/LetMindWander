package com.zhixing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionTemplate;

/** Real bound Mapper statements against each test's isolated datasource. */
public final class MapperTestSupport {
  private MapperTestSupport() {}

  public static <T> T mapper(DataSource datasource, Class<T> type) {
    try {
      MybatisConfiguration config = new MybatisConfiguration();
      config.setMapUnderscoreToCamelCase(true);
      com.zhixing.config.PersistenceMappingConfig.configureProjectionMappings(config);
      config.addMapper(type);
      MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
      factory.setDataSource(datasource);
      factory.setConfiguration(config);
      factory.setMapperLocations(
          new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
              .getResources("classpath*:mapper/*.xml"));
      return new SqlSessionTemplate(factory.getObject()).getMapper(type);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
