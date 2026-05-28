package com.demise.core.economy;

import com.demise.core.config.ConfigManager;
import com.demise.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyService {

    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;

    public EconomyService(DatabaseManager databaseManager, ConfigManager configManager) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    public CompletableFuture<Void> ensureAccountAsync(UUID uuid, String name) {
        return databaseManager.runAsync(connection -> ensureAccount(connection, uuid, name));
    }

    public CompletableFuture<Long> getBalance(UUID uuid, String name) {
        return databaseManager.supplyAsync(connection -> {
            ensureAccount(connection, uuid, name);
            return fetchBalance(connection, uuid);
        });
    }

    public CompletableFuture<TransferResult> transfer(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount) {
        return databaseManager.supplyAsync(connection -> {
            ensureAccount(connection, fromUuid, fromName);
            ensureAccount(connection, toUuid, toName);

            try {
                connection.setAutoCommit(false);
                long senderBalance = fetchBalance(connection, fromUuid);
                if (senderBalance < amount) {
                    connection.rollback();
                    return TransferResult.failure("INSUFFICIENT_FUNDS");
                }
                long receiverBalance = fetchBalance(connection, toUuid);
                updateBalance(connection, fromUuid, senderBalance - amount);
                updateBalance(connection, toUuid, receiverBalance + amount);
                logTransaction(connection, fromUuid.toString(), fromUuid.toString(), toUuid.toString(), amount, "PAY", "Player transfer");
                connection.commit();
                return TransferResult.success(senderBalance - amount, receiverBalance + amount);
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new RuntimeException(exception);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        });
    }

    public CompletableFuture<UpdateResult> setBalance(UUID targetUuid, String targetName, long amount, String actorId) {
        return databaseManager.supplyAsync(connection -> {
            ensureAccount(connection, targetUuid, targetName);
            updateBalance(connection, targetUuid, amount);
            logTransaction(connection, actorId, null, targetUuid.toString(), amount, "SET", "Admin set balance");
            return UpdateResult.success(amount);
        });
    }

    public CompletableFuture<UpdateResult> addBalance(UUID targetUuid, String targetName, long amount, String actorId) {
        return databaseManager.supplyAsync(connection -> {
            ensureAccount(connection, targetUuid, targetName);
            long current = fetchBalance(connection, targetUuid);
            long updated = current + amount;
            updateBalance(connection, targetUuid, updated);
            logTransaction(connection, actorId, null, targetUuid.toString(), amount, "GIVE", "Admin give balance");
            return UpdateResult.success(updated);
        });
    }

    public CompletableFuture<UpdateResult> takeBalance(UUID targetUuid, String targetName, long amount, String actorId) {
        return databaseManager.supplyAsync(connection -> {
            ensureAccount(connection, targetUuid, targetName);
            long current = fetchBalance(connection, targetUuid);
            if (current < amount) {
                return UpdateResult.failure("INSUFFICIENT_FUNDS", current);
            }
            long updated = current - amount;
            updateBalance(connection, targetUuid, updated);
            logTransaction(connection, actorId, null, targetUuid.toString(), amount, "TAKE", "Admin take balance");
            return UpdateResult.success(updated);
        });
    }

    private void ensureAccount(Connection connection, UUID uuid, String name) {
        String sql = """
                INSERT INTO players (uuid, name, balance, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name == null ? "Unknown" : name);
            statement.setLong(3, configManager.getStartingBalance());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private long fetchBalance(Connection connection, UUID uuid) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("balance");
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
        return 0L;
    }

    private void updateBalance(Connection connection, UUID uuid, long balance) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE players SET balance = ? WHERE uuid = ?")) {
            statement.setLong(1, balance);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void logTransaction(Connection connection, String actorId, String sourceUuid, String targetUuid, long amount, String type, String reason) {
        String sql = """
                INSERT INTO transactions (actor_uuid, source_uuid, target_uuid, amount, type, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actorId);
            statement.setString(2, sourceUuid);
            statement.setString(3, targetUuid);
            statement.setLong(4, amount);
            statement.setString(5, type);
            statement.setString(6, reason);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    public record TransferResult(boolean success, long senderBalance, long receiverBalance, String error) {
        public static TransferResult success(long senderBalance, long receiverBalance) {
            return new TransferResult(true, senderBalance, receiverBalance, null);
        }

        public static TransferResult failure(String error) {
            return new TransferResult(false, 0L, 0L, error);
        }
    }

    public record UpdateResult(boolean success, long balance, String error) {
        public static UpdateResult success(long balance) {
            return new UpdateResult(true, balance, null);
        }

        public static UpdateResult failure(String error, long balance) {
            return new UpdateResult(false, balance, error);
        }
    }
}
