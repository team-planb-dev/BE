package com.planb.performance.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 로컬 Prometheus·Grafana 설정 파일 검증
 */
class ObservabilityConfigTest {

    private static final Path ROOT = Path.of("src/test/observability");

    @Test
    @DisplayName("Prometheus는 관리 포트를 2초마다 credentials 파일 인증으로 수집")
    void prometheusScrapesManagementPortWithCredentialsFile() throws IOException {

        Map<String, Object> config = yaml(ROOT.resolve("prometheus/prometheus.yml"));

        assertThat(map(config, "global")
                        .get("scrape_interval"))
                .isEqualTo("2s");
        assertThat(map(config, "global")
                        .get("scrape_timeout"))
                .isEqualTo("2s");

        Map<String, Object> job = scrapeJob(config, "travel-app");

        assertThat(job.get("metrics_path"))
                .isEqualTo("/actuator/prometheus");

        assertThat(targets(job))
                .containsExactly("host.docker.internal:8081");

        Map<String, Object> authorization = map(job, "authorization");

        assertThat(authorization.get("credentials_file"))
                .isEqualTo("/etc/prometheus/scrape-token/token");

        // 기본 type Bearer와 JwtFilter가 요구하는 `Bearer ` 접두사
        assertThat(authorization.getOrDefault("type", "Bearer"))
                .isEqualTo("Bearer");
    }

    @Test
    @DisplayName("Prometheus 설정은 promtool 문법 검사를 통과")
    void prometheusConfigPassesPromtoolSyntaxCheck() throws Exception {

        assumeTrue(
                new ProcessBuilder(
                        "sh",
                        "-c",
                        "command -v promtool"
                )
                        .start()
                        .waitFor() == 0,
                "promtool이 없어 문법 검사를 건너뜀"
        );

        Process process = new ProcessBuilder(
                "promtool",
                "check",
                "config",
                "--syntax-only",
                ROOT
                        .resolve("prometheus/prometheus.yml")
                        .toString()
        )
                .redirectErrorStream(true)
                .start();

        String output = new String(process
                        .getInputStream()
                        .readAllBytes());

        assertThat(process.waitFor())
                .as(output)
                .isZero();
    }

    @Test
    @DisplayName("compose는 remote write 수신을 켜고 이미지를 다이제스트로 고정하며 로컬에만 노출")
    void composeEnablesRemoteWriteAndPinsImages() throws IOException {

        Map<String, Object> services = map(
                yaml(ROOT.resolve("docker-compose.yml")),
                "services"
        );

        Map<String, Object> prometheus = map(services, "prometheus");
        Map<String, Object> grafana = map(services, "grafana");

        assertThat((String) prometheus.get("image"))
                .contains("@sha256:");
        assertThat((String) grafana.get("image"))
                .contains("@sha256:");

        assertThat(strings(prometheus.get("command")))
                .contains(
                        "--config.file=/etc/prometheus/prometheus.yml",
                        "--web.enable-remote-write-receiver"
                );

        assertThat(strings(prometheus.get("extra_hosts")))
                .contains("host.docker.internal:host-gateway");

        // Grafana·Prometheus의 공개 인터넷 노출 금지
        assertThat(strings(prometheus.get("ports")))
                .isNotEmpty()
                .allMatch(port -> port.startsWith("127.0.0.1:"));

        assertThat(strings(grafana.get("ports")))
                .isNotEmpty()
                .allMatch(port -> port.startsWith("127.0.0.1:"));

        // 토큰 디렉터리의 읽기 전용 마운트
        assertThat(strings(prometheus.get("volumes")))
                .anyMatch(volume -> volume.contains("/etc/prometheus/scrape-token") && volume.endsWith(":ro"));
    }

    @Test
    @DisplayName("실행 스크립트는 k6 remote write 계약을 지키고 종료 때 토큰 파일을 지움")
    void runScriptKeepsK6RemoteWriteContract() throws IOException {

        String script = Files.readString(ROOT.resolve("run-observed-load.sh"));

        assertThat(script)
                .contains("-o experimental-prometheus-rw")
                .contains("K6_PROMETHEUS_RW_SERVER_URL=")
                .contains("K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),avg,max")
                .contains("--tag \"testid=$testid\"")
                .contains("GRACEFUL_STOP")
                .contains("Grafana live:")
                .contains("SCRAPE_DRAIN_SECONDS")
                .contains("needs_drain=1")
                .contains("trap cleanup EXIT")
                .contains("trap '' INT TERM HUP")
                .contains("scrape-token.sh\" remove")
                // 종료 안내 실행에 필요한 필수 SCRAPE_TOKEN_DIR 변수
                .contains("SCRAPE_TOKEN_DIR=/tmp docker compose")
                .doesNotContain("k6:latest");

        assertThat(script)
                .containsPattern("grafana/k6@sha256:[0-9a-f]{64}");

        assertThat(Files.readString(Path.of("src/test/k6/travel-plan.js")))
                .contains("gracefulStop: __ENV.GRACEFUL_STOP || '30s'");
    }

    @Test
    @DisplayName("Grafana는 Prometheus datasource와 대시보드 폴더를 provisioning")
    void grafanaProvisioning() throws IOException {

        Map<String, Object> datasources = yaml(ROOT.resolve("grafana/provisioning/datasources/prometheus.yml"));

        Map<String, Object> datasource = map(
                list(datasources, "datasources")
                        .getFirst(),
                null
        );

        assertThat(datasource.get("uid"))
                .isEqualTo("prometheus");
        assertThat(datasource.get("type"))
                .isEqualTo("prometheus");
        assertThat(datasource.get("url"))
                .isEqualTo("http://prometheus:9090");

        Map<String, Object> providers = yaml(ROOT.resolve("grafana/provisioning/dashboards/dashboards.yml"));

        assertThat(map(
                list(providers, "providers")
                        .getFirst(),
                "options"
        )
                .get("path"))
                .isEqualTo("/var/lib/grafana/dashboards");
    }

    @Test
    @DisplayName("대시보드는 계획서의 패널 묶음을 모두 포함")
    void dashboardCoversPlannedPanels() throws IOException {

        List<String> titles = panelTitles();

        for (String keyword : List.of(
                "k6 요청률",
                "k6 지연",
                "k6 오류율",
                "k6 VU",
                "서버 HTTP 지연",
                "Travel orchestration 지연",
                "Correction retry",
                "외부 HTTP 호스트별",
                "Hikari 커넥션",
                "usage",
                "Tomcat 요청 스레드",
                "일정 생성 HTTP 실패율",
                "JVM",
                "CPU",
                "scrape 대상"
        )) {
            assertThat(titles)
                    .as("패널 키워드 '%s'", keyword)
                    .anyMatch(title -> title.contains(keyword));
        }
    }

    @Test
    @DisplayName("외부 HTTP 패널은 동일 호스트 의존성 병합을 제목에 명시")
    void externalHttpPanelWarnsAboutMergedDependencies() throws IOException {

        assertThat(panelTitles())
                .anyMatch(title -> title.contains("외부 HTTP 호스트별 지연·상태")
                        && title.contains("동일 호스트 의존성 병합 주의"));
    }

    @Test
    @DisplayName("대시보드 쿼리는 Prometheus datasource와 testid 변수를 쓰고 외부 HTTP 버킷을 쓰지 않음")
    void dashboardQueries() throws IOException {

        JsonNode dashboard = dashboard();

        assertThat(dashboard.toString())
                .contains("$testid")
                .doesNotContain("http_client_requests_seconds_bucket");

        for (JsonNode panel : dashboard.path("panels")) {
            for (JsonNode target : panel.path("targets")) {
                assertThat(target
                                .path("datasource")
                                .path("uid")
                                .asString())
                        .as("패널 '%s'의 datasource", panel
                                .path("title")
                                .asString())
                        .isEqualTo("prometheus");

                assertThat(target
                                .path("expr")
                                .asString())
                        .as("패널 '%s'의 쿼리", panel
                                .path("title")
                                .asString())
                        .isNotBlank();
            }
        }

        assertThat(dashboard
                        .path("templating")
                        .toString())
                .contains("testid");

        assertThat(dashboard.toString())
                .contains("tomcat_threads_busy_threads")
                .contains("tomcat_threads_current_threads")
                .contains("tomcat_threads_config_max_threads")
                .contains("tomcat_threads_busy_threads / tomcat_threads_config_max_threads")
                .contains("increase(k6_http_reqs_total")
                .contains("error_code=\\\"1050\\\"")
                .contains("status=~\\\"5..\\\"")
                .contains("uri=\\\"/api/v1/travel/add-with-recommend\\\"");
    }

    @Test
    @DisplayName("관측 실행 정리 중 반복 중단에도 임시 토큰 삭제")
    void cleanupRemovesTokenAfterRepeatedInterrupts() throws Exception {

        Path tokenDirectory = Files.createTempDirectory("planb-observability-token-");
        Path token = tokenDirectory.resolve("token");

        Files.writeString(
                token,
                "secret"
        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                "bash",
                "-c",
                "source \"$SCRIPT\"; "
                        + "token_dir=\"$TOKEN_DIRECTORY\"; "
                        + "needs_drain=1; "
                        + "drain_seconds=1; "
                        + "cleanup & cleanup_pid=$!; "
                        + "sleep 0.1; "
                        + "kill -INT \"$cleanup_pid\"; "
                        + "kill -TERM \"$cleanup_pid\"; "
                        + "wait \"$cleanup_pid\"; "
                        + "test ! -e \"$TOKEN_DIRECTORY/token\""
        )
                .redirectErrorStream(true);

        processBuilder
                .environment()
                .put(
                        "SCRIPT",
                        ROOT
                                .resolve("run-observed-load.sh")
                                .toAbsolutePath()
                                .toString()
                );
        processBuilder
                .environment()
                .put(
                        "TOKEN_DIRECTORY",
                        tokenDirectory.toString()
                );

        Process process = processBuilder.start();

        assertThat(process.waitFor())
                .as(new String(process
                                .getInputStream()
                                .readAllBytes()))
                .isZero();

        assertThat(token)
                .doesNotExist();
        assertThat(tokenDirectory)
                .doesNotExist();
    }

    private List<String> panelTitles() throws IOException {

        List<String> titles = new ArrayList<>();

        for (JsonNode panel : dashboard()
                .path("panels")) {
            titles.add(panel
                            .path("title")
                            .asString());
        }

        return titles;
    }

    private JsonNode dashboard() throws IOException {

        return new ObjectMapper().readTree(
                Files.readString(ROOT.resolve("grafana/dashboards/travel-load.json"))
        );
    }

    private Map<String, Object> yaml(Path path) throws IOException {

        return new Yaml().load(Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(
            Object parent,
            String key
    ) {

        return key == null
                ? (Map<String, Object>) parent
                : (Map<String, Object>) ((Map<String, Object>) parent).get(key);
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(
            Map<String, Object> parent,
            String key
    ) {

        return (List<Object>) parent.get(key);
    }

    private List<String> strings(Object value) {

        return list(Map.of("value", value), "value")
                .stream()
                .map(Object::toString)
                .toList();
    }

    private Map<String, Object> scrapeJob(
            Map<String, Object> config,
            String name
    ) {

        return list(config, "scrape_configs")
                .stream()
                .map(job -> map(job, null))
                .filter(job -> name.equals(job.get("job_name")))
                .findFirst()
                .orElseThrow();
    }

    private List<String> targets(Map<String, Object> job) {

        return list(job, "static_configs")
                .stream()
                .flatMap(entry -> strings(map(entry, null)
                                .get("targets"))
                        .stream())
                .toList();
    }
}
