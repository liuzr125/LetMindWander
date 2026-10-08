package com.zhixing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhixing.controller.AdminWordStudyController;
import com.zhixing.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WordStudyPerformanceTest {
  @Test
  void twentyEventRowsFetchRoundsInOneBatchInsteadOfTwentyQueries() {
    List<Map<String, Object>> events = new ArrayList<>();
    for (int n = 0; n < 20; n++) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", "event" + n);
      events.add(row);
    }
    Map<String, Object> round = new LinkedHashMap<>();
    round.put("event_id", "event0");
    round.put("id", "round1");
    round.put("round_no", 1);
    round.put("book_name", "book");
    AdminWordStudyMapper mapper = mock(AdminWordStudyMapper.class);
    when(mapper.countEvents(null)).thenReturn(20);
    when(mapper.selectEvents(null, 0, 20)).thenReturn(events);
    when(mapper.selectEventRounds(anyList())).thenReturn(Collections.singletonList(round));
    AdminWordStudyService service =
        new AdminWordStudyService(
            mapper,
            mock(WordStudyModeMapper.class),
            mock(AdminAuditMapper.class),
            mock(WordStudyPreferenceService.class));
    AdminWordStudyController controller = new AdminWordStudyController(service);
    Map<String, Object> result = controller.events(null, 1, 20);
    assertEquals(20, result.get("total"));
    assertEquals(1, ((List<?>) events.get(0).get("rounds")).size());
    assertTrue(((List<?>) events.get(1).get("rounds")).isEmpty());
    verify(mapper, times(1)).countEvents(null);
    verify(mapper, times(1)).selectEvents(null, 0, 20);
    verify(mapper, times(1)).selectEventRounds(anyList());
    verifyNoMoreInteractions(mapper);
    assertFalse(round.containsKey("event_id"));
  }
}
