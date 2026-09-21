package com.zhixing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleAudioComposerTest {
    @Test void shortArticleRemainsOneRequest() {
        assertThat(ArticleAudioComposer.split("  Hello,   world!  ")).containsExactly("Hello, world!");
    }

    @Test void longArticlePreservesTextAndKeepsEachRequestBelowProviderLimit() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 20; i++) source.append("The child reads quietly. Another sentence follows! 你好，世界。 ");
        String text = source.toString();
        List<String> parts = ArticleAudioComposer.split(text);
        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts).allSatisfy(part -> assertThat(part.codePointCount(0, part.length())).isLessThanOrEqualTo(280));
        assertThat(String.join(" ", parts).replaceAll("\\s+", ""))
                .isEqualTo(text.replaceAll("\\s+", ""));
    }

    @Test void longUnbrokenTextSplitsWithoutLosingUnicodeCodePoints() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 650; i++) source.append("📖");
        String text = source.toString();
        List<String> parts = ArticleAudioComposer.split(text);
        assertThat(parts).hasSize(3);
        assertThat(String.join("", parts)).isEqualTo(text);
        assertThat(parts).allSatisfy(part -> assertThat(part.codePointCount(0, part.length())).isLessThanOrEqualTo(280));
    }

    @Test void mergedMp3ContainsAudioFromBothSegments() throws Exception {
        boolean installed;
        try { ArticleAudioComposer.requireFfmpeg(); installed = true; }
        catch (RuntimeException missing) { installed = false; }
        Assumptions.assumeTrue(installed, "ffmpeg is not installed in this test environment");
        Path folder = Files.createTempDirectory("article-tts-test-");
        try {
            Path first = folder.resolve("first.mp3"), second = folder.resolve("second.mp3");
            generateTone(first, "440");
            generateTone(second, "880");
            byte[] combined = ArticleAudioComposer.merge(Arrays.asList(Files.readAllBytes(first), Files.readAllBytes(second)));
            Path output = folder.resolve("combined.mp3");
            Files.write(output, combined);
            Process probe = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-i", output.toString(),
                    "-f", "null", "-").redirectErrorStream(true).start();
            assertThat(probe.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.exitValue()).isZero();
            assertThat((long) combined.length).isGreaterThan(Files.size(first));
        } finally {
            Files.deleteIfExists(folder.resolve("first.mp3"));
            Files.deleteIfExists(folder.resolve("second.mp3"));
            Files.deleteIfExists(folder.resolve("combined.mp3"));
            Files.deleteIfExists(folder);
        }
    }

    private static void generateTone(Path output, String frequency) throws Exception {
        Process process = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi",
                "-i", "sine=frequency=" + frequency + ":duration=0.2", "-c:a", "libmp3lame",
                output.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }
}
