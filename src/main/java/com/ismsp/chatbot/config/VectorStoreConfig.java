package com.ismsp.chatbot.config;

import java.io.File;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SimpleVectorStore는 인메모리 벡터 인덱스 + JSON 파일 저장을 지원한다 — TypeScript
 * 버전의 HNSWLib + JSON 사이드카(data/vectorstore/<namespace>/chunks.json)와
 * 동일한 역할. 앱 시작 시 파일이 있으면 로드하고, 종료 시 저장한다.
 */
@Configuration
public class VectorStoreConfig {

    @Value("${isms-p.vector-store-file:data/isms-p-vectorstore.json}")
    private String vectorStoreFile;

    @Bean
    public SimpleVectorStore companyDocVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(vectorStoreFile);
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }
}
