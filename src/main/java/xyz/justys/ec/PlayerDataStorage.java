package xyz.justys.ec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = FabricLoader.getInstance().getGameDir().resolve("justysessentials");
    private static final Path STORAGE_FILE = STORAGE_DIR.resolve("player-data.json");

    private final Logger logger;
    private StorageData data;

    public PlayerDataStorage(Logger logger) {
        this.logger = logger;
        this.data = loadOrCreate();
    }

    public synchronized void reload() {
        this.data = loadOrCreate();
    }

    public synchronized void recordPlayerName(ServerPlayerEntity player) {
        PlayerEntry entry = getOrCreate(player.getUuid());
        entry.lastKnownName = player.getName().getString();
        entry.lastSeenAtMs = System.currentTimeMillis();
        saveQuietly();
    }

    public synchronized void addPlaytime(ServerPlayerEntity player, long seconds) {
        if (seconds <= 0) {
            return;
        }
        PlayerEntry entry = getOrCreate(player.getUuid());
        entry.lastKnownName = player.getName().getString();
        entry.playtimeSeconds += seconds;
    }

    public synchronized long getPlaytimeSeconds(UUID uuid) {
        return getOrCreate(uuid).playtimeSeconds;
    }

    public synchronized List<PlaytimeEntry> getTopPlaytimes(int limit) {
        return data.players.stream()
                .filter(entry -> !entry.lastKnownName.isBlank())
                .sorted(Comparator.comparingLong((PlayerEntry entry) -> entry.playtimeSeconds).reversed())
                .limit(Math.max(1, limit))
                .map(entry -> new PlaytimeEntry(entry.lastKnownName, entry.playtimeSeconds))
                .toList();
    }

    public synchronized void recordDeath(ServerPlayerEntity player) {
        PlayerEntry entry = getOrCreate(player.getUuid());
        entry.lastKnownName = player.getName().getString();
        entry.lastDeath = new DeathEntry(
                player.getServerWorld().getRegistryKey().getValue().toString(),
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ()
        );
        saveQuietly();
    }

    public synchronized Optional<DeathEntry> getLastDeath(UUID uuid) {
        return Optional.ofNullable(getOrCreate(uuid).lastDeath);
    }

    public synchronized void flush() {
        saveQuietly();
    }

    private PlayerEntry getOrCreate(UUID uuid) {
        String id = uuid.toString();
        for (PlayerEntry entry : data.players) {
            if (id.equals(entry.uuid)) {
                return entry;
            }
        }

        PlayerEntry created = new PlayerEntry(id);
        data.players.add(created);
        return created;
    }

    private StorageData loadOrCreate() {
        try {
            Files.createDirectories(STORAGE_DIR);
            if (!Files.exists(STORAGE_FILE)) {
                StorageData fresh = new StorageData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            String raw = Files.readString(STORAGE_FILE);
            if (raw.isBlank()) {
                StorageData fresh = new StorageData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            StorageData loaded = GSON.fromJson(raw, StorageData.class);
            if (loaded == null || loaded.players == null) {
                return new StorageData();
            }
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load Justys' Essentials player data from {}. Using empty data.", STORAGE_FILE, e);
            return new StorageData();
        }
    }

    private void saveQuietly() {
        try {
            Files.createDirectories(STORAGE_DIR);
            Files.writeString(STORAGE_FILE, GSON.toJson(data));
        } catch (IOException e) {
            logger.error("Failed to save Justys' Essentials player data to {}.", STORAGE_FILE, e);
        }
    }

    private static final class StorageData {
        private List<PlayerEntry> players = new ArrayList<>();
    }

    private static final class PlayerEntry {
        private final String uuid;
        private String lastKnownName = "";
        private long playtimeSeconds = 0L;
        private long lastSeenAtMs = 0L;
        private DeathEntry lastDeath;

        private PlayerEntry(String uuid) {
            this.uuid = uuid;
        }
    }

    public record DeathEntry(String world, int x, int y, int z) {
    }

    public record PlaytimeEntry(String name, long seconds) {
    }
}
