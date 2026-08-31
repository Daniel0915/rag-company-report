package com.ismsp.chatbot.service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ismsp.chatbot.claude.ClaudeCliClient;
import com.ismsp.chatbot.dto.ChatResponse;
import com.ismsp.chatbot.dto.ChatTurnDto;
import com.ismsp.chatbot.dto.GraphExpansionRow;
import com.ismsp.chatbot.dto.SourceItem;
import com.ismsp.chatbot.gemini.GeminiApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

/**
 * DART 공시 원문(사업/반기/분기보고서 + 지분공시)을 근거로 기업 정보를 답변하는 채팅.
 * 기업마다 벡터 인덱스가 분리돼 있어(CompanyVectorStoreRegistry) 다른 기업 데이터가
 * 애초에 검색 후보에 섞이지 않는다. 벡터 검색으로는 안 되는 지분 관계(제출인)는
 * DisclosureGraphService의 그래프에서 함께 가져와 보강한다.
 *
 * 그래프 보강은 집계/랭킹/교집합처럼 고정 조회 하나로 못 푸는 질문일 때, 답변에
 * 쓰기로 선택된 provider(local/gemini/claude)가 그 자리에서 Cypher를 생성해
 * (Text2Cypher) 읽기 전용으로 실행하는 방식으로 확장된다. 생성 실패/쓰기 구문 포함/
 * 실행 오류 시에는 벡터 검색으로 이미 뽑힌 청크(rcept_no/article_url)에 앵커된
 * 고정 Cypher(VectorCypher)로 안전하게 폴백한다.
 *
 * 대화 히스토리(프론트 IndexedDB 보관분)가 있으면, 후속 질문("만원이 맞아?" 같은
 * 키워드 없는 질문)을 그대로 임베딩하지 않고 먼저 독립형 질문으로 재작성한 뒤
 * 그 결과로 벡터 검색을 한다 - 안 그러면 후속 질문마다 검색이 엉뚱하게 튄다.
 */
@Service
@Slf4j
public class CompanyChatService {

    private static final String REWRITE_PROMPT_TEMPLATE = """
            아래는 사용자와 어시스턴트의 이전 대화입니다.

            %s

            위 대화 맥락을 참고해서, 아래 "최신 질문"이 이 대화만 보고도 무엇을 묻는
            것인지 알 수 있는 완전한 질문 하나로 다시 쓰세요. 이전 대화에서 이미 나온
            기업명/보고서명/항목명이 있다면 그걸 명시적으로 포함하세요. 설명이나 답변
            없이 재작성된 질문 문장 하나만 출력하세요.

            최신 질문: %s
            """;

    private static final String CYPHER_ROUTER_PROMPT_TEMPLATE = """
            당신은 Neo4j 읽기 전용 Cypher 쿼리를 작성하는 어시스턴트입니다. 아래 그래프
            스키마로 답할 수 있는, 지분공시 제출인 관계에 대한 집계·랭킹·비교·교집합·
            역방향 조회 같은 그래프 탐색이 질문에 필요하면 그 질문에 맞는 Cypher 쿼리
            하나만 출력하세요 (MATCH, WHERE, RETURN, ORDER BY, LIMIT, count()만 사용하고
            CREATE/MERGE/SET/DELETE/REMOVE/DROP/LOAD CSV는 절대 쓰지 마세요). 회사 소개나
            재무/사업내용처럼 그래프 탐색이 필요 없는 질문이면 다른 설명 없이 정확히
            NONE 이라고만 출력하세요. 쿼리든 NONE이든 그 외 설명은 절대 덧붙이지 마세요.

            스키마:
            (:Company {corp_code, name, stock_code})
            (:Report {rcept_no, report_nm, rcept_dt, pblntf_ty})
            (:Filer {name})
            (:Article {url, title, published_date})
            (:Media {name})
            (:Report)-[:FILED_BY]->(:Company)
            (:Filer)-[:DISCLOSED]->(:Report)
            (:Article)-[:ABOUT]->(:Company)
            (:Media)-[:PUBLISHED]->(:Article)

            현재 대상 기업의 corp_code: '%s'

            질문: %s
            """;

    private static final Pattern WRITE_CLAUSE = Pattern.compile(
            "(?i)\\b(CREATE|MERGE|DELETE|SET|REMOVE|DROP|LOAD\\s+CSV|CALL\\s+apoc)\\b");

    /**
     * 벡터 검색은 doc_type 구분 없이 코사인 유사도로만 후보를 뽑기 때문에, "뉴스 알려줘"처럼
     * 뉴스 자체를 가리킬 뿐 구체적인 내용이 없는 질문은 임베딩이 모호해서 공시 청크가 더
     * 가깝게 나오는 경우가 있다. 질문에 이 키워드가 있으면 doc_type='NEWS'로 검색을
     * 좁혀서, 막연한 "뉴스 보여줘" 질문도 실제 뉴스 청크를 근거로 쓰게 한다.
     */
    private static final Pattern NEWS_KEYWORD = Pattern.compile("뉴스|기사|언론");
    private static final int NEWS_FILTER_CANDIDATE_POOL = 200;
    private static final int VECTOR_CYPHER_LIMIT = 30;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 DART 전자공시 자료를 근거로 기업 정보를 설명하는 어시스턴트입니다.
            아래 context는 해당 기업이 금융감독원에 제출한 사업보고서/반기보고서/분기보고서
            원문에서 발췌한 내용이고, 그래프 정보는 지분공시(주식등의대량보유상황보고서,
            임원ㆍ주요주주소유보고서)에서 추출한 제출인 관계입니다. 이 정보들만 근거로
            답변하고, 없는 내용이면 모른다고 답하세요. 답변에는 근거가 된 공시명과 섹션을
            함께 언급하세요. 한국어로 답변하세요. 뉴스 목록을 요청받으면 제목, 출처, 날짜,
            링크만 간결하게 나열하고, 관련성 여부에 대한 부연 설명이나 주석은 달지 마세요.

            context:
            %s

            그래프 정보 (지분공시 제출인 관계 및 뉴스 기사의 다른 기업 언급):
            %s
            """;

    private final ChatClient chatClient;
    private final GeminiApiClient geminiApiClient;
    private final ClaudeCliClient claudeCliClient;
    private final CompanyVectorStoreRegistry vectorStoreRegistry;
    private final DisclosureGraphService disclosureGraphService;

    public CompanyChatService(
            ChatClient.Builder chatClientBuilder,
            GeminiApiClient geminiApiClient,
            ClaudeCliClient claudeCliClient,
            CompanyVectorStoreRegistry vectorStoreRegistry,
            DisclosureGraphService disclosureGraphService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.geminiApiClient = geminiApiClient;
        this.claudeCliClient = claudeCliClient;
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.disclosureGraphService = disclosureGraphService;
    }

    /** provider: "local"(Ollama qwen2.5:3b, 기본값), "gemini", 또는 "claude"(claude CLI). */
    public ChatResponse chat(String question, String corpCode, int topK, List<ChatTurnDto> history, String provider) {
        if (corpCode == null || corpCode.isBlank()) {
            throw new IllegalArgumentException("corpCode는 필수입니다");
        }
        String llm = normalizeProvider(provider);

        String searchQuery = (history == null || history.isEmpty()) ? question : rewriteQuery(question, history, llm);

        boolean newsOnly = NEWS_KEYWORD.matcher(question).find();
        // Neo4jVectorStore는 filterExpression을 ANN 검색 이후 후보군(topK개)에 대한
        // WHERE절로 적용한다 - topK를 그대로 쓰면 상위 후보에 NEWS 청크가 하나도 안 걸려
        // 필터링 후 0건이 되기 쉽다. 필터가 걸릴 땐 후보군을 넉넉히 가져온 뒤 잘라낸다.
        int annTopK = newsOnly ? Math.max(topK, NEWS_FILTER_CANDIDATE_POOL) : topK;
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(searchQuery)
                                                   .topK(annTopK)
                                                   .filterExpression(newsOnly ? "doc_type == 'NEWS'" : null)
                                                   .build();

        List<Document> context = vectorStoreRegistry.forCompany(corpCode)
                                                    .similaritySearch(searchRequest);
        if (newsOnly && context.size() > topK) {
            context = context.subList(0, topK);
        }
        String contextText = buildContextText(context);
        String graphText   = buildGraphText(corpCode, searchQuery, llm, context);

        String answer = complete(llm, SYSTEM_PROMPT_TEMPLATE.formatted(contextText, graphText), question);

        List<SourceItem> sources = context.stream()
                .map(d -> new SourceItem(
                        String.valueOf(d.getMetadata().get("corp_name")),
                        String.valueOf(d.getMetadata().get("report_nm")),
                        String.valueOf(d.getMetadata().get("rcept_no")),
                        String.valueOf(d.getMetadata().get("section_title")),
                        (String) d.getMetadata().get("doc_type")
                ))
                .toList();

        return new ChatResponse(answer, sources);
    }

    private String rewriteQuery(String question, List<ChatTurnDto> history, String llm) {
        String historyText = history.stream()
                .map(t -> ("user".equals(t.role()) ? "사용자: " : "어시스턴트: ") + t.content())
                .collect(Collectors.joining("\n"));

        String rewritten = complete(llm, "", REWRITE_PROMPT_TEMPLATE.formatted(historyText, question));
        return (rewritten == null || rewritten.isBlank()) ? question : rewritten.trim();
    }

    private String normalizeProvider(String provider) {
        if ("gemini".equals(provider) || "cloud".equals(provider)) {
            return "gemini";
        }
        if ("claude".equals(provider)) {
            return "claude";
        }
        return "local";
    }

    private String complete(String llm, String systemPrompt, String userPrompt) {
        return switch (llm) {
            case "gemini" -> geminiApiClient.generate(systemPrompt, userPrompt);
            case "claude" -> claudeCliClient.generate(systemPrompt, userPrompt);
            default -> {
                ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
                if (systemPrompt != null && !systemPrompt.isBlank()) {
                    spec = spec.system(systemPrompt);
                }
                yield spec.user(userPrompt).call().content();
            }
        };
    }

    /**
     * 질문이 집계/랭킹/교집합처럼 고정 조회 하나로는 못 푸는 그래프 탐색을 요구하면,
     * 답변에 쓰기로 선택된 provider(local/gemini/claude)에게 그 자리에서 Cypher를
     * 생성시켜 읽기 전용으로 실행한다(Text2Cypher). 생성이 안 되거나(NONE), 쓰기 구문이
     * 섞여 있거나, 실행이 실패/빈 결과면 VectorCypher 확장(벡터 검색으로 이미 뽑힌
     * context 청크의 rcept_no/article_url에 앵커된 고정 Cypher)으로 폴백한다.
     */
    private String buildGraphText(String corpCode, String question, String llm, List<Document> context) {
        String cypher = generateGraphQuery(corpCode, question, llm);
        if (cypher != null) {
            log.info("[Text2Cypher] generated query: {}", cypher);
            try {
                String result = disclosureGraphService.runReadOnlyQuery(cypher);
                if (result != null) {
                    log.info("[Text2Cypher] execution succeeded, {} char(s) of result used", result.length());
                    return result;
                }
                log.info("[Text2Cypher] query returned no rows, falling back to VectorCypher expansion");
            } catch (Exception e) {
                log.info("[Text2Cypher] execution failed ({}), falling back to VectorCypher expansion", e.getMessage());
            }
        } else {
            log.info("[Text2Cypher] router returned NONE, using VectorCypher expansion");
        }
        return vectorCypherGraphText(corpCode, context);
    }

    private String generateGraphQuery(String corpCode, String question, String llm) {
        String raw = complete(llm, "", CYPHER_ROUTER_PROMPT_TEMPLATE.formatted(corpCode, question));
        if (raw == null) {
            return null;
        }
        String cypher = raw.replaceAll("(?s)```(?:cypher)?", "").trim();
        if (cypher.isBlank() || "NONE".equalsIgnoreCase(cypher) || WRITE_CLAUSE.matcher(cypher).find()) {
            return null;
        }
        return cypher;
    }

    /**
     * VectorCypher: 벡터 검색으로 이미 뽑힌 topK 청크 중 지분공시(D타입)/뉴스 청크의
     * rcept_no/article_url만 모아 disclosureGraphService.expandRetrievedContext에
     * 넘긴다. 이번 검색 결과와 무관한 "기업 전체 최근 제출인" 대신, 실제로 근거로
     * 쓰인 공시/기사와 연결된 그래프 정보만 보강한다.
     */
    private String vectorCypherGraphText(String corpCode, List<Document> context) {
        List<String> rceptNos = context.stream()
                .filter(d -> "D".equals(d.getMetadata().get("pblntf_ty")))
                .map(d -> String.valueOf(d.getMetadata().get("rcept_no")))
                .distinct()
                .toList();
        List<String> articleUrls = context.stream()
                .filter(d -> "NEWS".equals(d.getMetadata().get("doc_type")))
                .map(d -> String.valueOf(d.getMetadata().get("article_url")))
                .distinct()
                .toList();
        if (rceptNos.isEmpty() && articleUrls.isEmpty()) {
            log.info("[VectorCypher] no D-type/NEWS chunks in retrieved context, skipping graph expansion");
            return "(검색된 근거 청크에서 확장할 그래프 정보 없음)";
        }
        List<GraphExpansionRow> rows =
                disclosureGraphService.expandRetrievedContext(corpCode, rceptNos, articleUrls, VECTOR_CYPHER_LIMIT);
        log.info("[VectorCypher] expanded {} row(s) from {} rcept_no(s) / {} article_url(s)",
                rows.size(), rceptNos.size(), articleUrls.size());
        if (rows.isEmpty()) {
            return "(검색된 근거 청크에 연결된 추가 그래프 정보 없음)";
        }
        return formatGraphExpansionRows(rows);
    }

    private String formatGraphExpansionRows(List<GraphExpansionRow> rows) {
        StringBuilder sb = new StringBuilder();
        for (GraphExpansionRow row : rows) {
            if ("DISCLOSURE".equals(row.kind())) {
                sb.append("- [지분공시] 제출인 ").append(row.primaryName())
                        .append("이(가) 공시한 \"").append(row.secondaryName()).append("\"");
                if (row.relatedCorpCode() != null) {
                    sb.append(" | 같은 제출인이 지분을 공시한 다른 기업: ")
                            .append(row.relatedCorpName()).append(" (").append(row.relatedCorpCode()).append(")");
                }
            } else {
                sb.append("- [뉴스] \"").append(row.secondaryName()).append("\" (").append(row.primaryName())
                        .append(") 기사는 ").append(row.relatedCorpName()).append("에 대해서도 다룸");
            }
            sb.append("\n");
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
