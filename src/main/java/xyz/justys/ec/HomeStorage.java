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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class HomeStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = FabricLoader.getInstance().getGameDir().resolve("justysessentials");
    private static final Path STORAGE_FILE = STORAGE_DIR.resolve("storage.json");

    private final Logger logger;
    private StorageData data;

    public HomeStorage(Logger logger) {
        this.logger = logger;
        this.data = loadOrCreate();
    }

    public synchronized void reload() {
        this.data = loadOrCreate();
    }

    public synchronized Path getStorageFile() {
        return STORAGE_FILE;
    }

    public synchronized List<HomeEntry> getHomes(UUID playerUuid) {
        return List.copyOf(getOrCreatePlayer(playerUuid).Homes);
    }

    public synchronized Optional<HomeEntry> getHome(UUID playerUuid, String name) {
        return getOrCreatePlayer(playerUuid).Homes.stream()
                .filter(home -> home.name.equalsIgnoreCase(name))
                .findFirst();
    }

    public synchronized Optional<HomeEntry> getDefaultHome(UUID playerUuid) {
        PlayerEntry player = getOrCreatePlayer(playerUuid);
        if (player.DefaultHome == null || player.DefaultHome.isBlank()) {
            return Optional.empty();
        }
        return getHome(playerUuid, player.DefaultHome);
    }

    public synchronized boolean addHome(ServerPlayerEntity player, String name) throws IOException {
        PlayerEntry playerEntry = getOrCreatePlayer(player.getUuid());
        if (playerEntry.Homes.stream().anyMatch(home -> home.name.equalsIgnoreCase(name))) {
            return false;
        }

        BlockPos pos = player.getBlockPos();
        String worldId = player.getServerWorld().getRegistryKey().getValue().toString();
        playerEntry.Homes.add(new HomeEntry(name, pos.getX(), pos.getY(), pos.getZ(), worldId));
        if (playerEntry.DefaultHome == null || playerEntry.DefaultHome.isBlank()) {
            playerEntry.DefaultHome = name;
        }
        save();
        return true;
    }

    public synchronized boolean deleteHome(UUID playerUuid, String name) throws IOException {
        PlayerEntry player = getOrCreatePlayer(playerUuid);
        Optional<HomeEntry> match = getHome(playerUuid, name);
        if (match.isEmpty()) {
            return false;
        }

        player.Homes.remove(match.get());
        if (Objects.equals(player.DefaultHome, match.get().name)) {
            player.DefaultHome = player.Homes.isEmpty() ? "" : player.Homes.getFirst().name;
        }
        save();
        return true;
    }

    public synchronized boolean setDefaultHome(UUID playerUuid, String name) throws IOException {
        Optional<HomeEntry> match = getHome(playerUuid, name);
        if (match.isEmpty()) {
            return false;
        }

        getOrCreatePlayer(playerUuid).DefaultHome = match.get().name;
        save();
        return true;
    }

    public synchronized Optional<String> teleportToHome(ServerPlayerEntity player, String name) {
        Optional<HomeEntry> home = getHome(player.getUuid(), name);
        if (home.isEmpty()) {
            return Optional.of("Home '" + name + "' does not exist.");
        }

        return teleportToHome(player, home.get());
    }

    public synchronized Optional<String> teleportToDefaultHome(ServerPlayerEntity player) {
        Optional<HomeEntry> home = getDefaultHome(player.getUuid());
        if (home.isEmpty()) {
            return Optional.of("No default home is set.");
        }

        return teleportToHome(player, home.get());
    }

    private Optional<String> teleportToHome(ServerPlayerEntity player, HomeEntry home) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Optional.of("Server is not available.");
        }

        ServerWorld world;
        try {
            world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(home.world)));
        } catch (Exception e) {
            logger.warn("Failed to resolve world '{}' for home '{}'.", home.world, home.name, e);
            return Optional.of("Home world '" + home.world + "' could not be resolved.");
        }

        if (world == null) {
            return Optional.of("Home world '" + home.world + "' is not loaded.");
        }

        player.teleport(world, home.x + 0.5, home.y, home.z + 0.5, Collections.emptySet(), player.getYaw(), player.getPitch(), true);
        return Optional.empty();
    }

    private StorageData loadOrCreate() {
        try {
            Files.createDirectories(STORAGE_DIR);
            if (!Files.exists(STORAGE_FILE)) {
                StorageData fresh = new StorageData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                logger.info("Created Justys' Essentials home storage at {}.", STORAGE_FILE);
                return fresh;
            }

            String raw = Files.readString(STORAGE_FILE);
            if (raw.isBlank()) {
                StorageData fresh = new StorageData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            StorageData loaded = GSON.fromJson(raw, StorageData.class);
            if (loaded == null) {
                loaded = new StorageData();
            }
            if (loaded.Warps == null) {
                loaded.Warps = new ArrayList<>();
            }
            if (loaded.Players == null) {
                loaded.Players = new ArrayList<>();
            }
            for (PlayerEntry player : loaded.Players) {
                if (player.DefaultHome == null) {
                    player.DefaultHome = "";
                }
                if (player.Homes == null) {
                    player.Homes = new ArrayList<>();
                }
            }
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load Justys' Essentials home storage from {}. Using empty data.", STORAGE_FILE, e);
            return new StorageData();
        }
    }

    private PlayerEntry getOrCreatePlayer(UUID playerUuid) {
        String uuid = playerUuid.toString();
        for (PlayerEntry player : data.Players) {
            if (uuid.equals(player.UUID)) {
                return player;
            }
        }

        PlayerEntry created = new PlayerEntry(uuid);
        data.Players.add(created);
        return created;
    }

    private void save() throws IOException {
        Files.createDirectories(STORAGE_DIR);
        Files.writeString(STORAGE_FILE, GSON.toJson(data));
    }

    private static final class StorageData {
        private List<Object> Warps = new ArrayList<>();
        private List<PlayerEntry> Players = new ArrayList<>();
    }

    private static final class PlayerEntry {
        private final String UUID;
        private String DefaultHome = "";
        private List<HomeEntry> Homes = new ArrayList<>();

        private PlayerEntry(String uuid) {
            this.UUID = uuid;
        }
    }

    public static final class HomeEntry {
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final String world;

        private HomeEntry(String name, int x, int y, int z, String world) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }

        public String name() {
            return name;
        }

        public String world() {
            return world;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }
    }
}
