package com.ismsp.chatbot.controller;

import java.util.List;

import com.ismsp.chatbot.dto.IndexResult;
import com.ismsp.chatbot.service.NewsIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/news")
public class NewsController {

    private final NewsIngestService newsIngestService;

    public NewsController(NewsIngestService newsIngestService) {
        this.newsIngestService = newsIngestService;
    }

    /** 워치리스트 전체 기업의 최근 뉴스를 네이버 뉴스검색 API로 가져와 색인한다. */
    @PostMapping("/fetch")
    public List<IndexResult> fetch() {
        return newsIngestService.fetchWatchedCompanyNews();
    }
}
