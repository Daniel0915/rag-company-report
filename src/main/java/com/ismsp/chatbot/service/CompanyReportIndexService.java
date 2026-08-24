package com.ismsp.chatbot.service;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismsp.chatbot.dart.DartApiClient;
import com.ismsp.chatbot.dart.DartXmlDocumentReader;
import com.ismsp.chatbot.dart.dto.DartListResponse;
import com.ismsp.chatbot.dart.dto.DisclosureItem;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.IndexResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 워치리스트 기업(WatchedCompany.ALL)의 정기공시(사업/반기/분기보고서)와 지분공시
 * (대량보유상황보고서, 임원ㆍ주요주주소유보고서 등)를 DART에서 받아 목차 단위
 * (DartXmlDocumentReader)로 쪼갠 뒤 벡터스토어(Neo4j)에 색인한다.
 * rcept_no+파일명 단위 해시로 이미 색인된 문서는 재다운로드/재색인하지 않는다
 * (DART API 일일 호출 제한 때문에 같은 문서를 반복해서 받지 않도록).
 */
@Service
public class CompanyReportIndexService {

    private record DocRecord(String hash, List<String> chunkIds) {
    }

    private final DartApiClient dartApiClient;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File sidecarFile;
    private final Map<String, DocRecord> sidecar;

    public CompanyReportIndexService(
            DartApiClient dartApiClient,
            VectorStore vectorStore,
            @Value("${company-report.index-file:data/company-report-index.json}") String sidecarPath
    ) {
        this.dartApiClient = dartApiClient;
        this.vectorStore = vectorStore;
        this.sidecarFile = new File(sidecarPath);
        this.sidecarFile.getParentFile().mkdirs();
        this.sidecar = loadSidecar();
    }

    /**
     * 색인 대상 공시유형(pblntf_ty). A=정기공시(사업/반기/분기보고서),
     * D=지분공시(주식등의대량보유상황보고서, 임원ㆍ주요주주특정증권등소유상황보고서 등).
     */
    private static final List<String> DISCLOSURE_TYPES = List.of("A", "D");
    private static final int PAGE_SIZE = 100;

    /** 워치리스트 전체 기업의 기간 내 정기공시+지분공시를 색인한다. */
    public synchronized List<IndexResult> indexWatchedCompanies(LocalDate bgnDe, LocalDate endDe) {
        List<IndexResult> results = new ArrayList<>();
        for (WatchedCompany company : WatchedCompany.ALL) {
            results.addAll(indexCompany(company, bgnDe, endDe));
        }
        return results;
    }

    private List<IndexResult> indexCompany(WatchedCompany company, LocalDate bgnDe, LocalDate endDe) {
        List<IndexResult> results = new ArrayList<>();
        for (String pblntfTy : DISCLOSURE_TYPES) {
            for (DisclosureItem item : searchAllPages(company.corpCode(), bgnDe, endDe, pblntfTy)) {
                results.add(indexDisclosure(company, item, pblntfTy));
            }
        }
        return results;
    }

    /** total_page까지 전부 순회해서 기간 내 해당 공시유형의 모든 공시를 모은다. */
    private List<DisclosureItem> searchAllPages(String corpCode, LocalDate bgnDe, LocalDate endDe, String pblntfTy) {
        List<DisclosureItem> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            DartListResponse response = dartApiClient.searchDisclosures(corpCode, bgnDe, endDe, pblntfTy, pageNo, PAGE_SIZE);
            if (!response.hasData()) {
                break;
            }
            all.addAll(response.list());
            int totalPage = response.totalPage() != null ? response.totalPage() : 1;
            if (pageNo >= totalPage) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private IndexResult indexDisclosure(WatchedCompany company, DisclosureItem item, String pblntfTy) {
        Map<String, byte[]> files = dartApiClient.fetchDocument(item.rceptNo());

        int totalChunks = 0;
        boolean anyNew = false;
        boolean anyUpdated = false;

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String sourceFile = entry.getKey();
            byte[] xmlBytes = entry.getValue();
            String sidecarKey = item.rceptNo() + "/" + sourceFile;
            String hash = hashOf(xmlBytes);

            DocRecord existing = sidecar.get(sidecarKey);
            if (existing != null && existing.hash().equals(hash)) {
                continue;
            }

            List<Document> sections = new DartXmlDocumentReader(xmlBytes, sourceFile).get();
            Map<String, Object> tagMeta = Map.of(
                    "corp_name", company.name(),
                    "corp_code", company.corpCode(),
                    "stock_code", company.stockCode(),
                    "rcept_no", item.rceptNo(),
                    "report_nm", item.reportNm(),
                    "rcept_dt", item.rceptDt(),
                    "pblntf_ty", pblntfTy
            );
            List<Document> tagged = sections.stream()
                    .map(d -> {
                        Map<String, Object> merged = new HashMap<>(d.getMetadata());
                        merged.putAll(tagMeta);
                        return new Document(d.getText(), merged);
                    })
                    .toList();
            List<Document> chunks = splitter.apply(tagged);

            if (existing != null) {
                vectorStore.delete(existing.chunkIds());
                anyUpdated = true;
            } else {
                anyNew = true;
            }
            vectorStore.add(chunks);

            List<String> chunkIds = chunks.stream().map(Document::getId).toList();
            sidecar.put(sidecarKey, new DocRecord(hash, chunkIds));
            totalChunks += chunks.size();
        }

        if (totalChunks > 0) {
            persistSidecar();
        }

        String status = anyNew ? "new" : (anyUpdated ? "updated" : "skipped");
        return new IndexResult(company.name(), item.rceptNo(), item.reportNm(), status, totalChunks);
    }

    private String hashOf(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest(content)) sb.append(String.format("%02x", b));
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
            objectMapper.writeValue(sidecarFile, sidecar);
        } catch (IOException e) {
            throw new IllegalStateException("색인 사이드카 저장 실패", e);
        }
    }
}
