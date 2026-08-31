package com.ismsp.chatbot.dto;

import java.util.List;

/**
 * history는 프론트엔드가 보내는 최근 대화 몇 턴(현재 질문 제외). 없으면 빈 리스트로 취급.
 * topK는 검색해서 근거로 쓸 공시 조각 수. 없으면 기본값(4) 사용.
 * provider는 "local"(Ollama, 기본값) 또는 "cloud"(Gemini).
 */
public record ChatRequest(String question, String corpCode, List<ChatTurnDto> history, int topK, String provider) {
}
