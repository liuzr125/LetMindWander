package com.zhixing.config;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.springframework.util.LinkedCaseInsensitiveMap;

/**
 * Retain JDBC column labels while allowing the same case-insensitive lookups as ColumnMapRowMapper.
 */
public class JdbcProjectionObjectFactory extends DefaultObjectFactory {
  @Override
  @SuppressWarnings("unchecked")
  public <T> T create(
      Class<T> type, List<Class<?>> constructorTypes, List<Object> constructorValues) {
    if (type == Map.class) return (T) new LinkedCaseInsensitiveMap<Object>();
    return super.create(type, constructorTypes, constructorValues);
  }
}
