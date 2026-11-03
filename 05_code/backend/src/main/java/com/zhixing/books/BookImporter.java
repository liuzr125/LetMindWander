package com.zhixing.books;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

/** EPUB spine order and embedded pictures are preserved. XML never resolves external entities. */
@Service
public class BookImporter {
  public static class Result {
    public List<BookDocument.Chapter> chapters;
    public Map<String, byte[]> images = new LinkedHashMap<>();
  }

  public Result read(String filename, byte[] bytes) {
    if (bytes.length > 5000000) throw BookText.bad("导入文件不能超过 5MB");
    Result result = new Result();
    if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".epub")) return epub(bytes);
    if (filename == null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(txt|md)$"))
      throw BookText.bad("首版支持 UTF-8 TXT、Markdown 和 EPUB 文件");
    result.chapters = BookText.parse(new String(bytes, StandardCharsets.UTF_8));
    return result;
  }

  private Document xml(byte[] bytes) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    if (new String(bytes, StandardCharsets.UTF_8).contains("<!ENTITY"))
      throw BookText.bad("EPUB 不允许自定义 XML 实体");
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    f.setExpandEntityReferences(false);
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  private String resolve(String base, String relative) {
    String out =
        java.net
            .URI
            .create("https://epub.invalid/" + base)
            .resolve(relative)
            .normalize()
            .getPath()
            .substring(1);
    if (out.contains("..") || relative.contains(":") || relative.startsWith("/"))
      throw BookText.bad("EPUB 路径无效");
    return out;
  }

  private Result epub(byte[] data) {
    try {
      Map<String, byte[]> files = new HashMap<>();
      int total = 0, count = 0;
      try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
        ZipEntry entry;
        byte[] buffer = new byte[8192];
        while ((entry = zip.getNextEntry()) != null) {
          if (entry.isDirectory()) continue;
          if (++count > 4000 || entry.getName().contains("..") || entry.getName().startsWith("/"))
            throw BookText.bad("EPUB 文件结构无效");
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          int n;
          while ((n = zip.read(buffer)) != -1) {
            total += n;
            if (total > 30000000 || out.size() + n > 5000000) throw BookText.bad("EPUB 解压内容超过限制");
            out.write(buffer, 0, n);
          }
          if (files.put(entry.getName(), out.toByteArray()) != null)
            throw BookText.bad("EPUB 包含重复文件");
        }
      }
      Document container = xml(files.get("META-INF/container.xml"));
      String packagePath =
          ((Element) container.getElementsByTagNameNS("*", "rootfile").item(0))
              .getAttribute("full-path");
      Document pack = xml(files.get(packagePath));
      Map<String, String> manifest = new HashMap<>();
      NodeList items = pack.getElementsByTagNameNS("*", "item");
      for (int i = 0; i < items.getLength(); i++) {
        Element e = (Element) items.item(i);
        manifest.put(e.getAttribute("id"), resolve(packagePath, e.getAttribute("href")));
      }
      NodeList spine = pack.getElementsByTagNameNS("*", "itemref");
      Result result = new Result();
      result.chapters = new ArrayList<>();
      for (int i = 0; i < spine.getLength(); i++) {
        Element ref = (Element) spine.item(i);
        if ("no".equals(ref.getAttribute("linear"))) continue;
        String path = manifest.get(ref.getAttribute("idref"));
        Document page = xml(files.get(path));
        BookDocument.Chapter chapter = new BookDocument.Chapter();
        chapter.title = "第 " + (result.chapters.size() + 1) + " 节（需核对目录）";
        walk(page.getDocumentElement(), chapter, path, files, result);
        if (!chapter.paragraphs.isEmpty()) result.chapters.add(chapter);
      }
      if (result.chapters.isEmpty() || result.chapters.size() > 1000)
        throw BookText.bad("EPUB 没有有效正文，可能已加密或不受支持");
      return result;
    } catch (com.zhixing.common.ApiException e) {
      throw e;
    } catch (Exception e) {
      throw BookText.bad("EPUB 解析失败：需要未加密、结构有效的 EPUB；也可导入 TXT／Markdown");
    }
  }

  private void walk(
      Node node,
      BookDocument.Chapter chapter,
      String path,
      Map<String, byte[]> files,
      Result result) {
    if (node.getNodeType() != Node.ELEMENT_NODE) return;
    Element el = (Element) node;
    String tag = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
    if (Arrays.asList("script", "style", "nav", "head").contains(tag)) return;
    if (tag.matches("h[1-3]")) {
      chapter.title = el.getTextContent().trim();
      return;
    }
    if ("img".equals(tag)) {
      String key = resolve(path, el.getAttribute("src"));
      byte[] bytes = files.get(key);
      if (bytes != null) {
        result.images.put(key, bytes);
        BookDocument.Picture picture = new BookDocument.Picture();
        picture.assetId = key;
        picture.source = "original";
        picture.caption = el.getAttribute("alt");
        picture.paragraphId =
            chapter.paragraphs.isEmpty()
                ? ""
                : chapter.paragraphs.get(chapter.paragraphs.size() - 1).id;
        chapter.pictures.add(picture);
      }
      return;
    }
    if (Arrays.asList("p", "blockquote", "li").contains(tag)) {
      String text = el.getTextContent().trim().replaceAll("\\s+", " ");
      if (!text.isEmpty()) {
        BookDocument.Paragraph p = new BookDocument.Paragraph();
        p.text = text;
        chapter.paragraphs.add(p);
      }
      NodeList embedded = el.getElementsByTagNameNS("*", "img");
      for (int i = 0; i < embedded.getLength(); i++)
        walk(embedded.item(i), chapter, path, files, result);
      return;
    }
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++)
      walk(children.item(i), chapter, path, files, result);
  }
}
