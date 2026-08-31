package com.ismsp.chatbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ismsp.chatbot.dart.dto.WatchedCompany;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 뉴스 메타데이터(기업/기사/언론사)를 그래프로 구성한다. DisclosureGraphService와 같은
 * Company 노드를 재사용해서, 공시 그래프와 뉴스 그래프가 한 기업 노드에서 만나게 한다.
 *
 * (:Media)-[:PUBLISHED]->(:Article)-[:ABOUT]->(:Company)
 * (:Article)-[:HAS_CHUNK]->(청크 노드, CompanyReportChunk_{corpCode})
 *
 * 청크 자체는 CompanyVectorStoreRegistry가 관리하는 기업별 벡터 인덱스에 doc_type='NEWS'로
 * 그대로 합류하고(별도 Content 라벨 안 만듦), 이 서비스는 그 청크 노드를 Article과
 * 연결하는 그래프 관계만 추가로 만든다.
 */
@Service
@RequiredArgsConstructor
public class NewsGraphService {

    private final Driver driver;

    /** 기사 1건을 그래프에 반영한다. url이 유일키. */
    public void recordArticle(
            WatchedCompany company,
            String url,
            String title,
            String press,
            String publishedDate,
            List<String> chunkIds
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("corpCode", company.corpCode());
        params.put("corpName", company.name());
        params.put("stockCode", company.stockCode());
        params.put("url", url);
        params.put("title", title);
        params.put("publishedDate", publishedDate);

        try (Session session = driver.session()) {
            session.run("""
                    MERGE (co:Company {corp_code: $corpCode})
                      ON CREATE SET co.name = $corpName, co.stock_code = $stockCode
                    MERGE (a:Article {url: $url})
                      ON CREATE SET a.title = $title, a.published_date = $publishedDate
                    MERGE (a)-[:ABOUT]->(co)
                    """, params);

            if (StringUtils.hasText(press)) {
                session.run("""
                        MATCH (a:Article {url: $url})
                        MERGE (m:Media {name: $press})
                        MERGE (m)-[:PUBLISHED]->(a)
                        """, Map.of("url", url, "press", press));
            }

            String chunkLabel = CompanyVectorStoreRegistry.labelFor(company.corpCode());
            String linkChunkQuery = "MATCH (a:Article {url: $url}) "
                    + "MATCH (c:`" + chunkLabel + "` {id: $chunkId}) "
                    + "MERGE (a)-[:HAS_CHUNK]->(c)";
            for (String chunkId : chunkIds) {
                session.run(linkChunkQuery, Map.of("url", url, "chunkId", chunkId));
            }
        }
    }
}
