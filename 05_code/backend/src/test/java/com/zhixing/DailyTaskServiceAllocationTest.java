package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.entity.DailyPackageEntity;
import com.zhixing.entity.DailyTaskEntity;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.mapper.DailyPackageMapper;
import com.zhixing.mapper.DailyTaskMapper;
import com.zhixing.mapper.LearningPlanMapper;
import com.zhixing.mapper.MaterialMapper;
import com.zhixing.model.MaterialCandidate;
import com.zhixing.service.DailyTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyTaskServiceAllocationTest {
    @Test
    @SuppressWarnings("unchecked")
    void followsPrdPriorityUnderACompactBudget() {
        MaterialMapper materials = mock(MaterialMapper.class);
        when(materials.selectContent(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> "tech".equals(invocation.getArgument(2))
                        ? candidates("tech", 2, 180) : candidates("word", 10, 30));
        when(materials.selectDueReviews(anyString(), any(LocalDate.class), anyInt()))
                .thenReturn(candidates("review", 5, 30));
        when(materials.selectActions(anyString(), any(LocalDate.class), anyInt()))
                .thenReturn(Collections.<MaterialCandidate>emptyList());

        DailyTaskService service = new DailyTaskService(mock(DailyPackageMapper.class),
                mock(DailyTaskMapper.class), mock(LearningPlanMapper.class), materials, new ObjectMapper());
        DailyPackageEntity dailyPackage = new DailyPackageEntity();
        dailyPackage.setId("package");
        dailyPackage.setOwnerId("owner");
        dailyPackage.setBusinessDate(LocalDate.of(2026, 9, 7));
        dailyPackage.setBudgetSeconds(300);
        LearningPlanEntity plan = new LearningPlanEntity();
        plan.setId("plan");
        plan.setDifficulty("intro");
        plan.setTechCount(2);
        plan.setNewWordCount(10);
        plan.setJournalEnabled(1);
        plan.setReviewEnabled(1);
        plan.setReviewLimit(5);

        List<DailyTaskEntity> tasks = (List<DailyTaskEntity>) ReflectionTestUtils.invokeMethod(
                service, "generate", dailyPackage, plan);

        assertThat(tasks).extracting(DailyTaskEntity::getTaskType)
                .containsExactly("journal", "review", "review", "review", "review", "review", "word");
        assertThat(tasks.stream().mapToInt(DailyTaskEntity::getEstimatedSeconds).sum()).isEqualTo(300);
    }

    private static List<MaterialCandidate> candidates(String prefix, int count, int seconds) {
        List<MaterialCandidate> result = new ArrayList<MaterialCandidate>();
        for (int i = 1; i <= count; i++) {
            MaterialCandidate candidate = new MaterialCandidate();
            candidate.setId(prefix + i);
            candidate.setVersionId(prefix + "Version" + i);
            candidate.setTitle(prefix + i);
            candidate.setEstimatedSeconds(seconds);
            result.add(candidate);
        }
        return result;
    }
}
