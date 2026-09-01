package com.planb.global.client.foodNtrCpnt.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FoodNtrCpntResponse(
        Header header,
        Body body
) {

    public record Header
            (String resultCode,
             String resultMsg) {
    }

    public record Body
            (Integer pageNo,
             Integer totalCount,
             Integer numOfRows,
             List<Item> items) {
    }

    public record Item
            (@JsonProperty("FOOD_CD")
             String foodCode,

             @JsonProperty("FOOD_NM_KR")
             String foodName,

             @JsonProperty("DB_GRP_NM")
             String dbGroupName,

             @JsonProperty("FOOD_OR_NM")
             String foodOriginName,

             @JsonProperty("FOOD_CAT1_NM")
             String foodCategoryName,

             @JsonProperty("FOOD_REF_NM")
             String foodReferenceName,

             @JsonProperty("SERVING_SIZE")
             String servingSize,

             // 에너지(kcal)
             @JsonProperty("AMT_NUM1")
             String energy,

             // 단백질(g)
             @JsonProperty("AMT_NUM3")
             String protein,

             // 지방(g)
             @JsonProperty("AMT_NUM4")
             String fat,

             // 탄수화물(g)
             @JsonProperty("AMT_NUM6")
             String carbohydrate,

             // 당류(g)
             @JsonProperty("AMT_NUM7")
             String sugar,

             // 식이섬유(g)
             @JsonProperty("AMT_NUM8")
             String dietaryFiber,

             // 나트륨(mg)
             @JsonProperty("AMT_NUM13")
             String sodium,

             // 콜레스테롤(mg)
             @JsonProperty("AMT_NUM23")
             String cholesterol,

             // 포화지방산(g)
             @JsonProperty("AMT_NUM24")
             String saturatedFat,

             // 트랜스지방산(g)
             @JsonProperty("AMT_NUM25")
             String transFat) {
    }
}