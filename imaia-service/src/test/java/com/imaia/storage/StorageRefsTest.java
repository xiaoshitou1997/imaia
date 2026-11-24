package com.imaia.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** storageRef 键生成：category 前缀与后缀兜底规则。 */
class StorageRefsTest {

    @Test
    void keyHasCategoryDatePrefixAndSuffix() {
        String ref = StorageRefs.key("attachments", "photo.PNG");
        assertTrue(ref.startsWith("attachments/"), ref);
        assertTrue(ref.matches("attachments/[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9a-f-]{36}[.]png"), ref);
        assertTrue(ref.endsWith(".png"), ref);
    }

    @Test
    void unknownOrBareSuffixFallsBackToBin() {
        assertTrue(StorageRefs.key("artifacts", null).endsWith(".bin"));
        assertTrue(StorageRefs.key("artifacts", "noext").endsWith(".bin"));
        assertTrue(StorageRefs.key("artifacts", "trailing.").endsWith(".bin"));
    }
}
