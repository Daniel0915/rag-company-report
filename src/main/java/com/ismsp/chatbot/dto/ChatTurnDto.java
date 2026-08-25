package com.ismsp.chatbot.dto;

/** 프론트엔드(IndexedDB)에 저장된 과거 대화 한 턴. role은 "user" 또는 "assistant". */
public record ChatTurnDto(String role, String content) {
}
