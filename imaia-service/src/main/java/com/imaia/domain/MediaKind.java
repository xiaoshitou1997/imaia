package com.imaia.domain;

import java.util.Locale;

/**
 * Outbound media vocabulary of the {@code ImAdapter.sendMedia} seam: the core names
 * the kind, every adapter translates it into its platform's message type. Stored
 * values (ai_artifact.kind) are lowercase via {@link #value()}.
 */
public enum MediaKind {
    IMAGE, VIDEO, VOICE, STICKER, FILE;

    /** The stored/protocol representation (lowercase). */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value();
    }
}
