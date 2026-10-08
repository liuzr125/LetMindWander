package com.zhixing.mapper;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Connection metadata used by persistence startup policies, without querying business data. */
@Component
public class DatabaseDialect {
  private final DataSource datasource;

  public DatabaseDialect(DataSource datasource) {
    this.datasource = datasource;
  }

  public boolean isH2() {
    try (Connection connection = datasource.getConnection()) {
      return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    } catch (SQLException exception) {
      return false;
    }
  }
}
