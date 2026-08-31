package com.ismsp.chatbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ismsp.chatbot.dart.dto.DisclosureItem;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.FilerDisclosureDto;
import com.ismsp.chatbot.dto.GraphExpansionRow;
import com.ismsp.chatbot.dto.RelatedCompanyDto;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DisclosureGraphService {

    private final Driver driver;

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

    /**
     * VectorCypher: 벡터 검색이 이미 뽑은 topK 청크의 메타데이터(rcept_no, article_url)에
     * 고정 앵커된 단일 Cypher로, 청크 자체엔 없는 그래프 전용 정보를 보강한다. 쿼리
     * 문자열이 고정/파라미터화돼 있고 LLM이 만든 게 아니므로 runReadOnlyQuery처럼
     * WRITE_CLAUSE 검사나 READ 전용 세션이 필요 없다 - findFilers와 같은 신뢰 수준의
     * 고정 조회다.
     */
    public List<GraphExpansionRow> expandRetrievedContext(
            String corpCode, List<String> rceptNos, List<String> articleUrls, int limit
    ) {
        if (rceptNos.isEmpty() && articleUrls.isEmpty()) {
            return List.of();
        }
        String query = """
                CALL {
                    UNWIND $rceptNos AS rceptNo
                    MATCH (f:Filer)-[:DISCLOSED]->(r:Report {rcept_no: rceptNo})-[:FILED_BY]->(:Company {corp_code: $corpCode})
                    OPTIONAL MATCH (f)-[:DISCLOSED]->(:Report)-[:FILED_BY]->(other:Company)
                    WHERE other.corp_code <> $corpCode
                    RETURN 'DISCLOSURE' AS kind, rceptNo AS anchor, f.name AS primaryName,
                           r.report_nm AS secondaryName, other.corp_code AS relatedCorpCode, other.name AS relatedCorpName

                    UNION ALL

                    UNWIND $articleUrls AS articleUrl
                    MATCH (a:Article {url: articleUrl})-[:ABOUT]->(other:Company)
                    WHERE other.corp_code <> $corpCode
                    OPTIONAL MATCH (m:Media)-[:PUBLISHED]->(a)
                    RETURN 'NEWS' AS kind, articleUrl AS anchor, coalesce(m.name, '(언론사 미상)') AS primaryName,
                           a.title AS secondaryName, other.corp_code AS relatedCorpCode, other.name AS relatedCorpName
                }
                RETURN DISTINCT kind, anchor, primaryName, secondaryName, relatedCorpCode, relatedCorpName
                LIMIT $limit
                """;
        try (Session session = driver.session()) {
            return session.run(query, Map.of(
                    "corpCode", corpCode,
                    "rceptNos", rceptNos,
                    "articleUrls", articleUrls,
                    "limit", limit
            )).list(record -> new GraphExpansionRow(
                    record.get("kind").asString(""),
                    record.get("anchor").asString(""),
                    record.get("primaryName").asString(""),
                    record.get("secondaryName").asString(""),
                    record.get("relatedCorpCode").asString(null),
                    record.get("relatedCorpName").asString(null)
            ));
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
