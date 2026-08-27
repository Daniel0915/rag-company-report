package com.ismsp.chatbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ismsp.chatbot.dart.dto.DisclosureItem;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.FilerDisclosureDto;
import com.ismsp.chatbot.dto.RelatedCompanyDto;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 공시 메타데이터(기업/공시/제출인)를 그래프로 구성해서, 벡터 유사도 검색만으로는
 * 답할 수 없는 다중 홉(multi-hop) 질의를 지원한다 (예: "이 사람이 지분을 공시한
 * 다른 회사는?"). DART list.json이 이미 제공하는 flr_nm(제출인)만 사용하므로
 * 별도 텍스트/표 파싱이나 LLM 추출 없이도 신뢰도 높은 그래프를 만들 수 있다.
 *
 * CompanyReportChunk(벡터 청크)와는 별개의 그래프(:Company, :Report, :Filer)이며,
 * 기존 벡터 검색 파이프라인에는 영향을 주지 않는다.
 */
@Service
public class DisclosureGraphService {

    private final Driver driver;

    public DisclosureGraphService(Driver driver) {
        this.driver = driver;
    }

    /** 공시 1건을 그래프에 반영한다. 모든 공시는 Report-Company로 연결하고, 지분공시(D)는 제출인(Filer)까지 연결한다. */
    public void recordDisclosure(WatchedCompany company, DisclosureItem item, String pblntfTy) {
        Map<String, Object> params = new HashMap<>();
        params.put("corpCode", company.corpCode());
        params.put("corpName", company.name());
        params.put("stockCode", company.stockCode());
        params.put("rceptNo", item.rceptNo());
        params.put("reportNm", item.reportNm());
        params.put("rceptDt", item.rceptDt());
        params.put("pblntfTy", pblntfTy);

        try (Session session = driver.session()) {
            session.run("""
                    MERGE (co:Company {corp_code: $corpCode})
                      ON CREATE SET co.name = $corpName, co.stock_code = $stockCode
                    MERGE (r:Report {rcept_no: $rceptNo})
                      ON CREATE SET r.report_nm = $reportNm, r.rcept_dt = $rceptDt, r.pblntf_ty = $pblntfTy
                    MERGE (r)-[:FILED_BY]->(co)
                    """, params);

            if ("D".equals(pblntfTy) && StringUtils.hasText(item.flrNm()) && !item.flrNm().equals(company.name())) {
                Map<String, Object> filerParams = new HashMap<>(params);
                filerParams.put("filerName", item.flrNm());
                session.run("""
                        MATCH (r:Report {rcept_no: $rceptNo})
                        MERGE (f:Filer {name: $filerName})
                        MERGE (f)-[:DISCLOSED]->(r)
                        """, filerParams);
            }
        }
    }

    /** 특정 기업에 지분을 공시한 제출인 목록(최근순). */
    public List<FilerDisclosureDto> findFilers(String corpCode, int limit) {
        String query = """
                MATCH (f:Filer)-[:DISCLOSED]->(r:Report)-[:FILED_BY]->(co:Company {corp_code: $corpCode})
                RETURN f.name AS filerName, r.report_nm AS reportNm, r.rcept_dt AS rceptDt, r.rcept_no AS rceptNo
                ORDER BY r.rcept_dt DESC
                LIMIT $limit
                """;
        try (Session session = driver.session()) {
            return session.run(query, Map.of("corpCode", corpCode, "limit", limit))
                    .list(record -> new FilerDisclosureDto(
                            record.get("filerName").asString(),
                            record.get("reportNm").asString(""),
                            record.get("rceptDt").asString(""),
                            record.get("rceptNo").asString("")
                    ));
        }
    }

    /**
     * LLM(Text2Cypher)이 생성한 쿼리를 읽기 전용 세션으로 실행한다. 세션 자체를
     * READ 접근 모드로 열어서, 정규식 검사를 통과한 악의적/실수 쓰기 쿼리라도
     * Neo4j 서버 단에서 다시 한번 거부되도록 이중으로 막는다.
     * 결과가 없으면 null을 반환해 호출부가 고정 쿼리로 폴백할 수 있게 한다.
     */
    public String runReadOnlyQuery(String cypher) {
        SessionConfig readOnly = SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build();
        try (Session session = driver.session(readOnly)) {
            List<Record> records = session.run(cypher).list();
            if (records.isEmpty()) {
                return null;
            }
            return records.stream()
                    .map(record -> record.keys().stream()
                            .map(key -> key + "=" + record.get(key).asObject())
                            .collect(Collectors.joining(", ", "- ", "")))
                    .collect(Collectors.joining("\n"));
        }
    }

    /** 특정 제출인이 지분을 공시한 다른 회사들 (멀티홉 탐색). */
    public List<RelatedCompanyDto> findRelatedCompanies(String filerName, int limit) {
        String query = """
                MATCH (f:Filer {name: $filerName})-[:DISCLOSED]->(:Report)-[:FILED_BY]->(co:Company)
                RETURN DISTINCT co.corp_code AS corpCode, co.name AS corpName
                LIMIT $limit
                """;
        try (Session session = driver.session()) {
            return session.run(query, Map.of("filerName", filerName, "limit", limit))
                    .list(record -> new RelatedCompanyDto(
                            record.get("corpCode").asString(""),
                            record.get("corpName").asString("")
                    ));
        }
    }
}
