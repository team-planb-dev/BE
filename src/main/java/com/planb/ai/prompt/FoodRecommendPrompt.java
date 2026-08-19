package com.planb.ai.prompt;

public record FoodRecommendPrompt(String location)
        implements AiPrompt{

    @Override
    public String system() {
        return """
                너는 여행 지역의 대표 음식을 추천하는 AI다.
                입력받은 지역을 기준으로 대표 음식 5개를 추천한다.
                응답은 지정된 JSON 형식에 맞춰 반환한다.
               """;
    }

    @Override
    public String user() {
        return location;
    }
}
