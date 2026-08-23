package com.ismsp.chatbot.config;

import java.io.File;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SimpleVectorStore는 인메모리 벡터 인덱스 + JSON 파일 저장을 지원한다.
 * 앱 시작 시 파일이 있으면 로드하고, 색인할 때마다 저장한다.
 */
@Configuration
public class VectorStoreConfig {

    @Value("${company-report.vector-store-file:data/company-report-vectorstore.json}")
    private String vectorStoreFile;

    @Bean
    public SimpleVectorStore companyReportVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(vectorStoreFile);
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }
}
