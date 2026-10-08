package com.zhixing.config.typehandler;

import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.jdbc.support.JdbcUtils;

/** Preserve scalar Map values and binary bindings when replacing JdbcTemplate projections. */
public class JdbcProjectionTypeHandler extends BaseTypeHandler<Object> {
  @Override
  public void setNonNullParameter(
      PreparedStatement statement, int index, Object value, JdbcType jdbcType) throws SQLException {
    // This Object projection handler also receives enum bindings via the registry's parent-type
    // lookup.
    // Resolve the persisted scalar first so JDBC never serializes a Java enum into a VARCHAR
    // column.
    if (value instanceof com.baomidou.mybatisplus.annotation.IEnum<?>) {
      value = ((com.baomidou.mybatisplus.annotation.IEnum<?>) value).getValue();
    } else if (value instanceof Enum<?>) {
      value = ((Enum<?>) value).name();
    }
    if (value instanceof byte[]) statement.setBytes(index, (byte[]) value);
    else if (jdbcType == null) statement.setObject(index, value);
    else statement.setObject(index, value, jdbcType.TYPE_CODE);
  }

  @Override
  public Object getNullableResult(ResultSet result, String column) throws SQLException {
    return JdbcUtils.getResultSetValue(result, result.findColumn(column));
  }

  @Override
  public Object getNullableResult(ResultSet result, int column) throws SQLException {
    return JdbcUtils.getResultSetValue(result, column);
  }

  @Override
  public Object getNullableResult(CallableStatement statement, int column) throws SQLException {
    Object value = statement.getObject(column);
    if (value instanceof Clob) {
      Clob clob = (Clob) value;
      return clob.getSubString(1, (int) clob.length());
    }
    if (value instanceof Blob) {
      Blob blob = (Blob) value;
      return blob.getBytes(1, (int) blob.length());
    }
    return value;
  }
}
