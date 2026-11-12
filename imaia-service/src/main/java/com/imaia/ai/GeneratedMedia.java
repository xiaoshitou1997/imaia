package com.imaia.ai;

import com.imaia.domain.MediaKind;

/** One media artifact a generation tool produced during the current chat turn. */
public record GeneratedMedia(MediaKind kind, byte[] data, String filename, String mimeType,
        String prompt) {
}
