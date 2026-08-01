package com.planb.global.constant.enums;

import com.planb.global.constant.serializer.CodeCommDeserializer;
import com.planb.global.constant.serializer.CodeCommSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(
        using = CodeCommSerializer.class
)
@JsonDeserialize(
        using = CodeCommDeserializer.class
)
public interface CodeCommInterface {
    String getCode();
    String getCodeName();
}
