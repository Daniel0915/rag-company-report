package com.ismsp.chatbot.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.IndexedChunkDto;
import com.ismsp.chatbot.dto.IndexedChunkPage;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

/**
 * 어드민 페이지에서 Neo4j에 실제로 색인된 청크를 훑어보기 위한 읽기 전용 뷰어.
 * VectorStore 인터페이스는 유사도 검색만 지원하므로, 저장된 데이터를 있는 그대로
 * 브라우징하려면 Neo4j 드라이버로 청크 노드를 직접 조회해야 한다.
 * 기업마다 라벨이 분리돼 있어서(CompanyVectorStoreRegistry), 특정 기업이 지정되면
 * 그 라벨만 조회하고 "전체"면 워치리스트의 모든 라벨을 조회해 애플리케이션에서 합친다.
 */
@Service
public class IndexedChunkService {

    private final Driver driver;

    public IndexedChunkService(Driver driver) {
        this.driver = driver;
    }

    public IndexedChunkPage listChunks(String corpCode, String sourceType, int limit, int offset) {
        List<String> corpCodes = (corpCode == null || corpCode.isBlank())
                ? WatchedCompany.ALL.stream().map(WatchedCompany::corpCode).toList()
                : List.of(corpCode);
        String typeCondition = typeCondition(sourceType);

        long total = 0;
        for (String code : corpCodes) {
            total += countLabel(CompanyVectorStoreRegistry.labelFor(code), typeCondition);
        }

        List<IndexedChunkDto> items;
        if (corpCodes.size() == 1) {
            items = queryLabel(CompanyVectorStoreRegistry.labelFor(corpCodes.get(0)), typeCondition, offset, limit);
        } else {
            // 여러 라벨(전체 기업)을 합쳐야 하므로, 각 라벨에서 필요한 만큼(offset+limit)
            // 최신순으로 후보를 뽑은 뒤 애플리케이션에서 다시 정렬해 페이지를 잘라낸다.
            List<IndexedChunkDto> merged = new ArrayList<>();
            for (String code : corpCodes) {
                merged.addAll(queryLabel(CompanyVectorStoreRegistry.labelFor(code), typeCondition, 0, offset + limit));
            }
            items = merged.stream()
                    .sorted(Comparator.comparing((IndexedChunkDto d) -> d.rceptDt() == null ? "" : d.rceptDt()).reversed())
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }
        return new IndexedChunkPage(items, total);
    }

    private String typeCondition(String sourceType) {
        // pblntf_ty 태깅을 추가하기 전에 이미 색인된 정기공시 청크는 이 필드가 아예 없다.
        // "정기공시"로 필터링할 때 그런 레거시 청크가 누락되지 않도록, doc_type도 없는
        // (=관리자 PDF가 아닌) 청크는 정기공시로 간주해서 같이 포함한다.
        return switch (sourceType == null ? "" : sourceType) {
            case "A" -> "(n.`metadata.pblntf_ty` = 'A' OR (n.`metadata.pblntf_ty` IS NULL AND n.`metadata.doc_type` IS NULL))";
            case "D" -> "n.`metadata.pblntf_ty` = 'D'";
            case "ADMIN_PDF" -> "n.`metadata.doc_type` = 'ADMIN_PDF'";
            case "NEWS" -> "n.`metadata.doc_type` = 'NEWS'";
            default -> "true";
        };
    }

    private List<IndexedChunkDto> queryLabel(String label, String typeCondition, int skip, int limit) {
        String query = "MATCH (n:`" + label + "`) WHERE " + typeCondition + " " + """
                RETURN n.id AS id,
                       n.`metadata.corp_code` AS corpCode,
                       n.`metadata.corp_name` AS corpName,
                       n.`metadata.report_nm` AS reportNm,
                       n.`metadata.rcept_no` AS rceptNo,
                       n.`metadata.pblntf_ty` AS pblntfTy,
                       n.`metadata.doc_type` AS docType,
                       n.`metadata.section_title` AS sectionTitle,
                       n.`metadata.rcept_dt` AS rceptDt,
                       left(n.text, 200) AS textPreview
                ORDER BY coalesce(n.`metadata.rcept_dt`, '') DESC, coalesce(n.`metadata.section_order`, 0) ASC
                SKIP $skip LIMIT $limit
                """;
        try (Session session = driver.session()) {
            return session.run(query, Map.of("skip", skip, "limit", limit)).list(record -> new IndexedChunkDto(
                    stringOrNull(record, "id"),
                    stringOrNull(record, "corpCode"),
                    stringOrNull(record, "corpName"),
                    stringOrNull(record, "reportNm"),
                    stringOrNull(record, "rceptNo"),
                    stringOrNull(record, "pblntfTy"),
                    stringOrNull(record, "docType"),
                    stringOrNull(record, "sectionTitle"),
                    stringOrNull(record, "rceptDt"),
                    stringOrNull(record, "textPreview")
            ));
        }
    }

    private long countLabel(String label, String typeCondition) {
        String query = "MATCH (n:`" + label + "`) WHERE " + typeCondition + " RETURN count(n) AS total";
        try (Session session = driver.session()) {
            return session.run(query).single().get("total").asLong();
        }
    }

    private static String stringOrNull(Record record, String key) {
        var value = record.get(key);
        return value.isNull() ? null : value.asString();
    }
}
