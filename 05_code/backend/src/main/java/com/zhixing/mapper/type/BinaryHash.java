package com.zhixing.mapper.type;

/** Binary digest parameter held as a bean to avoid primitive-array collection expansion. */
public final class BinaryHash {
  private final byte[] bytes;

  public BinaryHash(byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }
}
