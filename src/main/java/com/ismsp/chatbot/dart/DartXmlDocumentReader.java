package com.ismsp.chatbot.dart;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.web.util.HtmlUtils;

/**
 * DART 공시서류 원본(document.xml 안의 XML)을 목차(TITLE ATOC="Y") 단위로 쪼개서
 * Spring AI Document로 만든다. DART XML은 완전한 well-formed XML이 아니라서
 * (예: "S&P" 처럼 이스케이프 안 된 &) 표준 XML 파서 대신 정규식으로 태그를 다룬다.
 */
public class DartXmlDocumentReader implements DocumentReader {

    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TR_PATTERN = Pattern.compile("<TR\\b[^>]*>(.*?)</TR>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_PATTERN = Pattern.compile("<(?:TD|TH|TE|TU)\\b[^>]*>(.*?)</(?:TD|TH|TE|TU)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<TITLE\\b([^>]*)>(.*?)</TITLE>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ATOC_PATTERN = Pattern.compile("ATOC=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    private static final Pattern ROMAN_HEADING = Pattern.compile("^[IVXLCM]+[.가-힣]");
    private static final Pattern SUB_NUMBER_HEADING = Pattern.compile("^\\d+-\\d+[.)]");
    private static final Pattern NUMBER_HEADING = Pattern.compile("^\\d+[.)]");
    private static final Pattern HANGUL_ORDINAL_HEADING = Pattern.compile("^[가-힣][.)]");
    private static final Pattern PAREN_NUMBER_HEADING = Pattern.compile("^\\(\\d+\\)");

    private record Heading(int tagStart, int tagEnd, int level, String title) {
    }

    private final byte[] xmlBytes;
    private final String sourceFile;

    /**
     * @param xmlBytes   document.xml zip 안에 들어있던 개별 xml 파일의 원본 바이트
     * @param sourceFile 원본 파일명 (예: "20260814003699.xml", "20230307000542_00760.xml")
     */
    public DartXmlDocumentReader(byte[] xmlBytes, String sourceFile) {
        this.xmlBytes = xmlBytes;
        this.sourceFile = sourceFile;
    }

    @Override
    public List<Document> get() {
        String content = new String(xmlBytes, StandardCharsets.UTF_8);
        content = COMMENT_PATTERN.matcher(content).replaceAll("");
        content = flattenTables(content);

        List<Heading> headings = findHeadings(content);
        List<Document> documents = new ArrayList<>();
        int order = 0;

        int firstStart = headings.isEmpty() ? content.length() : headings.get(0).tagStart();
        String coverBody = stripTags(content.substring(0, firstStart));
        if (!coverBody.isBlank()) {
            documents.add(buildDocument("표지", 0, order++, coverBody));
        }

        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int bodyEnd = (i + 1 < headings.size()) ? headings.get(i + 1).tagStart() : content.length();
            String body = stripTags(content.substring(heading.tagEnd(), bodyEnd));
            // 본문 없이 곧바로 하위 헤딩으로 이어지는 상위 목차(예: "II. 사업의 내용")는
            // 제목만 있는 빈 청크가 되어 실제 내용 청크보다 검색 유사도가 더 높게 나오므로 건너뛴다.
            if (body.isBlank()) {
                continue;
            }
            String text = heading.title() + "\n\n" + body;
            documents.add(buildDocument(heading.title(), heading.level(), order++, text));
        }

        return documents;
    }

    private Document buildDocument(String sectionTitle, int level, int order, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_file", sourceFile);
        metadata.put("section_title", sectionTitle);
        metadata.put("section_level", level);
        metadata.put("section_order", order);
        return new Document(text, metadata);
    }

    private List<Heading> findHeadings(String content) {
        List<Heading> headings = new ArrayList<>();
        Matcher matcher = TITLE_PATTERN.matcher(content);
        while (matcher.find()) {
            Matcher atocMatcher = ATOC_PATTERN.matcher(matcher.group(1));
            String atoc = atocMatcher.find() ? atocMatcher.group(1) : "N";
            String title = cleanInline(matcher.group(2));
            if ("Y".equals(atoc) && !title.isEmpty()) {
                headings.add(new Heading(matcher.start(), matcher.end(), headingLevel(title), title));
            }
        }
        return headings;
    }

    private static int headingLevel(String text) {
        String t = text.strip();
        if (t.startsWith("【")) return 1;
        if (ROMAN_HEADING.matcher(t).lookingAt()) return 1;
        if (SUB_NUMBER_HEADING.matcher(t).lookingAt()) return 3;
        if (NUMBER_HEADING.matcher(t).lookingAt()) return 2;
        if (HANGUL_ORDINAL_HEADING.matcher(t).lookingAt()) return 4;
        if (PAREN_NUMBER_HEADING.matcher(t).lookingAt()) return 5;
        return 3;
    }

    private static String flattenTables(String content) {
        Matcher matcher = TR_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(content, last, matcher.start());
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_PATTERN.matcher(matcher.group(1));
            while (cellMatcher.find()) {
                String cellText = cleanInline(cellMatcher.group(1));
                if (!cellText.isEmpty()) {
                    cells.add(cellText);
                }
            }
            if (!cells.isEmpty()) {
                result.append('\n').append(String.join(" | ", cells)).append('\n');
            }
            last = matcher.end();
        }
        result.append(content, last, content.length());
        return result.toString();
    }

    private static String cleanInline(String text) {
        String noTags = TAG_PATTERN.matcher(text).replaceAll(" ");
        String unescaped = HtmlUtils.htmlUnescape(noTags);
        return unescaped.replaceAll("\\s+", " ").strip();
    }

    private static String stripTags(String text) {
        String noTags = TAG_PATTERN.matcher(text).replaceAll("\n");
        String unescaped = HtmlUtils.htmlUnescape(noTags);
        StringBuilder result = new StringBuilder();
        for (String line : unescaped.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append('\n');
                }
                result.append(trimmed);
            }
        }
        return result.toString();
    }
}
