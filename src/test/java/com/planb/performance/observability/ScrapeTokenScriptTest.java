package com.planb.performance.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prometheus 임시 JWT 발급 스크립트 검증
 */
class ScrapeTokenScriptTest {

    private static final Path SCRIPT = Path.of("src/test/observability/scrape-token.sh");
    private static final String JWT = "fake-header.fake-payload.fake-signature";

    private HttpServer server;

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private volatile int createStatus = 201;

    private volatile String loginAuthorization = "Bearer " + JWT;

    @TempDir
    Path directory;

    @BeforeEach
    void startFakeApplication() throws IOException {

        assumeTrue(
                exists("bash") && exists("curl"),
                "bash와 curl이 있어야 스크립트를 실행할 수 있음"
        );

        server = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );

        server.createContext("/api/v1/user/create", exchange -> {
            record(exchange);
            reply(exchange, createStatus, null);
        });

        server.createContext("/login", exchange -> {
            record(exchange);
            reply(exchange, 200, loginAuthorization);
        });

        server.start();
    }

    @AfterEach
    void stopFakeApplication() {

        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Bearer 접두사를 뗀 raw JWT만 개행 없이 기록")
    void writesRawJwtWithoutPrefixOrNewline() throws Exception {

        Path token = directory.resolve("token");

        Result result = run("create", token);

        assertThat(result.exitCode).isZero();
        assertThat(Files.readString(token)).isEqualTo(JWT);
    }

    @Test
    @DisplayName("파일은 소유자만 읽고 쓸 수 있음")
    void tokenFileIsOwnerOnly() throws Exception {

        Path token = directory.resolve("token");

        run("create", token);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(token)))
                .isEqualTo("rw-------");
    }

    @Test
    @DisplayName("토큰을 표준 출력과 오류 출력에 남기지 않음")
    void neverPrintsTheToken() throws Exception {

        Result result = run("create", directory.resolve("token"));

        assertThat(result.output).doesNotContain(JWT);
    }

    @Test
    @DisplayName("가입 요청과 로그인 요청은 같은 계정으로 전송")
    void signsUpAndLogsInWithTheSameAccount() throws Exception {

        run("create", directory.resolve("token"));

        assertThat(requests).hasSize(2);

        String username = field(requests.get(0), "username");

        assertThat(username).endsWith("@example.com");
        assertThat(field(requests.get(1), "username")).isEqualTo(username);
        assertThat(field(requests.get(1), "password"))
                .isEqualTo(field(requests.get(0), "password"));
    }

    @Test
    @DisplayName("계정 생성이 실패하면 실패로 끝나고 파일을 남기지 않음")
    void failsWhenSignUpIsRejected() throws Exception {

        createStatus = 409;
        Path token = directory.resolve("token");

        Result result = run("create", token);

        assertThat(result.exitCode).isNotZero();
        assertThat(token).doesNotExist();
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("로그인 응답에 Bearer 토큰이 없으면 실패로 끝나고 파일을 남기지 않음")
    void failsWhenLoginHasNoBearerToken() throws Exception {

        loginAuthorization = "Basic abc";
        Path token = directory.resolve("token");

        Result result = run("create", token);

        assertThat(result.exitCode).isNotZero();
        assertThat(token).doesNotExist();
    }

    @Test
    @DisplayName("애플리케이션에 연결할 수 없으면 원인을 출력하고 실패로 끝남")
    void explainsWhenTheApplicationIsUnreachable() throws Exception {

        server.stop(0);
        Path token = directory.resolve("token");

        Result result = run("create", token);

        assertThat(result.exitCode).isNotZero();
        assertThat(result.output).contains("계정 생성 실패");
        assertThat(token).doesNotExist();
    }

    @Test
    @DisplayName("remove는 파일을 지우고 이미 없어도 성공")
    void removeDeletesAndIsIdempotent() throws Exception {

        Path token = directory.resolve("token");
        Files.writeString(token, JWT);

        assertThat(run("remove", token).exitCode).isZero();
        assertThat(token).doesNotExist();

        assertThat(run("remove", token).exitCode).isZero();
    }

    private Result run(
            String command,
            Path token
    ) throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
                "bash",
                SCRIPT.toAbsolutePath().toString(),
                command,
                token.toString()
        )
                .redirectErrorStream(true);

        builder
                .environment()
                .put("BASE_URL", "http://localhost:" + server.getAddress().getPort());

        Process process = builder.start();

        String output = new String(
                process
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        return new Result(
                process.waitFor(),
                output
        );
    }

    private void record(HttpExchange exchange) throws IOException {

        requests.add(new String(
                exchange
                        .getRequestBody()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        ));
    }

    private void reply(
            HttpExchange exchange,
            int status,
            String authorization
    ) throws IOException {

        if (authorization != null) {
            exchange
                    .getResponseHeaders()
                    .set("Authorization", authorization);
        }

        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    // 요청 본문은 평평한 JSON이라 정규식 한 줄로 값을 꺼낸다.
    private String field(
            String body,
            String name
    ) {

        var matcher = java.util.regex.Pattern
                .compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(body);

        assertThat(matcher.find())
                .as("%s 필드", name)
                .isTrue();

        return matcher.group(1);
    }

    private boolean exists(String command) {

        try {
            return new ProcessBuilder("sh", "-c", "command -v " + command)
                    .start()
                    .waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private record Result(
            int exitCode,
            String output
    ) {
    }
}
