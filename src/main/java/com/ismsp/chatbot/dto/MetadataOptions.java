package com.ismsp.chatbot.dto;

import java.util.List;

public record MetadataOptions(List<String> docTypes, List<DomainOption> domains) {

    public static final List<String> DOC_TYPES = List.of("정책서", "지침서", "매뉴얼", "절차서", "기타");
    public static final List<DomainOption> DOMAINS = List.of(
            new DomainOption("1", "1. 관리체계 수립 및 운영"),
            new DomainOption("2", "2. 보호대책 요구사항"),
            new DomainOption("3", "3. 개인정보 처리단계별 요구사항")
    );

    public static MetadataOptions defaults() {
        return new MetadataOptions(DOC_TYPES, DOMAINS);
    }
}
