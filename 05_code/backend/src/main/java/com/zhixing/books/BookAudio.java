package com.zhixing.books;

/** Read MPEG Layer III frame durations without re-encoding or assuming constant bit rate. */
public final class BookAudio {
  private BookAudio() {}

  public static double duration(byte[] data) {
    int offset = 0, frames = 0;
    double seconds = 0;
    if (data.length > 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
      offset =
          10
              + ((data[6] & 127) << 21)
              + ((data[7] & 127) << 14)
              + ((data[8] & 127) << 7)
              + (data[9] & 127);
      if ((data[5] & 16) != 0) offset += 10;
    }
    int[] high = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    int[] low = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
    int[] rates = {44100, 48000, 32000};
    while (offset + 4 <= data.length) {
      int b1 = data[offset] & 255, b2 = data[offset + 1] & 255, b3 = data[offset + 2] & 255;
      if (b1 != 255 || (b2 & 224) != 224 || (b2 & 6) != 2) {
        offset++;
        continue;
      }
      int version = (b2 >> 3) & 3, ri = (b3 >> 2) & 3, bi = b3 >> 4;
      if (version == 1 || ri == 3 || bi == 0 || bi == 15) {
        offset++;
        continue;
      }
      int rate = rates[ri] / (version == 3 ? 1 : version == 2 ? 2 : 4),
          bitrate = (version == 3 ? high : low)[bi] * 1000;
      int length = (version == 3 ? 144 : 72) * bitrate / rate + ((b3 >> 1) & 1);
      if (length < 4 || offset + length > data.length) break;
      seconds += (version == 3 ? 1152.0 : 576.0) / rate;
      frames++;
      offset += length;
    }
    if (frames < 2 || seconds <= 0) throw BookText.bad("音频不包含有效 MP3 帧，不能发布");
    return seconds;
  }
}
