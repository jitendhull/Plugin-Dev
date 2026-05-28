package com.demise.core.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private File databaseFile;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "demisecore-db");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void init() throws SQLException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException("Failed to create plugin data folder.");
        }
        databaseFile = new File(plugin.getDataFolder(), "demisecore.db");
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        balance INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        actor_uuid TEXT NOT NULL,
                        source_uuid TEXT,
                        target_uuid TEXT,
                        amount INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mutes (
                        uuid TEXT PRIMARY KEY,
                        muted_by TEXT,
                        reason TEXT,
                        expires_at INTEGER,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS warnings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        warned_by TEXT,
                        reason TEXT,
                        severity INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bans (
                        uuid TEXT PRIMARY KEY,
                        banned_by TEXT,
                        reason TEXT,
                        expires_at INTEGER,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS moderation_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        action TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        actor_uuid TEXT,
                        reason TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public <T> CompletableFuture<T> supplyAsync(Function<Connection, T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return task.apply(connection);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> runAsync(Consumer<Connection> task) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection()) {
                task.accept(connection);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }
}
