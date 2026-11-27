package com.imaia.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.json.JsonMapper;

/**
 * 锁死枚举的 wire 契约：API/JSON 一律小写值（@JsonValue/@JsonCreator），
 * 与库列的小写 VARCHAR 一致——前端与群内指令无需感知 Java 常量名。
 */
class EnumWireContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void serializesToLowercaseValue() {
        assertThat(MAPPER.writeValueAsString(MessageDirection.INCOMING)).isEqualTo("\"incoming\"");
        assertThat(MAPPER.writeValueAsString(ModelSource.SYNC)).isEqualTo("\"sync\"");
        assertThat(MAPPER.writeValueAsString(Platform.WECHAT)).isEqualTo("\"wechat\"");
    }

    @Test
    void deserializesFromLowercaseValue() {
        assertThat(MAPPER.readValue("\"active\"", TaskStatus.class)).isEqualTo(TaskStatus.ACTIVE);
        assertThat(MAPPER.readValue("\"month\"", ReportPeriod.class)).isEqualTo(ReportPeriod.MONTH);
        assertThat(MAPPER.readValue("\"sent\"", DeliveryStatus.class)).isEqualTo(DeliveryStatus.SENT);
        assertThat(MAPPER.readValue("\"vision\"", ModelSlot.class)).isEqualTo(ModelSlot.VISION);
        assertThat(MAPPER.readValue("\"feishu\"", Platform.class)).isEqualTo(Platform.FEISHU);
    }

    @Test
    void rejectsUnknownValue() {
        assertThatThrownBy(() -> MAPPER.readValue("\"weekly\"", ReportPeriod.class)).isInstanceOf(tools.jackson.databind.exc.ValueInstantiationException.class);
    }
}
