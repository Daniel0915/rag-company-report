package com.ismsp.chatbot.dto;

/** VectorCypher 확장 쿼리 1행. kind는 "DISCLOSURE" 또는 "NEWS". */
public record GraphExpansionRow(
        String kind,
        String anchor,
        String primaryName,
        String secondaryName,
        String relatedCorpCode,
        String relatedCorpName
) {
}
