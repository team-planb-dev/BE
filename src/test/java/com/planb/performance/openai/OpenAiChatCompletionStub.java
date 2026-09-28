package com.planb.performance.openai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OpenAiChatCompletionStub implements AutoCloseable {

    private static final String FIXTURE_ROOT = "loadtest/openai/";

    private final HttpServer server;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Queue<String> responses = new ConcurrentLinkedQueue<>();

    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    OpenAiChatCompletionStub() {

        try {
            server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0),
                    0
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        server.createContext("/v1/chat/completions", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {

        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    void enqueueFixtures(String... fixtureNames) {

        for (String fixtureName : fixtureNames) {
            responses.add(readFixture(fixtureName));
        }
    }

    List<String> requests() {

        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {

        requests.add(
                new String(
                        exchange
                                .getRequestBody()
                                .readAllBytes(),
                        StandardCharsets.UTF_8
                )
        );

        String response = responses.poll();
        if (response == null) {
            response = "{\"error\":{\"message\":\"No scripted response\"}}";
            send(
                    exchange,
                    500,
                    response
            );

            return;
        }

        send(
                exchange,
                200,
                response
        );
    }

    private void send(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange
                .getResponseHeaders()
                .set("Content-Type", "application/json");

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private String readFixture(String fixtureName) {

        String path = FIXTURE_ROOT + fixtureName;

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(path)) {

            if (input == null) {
                throw new IllegalArgumentException("OpenAI fixture가 없습니다: " + path);
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void close() {

        server.stop(0);
        executor.close();
    }
}
