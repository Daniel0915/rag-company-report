package com.ismsp.chatbot.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismsp.chatbot.chunking.PolicyDocChunker;
import com.ismsp.chatbot.dto.UploadResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * 문서 등록 + 메타데이터 태깅 + 해시 기반 신규/변경/중복 판별 업서트.
 * TypeScript 버전(src/modules/isms-p/step1_ingest_company_doc.ts, src/vectorstore/store.ts)의
 * VectorStore + JSON 사이드카 패턴을 Spring AI SimpleVectorStore로 포팅한 것.
 */
@Service
public class CompanyDocIndexService {

    private record DocRecord(String hash, List<String> chunkIds) {
    }

    private final SimpleVectorStore vectorStore;
    private final PolicyDocChunker chunker = new PolicyDocChunker();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File sidecarFile;
    private final File vectorStoreFile;
    private final Map<String, DocRecord> sidecar;

    public CompanyDocIndexService(
            SimpleVectorStore companyDocVectorStore,
            @Value("${isms-p.index-file:data/isms-p-company-docs.json}") String sidecarPath,
            @Value("${isms-p.vector-store-file:data/isms-p-vectorstore.json}") String vectorStorePath
    ) {
        this.vectorStore = companyDocVectorStore;
        this.sidecarFile = new File(sidecarPath);
        this.vectorStoreFile = new File(vectorStorePath);
        this.sidecarFile.getParentFile().mkdirs();
        this.vectorStoreFile.getParentFile().mkdirs();
        this.sidecar = loadSidecar();
    }

    public synchronized UploadResult indexDocument(
            File file, String filename, String docType, String domain, String year
    ) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String hash = hashOf(bytes, docType, domain, year);
        DocRecord existing = sidecar.get(filename);
        if (existing != null && existing.hash().equals(hash)) {
            return new UploadResult("skipped", filename, 0);
        }

        List<Document> pages = new PagePdfDocumentReader(new FileSystemResource(file)).get();
        Map<String, Object> tagMeta = Map.of(
                "source_file", filename,
                "doc_type", docType,
                "domain", domain,
                "year", year,
                "file_hash", hash
        );
        List<Document> tagged = pages.stream()
                .map(p -> {
                    Map<String, Object> merged = new HashMap<>(p.getMetadata());
                    merged.putAll(tagMeta);
                    return new Document(p.getText(), merged);
                })
                .toList();

        List<Document> chunks = chunker.chunk(tagged);

        // SimpleVectorStore doesn't implement delete(Filter.Expression) (throws
        // UnsupportedOperationException) — only delete(List<String> ids) works, so we
        // track each source file's chunk IDs ourselves, same as the TS version's
        // chunks.json sidecar tracks chunk records per source_file.
        if (existing != null) {
            vectorStore.delete(existing.chunkIds());
        }
        vectorStore.add(chunks);
        vectorStore.save(vectorStoreFile);

        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        sidecar.put(filename, new DocRecord(hash, chunkIds));
        persistSidecar();

        return new UploadResult(existing != null ? "updated" : "new", filename, chunks.size());
    }

    public synchronized void deleteAll() {
        List<String> allIds = sidecar.values().stream().flatMap(r -> r.chunkIds().stream()).toList();
        if (!allIds.isEmpty()) {
            vectorStore.delete(allIds);
        }
        sidecar.clear();
        persistSidecar();
        vectorStore.save(vectorStoreFile);
    }

    private String hashOf(byte[] content, String... extra) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(content);
            for (String e : extra) {
                if (e != null) digest.update(e.getBytes());
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, DocRecord> loadSidecar() {
        if (!sidecarFile.exists()) return new HashMap<>();
        try {
            return objectMapper.readValue(sidecarFile, new TypeReference<HashMap<String, DocRecord>>() {
            });
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void persistSidecar() {
        try {
            sidecarFile.getParentFile().mkdirs();
            objectMapper.writeValue(sidecarFile, sidecar);
        } catch (IOException e) {
            throw new IllegalStateException("사이드카 저장 실패", e);
        }
    }
}
