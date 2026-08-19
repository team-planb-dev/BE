package com.planb.ai.handler;


import com.planb.ai.client.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelManagementHandler {

    private final OpenAiClient openAiClient;

    // TODO : @Tool 작성 후 , 사용자 요청 받아서 보내기
}
