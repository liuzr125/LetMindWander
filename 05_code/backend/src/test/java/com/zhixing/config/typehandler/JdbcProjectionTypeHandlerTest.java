package com.zhixing.config.typehandler;

import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.zhixing.entity.DailyTaskStatus;
import java.sql.PreparedStatement;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class JdbcProjectionTypeHandlerTest {
  private enum OrdinaryState {
    ACTIVE
  }

  private enum NumericState implements IEnum<Integer> {
    READY;

    public Integer getValue() {
      return 7;
    }
  }

  @Test
  void enumBindingsUsePersistedScalarsAndKeepBinaryBinding() throws Exception {
    PreparedStatement statement = mock(PreparedStatement.class);
    JdbcProjectionTypeHandler handler = new JdbcProjectionTypeHandler();
    handler.setNonNullParameter(statement, 1, DailyTaskStatus.CANCELLED, null);
    handler.setNonNullParameter(statement, 2, OrdinaryState.ACTIVE, JdbcType.VARCHAR);
    handler.setNonNullParameter(statement, 3, NumericState.READY, null);
    byte[] bytes = {1, 2};
    handler.setNonNullParameter(statement, 4, bytes, JdbcType.VARBINARY);
    verify(statement).setObject(1, "CANCELLED");
    verify(statement).setObject(2, "ACTIVE", JdbcType.VARCHAR.TYPE_CODE);
    verify(statement).setObject(3, 7);
    verify(statement).setBytes(4, bytes);
    verifyNoMoreInteractions(statement);
  }
}
