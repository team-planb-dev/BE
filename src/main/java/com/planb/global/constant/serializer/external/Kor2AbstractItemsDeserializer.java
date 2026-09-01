package com.planb.global.constant.serializer.external;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class Kor2AbstractItemsDeserializer<ITEMS, ITEM>
        extends StdDeserializer<ITEMS> {

    private final Class<ITEM> itemClass;
    private final Function<List<ITEM>, ITEMS> itemsFactory;

    protected Kor2AbstractItemsDeserializer(
            Class<ITEMS> itemsClass,
            Class<ITEM> itemClass,
            Function<List<ITEM>, ITEMS> itemsFactory) {

        super(itemsClass);
        this.itemClass = itemClass;
        this.itemsFactory = itemsFactory;
    }

    @Override
    public ITEMS deserialize(
            JsonParser parser,
            DeserializationContext context) {

        JsonNode node = parser.readValueAsTree();

        // 검색/조회 결과가 없을 때 TourAPI가 items를 "" (빈 문자열)로 내려주는 케이스
        if (!node.isObject()) {
            return itemsFactory.apply(List.of());
        }

        JsonNode itemNode = node.get("item");

        if (itemNode == null || itemNode.isNull()) {
            return itemsFactory.apply(List.of());
        }

        List<ITEM> items = new ArrayList<>();

        if (itemNode.isArray()) {
            // 결과가 여러 건일 때: item이 배열로 내려오는 정상 케이스
            itemNode.forEach(single ->
                    items.add(
                            context.readTreeAsValue(single, itemClass)
                    )
            );
        } else {
            // 결과가 1건일 때: item이 배열이 아니라 단일 객체로 내려오는 케이스
            items.add(
                    context.readTreeAsValue(itemNode, itemClass)
            );
        }

        return itemsFactory.apply(items);
    }
}