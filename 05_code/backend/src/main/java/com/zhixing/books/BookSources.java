package com.zhixing.books;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixing.common.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookSources {
  private final BookRemote remote;
  private final com.zhixing.service.AppParameterService parameters;

  @Value("${app.books.licensed-catalog-url:}")
  private String licensedCatalog;

  @Value("${app.books.catalog-url:https://gutendex.com}")
  private String catalog;

  @Value("${app.books.text-url-template:}")
  private String textTemplate;

  public BookSources(BookRemote remote, com.zhixing.service.AppParameterService parameters) {
    this.remote = remote;
    this.parameters = parameters;
  }

  private String licensedRoot() {
    return BookSettings.value(parameters, "books.licensed_catalog.url", licensedCatalog);
  }

  private String catalogRoot() {
    return BookSettings.value(parameters, "books.catalog.url", catalog);
  }

  private String downloadTemplate() {
    return BookSettings.value(parameters, "books.source.text_url_template", textTemplate);
  }

  public String configurationFingerprint() {
    return BookText.hash(licensedRoot() + "\n" + catalogRoot() + "\n" + downloadTemplate());
  }

  public static class SearchResult {
    public List<Map<String, Object>> items = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<Map<String, Object>> relatedItems = new ArrayList<>();
    public String matchedQuery;
  }

  public SearchResult search(String term) {
    if (term == null || term.trim().isEmpty() || term.length() > 200)
      throw BookText.bad("请输入 1—200 字符的书名或作者");
    Map<String, String> aliases = new HashMap<>();
    aliases.put("小王子", "The Little Prince");
    aliases.put("爱丽丝梦游仙境", "Alice's Adventures in Wonderland");
    aliases.put("傲慢与偏见", "Pride and Prejudice");
    String query = aliases.getOrDefault(term.trim(), term.trim()), encoded;
    try {
      encoded = URLEncoder.encode(query, "UTF-8");
    } catch (Exception e) {
      throw BookText.bad("书名编码错误");
    }
    SearchResult out = new SearchResult();
    int succeeded = 0;
    if (!licensedRoot().isEmpty()) {
      try {
        String token =
            parameters.configured("BOOK_SOURCE_API_KEY")
                ? parameters.required("BOOK_SOURCE_API_KEY")
                : null;
        JsonNode licensed =
            remote.getJson(
                licensedRoot().replaceAll("/+$", "") + "/books?language=en&search=" + encoded,
                token);
        if (!licensed.path("results").isArray()) throw BookText.bad("合作书源返回格式错误");
        for (JsonNode b : licensed.path("results")) {
          if (b.path("id").asInt() < 1 || b.path("title").asText().isEmpty()) continue;
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("sourceId", b.path("id").asInt());
          item.put("provider", "licensed");
          for (String field :
              Arrays.asList("title", "author", "edition", "licenseNote", "rightsScope"))
            item.put(field, b.path(field).asText());
          item.put("sourceUrl", safeLink(b.path("sourceUrl").asText()));
          item.put("rightsStatus", "pending_review");
          item.put("downloadConfigured", true);
          out.items.add(item);
        }
        succeeded++;
      } catch (ApiException e) {
        out.warnings.add("合作正版书源暂不可用，请检查连接或授权配置");
      }
    }
    if (!catalogRoot().isEmpty()) {
      try {
        JsonNode result =
            remote.getJson(
                catalogRoot().replaceAll("/+$", "") + "/books/?languages=en&search=" + encoded);
        if (!result.path("results").isArray()) throw BookText.bad("公版书目返回格式错误");
        for (JsonNode b : result.path("results")) {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("sourceId", b.path("id").asInt());
          item.put("provider", "gutenberg");
          item.put("title", b.path("title").asText());
          List<String> authors = new ArrayList<>();
          for (JsonNode a : b.path("authors")) authors.add(a.path("name").asText());
          item.put("author", String.join("; ", authors));
          item.put("translators", b.path("translators"));
          item.put("sourceUrl", "https://www.gutenberg.org/ebooks/" + b.path("id").asInt());
          item.put("rightsStatus", "pending_review");
          item.put("copyrightInUs", b.path("copyright"));
          item.put("downloadConfigured", !downloadTemplate().isEmpty());
          out.items.add(item);
        }
        succeeded++;
      } catch (ApiException e) {
        out.warnings.add("公版书目服务暂不可用，请稍后重试");
      }
    }
    if (succeeded == 0)
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "BOOK_SEARCH_UNAVAILABLE", "书目服务均不可用，请稍后重试或导入授权全文");
    out.matchedQuery = query;
    java.util.regex.Pattern requested =
        java.util.regex.Pattern.compile(
            "(?:^| )"
                + java.util.regex.Pattern.quote(normalize(query).replaceFirst("^the ", ""))
                + "(?: |$)");
    java.util.Iterator<Map<String, Object>> it = out.items.iterator();
    while (it.hasNext()) {
      Map<String, Object> item = it.next();
      String title = normalize(String.valueOf(item.get("title"))),
          author = normalize(String.valueOf(item.get("author")));
      if (!requested.matcher(title).find() && !requested.matcher(author).find()) {
        out.relatedItems.add(item);
        it.remove();
      }
    }
    return out;
  }

  private String normalize(String value) {
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private String safeLink(String link) {
    try {
      java.net.URI uri = new java.net.URI(link);
      return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
          ? link
          : "";
    } catch (Exception e) {
      return "";
    }
  }

  public List<BookDocument.Chapter> collect(int sourceId) {
    if (sourceId < 1) throw BookText.bad("书源编号错误");
    if (downloadTemplate().isEmpty() || !downloadTemplate().contains("{id}"))
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "BOOK_SOURCE_NOT_CONFIGURED",
          "请配置允许自动下载的书源 URL 模板，或导入授权全文；不自动抓取 Gutenberg 主站");
    String url = downloadTemplate().replace("{id}", String.valueOf(sourceId));
    String host;
    try {
      host = new java.net.URI(url).getHost();
    } catch (Exception e) {
      throw BookText.bad("书源模板无效");
    }
    if ("gutenberg.org".equals(host) || "www.gutenberg.org".equals(host))
      throw BookText.bad("请使用书源允许的下载镜像或授权渠道，不自动抓取主站");
    return BookText.parse(new String(remote.get(url, 8000000), StandardCharsets.UTF_8));
  }

  public BookImporter.Result collect(String provider, int sourceId) {
    if (!"licensed".equals(provider)) {
      BookImporter.Result result = new BookImporter.Result();
      result.chapters = collect(sourceId);
      return result;
    }
    if (licensedRoot().isEmpty() || sourceId < 1) throw BookText.bad("合作书源未配置或编号错误");
    String token =
        parameters.configured("BOOK_SOURCE_API_KEY")
            ? parameters.required("BOOK_SOURCE_API_KEY")
            : null;
    JsonNode result =
        remote.getJson(licensedRoot().replaceAll("/+$", "") + "/books/" + sourceId, token);
    BookImporter.Result book = new BookImporter.Result();
    book.chapters = BookText.parse(result.path("text").asText());
    int count = 0;
    for (JsonNode image : result.path("images")) {
      if (++count > 200) throw BookText.bad("合作书源图片超过单次收录上限");
      int index = image.path("chapterIndex").asInt(-1);
      if (index < 0 || index >= book.chapters.size()) throw BookText.bad("合作书源图片章节关联错误");
      String encoded = image.path("base64").asText();
      if (encoded.length() > 6500000) throw BookText.bad("合作书源图片过大");
      byte[] bytes;
      try {
        bytes = Base64.getDecoder().decode(encoded);
      } catch (Exception e) {
        throw BookText.bad("合作书源图片编码错误");
      }
      String key = "partner:" + count;
      book.images.put(key, bytes);
      BookDocument.Picture p = new BookDocument.Picture();
      p.assetId = key;
      p.source = "original";
      p.caption = image.path("caption").asText();
      p.licenseNote = image.path("licenseNote").asText();
      int paragraph = image.path("paragraphIndex").asInt(-1);
      if (paragraph >= 0 && paragraph < book.chapters.get(index).paragraphs.size())
        p.paragraphId = book.chapters.get(index).paragraphs.get(paragraph).id;
      book.chapters.get(index).pictures.add(p);
    }
    return book;
  }

  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("licensedCatalogConfigured", !licensedRoot().isEmpty());
    out.put("catalogConfigured", !catalogRoot().isEmpty());
    out.put("downloadConfigured", !downloadTemplate().isEmpty());
    return out;
  }
}
