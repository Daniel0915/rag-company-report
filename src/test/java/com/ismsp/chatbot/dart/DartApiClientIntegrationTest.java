package com.ismsp.chatbot.dart;

import java.time.LocalDate;
import java.util.Map;

import com.ismsp.chatbot.dart.dto.DartListResponse;
import com.ismsp.chatbot.dart.dto.WatchedCompany;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DART Open API를 호출하는 검증용 테스트.
 * DART_API_KEY 환경변수가 없으면 자동으로 스킵된다(평상시 빌드/CI에서 네트워크 호출 방지).
 */
@EnabledIfEnvironmentVariable(named = "DART_API_KEY", matches = ".+")
class DartApiClientIntegrationTest {

    static final Iterable<WatchedCompany> COMPANIES = WatchedCompany.ALL;

    private final DartApiClient client = new DartApiClient(
            RestClient.builder(),
            "https://opendart.fss.or.kr/api",
            System.getenv("DART_API_KEY")
    );

    @ParameterizedTest
    @FieldSource("COMPANIES")
    void 워치리스트_기업의_최근_공시목록을_조회한다(WatchedCompany company) {
        DartListResponse response = client.searchDisclosures(
                company.corpCode(), LocalDate.of(2026, 1, 1), LocalDate.now(), 1, 5);

        assertThat(response.status()).isIn("000", "013");
        if (response.hasData()) {
            assertThat(response.list()).isNotEmpty();
            System.out.printf("[%s] %d건 중 최신: %s (%s)%n",
                    company.name(), response.totalCount(),
                    response.list().get(0).reportNm(), response.list().get(0).rceptNo());
        }
    }

    @ParameterizedTest
    @FieldSource("COMPANIES")
    void 최근_공시_원본문서를_다운로드한다(WatchedCompany company) {
        DartListResponse listResponse = client.searchDisclosures(
                company.corpCode(), LocalDate.of(2026, 1, 1), LocalDate.now(), 1, 1);
        assertThat(listResponse.hasData()).isTrue();

        String rceptNo = listResponse.list().get(0).rceptNo();
        Map<String, byte[]> files = client.fetchDocument(rceptNo);

        assertThat(files).isNotEmpty();
        files.forEach((name, bytes) -> System.out.printf("[%s] %s -> %d bytes%n", company.name(), name, bytes.length));
    }
}
