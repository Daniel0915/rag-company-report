package com.ismsp.chatbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ismsp.chatbot.dto.IndexedChunkDto;
import com.ismsp.chatbot.dto.IndexedChunkPage;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 어드민 페이지에서 Neo4j에 실제로 색인된 청크를 훑어보기 위한 읽기 전용 뷰어.
 * VectorStore 인터페이스는 유사도 검색만 지원하므로, 저장된 데이터를 있는 그대로
 * 브라우징하려면 Neo4j 드라이버로 청크 노드를 직접 조회해야 한다.
 */
@Service
public class IndexedChunkService {

    private final Driver driver;
    private final String label;

    public IndexedChunkService(
            Driver driver,
            @Value("${spring.ai.vectorstore.neo4j.label:CompanyReportChunk}") String label
    ) {
        this.driver = driver;
        this.label = label;
    }

    public IndexedChunkPage listChunks(String corpCode, String sourceType, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("corpCode", (corpCode == null || corpCode.isBlank()) ? null : corpCode);
        params.put("limit", limit);
        params.put("offset", offset);

        // pblntf_ty 태깅을 추가하기 전에 이미 색인된 정기공시 청크는 이 필드가 아예 없다.
        // "정기공시"로 필터링할 때 그런 레거시 청크가 누락되지 않도록, doc_type도 없는
        // (=관리자 PDF가 아닌) 청크는 정기공시로 간주해서 같이 포함한다.
        String typeCondition = switch (sourceType == null ? "" : sourceType) {
            case "A" -> "(n.`metadata.pblntf_ty` = 'A' OR (n.`metadata.pblntf_ty` IS NULL AND n.`metadata.doc_type` IS NULL))";
            case "D" -> "n.`metadata.pblntf_ty` = 'D'";
            case "ADMIN_PDF" -> "n.`metadata.doc_type` = 'ADMIN_PDF'";
            default -> "true";
        };

        String whereClause = "WHERE ($corpCode IS NULL OR n.`metadata.corp_code` = $corpCode) AND " + typeCondition + " ";

        String listQuery = "MATCH (n:`" + label + "`) " + whereClause + """
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
                SKIP $offset LIMIT $limit
                """;
        String countQuery = "MATCH (n:`" + label + "`) " + whereClause + "RETURN count(n) AS total";

        try (Session session = driver.session()) {
            List<IndexedChunkDto> items = session.run(listQuery, params).list(record -> new IndexedChunkDto(
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
            long total = session.run(countQuery, params).single().get("total").asLong();
            return new IndexedChunkPage(items, total);
        }
    }

    private static String stringOrNull(Record record, String key) {
        var value = record.get(key);
        return value.isNull() ? null : value.asString();
    }
}
