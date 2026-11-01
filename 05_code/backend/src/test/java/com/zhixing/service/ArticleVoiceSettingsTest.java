package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.entity.AppParameterEntity;
import com.zhixing.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class ArticleVoiceSettingsTest {
  @Test
  void defaultsToLucaAndSavesIndependentNonSecretParameterAcrossServiceInstances() {
    AppParameterMapper mapper = mock(AppParameterMapper.class);
    Map<String, AppParameterEntity> rows = new HashMap<>();
    when(mapper.selectActiveByKey(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
    when(mapper.selectByKeyIncludingDeleted(anyString()))
        .thenAnswer(call -> rows.get(call.getArgument(0)));
    when(mapper.insert(any(AppParameterEntity.class)))
        .thenAnswer(
            call -> {
              AppParameterEntity row = call.getArgument(0);
              rows.put(row.getParamKey(), row);
              return 1;
            });
    AppParameterService parameters =
        new AppParameterService(
            mapper, new AppProperties(), mock(AppParameterPersistenceMapper.class));
    AliyunTtsService service = tts(parameters);
    assertThat(service.articleVoiceSettings().get("voice")).isEqualTo("luca");
    service.saveArticleVoice(" Luna ");
    AppParameterEntity saved = rows.get("tts.aliyun.article_voice");
    assertThat(saved.getParamValue()).isEqualTo("luna");
    assertThat(saved.getIsSecret()).isZero();
    assertThat(saved.getState()).isEqualTo("active");
    assertThat(saved.getDelIs()).isZero();
    assertThat(rows).hasSize(1).doesNotContainKey("tts.aliyun.voice");
    assertThat(tts(parameters).articleVoiceSettings().get("voice")).isEqualTo("luna");
    service.saveArticleVoice("Luca");
    assertThat(saved.getParamValue()).isEqualTo("luca");
    assertThat(saved.getVersionNo()).isEqualTo(2);
  }

  @Test
  void rejectsUnknownOrEmptyVoiceBeforeAnyWriteAndReturnsOnlySafeSettings() {
    AppParameterService parameters = mock(AppParameterService.class);
    when(parameters.optionalStored(anyString(), anyString()))
        .thenAnswer(call -> call.getArgument(1));
    AliyunTtsService service = tts(parameters);
    for (String value : Arrays.asList(null, "", "unknown", "https://example.com", "Luca_v3.1")) {
      assertThatThrownBy(() -> service.saveArticleVoice(value)).isInstanceOf(ApiException.class);
    }
    verify(parameters, never()).saveValue(anyString(), anyString(), anyString());
    Map<String, Object> settings = service.articleVoiceSettings();
    assertThat(settings.keySet()).containsExactly("voice", "voices");
    assertThat((List<?>) settings.get("voices")).isNotEmpty();
    verify(parameters, never()).required(anyString());
  }

  private AliyunTtsService tts(AppParameterService parameters) {
    return new AliyunTtsService(
        mock(AliyunTtsPersistenceMapper.class),
        parameters,
        mock(MediaService.class),
        mock(RestTemplate.class),
        new AppProperties());
  }
}
