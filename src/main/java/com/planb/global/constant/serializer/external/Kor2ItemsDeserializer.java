package com.planb.global.constant.serializer.external;

import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.List;

public class Kor2ItemsDeserializer extends StdDeserializer<Kor2KeywordSearchResponse.Items> {

    public Kor2ItemsDeserializer() {
        super(Kor2KeywordSearchResponse.Items.class);
    }

    @Override
    public Kor2KeywordSearchResponse.Items deserialize(
            JsonParser parser,
            DeserializationContext context) {

        JsonNode node = parser.readValueAsTree();

        // 검색 결과 0건일 때 TourAPI가 items를 "" (빈 문자열)로 내려주는 케이스
        if (!node.isObject()) {
            return new Kor2KeywordSearchResponse.Items(List.of());
        }

        JsonNode itemNode = node.get("item");

        if (itemNode == null || itemNode.isNull()) {
            return new Kor2KeywordSearchResponse.Items(List.of());
        }

        List<Kor2KeywordSearchResponse.Item> items = new ArrayList<>();

        if (itemNode.isArray()) {
            // 검색 결과가 여러 건일 때: item이 배열로 내려오는 정상 케이스
            itemNode.forEach(single ->
                    items.add(
                            context.readTreeAsValue(
                                    single,
                                    Kor2KeywordSearchResponse.Item.class
                            )
                    )
            );
        } else {
            // 검색 결과가 1건일 때: item이 배열이 아니라 단일 객체로 내려오는 케이스
            items.add(
                    context.readTreeAsValue(
                            itemNode,
                            Kor2KeywordSearchResponse.Item.class
                    )
            );
        }

        return new Kor2KeywordSearchResponse.Items(items);
    }
}