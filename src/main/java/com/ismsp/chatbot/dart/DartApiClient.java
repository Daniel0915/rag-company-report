package com.ismsp.chatbot.dart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ismsp.chatbot.dart.dto.CorpCode;
import com.ismsp.chatbot.dart.dto.DartListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DART Open API(https://opendart.fss.or.kr) 클라이언트.
 * 공시검색(list.json), 공시서류원본파일(document.xml), 고유번호(corpCode.xml)만 다룬다.
 * pdf.do는 브라우저 세션 기반 비공개 엔드포인트라 API로는 안정적으로 못 받으므로 다루지 않음.
 */
@Component
public class DartApiClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern STATUS_PATTERN = Pattern.compile("<status>([^<]*)</status>");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("<message>([^<]*)</message>");

    private final RestClient restClient;
    private final String apiKey;

    public DartApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${dart.api.base-url}") String baseUrl,
            @Value("${dart.api.key}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    /** 공시검색: 특정 기업의 기간 내 공시 목록을 조회한다. */
    public DartListResponse searchDisclosures(String corpCode, LocalDate bgnDe, LocalDate endDe, int pageNo, int pageCount) {
        return searchDisclosures(corpCode, bgnDe, endDe, null, pageNo, pageCount);
    }

    /**
     * 공시검색: pblntfTy(공시유형)로 좁혀서 조회한다. "A"=정기공시(사업/반기/분기보고서)처럼
     * 회사 정보 챗봇에 의미 있는 공시만 필터링할 때 쓴다. null이면 필터 없이 전체 조회.
     */
    public DartListResponse searchDisclosures(String corpCode, LocalDate bgnDe, LocalDate endDe, String pblntfTy, int pageNo, int pageCount) {
        DartListResponse response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/list.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", bgnDe.format(DATE_FORMAT))
                            .queryParam("end_de", endDe.format(DATE_FORMAT))
                            .queryParam("page_no", pageNo)
                            .queryParam("page_count", pageCount);
                    if (pblntfTy != null && !pblntfTy.isBlank()) {
                        uriBuilder.queryParam("pblntf_ty", pblntfTy);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(DartListResponse.class);

        if (response == null) {
            throw new DartApiException("UNKNOWN", "list.json 응답이 비어 있습니다");
        }
        if (!response.hasData() && !response.noData()) {
            throw new DartApiException(response.status(), response.message());
        }
        return response;
    }

    /** 공시서류원본파일: rcept_no의 원본 zip을 받아 파일명별 바이트로 풀어서 반환한다. */
    public Map<String, byte[]> fetchDocument(String rceptNo) {
        byte[] body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/document.xml")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("rcept_no", rceptNo)
                        .build())
                .retrieve()
                .body(byte[].class);

        requireZip(body);
        return unzipAll(body);
    }

    /** 고유번호: 전체 기업의 corp_code ↔ 기업명/종목코드 매핑을 받아온다 (약 30MB, 자주 호출할 필요는 없음). */
    public List<CorpCode> fetchCorpCodes() {
        byte[] body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/corpCode.xml").queryParam("crtfc_key", apiKey).build())
                .retrieve()
                .body(byte[].class);

        requireZip(body);
        Map<String, byte[]> entries = unzipAll(body);
        byte[] xml = entries.getOrDefault("CORPCODE.xml", entries.values().iterator().next());
        return parseCorpCodeXml(xml);
    }

    private void requireZip(byte[] body) {
        if (body == null) {
            throw new DartApiException("UNKNOWN", "응답이 비어 있습니다");
        }
        boolean isZip = body.length >= 2 && body[0] == 'P' && body[1] == 'K';
        if (!isZip) {
            throw parseErrorXml(body);
        }
    }

    private DartApiException parseErrorXml(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        Matcher statusMatcher = STATUS_PATTERN.matcher(text);
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(text);
        String status = statusMatcher.find() ? statusMatcher.group(1) : "UNKNOWN";
        String message = messageMatcher.find() ? messageMatcher.group(1) : text;
        return new DartApiException(status, message);
    }

    private Map<String, byte[]> unzipAll(byte[] zipBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        } catch (IOException e) {
            throw new IllegalStateException("공시서류 zip 압축 해제 실패", e);
        }
        return entries;
    }

    private List<CorpCode> parseCorpCodeXml(byte[] xmlBytes) {
        List<CorpCode> result = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try (ByteArrayInputStream in = new ByteArrayInputStream(xmlBytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            String corpCode = null, corpName = null, corpEngName = null, stockCode = null, modifyDate = null;
            String currentTag = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentTag = reader.getLocalName();
                    if ("list".equals(currentTag)) {
                        corpCode = corpName = corpEngName = stockCode = modifyDate = null;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && currentTag != null) {
                    String text = reader.getText().trim();
                    if (!text.isEmpty()) {
                        switch (currentTag) {
                            case "corp_code" -> corpCode = text;
                            case "corp_name" -> corpName = text;
                            case "corp_eng_name" -> corpEngName = text;
                            case "stock_code" -> stockCode = text;
                            case "modify_date" -> modifyDate = text;
                            default -> {
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT
                        && "list".equals(reader.getLocalName())
                        && corpCode != null) {
                    result.add(new CorpCode(corpCode, corpName, corpEngName, stockCode, modifyDate));
                }
            }
            reader.close();
        } catch (XMLStreamException | IOException e) {
            throw new IllegalStateException("CORPCODE.xml 파싱 실패", e);
        }
        return result;
    }
}
