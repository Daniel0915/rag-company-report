package com.ismsp.chatbot.service;

import java.util.List;

import com.ismsp.chatbot.dto.ChatResponse;
import com.ismsp.chatbot.dto.FilerDisclosureDto;
import com.ismsp.chatbot.dto.SourceItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

/**
 * DART 공시 원문(사업/반기/분기보고서 + 지분공시)을 근거로 기업 정보를 답변하는 채팅.
 * 기업마다 벡터 인덱스가 분리돼 있어(CompanyVectorStoreRegistry) 다른 기업 데이터가
 * 애초에 검색 후보에 섞이지 않는다. 벡터 검색으로는 안 되는 지분 관계(제출인)는
 * DisclosureGraphService의 그래프에서 함께 가져와 보강한다.
 */
@Service
public class CompanyChatService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 DART 전자공시 자료를 근거로 기업 정보를 설명하는 어시스턴트입니다.
            아래 context는 해당 기업이 금융감독원에 제출한 사업보고서/반기보고서/분기보고서
            원문에서 발췌한 내용이고, 그래프 정보는 지분공시(주식등의대량보유상황보고서,
            임원ㆍ주요주주소유보고서)에서 추출한 제출인 관계입니다. 이 정보들만 근거로
            답변하고, 없는 내용이면 모른다고 답하세요. 답변에는 근거가 된 공시명과 섹션을
            함께 언급하세요. 한국어로 답변하세요.

            context:
            %s

            그래프 정보 (이 기업에 지분을 공시한 사람/기관):
            %s
            """;

    private final ChatClient chatClient;
    private final CompanyVectorStoreRegistry vectorStoreRegistry;
    private final DisclosureGraphService disclosureGraphService;

    public CompanyChatService(
            ChatClient.Builder chatClientBuilder,
            CompanyVectorStoreRegistry vectorStoreRegistry,
            DisclosureGraphService disclosureGraphService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.disclosureGraphService = disclosureGraphService;
    }

    public ChatResponse chat(String question, String corpCode, int topK) {
        if (corpCode == null || corpCode.isBlank()) {
            throw new IllegalArgumentException("corpCode는 필수입니다");
        }

        SearchRequest searchRequest = SearchRequest.builder().query(question).topK(topK).build();
        List<Document> context = vectorStoreRegistry.forCompany(corpCode).similaritySearch(searchRequest);
        String contextText = buildContextText(context);
        String graphText   = buildGraphText(corpCode);

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT_TEMPLATE.formatted(contextText, graphText))
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

    private String buildGraphText(String corpCode) {
        List<FilerDisclosureDto> filers = disclosureGraphService.findFilers(corpCode, 10);
        if (filers.isEmpty()) {
            return "(지분공시 그래프 정보 없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (FilerDisclosureDto f : filers) {
            sb.append("- 제출인: ").append(f.filerName())
                    .append(" | 공시명: ").append(f.reportNm())
                    .append(" | 접수일: ").append(f.rceptDt()).append("\n");
        }
        return sb.toString();
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
