package com.planb.performance;

import com.planb.performance.external.ExternalHttpStubServer;
import com.planb.performance.openai.OpenAiChatCompletionStub;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

public final class TravelLoadTestStubServer {

    private TravelLoadTestStubServer() {
    }

    public static void main(String[] args) throws InterruptedException {

        int externalPort = Integer.parseInt(System
                .getenv()
                .getOrDefault(
                        "STUB_PORT",
                        "18080"
                ));

        int openAiPort = Integer.parseInt(System
                .getenv()
                .getOrDefault(
                        "OPENAI_STUB_PORT",
                        "18081"
                ));

        LocalDate startDate = LocalDate.parse(System
                .getenv()
                .getOrDefault(
                        "STUB_PLAN_START_DATE",
                        "2030-01-01"
                ));

        Duration openAiDelay = Duration.ofMillis(Long.parseLong(System
                .getenv()
                .getOrDefault(
                        "OPENAI_STUB_DELAY_MS",
                        "0"
                )));

        if (openAiDelay.isNegative()) {
            throw new IllegalArgumentException("OPENAI_STUB_DELAY_MS는 음수일 수 없습니다.");
        }

        ExternalHttpStubServer externalStub = ExternalHttpStubServer.start(
                externalPort,
                ExternalHttpStubServer.Settings.fromEnvironment(System.getenv())
        );

        OpenAiChatCompletionStub openAiStub = OpenAiChatCompletionStub.startTravelPlan(
                openAiPort,
                startDate,
                startDate.plusDays(1),
                openAiDelay
        );

        CountDownLatch stopped = new CountDownLatch(1);

        Runtime
                .getRuntime()
                .addShutdownHook(new Thread(() -> {
                    openAiStub.close();
                    externalStub.close();
                    stopped.countDown();
                }));

        System.out.println("OpenAI " + openAiStub.baseUrl());
        System.out.println("OpenAI fixed delay " + openAiDelay.toMillis() + "ms");

        for (ExternalHttpStubServer.Api api : ExternalHttpStubServer.Api.values()) {
            System.out.println(api + " " + externalStub.baseUrl(api));
        }

        stopped.await();
    }
}
