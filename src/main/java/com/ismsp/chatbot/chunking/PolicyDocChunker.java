package com.ismsp.chatbot.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * 소기업 정책 문서는 "제N조" 형식을 따르지 않는 경우가 많다 — 전체 텍스트에서 조문
 * 마커 밀도를 먼저 검사하고, 충분하면 조 단위로, 아니면 일반 토큰 분할로 폴백한다.
 * 각 청크에 chunkStrategy를 태깅해 어느 경로를 탔는지 추적한다. (TypeScript 버전의
 * src/modules/isms-p/chunkPolicyDoc.ts와 동일한 로직을 Spring AI Document로 포팅.)
 */
public class PolicyDocChunker {

    private static final Pattern ARTICLE_RE = Pattern.compile("제\\s*\\d+\\s*조(?:의\\s*\\d+)?");
    private static final int MIN_ARTICLES_FOR_ARTICLE_SPLIT = 3;
    private static final int MAX_ARTICLE_CHUNK_LENGTH = 1200;

    private final TokenTextSplitter fallbackSplitter = new TokenTextSplitter();

    public List<Document> chunk(List<Document> pages) {
        String fullText = pages.stream().map(Document::getText).reduce("", (a, b) -> a + "\n" + b);
        long articleCount = ARTICLE_RE.matcher(fullText).results().count();

        if (articleCount < MIN_ARTICLES_FOR_ARTICLE_SPLIT) {
            List<Document> chunks = fallbackSplitter.apply(pages);
            chunks.forEach(c -> c.getMetadata().put("chunk_strategy", "recursive"));
            return chunks;
        }

        List<Document> articleChunks = new ArrayList<>();
        for (Document page : pages) {
            String text = page.getText();
            Matcher matcher = ARTICLE_RE.matcher(text);
            List<Integer> starts = new ArrayList<>();
            while (matcher.find()) {
                starts.add(matcher.start());
            }

            if (starts.isEmpty()) {
                articleChunks.add(taggedCopy(page, text));
                continue;
            }

            for (int i = 0; i < starts.size(); i++) {
                int start = starts.get(i);
                int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
                String articleText = text.substring(start, end).trim();
                if (articleText.isEmpty()) continue;

                if (articleText.length() <= MAX_ARTICLE_CHUNK_LENGTH) {
                    articleChunks.add(taggedCopy(page, articleText));
                } else {
                    Document articleDoc = taggedCopy(page, articleText);
                    articleChunks.addAll(fallbackSplitter.apply(List.of(articleDoc)));
                    // sub-split chunks keep "article" from the parent's metadata copy above.
                }
            }
        }
        return articleChunks;
    }

    private Document taggedCopy(Document source, String text) {
        Map<String, Object> metadata = new java.util.HashMap<>(source.getMetadata());
        metadata.put("chunk_strategy", "article");
        return new Document(text, metadata);
    }
}
