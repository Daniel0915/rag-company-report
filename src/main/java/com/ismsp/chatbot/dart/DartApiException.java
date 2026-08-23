package com.ismsp.chatbot.dart;

public class DartApiException extends RuntimeException {

    private final String status;

    public DartApiException(String status, String message) {
        super("DART API 오류 [%s]: %s".formatted(status, message));
        this.status = status;
    }

    public String status() {
        return status;
    }
}
