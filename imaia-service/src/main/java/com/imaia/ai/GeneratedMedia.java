package com.imaia.ai;

/** One media artifact a generation tool produced during the current chat turn. */
public record GeneratedMedia(String kind, byte[] data, String filename, String mimeType,
        String prompt) {
}