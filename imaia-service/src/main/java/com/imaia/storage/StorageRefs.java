package com.imaia.storage;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** storageRef 键生成：category/yyyy/mm/uuid.ext（后缀取文件名，未知 .bin 兜底）。 */
final class StorageRefs {

    private StorageRefs() {
    }

    static String key(String category, String filename) {
        return category + "/" + LocalDate.now().toString().replace('-', '/')
                + "/" + UUID.randomUUID() + suffixOf(filename);
    }

    private static String suffixOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return ".bin";
        }
        String name = filename.strip();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".bin";
        }
        String suffix = name.substring(dot).toLowerCase(Locale.ROOT);
        return suffix.length() <= 16 ? suffix : ".bin";
    }
}
