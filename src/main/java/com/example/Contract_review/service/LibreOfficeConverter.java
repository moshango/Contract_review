package com.example.Contract_review.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LibreOfficeConverter {

    @Value("${libreoffice.soffice-path:soffice}")
    private String sofficePath;

    @Value("${libreoffice.convert-timeout-seconds:60}")
    private long convertTimeoutSeconds;

    @Value("${libreoffice.disable-fonts-embedding:true}")
    private boolean disableFontEmbedding;

    public byte[] convertDocToDocx(byte[] docBytes, String originalFilename) throws IOException {
        if (docBytes == null || docBytes.length == 0) {
            throw new IllegalArgumentException("待转换的文档内容为空");
        }

        String safeName = sanitizeFilename(originalFilename != null ? originalFilename : "document.doc");
        if (!safeName.toLowerCase().endsWith(".doc")) {
            safeName = safeName + ".doc";
        }

        Path tempDir = Files.createTempDirectory("doc-convert-");
        Path inputFile = tempDir.resolve(safeName);
        Files.write(inputFile, docBytes);

        List<String> command = new ArrayList<>();
        command.add(sofficePath);
        command.add("--headless");
        command.add("--nologo");
        command.add("--nofirststartwizard");
        command.add("--invisible");
        command.add("--convert-to");
        command.add("docx:MS Word 2007 XML");
        command.add("--outdir");
        command.add(tempDir.toAbsolutePath().toString());
        command.add(inputFile.toAbsolutePath().toString());

        log.info("使用LibreOffice转换DOC为DOCX: command={} timeout={}s", command, convertTimeoutSeconds);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(tempDir.toFile());
        Process process = null;
        String stdout = "";
        String stderr = "";
        try {
            process = builder.start();
            StreamCollector outCollector = new StreamCollector(process.getInputStream());
            StreamCollector errCollector = new StreamCollector(process.getErrorStream());
            outCollector.start();
            errCollector.start();

            boolean finished = process.waitFor(convertTimeoutSeconds, TimeUnit.SECONDS);
            stdout = outCollector.getContent();
            stderr = errCollector.getContent();

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice转换超时 (" + convertTimeoutSeconds + "s)");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("LibreOffice转换失败，退出码=" + exitCode + ", stderr=" + stderr);
            }

            String outputName = safeName.substring(0, safeName.lastIndexOf('.')) + ".docx";
            Path outputFile = tempDir.resolve(outputName);
            if (!Files.exists(outputFile)) {
                throw new IOException("未找到转换后的DOCX文件: " + outputFile);
            }

            byte[] converted = Files.readAllBytes(outputFile);
            log.info("LibreOffice转换完成，输出大小={} 字节", converted.length);
            if (!stdout.isEmpty()) {
                log.debug("LibreOffice stdout: {}", stdout);
            }
            if (!stderr.isEmpty()) {
                log.debug("LibreOffice stderr: {}", stderr);
            }
            return converted;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice转换被中断", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteDirectoryQuietly(tempDir);
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("删除临时目录失败: {}", e.getMessage());
        }
    }

    private static class StreamCollector extends Thread {
        private final java.io.InputStream stream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamCollector(java.io.InputStream stream) {
            this.stream = stream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.write(line.getBytes(StandardCharsets.UTF_8));
                    buffer.write('\n');
                }
            } catch (IOException ignored) {
            }
        }

        String getContent() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
