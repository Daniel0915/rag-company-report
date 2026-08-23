package com.ismsp.chatbot.service;

import java.util.List;

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
 * DART 공시 원문(사업/반기/분기보고서)만 근거로 기업 정보를 답변하는 채팅.
 * corp_code로 필터링해서 특정 기업의 공시 내용에만 근거하도록 한다.
 */
@Service
public class CompanyChatService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 DART 전자공시 자료를 근거로 기업 정보를 설명하는 어시스턴트입니다.
            아래 context는 해당 기업이 금융감독원에 제출한 사업보고서/반기보고서/분기보고서
            원문에서 발췌한 내용입니다. context만 근거로 답변하고, context에 없는 내용이면
            모른다고 답하세요. 답변에는 근거가 된 공시명과 섹션을 함께 언급하세요.
            한국어로 답변하세요.

            context:
            %s
            """;

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;

    public CompanyChatService(ChatClient.Builder chatClientBuilder, SimpleVectorStore companyReportVectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = companyReportVectorStore;
    }

    public ChatResponse chat(String question, String corpCode, int topK) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(question).topK(topK);
        if (corpCode != null && !corpCode.isBlank()) {
            Filter.Expression expression = new FilterExpressionBuilder().eq("corp_code", corpCode).build();
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
                        String.valueOf(d.getMetadata().get("corp_name")),
                        String.valueOf(d.getMetadata().get("report_nm")),
                        String.valueOf(d.getMetadata().get("rcept_no")),
                        String.valueOf(d.getMetadata().get("section_title"))
                ))
                .toList();

        return new ChatResponse(answer, sources);
    }

    private String buildContextText(List<Document> context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            Document d = context.get(i);
            sb.append("[").append(i + 1).append("] (출처: ")
                    .append(d.getMetadata().get("corp_name")).append(" ")
                    .append(d.getMetadata().get("report_nm")).append(" > ")
                    .append(d.getMetadata().get("section_title")).append(") ")
                    .append(d.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
