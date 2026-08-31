package com.ismsp.chatbot.controller;

import com.ismsp.chatbot.dto.IndexedChunkPage;
import com.ismsp.chatbot.service.IndexedChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IndexedChunkController {

    private final IndexedChunkService indexedChunkService;

    /** 어드민 뷰어: 실제로 Neo4j에 색인된 청크를 기업/공시유형으로 필터링해서 조회한다. */
    @GetMapping("/api/admin/indexed-chunks")
    public IndexedChunkPage indexedChunks(
            @RequestParam(value = "corpCode", required = false) String corpCode,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        int safeOffset = Math.max(offset, 0);
        return indexedChunkService.listChunks(corpCode, sourceType, safeLimit, safeOffset);
    }
}
