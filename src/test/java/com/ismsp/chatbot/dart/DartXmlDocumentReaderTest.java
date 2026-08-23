package com.ismsp.chatbot.dart;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

class DartXmlDocumentReaderTest {

    /**
     * 실제 DART document.xml 구조를 축약한 표본. S&amp;P가 아니라 "S&P"로 이스케이프
     * 안 된 &를 그대로 넣어서, 표준 XML 파서였다면 깨졌을 케이스를 검증한다.
     */
    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <DOCUMENT>
            <DOCUMENT-NAME ACODE="11012">반기보고서</DOCUMENT-NAME>
            <COMPANY-NAME AREGCIK="00126380">삼성전자주식회사</COMPANY-NAME>
            <COVER-TITLE ATOC="Y">반 기 보 고 서</COVER-TITLE>
            <P>표지 안내 문구</P>
            <TITLE ATOC="Y">I. 회사의 개요</TITLE>
            <P>회사의 개요 섹션 본문입니다.</P>
            <TITLE ATOC="Y" AASSOCNOTE="D-0-1-1-0">1. 회사의 개요</TITLE>
            <P>신용등급 현황</P>
            <TABLE>
            <TR><TD>S&P</TD><TD>BBB+</TD><TD>2026.02.05</TD></TR>
            </TABLE>
            <TITLE ATOC="N"></TITLE>
            <P>목차에는 없는 내부 표시용 빈 타이틀 뒤 문단</P>
            </DOCUMENT>
            """;

    @Test
    void 목차_구조대로_섹션이_나뉜다() {
        List<Document> documents = new DartXmlDocumentReader(
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8), "sample.xml").get();

        assertThat(documents).extracting(d -> d.getMetadata().get("section_title"))
                .containsExactly("표지", "I. 회사의 개요", "1. 회사의 개요");
    }

    @Test
    void 로마숫자와_아라비아숫자_제목의_레벨이_다르게_매겨진다() {
        List<Document> documents = new DartXmlDocumentReader(
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8), "sample.xml").get();

        Document romanSection = documents.get(1);
        Document numberSection = documents.get(2);

        assertThat(romanSection.getMetadata().get("section_level")).isEqualTo(1);
        assertThat(numberSection.getMetadata().get("section_level")).isEqualTo(2);
    }

    @Test
    void 이스케이프_안된_앰퍼샌드가_있어도_깨지지_않고_표가_행단위로_펼쳐진다() {
        List<Document> documents = new DartXmlDocumentReader(
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8), "sample.xml").get();

        Document numberSection = documents.get(2);
        assertThat(numberSection.getText()).contains("S&P | BBB+ | 2026.02.05");
    }

    @Test
    void ATOC가_N인_제목은_섹션_경계로_취급하지_않는다() {
        List<Document> documents = new DartXmlDocumentReader(
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8), "sample.xml").get();

        // "1. 회사의 개요" 섹션이 마지막 섹션이고, 그 뒤 ATOC="N" 타이틀 이후 문단까지 흡수한다.
        Document lastSection = documents.get(documents.size() - 1);
        assertThat(lastSection.getText()).contains("목차에는 없는 내부 표시용 빈 타이틀 뒤 문단");
    }

    @Test
    void source_file_메타데이터가_그대로_들어간다() {
        List<Document> documents = new DartXmlDocumentReader(
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8), "sample.xml").get();

        assertThat(documents).allSatisfy(d ->
                assertThat(d.getMetadata().get("source_file")).isEqualTo("sample.xml"));
    }
}
