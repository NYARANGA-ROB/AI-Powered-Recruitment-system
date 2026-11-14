package com.smartaps.module.resume.service;

import com.smartaps.common.exception.BusinessException;
import com.smartaps.common.result.ResultCode;
import com.smartaps.infrastructure.storage.FileStorageService;
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

/**
 * Resume content extraction service
 * Extracts plain text from PDF/DOC/DOCX
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeContentExtractor {

    private final FileStorageService fileStorageService;

    public String extractText(String objectName, String fileType) {
        log.info("Start extracting stored file content: fileType={}", fileType);
        try (InputStream inputStream = fileStorageService.openFile(objectName)) {
            String text = switch (fileType) {
                case "application/pdf" -> extractFromPDF(inputStream);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        -> extractFromDOCX(inputStream);
                case "application/msword" -> extractFromDOC(inputStream);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "Unsupported file type");
            };

            log.info("File content extraction complete: fileType={}, textLength={}", fileType, text.length());

            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("File content extraction failed: fileType={}", fileType, e);
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