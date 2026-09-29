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
 * Travel 부하 테스트용 외부 HTTP 스텁 서버.
 *
 * <p>Kor2Service, Kakao Map, Kakao Mobility, 식품영양성분 API를 하나의 로컬 서버에서 경로 접두어로
 * 나눠 흉내 낸다. 각 client의 {@code external.<이름>.base-url}을 {@link #baseUrl(Api)}로 바꾸면
 * 운영 코드 수정 없이 실제 외부 호출이 사라진다.
 *
 * <p>응답은 {@code src/test/resources/loadtest/} 아래 fixture에서 읽는다. 요청을 따라가야 하는 값
 * (음식명, Kakao 장소명과 ID)은 fixture의 {@code {{param:이름}}}, {@code {{hash:이름}}} 자리에
 * 요청 파라미터로 채운다. 같은 요청은 항상 같은 응답을 받는다.
 *
 * <p>단독 실행은 Gradle {@code externalHttpStub} 태스크를 쓴다. 설정은 {@link Settings#fromEnvironment}를 본다.
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
     * @param defaultScenario 모든 API에 적용할 시나리오
     * @param overrides       API별로 덮어쓸 시나리오
     * @param delay           DELAY 시나리오에서 응답을 늦출 시간
     * @param timeout         TIMEOUT 시나리오에서 응답 없이 연결을 붙잡을 시간
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
         * {@code STUB_SCENARIO}, {@code STUB_SCENARIO_<API>}, {@code STUB_DELAY_MS}, {@code STUB_TIMEOUT_MS}를 읽는다.
         * 시나리오 값은 {@code normal}, {@code delay}, {@code client-error}, {@code server-error}, {@code timeout}이다.
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
     * @param port 0이면 빈 포트를 고른다
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

            // 지연·timeout 시나리오에서 요청마다 스레드를 붙잡으므로 가상 스레드로 처리한다.
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

            // 응답 헤더를 보내지 않고 연결을 닫아, 외부 API가 응답하지 않는 상황을 흉내 낸다.
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
        ).flatMap(name -> fixture(api.fixtureDirectory + "/" + name));

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

    // 요청 경로와 파라미터로 fixture 파일을 고른다. 없으면 빈 값을 돌려 404로 드러낸다.
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

                case "/detailIntro2" -> fixture("kor2/detail-intro/" + query.get("contentId") + ".json").isPresent()
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

    // {{param:이름}}은 요청 값으로, {{hash:이름}}은 요청 값에서 계산한 고정 숫자로 채운다.
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

    // 단독 실행 시 부하 테스트 스크립트가 호출 수를 확인하거나 비울 때 쓴다.
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
     * 부하 테스트용 단독 실행. {@code STUB_PORT}(기본 18080)와 {@link Settings#fromEnvironment}의 변수를 읽는다.
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
