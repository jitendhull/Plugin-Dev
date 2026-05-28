package com.demise.core.moderation;

public record ModerationLogEntry(String action, String actorId, String reason, long createdAt) {
}
