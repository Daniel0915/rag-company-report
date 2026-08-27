package com.ismsp.chatbot.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import com.ismsp.chatbot.dto.IndexResult;
import com.ismsp.chatbot.naver.NaverArticleScraper;
import com.ismsp.chatbot.naver.NaverSearchScraper;
import com.ismsp.chatbot.naver.dto.ScrapedArticle;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 워치리스트 기업마다 네이버 뉴스 검색결과 페이지(NaverSearchScraper, API 키 불필요)에서
 * 기사 URL을 찾고, 본문을 크롤링(NaverArticleScraper)해서 청킹한 뒤 기업별 벡터 인덱스
 * (CompanyVectorStoreRegistry)에 doc_type='NEWS'로 합류시킨다. CompanyReportIndexService와
 * 같은 메타데이터 키(report_nm/rcept_no/rcept_dt/section_title)를 써서 CompanyChatService가
 * 코드 변경 없이 뉴스도 그대로 근거로 쓸 수 있게 한다.
 * 기사 url을 유일키로 이미 색인한 건 스킵한다. 본문 크롤링에 실패한 URL은 실패로 기록만
 * 하고 seenUrls에는 안 남겨서 다음 실행 때 다시 시도한다(일시적 오류일 수 있으므로).
 */
@Service
public class NewsIngestService {

    private static final long DELAY_MS = 400;

    private final NaverSearchScraper naverSearchScraper;
    private final NaverArticleScraper naverArticleScraper;
    private final CompanyVectorStoreRegistry vectorStoreRegistry;
    private final NewsGraphService newsGraphService;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File sidecarFile;
    private final Set<String> seenUrls;
    private final int articlesPerCompany;

    public NewsIngestService(
            NaverSearchScraper naverSearchScraper,
            NaverArticleScraper naverArticleScraper,
            CompanyVectorStoreRegistry vectorStoreRegistry,
            NewsGraphService newsGraphService,
            @Value("${news.index-file:data/news-index.json}") String sidecarPath,
            @Value("${news.articles-per-company:20}") int articlesPerCompany
    ) {
        this.naverSearchScraper = naverSearchScraper;
        this.naverArticleScraper = naverArticleScraper;
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.newsGraphService = newsGraphService;
        this.sidecarFile = new File(sidecarPath);
        this.sidecarFile.getParentFile().mkdirs();
        this.seenUrls = loadSidecar();
        this.articlesPerCompany = articlesPerCompany;
    }

    public synchronized List<IndexResult> fetchWatchedCompanyNews() {
        List<IndexResult> results = new ArrayList<>();
        for (WatchedCompany company : WatchedCompany.ALL) {
            results.addAll(fetchCompanyNews(company));
        }
        return results;
    }

    private List<IndexResult> fetchCompanyNews(WatchedCompany company) {
        List<String> urls = naverSearchScraper.searchArticleUrls(company.name(), articlesPerCompany);

        List<IndexResult> results = new ArrayList<>();
        for (String url : urls) {
            if (seenUrls.contains(url)) {
                results.add(new IndexResult(company.name(), url, url, "skipped", 0));
                continue;
            }
            results.add(ingestArticle(company, url));
            sleep();
        }
        return results;
    }

    private IndexResult ingestArticle(WatchedCompany company, String url) {
        Optional<ScrapedArticle> scraped = naverArticleScraper.fetchArticle(url);
        if (scraped.isEmpty()) {
            return new IndexResult(company.name(), url, url, "failed", 0);
        }
        ScrapedArticle article = scraped.get();
        String title = StringUtils.hasText(article.title()) ? article.title() : url;
        String press = article.press();
        String publishedDate = article.publishedDate();

        Map<String, Object> tagMeta = new HashMap<>();
        tagMeta.put("doc_type", "NEWS");
        tagMeta.put("corp_name", company.name());
        tagMeta.put("corp_code", company.corpCode());
        tagMeta.put("stock_code", company.stockCode());
        tagMeta.put("report_nm", title);
        tagMeta.put("rcept_no", url);
        tagMeta.put("section_title", StringUtils.hasText(press) ? press : "뉴스");
        tagMeta.put("article_url", url);
        if (publishedDate != null) {
            tagMeta.put("rcept_dt", publishedDate);
        }

        Document document = new Document(title + "\n\n" + article.content(), tagMeta);
        List<Document> chunks = splitter.apply(List.of(document));
        vectorStoreRegistry.forCompany(company.corpCode()).add(chunks);

        List<String> chunkIds = chunks.stream().map(Document::getId).toList();
        newsGraphService.recordArticle(company, url, title, press, publishedDate, chunkIds);

        seenUrls.add(url);
        persistSidecar();

        return new IndexResult(company.name(), url, title, "new", chunks.size());
    }

    private void sleep() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Set<String> loadSidecar() {
        if (!sidecarFile.exists()) {
            return new HashSet<>();
        }
        try {
            List<String> urls = objectMapper.readValue(sidecarFile, new TypeReference<ArrayList<String>>() {
            });
            return new HashSet<>(urls);
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    private void persistSidecar() {
        try {
            objectMapper.writeValue(sidecarFile, seenUrls);
        } catch (IOException e) {
            throw new IllegalStateException("뉴스 색인 사이드카 저장 실패", e);
        }
    }
}
