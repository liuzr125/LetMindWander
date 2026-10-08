package com.zhixing.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.zhixing.config.typehandler.JdbcProjectionTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.springframework.context.annotation.Bean;

/** Preserve existing JDBC Map contracts while all persistence uses MyBatis-Plus. */
@org.springframework.context.annotation.Configuration
public class PersistenceMappingConfig {
  public static void configureProjectionMappings(Configuration configuration) {
    configuration.setCallSettersOnNulls(true);
    configuration.setObjectFactory(new JdbcProjectionObjectFactory());
    JdbcProjectionTypeHandler handler = new JdbcProjectionTypeHandler();
    configuration.getTypeHandlerRegistry().register(Object.class, handler);
    configuration.getTypeHandlerRegistry().register(JdbcType.CLOB, handler);
    configuration.getTypeHandlerRegistry().register(JdbcType.BLOB, handler);
  }

  @Bean
  public ConfigurationCustomizer preserveNullMapFields() {
    return PersistenceMappingConfig::configureProjectionMappings;
  }
}
