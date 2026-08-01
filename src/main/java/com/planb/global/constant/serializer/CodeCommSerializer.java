package com.planb.global.constant.serializer;

import com.planb.global.constant.enums.CodeCommInterface;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;


public class CodeCommSerializer extends StdSerializer<CodeCommInterface> {

    public CodeCommSerializer() {
        this((Class)null); // 기본 생성자에서 null을 넘겨 StdSerializer 생성자를 호출
    }

    protected CodeCommSerializer(Class<CodeCommInterface> t) {
        super(t); // Jackson이 직렬화 할때 ,
        // 타입 정보 없이도 Serializer를 생성할 수 있도록함
    }

    // 자동으로 enum 클래스의 필드를 직렬화
    public void serialize(CodeCommInterface value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeStartObject();
            gen.writeStringField("code", "");
            gen.writeStringField("codeName", "");
            gen.writeEndObject();
        } else {
            gen.writeStartObject();
            gen.writeStringField("code", value.getCode());
            gen.writeStringField("codeName", value.getCodeName());
            gen.writeEndObject();
        }

    }
}
