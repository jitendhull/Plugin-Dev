package com.demise.core.moderation;

public record BanRecord(String actorId, String reason, Long expiresAt) {
    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
