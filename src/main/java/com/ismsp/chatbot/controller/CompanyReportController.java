package com.ismsp.chatbot.controller;

import java.time.LocalDate;
import java.util.List;

import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.ChatRequest;
import com.ismsp.chatbot.dto.ChatResponse;
import com.ismsp.chatbot.dto.ChatTurnDto;
import com.ismsp.chatbot.dto.IndexResult;
import com.ismsp.chatbot.service.CompanyChatService;
import com.ismsp.chatbot.service.CompanyReportIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompanyReportController {

    private final CompanyReportIndexService indexService;
    private final CompanyChatService chatService;

    public CompanyReportController(CompanyReportIndexService indexService, CompanyChatService chatService) {
        this.indexService = indexService;
        this.chatService = chatService;
    }

    @GetMapping("/api/company-report/watchlist")
    public List<WatchedCompany> watchlist() {
        return WatchedCompany.ALL;
    }

    /** 워치리스트 기업의 정기공시를 색인한다. 기간 미지정 시 최근 2년치. */
    @PostMapping("/api/company-report/index")
    public List<IndexResult> index(
            @RequestParam(value = "bgn_de", required = false) String bgnDe,
            @RequestParam(value = "end_de", required = false) String endDe
    ) {
        LocalDate end = endDe != null ? LocalDate.parse(endDe) : LocalDate.now();
        LocalDate begin = bgnDe != null ? LocalDate.parse(bgnDe) : end.minusYears(2);
        return indexService.indexWatchedCompanies(begin, end);
    }

    @PostMapping("/api/company-report/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        List<ChatTurnDto> history = request.history() != null ? request.history() : List.of();
        int topK = request.topK() != null ? Math.min(Math.max(request.topK(), 1), 20) : 4;
        return chatService.chat(request.question(), request.corpCode(), topK, history, request.provider());
    }
}
