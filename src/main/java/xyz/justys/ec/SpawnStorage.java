package xyz.justys.ec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SpawnStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = FabricLoader.getInstance().getGameDir().resolve("justysessentials");
    private static final Path STORAGE_FILE = STORAGE_DIR.resolve("spawn.json");

    private final Logger logger;
    private SpawnData data;

    public SpawnStorage(Logger logger) {
        this.logger = logger;
        this.data = loadOrCreate();
    }

    public synchronized void reload() {
        this.data = loadOrCreate();
    }

    public synchronized Path getStorageFile() {
        return STORAGE_FILE;
    }

    public synchronized boolean hasSpawn() {
        return data.world != null && !data.world.isBlank();
    }

    public synchronized Optional<SpawnPoint> getSpawn() {
        if (!hasSpawn()) {
            return Optional.empty();
        }
        return Optional.of(new SpawnPoint(data.world, data.x, data.y, data.z));
    }

    public synchronized void setSpawn(ServerPlayerEntity player) throws IOException {
        BlockPos pos = player.getBlockPos();
        data = new SpawnData(player.getServerWorld().getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
        save();
    }

    public synchronized Optional<String> teleportToSpawn(ServerPlayerEntity player) {
        if (!hasSpawn()) {
            return Optional.of("Spawn has not been set yet.");
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return Optional.of("Server is not available.");
        }

        ServerWorld world;
        try {
            world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(data.world)));
        } catch (Exception e) {
            logger.warn("Failed to resolve saved spawn world '{}'.", data.world, e);
            return Optional.of("Spawn world '" + data.world + "' could not be resolved.");
        }

        if (world == null) {
            return Optional.of("Spawn world '" + data.world + "' is not loaded.");
        }

        Optional<net.minecraft.util.math.Vec3d> safeTarget = TeleportHelper.findSafeTeleportTarget(world, new BlockPos(data.x, data.y, data.z));
        if (safeTarget.isEmpty()) {
            return Optional.of("Spawn is obstructed or unsafe.");
        }

        net.minecraft.util.math.Vec3d destination = safeTarget.get();
        player.teleport(world, destination.x, destination.y, destination.z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
        return Optional.empty();
    }

    private SpawnData loadOrCreate() {
        try {
            Files.createDirectories(STORAGE_DIR);
            if (!Files.exists(STORAGE_FILE)) {
                SpawnData fresh = new SpawnData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            String raw = Files.readString(STORAGE_FILE);
            if (raw.isBlank()) {
                SpawnData fresh = new SpawnData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            SpawnData loaded = GSON.fromJson(raw, SpawnData.class);
            return loaded == null ? new SpawnData() : loaded;
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load Justys' Essentials spawn storage from {}. Using empty data.", STORAGE_FILE, e);
            return new SpawnData();
        }
    }

    private void save() throws IOException {
        Files.createDirectories(STORAGE_DIR);
        Files.writeString(STORAGE_FILE, GSON.toJson(data));
    }

    private static final class SpawnData {
        private String world = "";
        private int x = 0;
        private int y = 64;
        private int z = 0;

        private SpawnData() {
        }

        private SpawnData(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public record SpawnPoint(String world, int x, int y, int z) {
    }
}
