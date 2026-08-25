package com.ismsp.chatbot.claude;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * 로컬에 로그인되어 있는 Claude Code CLI(`claude -p`)를 그대로 호출해서 답변을 받는다.
 * ANTHROPIC_API_KEY(종량제 API) 대신 claude.ai 구독 계정 로그인 기반으로 동작한다는
 * 프로젝트 방침(CLAUDE.md)에 맞춰, API 키 없이 이미 로그인된 CLI 세션을 그대로 쓴다.
 * `--bare`는 ANTHROPIC_API_KEY를 강제로 요구해서 쓰지 않는다.
 */
@Component
public class ClaudeCliClient {

    private static final long TIMEOUT_SECONDS = 60;

    public String generate(String systemPrompt, String userPrompt) {
        List<String> command = List.of(
                "claude", "-p", userPrompt,
                "--system-prompt", systemPrompt,
                "--model", "sonnet",
                "--output-format", "text",
                "--allowedTools", ""
        );

        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(new File(System.getProperty("java.io.tmpdir")))
                    // stdin을 안 닫아두면 claude가 파이프 입력을 3초간 기다리다 경고를 찍는다.
                    // 우리는 프롬프트를 인자로만 주니 즉시 EOF를 줘서 그 대기/경고를 없앤다.
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("claude CLI 실행 실패 (설치/PATH 확인 필요)", e);
        }

        // stdout/stderr를 각각 별도 스레드로 동시에 비워야 한다 - 순서대로 읽으면 한쪽
        // 파이프가 가득 찼을 때 자식 프로세스가 거기 막혀서 다른 쪽도 영영 안 끝날 수 있다.
        StreamReader stdout = new StreamReader(process.getInputStream());
        StreamReader stderr = new StreamReader(process.getErrorStream());
        Thread stdoutThread = new Thread(stdout);
        Thread stderrThread = new Thread(stderr);
        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stdoutThread.join(TimeUnit.SECONDS.toMillis(5));
            stderrThread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("claude CLI 호출 중 오류", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("claude CLI 응답이 " + TIMEOUT_SECONDS + "초 안에 오지 않았습니다");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("claude CLI 오류 (exit " + process.exitValue() + "): " + stderr.text().trim());
        }
        return stdout.text().trim();
    }

    private static final class StreamReader implements Runnable {
        private final java.io.InputStream in;
        private volatile String text = "";

        StreamReader(java.io.InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "";
            }
        }

        String text() {
            return text;
        }
    }
}
