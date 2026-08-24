package com.ismsp.chatbot.dto;

import java.util.List;

public record IndexedChunkPage(List<IndexedChunkDto> items, long total) {
}
