package com.ismsp.chatbot.naver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 네이버 API 키 없이, 네이버 뉴스 검색결과 페이지(search.naver.com)를 직접 파싱해서
 * 기사 URL 목록만 뽑는다. 검색결과 페이지의 CSS 클래스는 네이버가 자주 바꾸지만
 * n.news.naver.com 기사 URL 패턴 자체는 비교적 안정적이라, 클래스 이름에 의존하는
 * 대신 페이지의 모든 링크를 URL 패턴으로 걸러낸다. 제목/본문/언론사/날짜는 여기서
 * 안 뽑고 NaverArticleScraper가 각 기사 페이지에서 따로 가져온다(그쪽이 더 안정적).
 */
@Component
public class NaverSearchScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;
    private static final Pattern ARTICLE_URL = Pattern.compile("^https://n\\.news\\.naver\\.com/(mnews/)?article/\\d+/\\d+");
    private static final int RESULTS_PER_PAGE = 10;
    private static final int MAX_PAGES = 6;

    /**
     * sort=1(최신순)로 검색해서 상위 limit개 기사 URL을 반환한다. 검색결과 한 페이지(10건)
     * 중 네이버 자체 호스팅(n.news.naver.com) 기사는 절반 이하인 경우가 많아서, limit을
     * 채울 때까지(또는 MAX_PAGES까지) 페이지를 넘기며 모은다.
     */
    public List<String> searchArticleUrls(String query, int limit) {
        Set<String> urls = new LinkedHashSet<>();
        for (int page = 0; page < MAX_PAGES && urls.size() < limit; page++) {
            int start = page * RESULTS_PER_PAGE + 1;
            urls.addAll(fetchPage(query, start));
        }
        return urls.stream().limit(limit).toList();
    }

    private List<String> fetchPage(String query, int start) {
        try {
            Document doc = Jsoup.connect("https://search.naver.com/search.naver")
                    .data("where", "news")
                    .data("query", query)
                    .data("sort", "1")
                    .data("start", String.valueOf(start))
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            List<String> urls = new ArrayList<>();
            for (Element a : doc.select("a[href]")) {
                String normalized = stripQuery(a.attr("href"));
                if (ARTICLE_URL.matcher(normalized).find()) {
                    urls.add(normalized);
                }
            }
            return urls;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private String stripQuery(String url) {
        int idx = url.indexOf('?');
        return idx >= 0 ? url.substring(0, idx) : url;
    }
}
