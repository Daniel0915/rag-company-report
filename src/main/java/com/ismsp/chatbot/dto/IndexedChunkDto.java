package com.ismsp.chatbot.dto;

public record IndexedChunkDto(
        String id,
        String corpCode,
        String corpName,
        String reportNm,
        String rceptNo,
        String pblntfTy,
        String docType,
        String sectionTitle,
        String rceptDt,
        String textPreview
) {
}
