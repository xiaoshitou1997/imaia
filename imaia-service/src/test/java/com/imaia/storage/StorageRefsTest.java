package com.imaia.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/** storageRef 键生成：category 前缀与后缀兜底规则。 */
class StorageRefsTest {

    @Test
    void keyHasCategoryDatePrefixAndSuffix() {
        String ref = StorageRefs.key("attachments", "photo.PNG");
        assertThat(ref.startsWith("attachments/")).as(ref).isTrue();
        assertThat(ref.matches("attachments/[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9a-f-]{36}[.]png")).as(ref).isTrue();
        assertThat(ref.endsWith(".png")).as(ref).isTrue();
    }

    @Test
    void unknownOrBareSuffixFallsBackToBin() {
        assertThat(StorageRefs.key("artifacts", null).endsWith(".bin")).isTrue();
        assertThat(StorageRefs.key("artifacts", "noext").endsWith(".bin")).isTrue();
        assertThat(StorageRefs.key("artifacts", "trailing.").endsWith(".bin")).isTrue();
    }
}
