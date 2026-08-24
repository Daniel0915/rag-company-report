package com.ismsp.chatbot.dto;

public record AdminDocumentDto(
        String id,
        String corpCode,
        String corpName,
        String title,
        String category,
        String description,
        String docDate,
        String fileName,
        String uploadedAt,
        int chunkCount
) {
}
