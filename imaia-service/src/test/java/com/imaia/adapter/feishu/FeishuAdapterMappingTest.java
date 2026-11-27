package com.imaia.adapter.feishu;

import com.imaia.config.JsonSupportTestSeeder;
import com.imaia.domain.MessageKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Feishu pure mappings: config parsing, msg_type → category, content → body and keys. */
class FeishuAdapterMappingTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static {
        JsonSupportTestSeeder.seed(MAPPER);
    }

    @Test
    void configParsesStructuredFieldsWithDefaults() {
        JsonNode config = MAPPER.readTree(
                "{\"appId\": \"cli_1\", \"appSecret\": \"s3cret\", \"exitOnFailure\": false}");

        FeishuAccountConfig parsed = FeishuAccountConfig.fromJson(config);

        assertThat(parsed.appId()).isEqualTo("cli_1");
        assertThat(parsed.appSecret()).isEqualTo("s3cret");
        assertThat(parsed.domain()).isEqualTo("https://open.feishu.cn");
        assertThat(parsed.exitOnFailure()).isFalse();

        FeishuAccountConfig empty = FeishuAccountConfig.fromJson(MAPPER.readTree("{}"));
        assertThat(empty.appId()).isEmpty();
        assertThat(empty.appSecret()).isEmpty();
        assertThat(empty.domain()).isEqualTo("https://open.feishu.cn");
        assertThat(empty.exitOnFailure()).isTrue();
    }

    @Test
    void msgTypeMapsToNormalizedCategories() {
        assertThat(FeishuMessages.categoryOf("text")).isEqualTo(MessageKind.TEXT);
        assertThat(FeishuMessages.categoryOf("image")).isEqualTo(MessageKind.IMAGE);
        assertThat(FeishuMessages.categoryOf("audio")).isEqualTo(MessageKind.VOICE);
        assertThat(FeishuMessages.categoryOf("media")).isEqualTo(MessageKind.VIDEO);
        assertThat(FeishuMessages.categoryOf("sticker")).isEqualTo(MessageKind.STICKER);
        assertThat(FeishuMessages.categoryOf("file")).isEqualTo(MessageKind.FILE);
        assertThat(FeishuMessages.categoryOf("post")).isEqualTo(MessageKind.RICH);
        assertThat(FeishuMessages.categoryOf("interactive")).isEqualTo(MessageKind.RICH);
        assertThat(FeishuMessages.categoryOf("unknown_kind")).isEqualTo(MessageKind.OTHER);
        assertThat(FeishuMessages.categoryOf(null)).isEqualTo(MessageKind.OTHER);
    }

    @Test
    void textBodyIsExtractedAndAtTagsStripped() {
        String content = "{\"text\":\"<at user_id=\\\"ou_a\\\">张三</at> 帮我看看这个\"}";
        assertThat(FeishuMessages.extractBody("text", content)).isEqualTo("帮我看看这个");
        assertThat(FeishuMessages.extractBody("text", "{\"text\":\"纯文本\"}")).isEqualTo("纯文本");
        assertThat(FeishuMessages.extractBody("image", "{\"image_key\":\"img_k\"}")).isNull();
        assertThat(FeishuMessages.extractBody("text", "not-json")).isNull();
    }

    @Test
    void mediaKeysAreReadPerCategory() {
        String media = "{\"file_key\":\"vid_k\",\"image_key\":\"cover_k\",\"duration\":5200}";
        assertThat(FeishuMessages.mediaKey("media", media, MessageKind.VIDEO)).isEqualTo("vid_k");
        assertThat(FeishuMessages.previewKey(media)).isEqualTo("cover_k");
        String audio = "{\"file_key\":\"aud_k\",\"duration\":1300}";
        assertThat(FeishuMessages.mediaKey("audio", audio, MessageKind.VOICE)).isEqualTo("aud_k");
        String image = "{\"image_key\":\"img_k\"}";
        assertThat(FeishuMessages.mediaKey("image", image, MessageKind.IMAGE)).isEqualTo("img_k");
        assertThat(FeishuMessages.mediaKey("image", image, MessageKind.VOICE)).isNull();
        assertThat(FeishuMessages.previewKey("{\"file_key\":\"only_file\"}")).isNull();
    }
}
