package com.zhixing.books;

/** Aliyun NLS short-text TTS: each request is rounded up to 100 effective characters. */
public final class BookNlsBilling {
  private BookNlsBilling() {}

  public static int weight(int codePoint) {
    return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN ? 2 : 1;
  }

  public static long calls(String text) {
    long units = text.codePoints().map(BookNlsBilling::weight).asLongStream().sum();
    return (units + 99) / 100;
  }
}
