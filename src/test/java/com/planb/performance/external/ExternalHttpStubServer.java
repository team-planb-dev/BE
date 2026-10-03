package com.planb.performance.external;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 여행 부하 테스트용 외부 HTTP 응답 스텁
 * 고정 fixture와 요청 파라미터로 생성하는 결정적 응답
 */
public final class ExternalHttpStubServer implements AutoCloseable {

    public enum Api {

        KOR2("/kor2", "kor2"),
        KAKAO_MAP("/kakao-map", "kakao"),
        KAKAO_MOBILITY("/kakao-mobility", "kakao"),
        FOOD_NUTRITION("/food-nutrition", "food-nutrition");

        private final String prefix;
        private final String fixtureDirectory;

        Api(
                String prefix,
                String fixtureDirectory
        ) {
            this.prefix = prefix;
            this.fixtureDirectory = fixtureDirectory;
        }

        public String prefix() {
            return prefix;
        }
    }

    public enum Scenario {

        NORMAL,
        DELAY,
        CLIENT_ERROR,
        SERVER_ERROR,
        TIMEOUT;

        static Scenario parse(String value) {
            return valueOf(value
                    .strip()
                    .toUpperCase()
                    .replace('-', '_'));
        }
    }

    /**
     * API별 응답 시나리오와 지연 시간 설정
     */
    public record Settings(
            Scenario defaultScenario,
            Map<Api, Scenario> overrides,
            Duration delay,
            Duration timeout
    ) {

        private static final Duration DEFAULT_DELAY = Duration.ofMillis(800);
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

        public static Settings normal() {

            return new Settings(
                    Scenario.NORMAL,
                    Map.of(),
                    DEFAULT_DELAY,
                    DEFAULT_TIMEOUT
            );
        }

        /**
         * 환경변수에 따른 스텁 응답 설정
         */
        public static Settings fromEnvironment(Map<String, String> environment) {

            Map<Api, Scenario> overrides = new EnumMap<>(Api.class);

            for (Api api : Api.values()) {
                Optional
                        .ofNullable(environment.get("STUB_SCENARIO_" + api.name()))
                        .map(Scenario::parse)
                        .ifPresent(scenario -> overrides.put(api, scenario));
            }

            return new Settings(
                    Optional
                            .ofNullable(environment.get("STUB_SCENARIO"))
                            .map(Scenario::parse)
                            .orElse(Scenario.NORMAL),
                    overrides,
                    millis(
                            environment,
                            "STUB_DELAY_MS",
                            DEFAULT_DELAY
                    ),
                    millis(
                            environment,
                            "STUB_TIMEOUT_MS",
                            DEFAULT_TIMEOUT
                    )
            );
        }

        public Settings with(
                Api api,
                Scenario scenario
        ) {

            Map<Api, Scenario> changed = new EnumMap<>(Api.class);
            changed.putAll(overrides);
            changed.put(api, scenario);

            return new Settings(
                    defaultScenario,
                    changed,
                    delay,
                    timeout
            );
        }

        public Scenario scenarioOf(Api api) {
            return overrides.getOrDefault(api, defaultScenario);
        }

        private static Duration millis(
                Map<String, String> environment,
                String key,
                Duration fallback
        ) {

            return Optional
                    .ofNullable(environment.get(key))
                    .map(value -> Duration.ofMillis(Long.parseLong(value.strip())))
                    .orElse(fallback);
        }
    }

    public record RecordedRequest(
            Api api,
            String path,
            Map<String, String> query
    ) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(param|hash):([A-Za-z_]+)}}");

    private final HttpServer server;
    private final ExecutorService executor;
    private final Settings settings;
    private final Map<Api, Queue<RecordedRequest>> requests = new EnumMap<>(Api.class);
    private final Map<String, Optional<String>> fixtures = new ConcurrentHashMap<>();

    private ExternalHttpStubServer(
            HttpServer server,
            ExecutorService executor,
            Settings settings
    ) {

        this.server = server;
        this.executor = executor;
        this.settings = settings;

        for (Api api : Api.values()) {
            requests.put(api, new ConcurrentLinkedQueue<>());
        }
    }

    /**
     * 지정 포트의 스텁 시작, 0은 임의 포트
     */
    public static ExternalHttpStubServer start(
            int port,
            Settings settings
    ) {

        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(
                            "localhost",
                            port
                    ),
                    0
            );

            // 요청별 스레드 점유가 필요한 지연·timeout 시나리오의 가상 스레드 처리
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);

            ExternalHttpStubServer stub = new ExternalHttpStubServer(
                    server,
                    executor,
                    settings
            );

            for (Api api : Api.values()) {
                server.createContext(
                        api.prefix,
                        exchange -> stub.handle(
                                api,
                                exchange
                        )
                );
            }

            server.createContext(
                    "/__stub/requests",
                    stub::handleAdmin
            );

            server.start();

            return stub;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public String baseUrl(Api api) {
        return "http://localhost:" + server
                .getAddress()
                .getPort() + api.prefix;
    }

    public List<RecordedRequest> requests(Api api) {
        return List.copyOf(requests.get(api));
    }

    public void clear() {
        requests
                .values()
                .forEach(Queue::clear);
    }

    @Override
    public void close() {

        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(
            Api api,
            HttpExchange exchange
    ) throws IOException {

        String path = exchange
                .getRequestURI()
                .getPath()
                .substring(api.prefix.length());

        Map<String, String> query = parseQuery(exchange
                .getRequestURI()
                .getRawQuery());

        requests
                .get(api)
                .add(new RecordedRequest(
                        api,
                        path,
                        query
                ));

        switch (settings.scenarioOf(api)) {
            case NORMAL -> respondWithFixture(
                    api,
                    path,
                    query,
                    exchange
            );

            case DELAY -> {
                pause(settings.delay());
                respondWithFixture(
                        api,
                        path,
                        query,
                        exchange
                );
            }

            case CLIENT_ERROR -> respond(
                    exchange,
                    401,
                    "{\"stub\":\"client-error\",\"message\":\"stubbed unauthorized\"}"
            );

            case SERVER_ERROR -> respond(
                    exchange,
                    503,
                    "{\"stub\":\"server-error\",\"message\":\"stubbed unavailable\"}"
            );

            // 응답 헤더 없이 연결을 닫는 외부 API 무응답 시뮬레이션
            case TIMEOUT -> {
                pause(settings.timeout());
                exchange.close();
            }
        }
    }

    private void respondWithFixture(
            Api api,
            String path,
            Map<String, String> query,
            HttpExchange exchange
    ) throws IOException {

        Optional<String> body = fixtureName(
                api,
                path,
                query
        )
                .flatMap(name -> fixture(api.fixtureDirectory + "/" + name));

        if (body.isEmpty()) {
            respond(
                    exchange,
                    404,
                    "{\"stub\":\"no-fixture\",\"path\":\"" + escape(path) + "\"}"
            );
            return;
        }

        respond(
                exchange,
                200,
                fill(
                        body.get(),
                        query
                )
        );
    }

    // 경로·파라미터별 fixture 선택, 파일이 없으면 빈 값과 404 응답
    private Optional<String> fixtureName(
            Api api,
            String path,
            Map<String, String> query
    ) {

        String restaurantOrAttraction = "39".equals(query.get("contentTypeId"))
                ? "restaurants.json"
                : "attractions.json";

        return Optional.ofNullable(switch (api) {
            case KOR2 -> switch (path) {
                case "/areaCode2" -> query.containsKey("areaCode")
                        ? "sigungu-codes.json"
                        : "area-codes.json";

                case "/areaBasedList2", "/searchKeyword2" -> restaurantOrAttraction;

                case "/detailIntro2" -> fixture("kor2/detail-intro/" + query.get("contentId") + ".json")
                        .isPresent()
                        ? "detail-intro/" + query.get("contentId") + ".json"
                        : "detail-intro/default.json";

                default -> null;
            };

            case KAKAO_MAP -> switch (path) {
                case "/v2/local/search/keyword.json" -> "place-search.json";
                case "/v2/routing/publictraffic" -> "public-traffic-route.json";
                default -> null;
            };

            case KAKAO_MOBILITY -> "/v1/directions".equals(path)
                    ? "car-route.json"
                    : null;

            case FOOD_NUTRITION -> "/getFoodNtrCpntDbInq02".equals(path)
                    ? "food-nutrition.json"
                    : null;
        });
    }

    private Optional<String> fixture(String name) {

        return fixtures.computeIfAbsent(
                name,
                key -> {
                    try (InputStream stream = ExternalHttpStubServer.class
                            .getClassLoader()
                            .getResourceAsStream("loadtest/" + key)) {
                        return stream == null
                                ? Optional.empty()
                                : Optional.of(new String(
                                        stream.readAllBytes(),
                                        StandardCharsets.UTF_8
                                ));
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }
        );
    }

    // {{param:이름}}의 요청 값 치환과 {{hash:이름}}의 고정 숫자 치환
    private String fill(
            String body,
            Map<String, String> query
    ) {

        Matcher matcher = PLACEHOLDER.matcher(body);
        StringBuilder filled = new StringBuilder();

        while (matcher.find()) {
            String value = query.getOrDefault(matcher.group(2), "");

            String replacement = "param".equals(matcher.group(1))
                    ? escape(value)
                    : Integer.toUnsignedString(value.hashCode());

            matcher.appendReplacement(
                    filled,
                    Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(filled);

        return filled.toString();
    }

    // 단독 실행 부하 테스트의 호출 수 조회·초기화
    private void handleAdmin(HttpExchange exchange) throws IOException {

        if ("DELETE".equals(exchange.getRequestMethod())) {
            clear();
            respond(
                    exchange,
                    200,
                    "{}"
            );
            return;
        }

        String counts = Arrays
                .stream(Api.values())
                .map(api -> "\"" + api.name() + "\":{" + requests
                        .get(api)
                        .stream()
                        .collect(Collectors.groupingBy(
                                RecordedRequest::path,
                                LinkedHashMap::new,
                                Collectors.counting()
                        ))
                        .entrySet()
                        .stream()
                        .map(entry -> "\"" + escape(entry.getKey()) + "\":" + entry.getValue())
                        .collect(Collectors.joining(",")) + "}")
                .collect(Collectors.joining(
                        ",",
                        "{",
                        "}"
                ));

        respond(
                exchange,
                200,
                counts
        );
    }

    private static Map<String, String> parseQuery(String rawQuery) {

        Map<String, String> query = new LinkedHashMap<>();

        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');

            String name = separator < 0
                    ? pair
                    : pair.substring(0, separator);

            String value = separator < 0
                    ? ""
                    : pair.substring(separator + 1);

            query.put(
                    URLDecoder.decode(name, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }

        return query;
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange
                .getResponseHeaders()
                .set("Content-Type", "application/json;charset=UTF-8");

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void pause(Duration duration) {

        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread
                    .currentThread()
                    .interrupt();
        }
    }

    private static String escape(String value) {

        StringBuilder escaped = new StringBuilder();

        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }

        return escaped.toString();
    }

    /**
     * 기본 포트 18080의 부하 테스트용 스텁 단독 실행
     */
    public static void main(String[] args) throws InterruptedException {

        int port = Integer.parseInt(System
                .getenv()
                .getOrDefault("STUB_PORT", "18080"));

        Settings settings = Settings.fromEnvironment(System.getenv());

        ExternalHttpStubServer stub = start(
                port,
                settings
        );

        CountDownLatch stopped = new CountDownLatch(1);

        Runtime
                .getRuntime()
                .addShutdownHook(new Thread(() -> {
                    stub.close();
                    stopped.countDown();
                }));

        for (Api api : Api.values()) {
            System.out.println(api + " " + stub.baseUrl(api) + " " + settings.scenarioOf(api));
        }

        stopped.await();
    }
}
