package com.zhixing.books;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.service.AppParameterService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class BookImportSourceTest {
  @Test
  void epubPreservesSpineOrderAndEmbeddedIllustrations() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put(
        "META-INF/container.xml",
        "<container><rootfiles><rootfile full-path='OPS/package.opf'/></rootfiles></container>");
    files.put(
        "OPS/package.opf",
        "<package><manifest><item id='a' href='one.xhtml'/><item id='b' href='two.xhtml'/></manifest><spine><itemref idref='b'/><itemref idref='a'/></spine></package>");
    files.put(
        "OPS/one.xhtml",
        "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.1//EN' 'http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd'><html xmlns='http://www.w3.org/1999/xhtml'><body><h1>First</h1><p>First text.</p></body></html>");
    files.put(
        "OPS/two.xhtml",
        "<html><body><h1>Second</h1><p>Second text.</p><img src='image.png' alt='A fox'/></body></html>");
    files.put("OPS/image.png", "test-image");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, String> e : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    BookImporter.Result result = new BookImporter().read("book.epub", bytes.toByteArray());
    assertEquals("Second", result.chapters.get(0).title);
    assertEquals("First", result.chapters.get(1).title);
    assertEquals(1, result.images.size());
    assertEquals("A fox", result.chapters.get(0).pictures.get(0).caption);
    assertEquals(
        result.chapters.get(0).paragraphs.get(0).id,
        result.chapters.get(0).pictures.get(0).paragraphId);
  }

  @Test
  void rejectsOversizedAndEmptyAndUnsupportedImports() {
    BookImporter importer = new BookImporter();
    assertThrows(ApiException.class, () -> importer.read("book.pdf", new byte[5]));
    assertThrows(ApiException.class, () -> importer.read("book.txt", new byte[0]));
    assertThrows(ApiException.class, () -> importer.read("book.txt", new byte[5000001]));
  }

  @Test
  void licensedSourcesComeFirstAndPublicCatalogFailureDoesNotHideThem() throws Exception {
    BookRemote remote = mock(BookRemote.class);
    AppParameterService parameters = mock(AppParameterService.class);
    BookSources sources = new BookSources(remote, parameters);
    ReflectionTestUtils.setField(sources, "licensedCatalog", "https://partner.example");
    ReflectionTestUtils.setField(sources, "catalog", "https://catalog.example");
    ReflectionTestUtils.setField(sources, "textTemplate", "");
    ObjectMapper json = new ObjectMapper();
    when(remote.getJson(contains("partner.example"), isNull()))
        .thenReturn(
            json.readTree(
                "{\"results\":[{\"id\":7,\"title\":\"Licensed Fox\",\"sourceUrl\":\"javascript:alert(1)\"}]}"));
    when(remote.getJson(contains("catalog.example")))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "UNAVAILABLE", "offline"));
    BookSources.SearchResult result = sources.search("fox");
    assertEquals("licensed", result.items.get(0).get("provider"));
    assertEquals("", result.items.get(0).get("sourceUrl"));
    assertEquals(1, result.warnings.size());
  }

  @Test
  void publicDomainMetadataIsNotAnAutomaticLicenseConfirmation() throws Exception {
    BookRemote remote = mock(BookRemote.class);
    BookSources sources = new BookSources(remote, mock(AppParameterService.class));
    ReflectionTestUtils.setField(sources, "licensedCatalog", "");
    ReflectionTestUtils.setField(sources, "catalog", "https://catalog.example");
    ReflectionTestUtils.setField(sources, "textTemplate", "");
    when(remote.getJson(anyString()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"results\":[{\"id\":11,\"title\":\"Alice\",\"copyright\":false,\"authors\":[]}]}"));
    assertEquals("pending_review", sources.search("Alice").items.get(0).get("rightsStatus"));
    assertThrows(ApiException.class, () -> sources.collect(11));
  }

  @Test
  void littlePrinceSearchDoesNotPresentLittlePrincessAsTheRequestedBook() throws Exception {
    BookRemote remote = mock(BookRemote.class);
    BookSources sources = new BookSources(remote, mock(AppParameterService.class));
    ReflectionTestUtils.setField(sources, "licensedCatalog", "");
    ReflectionTestUtils.setField(sources, "catalog", "https://catalog.example");
    ReflectionTestUtils.setField(sources, "textTemplate", "");
    when(remote.getJson(anyString()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"results\":[{\"id\":146,\"title\":\"A Little Princess\",\"authors\":[]},{\"id\":7,\"title\":\"The Little Lame Prince\",\"authors\":[]}]}"));
    BookSources.SearchResult result = sources.search("小王子");
    assertTrue(result.items.isEmpty());
    assertEquals(2, result.relatedItems.size());
  }
}
