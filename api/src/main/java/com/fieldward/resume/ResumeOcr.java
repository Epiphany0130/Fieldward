package com.fieldward.resume;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fieldward.mentor.ocr.enabled", havingValue = "true")
class ResumeOcr {
    private static final Logger log = LoggerFactory.getLogger(ResumeOcr.class);
    private static final int MAX_PAGES = 10;
    private static final int MAX_TEXT = 30_000;
    private final ResumeOcrMapper documents;
    private final String command;
    private final String languages;

    ResumeOcr(ResumeOcrMapper documents, @Value("${fieldward.mentor.ocr.command}") String command,
              @Value("${fieldward.mentor.ocr.languages}") String languages) {
        this.documents = documents;
        this.command = command;
        this.languages = languages;
    }

    @Scheduled(initialDelay = 4_000, fixedDelay = 4_000)
    void run() {
        var job = documents.claim();
        if (job == null) return;
        try {
            var input = documents.input(job);
            if (input == null) {
                documents.settle(job, "EMPTY", null);
                return;
            }
            String text = extract(input.bytes());
            documents.settle(job, text.isBlank() ? "EMPTY" : "READY", text.isBlank() ? null : text);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            log.warn("Document OCR attempt {} failed ({})", job.attempt(), exception.getClass().getSimpleName());
            documents.settle(job, job.attempt() >= 3 ? "FAILED" : "RETRY", null);
        }
    }

    private String extract(byte[] bytes) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("fieldward-ocr-");
        Path image = directory.resolve("page.png");
        Path output = directory.resolve("text.txt");
        var text = new StringBuilder();
        try (var source = new RandomAccessReadBuffer(bytes);
             var document = Loader.loadPDF(source, IOUtils.createTempFileOnlyStreamCache())) {
            var renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            for (int i = 0; i < pages && text.length() < MAX_TEXT; i++) {
                var bounds = document.getPage(i).getCropBox();
                float dimension = Math.max(bounds.getWidth(), bounds.getHeight());
                if (!Float.isFinite(dimension) || dimension <= 0) continue;
                float scale = Math.min(2.8f, 1800f / dimension);
                BufferedImage rendered = renderer.renderImage(i, scale, ImageType.GRAY);
                try {
                    ImageIO.write(rendered, "png", image.toFile());
                } finally {
                    rendered.flush();
                }
                Process process = new ProcessBuilder(command, image.toString(), "stdout", "-l", languages, "--psm", "3")
                        .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                try {
                    if (!process.waitFor(12, TimeUnit.SECONDS) || process.exitValue() != 0) {
                        throw new IOException("OCR did not finish successfully");
                    }
                } finally {
                    if (process.isAlive()) process.destroyForcibly();
                }
                String page = Files.readString(output).replace("\u0000", "").strip();
                if (!page.isBlank()) text.append(page).append("\n\n");
            }
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(image);
            Files.deleteIfExists(directory);
        }
        int end = Math.min(text.length(), MAX_TEXT);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end).strip();
    }
}
