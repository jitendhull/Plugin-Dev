package com.demise.core.moderation;

import com.demise.core.config.ConfigManager;
import com.demise.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationService {

    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Map<UUID, MuteRecord> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, BanRecord> bans = new ConcurrentHashMap<>();

    public ModerationService(DatabaseManager databaseManager, ConfigManager configManager) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    public CompletableFuture<Void> loadCaches() {
        long now = System.currentTimeMillis();
        return databaseManager.runAsync(connection -> {
            mutes.clear();
            bans.clear();
            loadMutes(connection, now);
            loadBans(connection, now);
        });
    }

    public MuteRecord getActiveMute(UUID uuid) {
        MuteRecord record = mutes.get(uuid);
        if (record == null) {
            return null;
        }
        if (record.isExpired(System.currentTimeMillis())) {
            mutes.remove(uuid);
            databaseManager.runAsync(connection -> deleteMute(connection, uuid));
            return null;
        }
        return record;
    }

    public BanRecord getActiveBan(UUID uuid) {
        BanRecord record = bans.get(uuid);
        if (record == null) {
            return null;
        }
        if (record.isExpired(System.currentTimeMillis())) {
            bans.remove(uuid);
            databaseManager.runAsync(connection -> deleteBan(connection, uuid));
            return null;
        }
        return record;
    }

    public CompletableFuture<Void> mute(UUID targetUuid, String actorId, String reason, Long expiresAt) {
        return databaseManager.runAsync(connection -> {
            upsertMute(connection, targetUuid, actorId, reason, expiresAt);
            logAction(connection, "MUTE", targetUuid, actorId, reason);
            mutes.put(targetUuid, new MuteRecord(actorId, reason, expiresAt));
        });
    }

    public CompletableFuture<Void> unmute(UUID targetUuid, String actorId) {
        return databaseManager.runAsync(connection -> {
            deleteMute(connection, targetUuid);
            logAction(connection, "UNMUTE", targetUuid, actorId, "Unmuted");
            mutes.remove(targetUuid);
        });
    }

    public CompletableFuture<Void> ban(UUID targetUuid, String actorId, String reason, Long expiresAt) {
        return databaseManager.runAsync(connection -> {
            upsertBan(connection, targetUuid, actorId, reason, expiresAt);
            logAction(connection, "BAN", targetUuid, actorId, reason);
            bans.put(targetUuid, new BanRecord(actorId, reason, expiresAt));
        });
    }

    public CompletableFuture<Void> unban(UUID targetUuid, String actorId) {
        return databaseManager.runAsync(connection -> {
            deleteBan(connection, targetUuid);
            logAction(connection, "UNBAN", targetUuid, actorId, "Unbanned");
            bans.remove(targetUuid);
        });
    }

    public CompletableFuture<Integer> warn(UUID targetUuid, String actorId, String reason, int severity) {
        return databaseManager.supplyAsync(connection -> {
            insertWarning(connection, targetUuid, actorId, reason, severity);
            logAction(connection, "WARN", targetUuid, actorId, reason);
            return getWarningCount(connection, targetUuid);
        });
    }

    public CompletableFuture<List<ModerationLogEntry>> getLogs(UUID targetUuid, int limit) {
        return databaseManager.supplyAsync(connection -> fetchLogs(connection, targetUuid, limit));
    }

    public CompletableFuture<Void> logAction(String action, UUID targetUuid, String actorId, String reason) {
        return databaseManager.runAsync(connection -> logAction(connection, action, targetUuid, actorId, reason));
    }

    public int getWarnThreshold() {
        return configManager.getWarnThreshold();
    }

    private void loadMutes(Connection connection, long now) {
        String sql = "SELECT uuid, muted_by, reason, expires_at FROM mutes";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuid = resultSet.getString("uuid");
                Long expiresAt = resultSet.getObject("expires_at", Long.class);
                if (expiresAt != null && expiresAt <= now) {
                    continue;
                }
                mutes.put(UUID.fromString(uuid),
                        new MuteRecord(resultSet.getString("muted_by"), resultSet.getString("reason"), expiresAt));
            }
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void loadBans(Connection connection, long now) {
        String sql = "SELECT uuid, banned_by, reason, expires_at FROM bans";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuid = resultSet.getString("uuid");
                Long expiresAt = resultSet.getObject("expires_at", Long.class);
                if (expiresAt != null && expiresAt <= now) {
                    continue;
                }
                bans.put(UUID.fromString(uuid),
                        new BanRecord(resultSet.getString("banned_by"), resultSet.getString("reason"), expiresAt));
            }
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void upsertMute(Connection connection, UUID targetUuid, String actorId, String reason, Long expiresAt) {
        String sql = """
                INSERT INTO mutes (uuid, muted_by, reason, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET muted_by = excluded.muted_by, reason = excluded.reason,
                expires_at = excluded.expires_at, created_at = excluded.created_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, actorId);
            statement.setString(3, reason);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void deleteMute(Connection connection, UUID targetUuid) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mutes WHERE uuid = ?")) {
            statement.setString(1, targetUuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void upsertBan(Connection connection, UUID targetUuid, String actorId, String reason, Long expiresAt) {
        String sql = """
                INSERT INTO bans (uuid, banned_by, reason, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET banned_by = excluded.banned_by, reason = excluded.reason,
                expires_at = excluded.expires_at, created_at = excluded.created_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, actorId);
            statement.setString(3, reason);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void deleteBan(Connection connection, UUID targetUuid) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM bans WHERE uuid = ?")) {
            statement.setString(1, targetUuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void insertWarning(Connection connection, UUID targetUuid, String actorId, String reason, int severity) {
        String sql = """
                INSERT INTO warnings (uuid, warned_by, reason, severity, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, actorId);
            statement.setString(3, reason);
            statement.setInt(4, severity);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private int getWarningCount(Connection connection, UUID targetUuid) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS total FROM warnings WHERE uuid = ?")) {
            statement.setString(1, targetUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("total");
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
        return 0;
    }

    private void logAction(Connection connection, String action, UUID targetUuid, String actorId, String reason) {
        String sql = """
                INSERT INTO moderation_logs (action, target_uuid, actor_uuid, reason, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action);
            statement.setString(2, targetUuid.toString());
            statement.setString(3, actorId);
            statement.setString(4, reason);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private List<ModerationLogEntry> fetchLogs(Connection connection, UUID targetUuid, int limit) {
        List<ModerationLogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT action, actor_uuid, reason, created_at
                FROM moderation_logs
                WHERE target_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new ModerationLogEntry(
                            resultSet.getString("action"),
                            resultSet.getString("actor_uuid"),
                            resultSet.getString("reason"),
                            resultSet.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
        return entries;
    }
}
