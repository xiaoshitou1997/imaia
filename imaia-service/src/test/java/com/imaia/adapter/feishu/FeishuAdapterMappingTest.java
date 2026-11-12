package com.imaia.adapter.feishu;

import com.imaia.domain.MessageCategory;
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
        assertThat(FeishuMessages.categoryOf("text")).isEqualTo(MessageCategory.TEXT);
        assertThat(FeishuMessages.categoryOf("image")).isEqualTo(MessageCategory.IMAGE);
        assertThat(FeishuMessages.categoryOf("audio")).isEqualTo(MessageCategory.VOICE);
        assertThat(FeishuMessages.categoryOf("media")).isEqualTo(MessageCategory.VIDEO);
        assertThat(FeishuMessages.categoryOf("sticker")).isEqualTo(MessageCategory.STICKER);
        assertThat(FeishuMessages.categoryOf("file")).isEqualTo(MessageCategory.FILE);
        assertThat(FeishuMessages.categoryOf("post")).isEqualTo(MessageCategory.RICH);
        assertThat(FeishuMessages.categoryOf("interactive")).isEqualTo(MessageCategory.RICH);
        assertThat(FeishuMessages.categoryOf("unknown_kind")).isEqualTo(MessageCategory.OTHER);
        assertThat(FeishuMessages.categoryOf(null)).isEqualTo(MessageCategory.OTHER);
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
        assertThat(FeishuMessages.mediaKey("media", media, MessageCategory.VIDEO)).isEqualTo("vid_k");
        assertThat(FeishuMessages.previewKey(media)).isEqualTo("cover_k");
        String audio = "{\"file_key\":\"aud_k\",\"duration\":1300}";
        assertThat(FeishuMessages.mediaKey("audio", audio, MessageCategory.VOICE)).isEqualTo("aud_k");
        String image = "{\"image_key\":\"img_k\"}";
        assertThat(FeishuMessages.mediaKey("image", image, MessageCategory.IMAGE)).isEqualTo("img_k");
        assertThat(FeishuMessages.mediaKey("image", image, MessageCategory.VOICE)).isNull();
        assertThat(FeishuMessages.previewKey("{\"file_key\":\"only_file\"}")).isNull();
    }
}
