package com.ismsp.chatbot.naver.dto;

/** NaverArticleScraper가 뉴스 기사 페이지에서 뽑아낸 결과. press/publishedDate는 못 찾으면 null일 수 있다. */
public record ScrapedArticle(String title, String content, String press, String publishedDate) {
}
