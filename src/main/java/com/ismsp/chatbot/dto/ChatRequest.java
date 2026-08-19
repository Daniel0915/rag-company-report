package com.ismsp.chatbot.dto;

import java.util.Map;

public record ChatRequest(String question, Map<String, String> filter) {
}
