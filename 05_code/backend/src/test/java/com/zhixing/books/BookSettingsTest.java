package com.zhixing.books;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.*;
import com.zhixing.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class BookSettingsTest {
  AppParameterService parameters;
  EnglishBookRepository persistence;
  BookSettings settings;
  Map<String, String> values;

  @BeforeEach
  void setup() {
    values = new HashMap<>();
    parameters = mock(AppParameterService.class);
    persistence = mock(EnglishBookRepository.class);
    when(parameters.optionalStored(anyString(), nullable(String.class)))
        .thenAnswer(c -> values.getOrDefault(c.getArgument(0), c.getArgument(1)));
    when(parameters.intValue(anyString(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            c ->
                Integer.parseInt(
                    values.getOrDefault(
                        c.getArgument(0), String.valueOf((Integer) c.getArgument(1)))));
    doAnswer(
            c -> {
              values.put(c.getArgument(0), c.getArgument(1));
              return null;
            })
        .when(parameters)
        .saveValue(anyString(), anyString(), anyString());
    when(persistence.bookMigrationReady()).thenReturn(true);
    AliyunTtsService tts = mock(AliyunTtsService.class);
    MediaService media = mock(MediaService.class);
    when(tts.configured()).thenReturn(true);
    when(media.bookStorageReady()).thenReturn(true);
    settings = new BookSettings(parameters, persistence, tts, media, mock(AdminAuditMapper.class));
    ReflectionTestUtils.setField(settings, "catalogDefault", "https://gutendex.com");
    ReflectionTestUtils.setField(settings, "licensedDefault", "");
    ReflectionTestUtils.setField(settings, "textDefault", "");
    ReflectionTestUtils.setField(settings, "endpointDefault", "");
    ReflectionTestUtils.setField(settings, "modelDefault", "");
    ReflectionTestUtils.setField(settings, "budgetDefault", "30");
  }

  BookSettings.Request request() {
    BookSettings.Request r = new BookSettings.Request();
    r.revision = 0;
    r.workerEnabled = true;
    r.catalogUrl = "https://gutendex.com";
    r.licensedCatalogUrl = "";
    r.textUrlTemplate = "";
    r.imageEndpoint = "https://images.example/v1/images/generations";
    r.imageModel = "example-model";
    r.maxJobCost = "30";
    r.imageUnitPrice = "0.2";
    r.ttsUnitPrice = "100";
    return r;
  }

  @Test
  void writesEncryptedSecretsWithoutReturningThemAndRuntimeValuesUpdateImmediately() {
    BookSettings.Request r = request();
    r.imageApiKey = "private-example-key";
    Map<String, Object> saved = settings.save(r, "admin");
    assertEquals(true, saved.get("workerEnabled"));
    assertEquals(1, saved.get("revision"));
    assertFalse(saved.containsKey("imageApiKey"));
    assertFalse(saved.toString().contains(r.imageApiKey));
    verify(parameters).saveSecret("BOOK_IMAGE_API_KEY", r.imageApiKey);
    assertEquals("true", BookSettings.value(parameters, "books.worker.enabled", "false"));
  }

  @Test
  void unsafeEndpointIsRejectedBeforeAnyParameterWrite() {
    BookSettings.Request r = request();
    r.imageEndpoint = "http://127.0.0.1/private";
    assertThrows(ApiException.class, () -> settings.save(r, "admin"));
    verify(parameters, never()).saveValue(anyString(), anyString(), anyString());
  }

  @Test
  void changingHostsRequiresNewCredentials() {
    values.put("books.image.endpoint", "https://old.example/v1/images");
    when(parameters.configured("BOOK_IMAGE_API_KEY")).thenReturn(true);
    assertThrows(ApiException.class, () -> settings.save(request(), "admin"));
    verify(parameters, never()).saveSecret(anyString(), anyString());
  }

  @Test
  void emptyOptionalEndpointOverridesFileDefaultWithoutErasingSecret() {
    BookSettings.Request r = request();
    r.catalogUrl = "";
    settings.save(r, "admin");
    assertEquals("", settings.view().get("catalogUrl"));
    verify(parameters).saveSecret("BOOK_IMAGE_API_KEY", null);
  }

  @Test
  void staleSettingsVersionCannotOverwriteNewConfiguration() {
    values.put("books.settings.version", "2");
    assertThrows(ApiException.class, () -> settings.save(request(), "admin"));
    verify(parameters, never()).saveSecret(anyString(), anyString());
  }

  @Test
  void missingMigrationBlocksEnablingWorker() {
    when(persistence.bookMigrationReady()).thenReturn(false);
    assertThrows(ApiException.class, () -> settings.save(request(), "admin"));
    verify(parameters, never()).saveValue(anyString(), anyString(), anyString());
  }

  @Test
  void activeGenerationBlocksChangingProviders() {
    when(persistence.activeBookJobs()).thenReturn(1);
    assertThrows(ApiException.class, () -> settings.save(request(), "admin"));
  }

  @Test
  void negativePricingCannotEnablePaidGeneration() {
    BookSettings.Request r = request();
    r.ttsUnitPrice = "-1";
    assertThrows(ApiException.class, () -> settings.save(r, "admin"));
    verify(parameters, never()).saveValue(anyString(), anyString(), anyString());
  }
}
