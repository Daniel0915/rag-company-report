package com.ismsp.chatbot.dart.dto;

import java.util.List;

public record WatchedCompany(String name, String corpCode, String stockCode) {

    public static final List<WatchedCompany> ALL = List.of(
            new WatchedCompany("삼성전자", "00126380", "005930"),
            new WatchedCompany("F&F", "01568413", "383220")
    );
}
