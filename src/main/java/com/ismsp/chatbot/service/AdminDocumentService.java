package com.ismsp.chatbot.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.AdminDocumentDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 어드민 페이지에서 업로드한 PDF를 텍스트 추출 → 청킹 → 태깅해서 벡터스토어(Neo4j)에 색인한다.
 * DART 공시 색인(CompanyReportIndexService)과 동일한 메타데이터 키(corp_code, report_nm,
 * rcept_no, section_title 등)를 사용해서 CompanyChatService가 그대로 근거로 쓸 수 있게 한다.
 */
@Service
public class AdminDocumentService {

    private record AdminDocumentRecord(
            String id,
            String corpCode,
            String corpName,
            String title,
            String category,
            String description,
            String docDate,
            String fileName,
            String storedFileName,
            String uploadedAt,
            List<String> chunkIds
    ) {
    }

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File registryFile;
    private final File uploadDir;
    private final Map<String, AdminDocumentRecord> registry;

    public AdminDocumentService(
            VectorStore vectorStore,
            @Value("${admin-document.registry-file:data/admin-documents.json}") String registryPath,
            @Value("${admin-document.upload-dir:data/admin-uploads}") String uploadDirPath
    ) {
        this.vectorStore = vectorStore;
        this.registryFile = new File(registryPath);
        this.registryFile.getParentFile().mkdirs();
        this.uploadDir = new File(uploadDirPath);
        this.uploadDir.mkdirs();
        this.registry = loadRegistry();
    }

    public synchronized List<AdminDocumentDto> listDocuments() {
        return registry.values().stream()
                .sorted(Comparator.comparing(AdminDocumentRecord::uploadedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    public synchronized AdminDocumentDto uploadDocument(
            MultipartFile file,
            String corpCode,
            String title,
            String category,
            String description,
            String docDate
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다");
        }
        WatchedCompany company = WatchedCompany.ALL.stream()
                .filter(c -> c.corpCode().equals(corpCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("워치리스트에 없는 기업입니다: " + corpCode));

        String id = UUID.randomUUID().toString();
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "document.pdf";
        String storedFileName = id + "_" + originalFilename;
        String effectiveTitle = StringUtils.hasText(title) ? title : originalFilename;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽을 수 없습니다", e);
        }

        try {
            Files.write(new File(uploadDir, storedFileName).toPath(), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }

        Resource pdfResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        };
        List<Document> pages = new PagePdfDocumentReader(pdfResource).get();

        Map<String, Object> tagMeta = new HashMap<>();
        tagMeta.put("doc_type", "ADMIN_PDF");
        tagMeta.put("admin_doc_id", id);
        tagMeta.put("corp_name", company.name());
        tagMeta.put("corp_code", company.corpCode());
        tagMeta.put("stock_code", company.stockCode());
        tagMeta.put("report_nm", effectiveTitle);
        tagMeta.put("rcept_no", "ADMIN-" + id.substring(0, 8));
        tagMeta.put("section_title", StringUtils.hasText(category) ? category : effectiveTitle);
        if (StringUtils.hasText(docDate)) tagMeta.put("rcept_dt", docDate);
        if (StringUtils.hasText(description)) tagMeta.put("description", description);
        tagMeta.put("source_file", originalFilename);

        List<Document> tagged = pages.stream()
                .map(d -> {
                    Map<String, Object> merged = new HashMap<>(d.getMetadata());
                    merged.putAll(tagMeta);
                    return new Document(d.getText(), merged);
                })
                .toList();
        List<Document> chunks = splitter.apply(tagged);
        vectorStore.add(chunks);

        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        AdminDocumentRecord record = new AdminDocumentRecord(
                id, company.corpCode(), company.name(), effectiveTitle, category, description, docDate,
                originalFilename, storedFileName, LocalDateTime.now().toString(), chunkIds
        );
        registry.put(id, record);
        persistRegistry();

        return toDto(record);
    }

    public synchronized void deleteDocument(String id) {
        AdminDocumentRecord record = registry.remove(id);
        if (record == null) {
            throw new NoSuchElementException("문서를 찾을 수 없습니다: " + id);
        }
        if (!record.chunkIds().isEmpty()) {
            vectorStore.delete(record.chunkIds());
        }
        new File(uploadDir, record.storedFileName()).delete();
        persistRegistry();
    }

    private AdminDocumentDto toDto(AdminDocumentRecord r) {
        return new AdminDocumentDto(
                r.id(), r.corpCode(), r.corpName(), r.title(), r.category(),
                r.description(), r.docDate(), r.fileName(), r.uploadedAt(), r.chunkIds().size()
        );
    }

    private Map<String, AdminDocumentRecord> loadRegistry() {
        if (!registryFile.exists()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(registryFile, new TypeReference<LinkedHashMap<String, AdminDocumentRecord>>() {
            });
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private void persistRegistry() {
        try {
            objectMapper.writeValue(registryFile, registry);
        } catch (IOException e) {
            throw new IllegalStateException("문서 등록 정보 저장 실패", e);
        }
    }
}
