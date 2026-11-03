package com.zhixing.books;

import com.zhixing.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import org.springframework.http.HttpStatus;

public final class BookText {
  private BookText() {}

  public static String hash(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : bytes) out.append(String.format("%02x", b & 255));
      return out.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static ApiException bad(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "BOOK_INVALID", message);
  }
  /** Split at code-point-safe sentence/word boundaries, without truncating long chapters. */
  public static List<String> split(String value) {
    String text = value.trim().replaceAll("\\s+", " ");
    List<String> result = new ArrayList<>();
    while (!text.isEmpty()) {
      if (text.codePoints().map(BookNlsBilling::weight).sum() <= 280) {
        result.add(text);
        break;
      }
      int end = 0, units = 0;
      while (end < text.length()) {
        int cp = text.codePointAt(end), weight = BookNlsBilling.weight(cp);
        if (units + weight > 280) break;
        units += weight;
        end += Character.charCount(cp);
      }
      int sentence = -1, word = -1;
      for (int i = 1; i < end; i++) {
        if (Character.isWhitespace(text.charAt(i))) {
          word = i;
          if (".!?;:".indexOf(text.charAt(i - 1)) >= 0) sentence = i;
        }
      }
      end = sentence > end / 2 ? sentence : word > end / 2 ? word : end;
      result.add(text.substring(0, end).trim());
      text = text.substring(end).trim();
    }
    return result;
  }

  public static List<BookDocument.Chapter> parse(String input) {
    if (input == null || input.length() > 4000000) throw bad("正文为空或超过 400 万字符");
    String text = input.replace("\r\n", "\n").replace('\r', '\n');
    // Gutenberg headers/footers contain licensing metadata, not spoken story text.
    Matcher start =
        Pattern.compile(
                "(?im)^\\*\\*\\* START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\\*\\*\\*[^\\n]*\\n")
            .matcher(text);
    if (start.find()) text = text.substring(start.end());
    Matcher finish =
        Pattern.compile("(?im)^\\*\\*\\* END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK")
            .matcher(text);
    if (finish.find()) text = text.substring(0, finish.start());
    List<BookDocument.Chapter> chapters = new ArrayList<>();
    BookDocument.Chapter current = null;
    for (String block : text.split("\\n\\s*\\n")) {
      String trimmed = block.trim();
      if (trimmed.isEmpty()) continue;
      boolean heading =
          trimmed.matches("(?is)^#{1,3} .{1,160}$")
              || trimmed.matches(
                  "(?is)^(?:chapter|book|part)\\s+(?:[0-9IVXLCDM]+|one|two|three|four|five|six|seven|eight|nine|ten)(?:[.: \\n].{0,130})?$");
      if (current == null || heading) {
        current = new BookDocument.Chapter();
        current.title = heading ? trimmed.replaceFirst("^#+\\s*", "") : "正文（需核对目录）";
        chapters.add(current);
        if (heading) continue;
      }
      BookDocument.Paragraph p = new BookDocument.Paragraph();
      p.text = trimmed.replaceAll("[ \\t]*\\n[ \\t]*", " ");
      current.paragraphs.add(p);
    }
    chapters.removeIf(c -> c.paragraphs.isEmpty());
    if (chapters.isEmpty() || chapters.size() > 1000) throw bad("未解析到有效正文，或章节数量超过 1000");
    return chapters;
  }
}
