package com.planb.performance;

import com.planb.performance.external.ExternalHttpStubServer;
import com.planb.performance.openai.OpenAiChatCompletionStub;

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

        ExternalHttpStubServer externalStub = ExternalHttpStubServer.start(
                externalPort,
                ExternalHttpStubServer.Settings.fromEnvironment(System.getenv())
        );

        OpenAiChatCompletionStub openAiStub = OpenAiChatCompletionStub.startTravelPlan(
                openAiPort,
                startDate,
                startDate.plusDays(1)
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

        for (ExternalHttpStubServer.Api api : ExternalHttpStubServer.Api.values()) {
            System.out.println(api + " " + externalStub.baseUrl(api));
        }

        stopped.await();
    }
}
