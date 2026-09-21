package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Splits article text before NLS's 300-character limit and remuxes the MP3 parts as one playable asset. */
final class ArticleAudioComposer {
    private static final int SEGMENT_LIMIT = 280;
    private static final int MAX_SEGMENTS = 24;

    private ArticleAudioComposer() {}

    static List<String> split(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_TEXT_EMPTY", "朗读文本为空");
        List<String> parts = new ArrayList<String>();
        while (!normalized.isEmpty()) {
            int codePoints = normalized.codePointCount(0, normalized.length());
            if (codePoints <= SEGMENT_LIMIT) { parts.add(normalized); break; }
            int limit = normalized.offsetByCodePoints(0, SEGMENT_LIMIT);
            int sentenceEnd = -1, wordEnd = -1;
            for (int i = 1; i <= limit; i++) {
                if (Character.isWhitespace(normalized.charAt(i - 1))) wordEnd = i - 1;
                if (i < normalized.length() && Character.isWhitespace(normalized.charAt(i)) &&
                        ".!?;:。！？；：".indexOf(normalized.charAt(i - 1)) >= 0) sentenceEnd = i;
            }
            int end = sentenceEnd >= limit / 2 ? sentenceEnd : wordEnd >= limit / 2 ? wordEnd : limit;
            parts.add(normalized.substring(0, end).trim());
            normalized = normalized.substring(end).trim();
            if (parts.size() >= MAX_SEGMENTS && !normalized.isEmpty())
                throw new ApiException(HttpStatus.BAD_REQUEST, "TTS_TEXT_TOO_LONG", "短文过长，分段数量超过安全上限");
        }
        return parts;
    }

    static void requireFfmpeg() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("ffmpeg timed out"); }
            process.getInputStream().close();
            if (process.exitValue() != 0) throw new IOException("ffmpeg unavailable");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_AUDIO_MERGER_UNAVAILABLE", "长短文音频需要在后端安装 ffmpeg；尚未调用语音合成服务");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_AUDIO_MERGER_UNAVAILABLE", "音频合并准备中断；尚未调用语音合成服务");
        }
    }

    static byte[] merge(List<byte[]> parts) {
        Path folder = null;
        try {
            folder = Files.createTempDirectory("article-tts-");
            StringBuilder playlist = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                String name = String.format("%03d.mp3", i);
                Files.write(folder.resolve(name), parts.get(i));
                playlist.append("file '").append(name).append("'\n");
            }
            Files.write(folder.resolve("list.txt"), playlist.toString().getBytes(StandardCharsets.UTF_8));
            Process process = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-f", "concat", "-safe", "1",
                    "-i", "list.txt", "-map", "0:a:0", "-c:a", "copy", "output.mp3")
                    .directory(folder.toFile()).redirectErrorStream(true)
                    .redirectOutput(folder.resolve("ffmpeg.log").toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("ffmpeg timed out"); }
            Path output = folder.resolve("output.mp3");
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0)
                throw new IOException("ffmpeg could not merge audio");
            return Files.readAllBytes(output);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TTS_AUDIO_MERGE_FAILED", "分段合成已完成但音频合并失败，未发布音频；请检查 ffmpeg 与音频格式");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TTS_AUDIO_MERGE_FAILED", "音频合并中断，未发布音频");
        } finally {
            if (folder != null) {
                for (int i = 0; i < parts.size(); i++) delete(folder.resolve(String.format("%03d.mp3", i)));
                delete(folder.resolve("list.txt")); delete(folder.resolve("ffmpeg.log")); delete(folder.resolve("output.mp3")); delete(folder);
            }
        }
    }

    private static void delete(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { /* OS temp cleanup */ } }
}
