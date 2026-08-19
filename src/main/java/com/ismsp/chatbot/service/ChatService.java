package com.ismsp.chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ismsp.chatbot.dto.ChatResponse;
import com.ismsp.chatbot.dto.SourceItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * 등록된 기업 문서만 근거로 답변하는 필터 기반 채팅.
 * TypeScript 버전(src/modules/isms-p/step2_chat.ts)과 동일한 프롬프트/필터 구조.
 */
@Service
public class ChatService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 기업의 ISMS-P(정보보호 및 개인정보보호 관리체계) 인증 준비를 돕는 어시스턴트입니다.
            아래 context는 이 기업이 등록한 정책/지침 문서에서 발췌한 내용입니다. context만 근거로 답변하고,
            context에 없는 내용이면 모른다고 답하세요. 답변에는 근거가 된 문서 파일명을 함께 언급하세요.
            한국어로 답변하세요.

            context:
            %s
            """;

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;

    public ChatService(ChatClient.Builder chatClientBuilder, SimpleVectorStore companyDocVectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = companyDocVectorStore;
    }

    public ChatResponse chat(String question, Map<String, String> filter, int topK) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(question).topK(topK);
        Filter.Expression expression = buildFilter(filter);
        if (expression != null) {
            requestBuilder.filterExpression(expression);
        }

        List<Document> context = vectorStore.similaritySearch(requestBuilder.build());
        String contextText = buildContextText(context);

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT_TEMPLATE.formatted(contextText))
                .user(question)
                .call()
                .content();

        List<SourceItem> sources = context.stream()
                .map(d -> new SourceItem(
                        String.valueOf(d.getMetadata().get("source_file")),
                        String.valueOf(d.getMetadata().get("doc_type")),
                        String.valueOf(d.getMetadata().get("chunk_strategy"))
                ))
                .toList();

        return new ChatResponse(answer, sources);
    }

    private String buildContextText(List<Document> context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            Document d = context.get(i);
            sb.append("[").append(i + 1).append("] (출처: ")
                    .append(d.getMetadata().get("source_file")).append(", ")
                    .append(d.getMetadata().get("chunk_strategy")).append(") ")
                    .append(d.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private Filter.Expression buildFilter(Map<String, String> filter) {
        if (filter == null) return null;
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        String docType = filter.get("doc_type");
        String domain = filter.get("domain");
        if (docType != null && !docType.isBlank()) ops.add(builder.eq("doc_type", docType));
        if (domain != null && !domain.isBlank()) ops.add(builder.eq("domain", domain));

        if (ops.isEmpty()) return null;
        FilterExpressionBuilder.Op combined = ops.get(0);
        for (int i = 1; i < ops.size(); i++) {
            combined = builder.and(combined, ops.get(i));
        }
        return combined.build();
    }
}
