package com.ismsp.chatbot.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ismsp.chatbot.dart.dto.WatchedCompany;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.stereotype.Component;

/**
 * 기업(corp_code)마다 별도의 Neo4j 벡터 인덱스/라벨을 쓰는 VectorStore를 만들고 캐싱한다.
 * 예전에는 모든 기업이 CompanyReportChunk 라벨 하나를 공유해서, 벡터 인덱스가
 * corp_code 필터를 ANN 검색 이후에 적용(post-filter)하는 탓에 topK가 작으면
 * 다른 기업 청크만 뽑히고 필터 후 0건이 되는 문제가 있었다. 기업별로 인덱스 자체를
 * 분리하면 다른 기업 데이터가 애초에 후보에 섞일 수 없다.
 *
 * 워치리스트(WatchedCompany.ALL)가 코드에 하드코딩된 소규모 고정 목록이라, 지연 생성
 * 대신 시작 시점에 전부 미리 만들어둔다.
 */
@Component
public class CompanyVectorStoreRegistry {

    private final Driver driver;
    private final EmbeddingModel embeddingModel;
    private final Map<String, VectorStore> stores = new ConcurrentHashMap<>();

    public CompanyVectorStoreRegistry(Driver driver, EmbeddingModel embeddingModel) {
        this.driver = driver;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    void initWatchlist() {
        for (WatchedCompany company : WatchedCompany.ALL) {
            forCompany(company.corpCode());
        }
    }

    public VectorStore forCompany(String corpCode) {
        return stores.computeIfAbsent(corpCode, this::createStore);
    }

    public static String labelFor(String corpCode) {
        return "CompanyReportChunk_" + corpCode;
    }

    private VectorStore createStore(String corpCode) {
        String label = labelFor(corpCode);
        Neo4jVectorStore store = Neo4jVectorStore.builder(driver, embeddingModel)
                .label(label)
                .indexName("company-report-index-" + corpCode)
                .embeddingDimension(1024)
                .distanceType(Neo4jVectorStore.Neo4jDistanceType.COSINE)
                .constraintName(label + "_unique_idx")
                .initializeSchema(true)
                .build();
        // builder()로 직접 만들면 Spring 빈 생명주기를 안 타서 InitializingBean 콜백
        // (제약조건/벡터 인덱스 생성)이 자동으로 안 불린다 - 직접 한 번 호출해줘야 한다.
        store.afterPropertiesSet();
        return store;
    }
}
