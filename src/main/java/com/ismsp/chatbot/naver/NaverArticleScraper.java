package com.ismsp.chatbot.naver;

import java.io.IOException;
import java.util.Optional;

import com.ismsp.chatbot.naver.dto.ScrapedArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 네이버뉴스 기사 페이지(n.news.naver.com)에서 본문을 크롤링한다. 셀렉터는
 * graphrag-tools-retriever 참고 프로젝트의 DataScrapping.ipynb(parse_article_detail)를
 * 그대로 이식했다. 네이버뉴스 기사 페이지는 서버사이드 렌더링이라 Selenium 없이
 * Jsoup(정적 HTML 파서)만으로 충분하다.
 */
@Component
public class NaverArticleScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;

    private static final String[] CONTENT_SELECTORS = {
            "#dic_area", "article#dic_area", ".go_trans._article_content", "._article_body_contents"
    };
    private static final String[] TITLE_SELECTORS = {
            "#title_area span", "#ct .media_end_head_headline", ".media_end_head_headline",
            "h2#title_area", ".news_end_title"
    };

    /** 구조가 안 맞거나(요즘 개편된 페이지 등) 네트워크 오류면 빈 값 - 호출부가 description으로 폴백한다. */
    public Optional<ScrapedArticle> fetchArticle(String url) {
        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();

            String content = firstNonBlankText(doc, CONTENT_SELECTORS);
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }
            String title = firstNonBlankText(doc, TITLE_SELECTORS);
            String press = extractPress(doc);
            String publishedDate = extractPublishedDate(doc);

            return Optional.of(new ScrapedArticle(title, content, press, publishedDate));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private String firstNonBlankText(Document doc, String[] selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null && !el.text().isBlank()) {
                return el.text().trim();
            }
        }
        return null;
    }

    private String extractPress(Document doc) {
        Element logoImg = doc.selectFirst("a.media_end_head_top_logo img");
        if (logoImg != null && StringUtils.hasText(logoImg.attr("alt"))) {
            return logoImg.attr("alt").trim();
        }
        Element logoText = doc.selectFirst(".media_end_head_top_logo_text");
        return logoText != null ? logoText.text().trim() : null;
    }

    private String extractPublishedDate(Document doc) {
        Element dateEl = doc.selectFirst("span.media_end_head_info_datestamp_time, span[data-date-time]");
        if (dateEl == null) {
            return null;
        }
        String dataDateTime = dateEl.attr("data-date-time");
        return StringUtils.hasText(dataDateTime) ? dataDateTime.trim() : dateEl.text().trim();
    }
}
