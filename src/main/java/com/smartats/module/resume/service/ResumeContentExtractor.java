package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;

/**
 * Resume content extraction service
 * Extracts plain text from PDF/DOC/DOCX
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeContentExtractor {

    /**
     * Extract text content from a file URL
     */
    public String extractText(String fileUrl, String fileType) {
        log.info("Start extracting file content: fileUrl={}, fileType={}", fileUrl, fileType);

        try {
            URL url = new URL(fileUrl);
            InputStream inputStream = url.openStream();

            String text = switch (fileType) {
                case "application/pdf" -> extractFromPDF(inputStream);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        -> extractFromDOCX(inputStream);
                case "application/msword" -> extractFromDOC(inputStream);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "Unsupported file type");
            };

            inputStream.close();

            log.info("File content extraction complete: fileType={}, textLength={}, first 200 chars=[{}]",
                    fileType, text.length(),
                    text.trim().substring(0, Math.min(200, text.trim().length())));

            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("File content extraction failed: fileUrl={}", fileUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "File parsing failed");
        }
    }

    private String extractFromPDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractFromDOCX(InputStream inputStream) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
        }
        return text.toString();
    }

    private String extractFromDOC(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}