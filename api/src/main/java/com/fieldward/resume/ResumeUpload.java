package com.fieldward.resume;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public record ResumeUpload(String name, String contentType, String sha256, String text, byte[] bytes) {
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 30_000;
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    static {
        ZipSecureFile.setMaxEntrySize(16L * 1024 * 1024);
        ZipSecureFile.setMaxFileCount(256);
    }

    public static ResumeUpload read(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有内容的简历文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "简历文件不能超过 5 MB");
        }
        String name = safeName(file.getOriginalFilename());
        String extension = name.substring(Math.max(0, name.lastIndexOf('.'))).toLowerCase(Locale.ROOT);
        if (!extension.equals(".pdf") && !extension.equals(".docx")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请选择 PDF 或 Word（.docx）文件");
        }
        boolean pdf = extension.equals(".pdf");
        try {
            byte[] bytes = file.getBytes();
            String text = pdf ? readPdf(bytes) : readDocx(bytes);
            return new ResumeUpload(name, pdf ? MediaType.APPLICATION_PDF_VALUE : DOCX, hash(bytes),
                    limit(text.replace("\u0000", "").strip(), MAX_TEXT_LENGTH), bytes);
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf();
        } catch (IOException | IllegalArgumentException | POIXMLException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "无法打开文件，请重新保存为 PDF 或 Word（.docx）后上传");
        }
    }

    private static String readPdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IOException("Missing PDF signature");
        }
        try (var source = new RandomAccessReadBuffer(bytes);
             var document = Loader.loadPDF(source, IOUtils.createTempFileOnlyStreamCache())) {
            if (document.isEncrypted()) {
                throw encryptedPdf();
            }
            if (document.getNumberOfPages() == 0) {
                throw new IOException("Empty PDF");
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(30);
            return stripper.getText(document);
        }
    }

    private static ResponseStatusException encryptedPdf() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "请先移除 PDF 密码再上传");
    }

    private static String readDocx(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) {
            throw new IOException("Missing ZIP signature");
        }
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
             var extractor = new XWPFWordExtractor(document)) {
            if (!XWPFRelation.DOCUMENT.getContentType().equals(document.getPackagePart().getContentType())) {
                throw new IOException("Unsupported Word document type");
            }
            return extractor.getText();
        }
    }

    private static String safeName(String original) {
        String value = original == null ? "" : original.replace('\\', '/');
        String name = value.substring(value.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        int extension = name.lastIndexOf('.');
        if (extension >= 0) {
            return limit(name.substring(0, extension), 235) + name.substring(extension);
        }
        return name;
    }

    private static String limit(String text, int maxCodePoints) {
        int length = text.codePointCount(0, text.length());
        return length <= maxCodePoints ? text : text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
