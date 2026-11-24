package com.imaia.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 锁死枚举的 wire 契约：API/JSON 一律小写值（@JsonValue/@JsonCreator），
 * 与库列的小写 VARCHAR 一致——前端与群内指令无需感知 Java 常量名。
 */
class EnumWireContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void serializesToLowercaseValue() {
        assertEquals("\"incoming\"", MAPPER.writeValueAsString(MessageDirection.INCOMING));
        assertEquals("\"sync\"", MAPPER.writeValueAsString(ModelSource.SYNC));
        assertEquals("\"wechat\"", MAPPER.writeValueAsString(Platform.WECHAT));
    }

    @Test
    void deserializesFromLowercaseValue() {
        assertEquals(TaskStatus.ACTIVE, MAPPER.readValue("\"active\"", TaskStatus.class));
        assertEquals(ReportPeriod.MONTH, MAPPER.readValue("\"month\"", ReportPeriod.class));
        assertEquals(DeliveryStatus.SENT, MAPPER.readValue("\"sent\"", DeliveryStatus.class));
        assertEquals(ModelSlot.VISION, MAPPER.readValue("\"vision\"", ModelSlot.class));
        assertEquals(Platform.FEISHU, MAPPER.readValue("\"feishu\"", Platform.class));
    }

    @Test
    void rejectsUnknownValue() {
        assertThrows(tools.jackson.databind.exc.ValueInstantiationException.class,
                () -> MAPPER.readValue("\"weekly\"", ReportPeriod.class));
    }
}
