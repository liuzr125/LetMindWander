package com.zhixing.service;

import com.zhixing.controller.AdminWordStudyController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WordStudyPerformanceTest {
 @Test void twentyEventRowsFetchRoundsInOneBatchInsteadOfTwentyQueries(){
  List<Map<String,Object>> events=new ArrayList<>();
  for(int n=0;n<20;n++){Map<String,Object> row=new LinkedHashMap<>();row.put("id","event"+n);events.add(row);}
  Map<String,Object> round=new LinkedHashMap<>();round.put("event_id","event0");round.put("id","round1");round.put("round_no",1);round.put("book_name","book");
  JdbcTemplate jdbc=mock(JdbcTemplate.class,invocation->{
   String name=invocation.getMethod().getName();
   if(name.equals("queryForObject"))return 20;
   if(name.equals("queryForList"))return String.valueOf((Object)invocation.getArgument(0)).startsWith("SELECT e.*")?events:Collections.singletonList(round);
   return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
  });
  AdminWordStudyController controller=new AdminWordStudyController(jdbc,mock(WordStudyPreferenceService.class));
  Map<String,Object> result=controller.events(null,1,20);
  assertEquals(20,result.get("total"));assertEquals(1,((List<?>)events.get(0).get("rounds")).size());assertTrue(((List<?>)events.get(1).get("rounds")).isEmpty());
  assertEquals(2,mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("queryForList")).count());
  assertEquals(1,mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("queryForObject")).count());
  assertFalse(round.containsKey("event_id"));
 }
}
