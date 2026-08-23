package com.ismsp.chatbot.dto;

import java.util.List;

public record ChatResponse(String answer, List<SourceItem> sources) {
}
