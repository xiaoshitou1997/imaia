package com.imaia.consumer.adapter.feishu;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Feishu pure mappings: config parsing, msg_type → category, content → body and keys. */
class FeishuAdapterMappingTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

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
        assertThat(FeishuMessages.categoryOf("text")).isEqualTo("text");
        assertThat(FeishuMessages.categoryOf("image")).isEqualTo("image");
        assertThat(FeishuMessages.categoryOf("audio")).isEqualTo("voice");
        assertThat(FeishuMessages.categoryOf("media")).isEqualTo("video");
        assertThat(FeishuMessages.categoryOf("sticker")).isEqualTo("sticker");
        assertThat(FeishuMessages.categoryOf("file")).isEqualTo("file");
        assertThat(FeishuMessages.categoryOf("post")).isEqualTo("rich");
        assertThat(FeishuMessages.categoryOf("interactive")).isEqualTo("rich");
        assertThat(FeishuMessages.categoryOf("unknown_kind")).isEqualTo("other");
        assertThat(FeishuMessages.categoryOf(null)).isEqualTo("other");
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
        assertThat(FeishuMessages.mediaKey("media", media, "video")).isEqualTo("vid_k");
        assertThat(FeishuMessages.previewKey(media)).isEqualTo("cover_k");
        String audio = "{\"file_key\":\"aud_k\",\"duration\":1300}";
        assertThat(FeishuMessages.mediaKey("audio", audio, "voice")).isEqualTo("aud_k");
        String image = "{\"image_key\":\"img_k\"}";
        assertThat(FeishuMessages.mediaKey("image", image, "image")).isEqualTo("img_k");
        assertThat(FeishuMessages.mediaKey("image", image, "voice")).isNull();
        assertThat(FeishuMessages.previewKey("{\"file_key\":\"only_file\"}")).isNull();
    }
}
