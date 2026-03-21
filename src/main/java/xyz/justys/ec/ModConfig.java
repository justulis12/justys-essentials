package xyz.justys.ec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("justys-essentials");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "justys-essentials.json";
    private static volatile ModConfig cachedConfig;
    private static final String ROWS_NOTE = "Rows are clamped to the supported range 3-6. Maximum supported ender chest rows: 6.";
    private static final String WOODCUTTER_NOTE = "/woodcutter only registers and works when Nemo's Woodcutter is installed.";
    private static final String UPDATER_NOTE = "/justys update checks the official Justys' Essentials GitHub releases, downloads a newer version if one exists, and stages it to replace the current jar automatically after the server fully stops.";
    private static final String TREE_FELLER_NOTE = "Tree feller works on connected logs, nether stems, and huge mushroom blocks. Sneak while breaking to activate it.";
    private static final String VEIN_MINER_NOTE = "Vein miner works on connected ore blocks and ancient debris. Sneak while breaking to activate it.";
    private static final String VILLAGER_TRADING_NOTE = "If villager_infinite_trading is true, villager trades are reset whenever a player opens trading with them.";
    public static final int MIN_ROWS = 3;
    public static final int MAX_ROWS = 6;
    private static final int DEFAULT_ROWS = 3;

    private final int rows;
    private final String ecPermission;
    private final String reloadPermission;
    private final String craftPermission;
    private final String anvilPermission;
    private final String trashPermission;
    private final String beaconRangePermission;
    private final String stonecutterPermission;
    private final String loomPermission;
    private final String grindstonePermission;
    private final String woodcutterPermission;
    private final String homePermission;
    private final String sethomePermission;
    private final String delhomePermission;
    private final String homesPermission;
    private final String setDefaultHomePermission;
    private final String tpaPermission;
    private final String tpacceptPermission;
    private final String tpdenyPermission;
    private final String updatePermission;
    private final int maxBeaconRangeBonusChunks;
    private final boolean ecEnabled;
    private final boolean craftEnabled;
    private final boolean anvilEnabled;
    private final boolean trashEnabled;
    private final boolean cartographyEnabled;
    private final boolean smithingEnabled;
    private final boolean stonecutterEnabled;
    private final boolean loomEnabled;
    private final boolean grindstoneEnabled;
    private final boolean woodcutterEnabled;
    private final boolean homeCommandsEnabled;
    private final boolean tpaCommandsEnabled;
    private final boolean treeFellerEnabled;
    private final boolean veinMinerEnabled;
    private final boolean villagerInfiniteTrading;
    private final boolean treeFellerRequireSneak;
    private final boolean veinMinerRequireSneak;
    private final boolean treeFellerRequireAxe;
    private final boolean veinMinerRequirePickaxe;
    private final int maxHomesPerPlayer;
    private final int maxTreeBlocks;
    private final int maxVeinBlocks;
    private ModConfig(int rows, String ecPermission, String reloadPermission, String craftPermission,
                      String anvilPermission, String trashPermission, String beaconRangePermission,
                      String stonecutterPermission, String loomPermission, String grindstonePermission,
                      String woodcutterPermission, String homePermission, String sethomePermission,
                      String delhomePermission, String homesPermission, String setDefaultHomePermission,
                      String tpaPermission, String tpacceptPermission, String tpdenyPermission,
                      String updatePermission, int maxBeaconRangeBonusChunks, boolean ecEnabled,
                      boolean craftEnabled, boolean anvilEnabled, boolean trashEnabled, boolean cartographyEnabled,
                      boolean smithingEnabled, boolean stonecutterEnabled, boolean loomEnabled, boolean grindstoneEnabled,
                      boolean woodcutterEnabled, boolean homeCommandsEnabled, boolean tpaCommandsEnabled,
                      boolean treeFellerEnabled, boolean veinMinerEnabled, boolean villagerInfiniteTrading, boolean treeFellerRequireSneak,
                      boolean veinMinerRequireSneak,
                      boolean treeFellerRequireAxe, boolean veinMinerRequirePickaxe,
                      int maxHomesPerPlayer, int maxTreeBlocks, int maxVeinBlocks) {
        this.rows = rows;
        this.ecPermission = ecPermission;
        this.reloadPermission = reloadPermission;
        this.craftPermission = craftPermission;
        this.anvilPermission = anvilPermission;
        this.trashPermission = trashPermission;
        this.beaconRangePermission = beaconRangePermission;
        this.stonecutterPermission = stonecutterPermission;
        this.loomPermission = loomPermission;
        this.grindstonePermission = grindstonePermission;
        this.woodcutterPermission = woodcutterPermission;
        this.homePermission = homePermission;
        this.sethomePermission = sethomePermission;
        this.delhomePermission = delhomePermission;
        this.homesPermission = homesPermission;
        this.setDefaultHomePermission = setDefaultHomePermission;
        this.tpaPermission = tpaPermission;
        this.tpacceptPermission = tpacceptPermission;
        this.tpdenyPermission = tpdenyPermission;
        this.updatePermission = updatePermission;
        this.maxBeaconRangeBonusChunks = Math.max(0, maxBeaconRangeBonusChunks);
        this.ecEnabled = ecEnabled;
        this.craftEnabled = craftEnabled;
        this.anvilEnabled = anvilEnabled;
        this.trashEnabled = trashEnabled;
        this.cartographyEnabled = cartographyEnabled;
        this.smithingEnabled = smithingEnabled;
        this.stonecutterEnabled = stonecutterEnabled;
        this.loomEnabled = loomEnabled;
        this.grindstoneEnabled = grindstoneEnabled;
        this.woodcutterEnabled = woodcutterEnabled;
        this.homeCommandsEnabled = homeCommandsEnabled;
        this.tpaCommandsEnabled = tpaCommandsEnabled;
        this.treeFellerEnabled = treeFellerEnabled;
        this.veinMinerEnabled = veinMinerEnabled;
        this.villagerInfiniteTrading = villagerInfiniteTrading;
        this.treeFellerRequireSneak = treeFellerRequireSneak;
        this.veinMinerRequireSneak = veinMinerRequireSneak;
        this.treeFellerRequireAxe = treeFellerRequireAxe;
        this.veinMinerRequirePickaxe = veinMinerRequirePickaxe;
        this.maxHomesPerPlayer = maxHomesPerPlayer;
        this.maxTreeBlocks = Math.max(1, maxTreeBlocks);
        this.maxVeinBlocks = Math.max(1, maxVeinBlocks);
    }

    public int getRows() {
        return rows;
    }

    public String getEcPermission() {
        return ecPermission;
    }

    public String getReloadPermission() {
        return reloadPermission;
    }

    public String getCraftPermission() {
        return craftPermission;
    }

    public String getAnvilPermission() {
        return anvilPermission;
    }

    public String getTrashPermission() {
        return trashPermission;
    }

    public String getBeaconRangePermission() {
        return beaconRangePermission;
    }

    public String getStonecutterPermission() {
        return stonecutterPermission;
    }

    public String getLoomPermission() {
        return loomPermission;
    }

    public String getGrindstonePermission() {
        return grindstonePermission;
    }

    public String getWoodcutterPermission() {
        return woodcutterPermission;
    }

    public String getHomePermission() {
        return homePermission;
    }

    public String getSethomePermission() {
        return sethomePermission;
    }

    public String getDelhomePermission() {
        return delhomePermission;
    }

    public String getHomesPermission() {
        return homesPermission;
    }

    public String getSetDefaultHomePermission() {
        return setDefaultHomePermission;
    }

    public String getTpaPermission() {
        return tpaPermission;
    }

    public String getTpacceptPermission() {
        return tpacceptPermission;
    }

    public String getTpdenyPermission() {
        return tpdenyPermission;
    }

    public String getUpdatePermission() {
        return updatePermission;
    }

    public int getMaxBeaconRangeBonusChunks() {
        return maxBeaconRangeBonusChunks;
    }

    public boolean isEcEnabled() {
        return ecEnabled;
    }

    public boolean isCraftEnabled() {
        return craftEnabled;
    }

    public boolean isAnvilEnabled() {
        return anvilEnabled;
    }

    public boolean isTrashEnabled() {
        return trashEnabled;
    }

    public boolean isCartographyEnabled() {
        return cartographyEnabled;
    }

    public boolean isSmithingEnabled() {
        return smithingEnabled;
    }

    public boolean isStonecutterEnabled() {
        return stonecutterEnabled;
    }

    public boolean isLoomEnabled() {
        return loomEnabled;
    }

    public boolean isGrindstoneEnabled() {
        return grindstoneEnabled;
    }

    public boolean isWoodcutterEnabled() {
        return woodcutterEnabled;
    }

    public boolean isHomeCommandsEnabled() {
        return homeCommandsEnabled;
    }

    public boolean isTpaCommandsEnabled() {
        return tpaCommandsEnabled;
    }

    public boolean isTreeFellerEnabled() {
        return treeFellerEnabled;
    }

    public boolean isVeinMinerEnabled() {
        return veinMinerEnabled;
    }

    public boolean isVillagerInfiniteTrading() {
        return villagerInfiniteTrading;
    }

    public boolean isTreeFellerRequireSneak() {
        return treeFellerRequireSneak;
    }

    public boolean isVeinMinerRequireSneak() {
        return veinMinerRequireSneak;
    }

    public boolean isTreeFellerRequireAxe() {
        return treeFellerRequireAxe;
    }

    public boolean isVeinMinerRequirePickaxe() {
        return veinMinerRequirePickaxe;
    }

    public int getMaxHomesPerPlayer() {
        return maxHomesPerPlayer;
    }

    public int getMaxTreeBlocks() {
        return maxTreeBlocks;
    }

    public int getMaxVeinBlocks() {
        return maxVeinBlocks;
    }

    public static ModConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try {
                String raw = Files.readString(file);
                JsonObject obj = GSON.fromJson(raw, JsonObject.class);
                if (obj != null) {
                    ModConfig config = fromJson(obj);
                    if (needsRewrite(obj, config)) {
                        config.save(file);
                    }
                    cachedConfig = config;
                    return config;
                }
            } catch (IOException ignored) {
            }
        }

        ModConfig cfg = defaults();
        cfg.save(file);
        cachedConfig = cfg;
        return cfg;
    }

    public static ModConfig current() {
        return cachedConfig != null ? cachedConfig : load();
    }

    private static ModConfig fromJson(JsonObject obj) {
        int configuredRows = getInt(obj, "rows", DEFAULT_ROWS);
        int clampedRows = clampRows(configuredRows);
        if (configuredRows != clampedRows) {
            LOGGER.warn("Configured ender chest rows value {} is outside the supported range of {}-{}. Using {} instead.",
                    configuredRows, MIN_ROWS, MAX_ROWS, clampedRows);
        }

        JsonObject permissions = obj.has("permissions") && obj.get("permissions").isJsonObject()
                ? obj.getAsJsonObject("permissions")
                : new JsonObject();
        JsonObject enabledCommands = obj.has("enabled_commands") && obj.get("enabled_commands").isJsonObject()
                ? obj.getAsJsonObject("enabled_commands")
                : new JsonObject();

        boolean legacyHarvestingRequireSneak = getBoolean(obj, "harvesting_require_sneak", true);

        return new ModConfig(
                clampedRows,
                getString(permissions, "ec", "justysessentials.ec"),
                getString(permissions, "reload", "justysessentials.reload"),
                getString(permissions, "craft", "justysessentials.craft"),
                getString(permissions, "anvil", "justysessentials.anvil"),
                getString(permissions, "trash", "justysessentials.trash"),
                getString(permissions, "beaconrange", "justysessentials.beaconrange"),
                getString(permissions, "stonecutter", "justysessentials.stonecutter"),
                getString(permissions, "loom", "justysessentials.loom"),
                getString(permissions, "grindstone", "justysessentials.grindstone"),
                getString(permissions, "woodcutter", "justysessentials.woodcutter"),
                getString(permissions, "home", "justysessentials.home"),
                getString(permissions, "sethome", "justysessentials.sethome"),
                getString(permissions, "delhome", "justysessentials.delhome"),
                getString(permissions, "homes", "justysessentials.homes"),
                getString(permissions, "setdefaulthome", "justysessentials.setdefaulthome"),
                getString(permissions, "tpa", "justysessentials.tpa"),
                getString(permissions, "tpaccept", "justysessentials.tpaccept"),
                getString(permissions, "tpdeny", "justysessentials.tpdeny"),
                getString(permissions, "update", "justysessentials.update"),
                getInt(obj, "max_beacon_range_bonus_chunks", 64),
                getBoolean(enabledCommands, "ec", true),
                getBoolean(enabledCommands, "craft", true),
                getBoolean(enabledCommands, "anvil", true),
                getBoolean(enabledCommands, "trash", true),
                getBoolean(enabledCommands, "cartography", true),
                getBoolean(enabledCommands, "smithing", true),
                getBoolean(enabledCommands, "stonecutter", true),
                getBoolean(enabledCommands, "loom", true),
                getBoolean(enabledCommands, "grindstone", true),
                getBoolean(enabledCommands, "woodcutter", true),
                getBoolean(enabledCommands, "home_commands", true),
                getBoolean(enabledCommands, "tpa_commands", true),
                getBoolean(obj, "tree_feller_enabled", true),
                getBoolean(obj, "vein_miner_enabled", true),
                getBoolean(obj, "villager_infinite_trading", false),
                getBoolean(obj, "tree_feller_require_sneak", legacyHarvestingRequireSneak),
                getBoolean(obj, "vein_miner_require_sneak", legacyHarvestingRequireSneak),
                getBoolean(obj, "tree_feller_require_axe", true),
                getBoolean(obj, "vein_miner_require_pickaxe", true),
                getInt(obj, "max_homes_per_player", -1),
                getInt(obj, "max_tree_blocks", 256),
                getInt(obj, "max_vein_blocks", 64)
        );
    }

    private static ModConfig defaults() {
        return new ModConfig(
                DEFAULT_ROWS,
                "justysessentials.ec",
                "justysessentials.reload",
                "justysessentials.craft",
                "justysessentials.anvil",
                "justysessentials.trash",
                "justysessentials.beaconrange",
                "justysessentials.stonecutter",
                "justysessentials.loom",
                "justysessentials.grindstone",
                "justysessentials.woodcutter",
                "justysessentials.home",
                "justysessentials.sethome",
                "justysessentials.delhome",
                "justysessentials.homes",
                "justysessentials.setdefaulthome",
                "justysessentials.tpa",
                "justysessentials.tpaccept",
                "justysessentials.tpdeny",
                "justysessentials.update",
                64,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                -1,
                256,
                64
        );
    }

    private static boolean needsRewrite(JsonObject obj, ModConfig config) {
        if (getInt(obj, "rows", DEFAULT_ROWS) != config.rows) {
            return true;
        }
        if (!ROWS_NOTE.equals(getString(obj, "rows_note", ""))) {
            return true;
        }
        if (!WOODCUTTER_NOTE.equals(getString(obj, "woodcutter_note", ""))) {
            return true;
        }
        if (!UPDATER_NOTE.equals(getString(obj, "updater_note", ""))) {
            return true;
        }
        if (!TREE_FELLER_NOTE.equals(getString(obj, "tree_feller_note", ""))) {
            return true;
        }
        if (!VEIN_MINER_NOTE.equals(getString(obj, "vein_miner_note", ""))) {
            return true;
        }
        if (!VILLAGER_TRADING_NOTE.equals(getString(obj, "villager_trading_note", ""))) {
            return true;
        }
        if (!obj.has("permissions") || !obj.get("permissions").isJsonObject()) {
            return true;
        }
        if (!obj.has("enabled_commands") || !obj.get("enabled_commands").isJsonObject()) {
            return true;
        }
        JsonObject permissions = obj.getAsJsonObject("permissions");
        JsonObject enabledCommands = obj.getAsJsonObject("enabled_commands");
        return !config.ecPermission.equals(getString(permissions, "ec", ""))
                || !config.reloadPermission.equals(getString(permissions, "reload", ""))
                || !config.craftPermission.equals(getString(permissions, "craft", ""))
                || !config.anvilPermission.equals(getString(permissions, "anvil", ""))
                || !config.trashPermission.equals(getString(permissions, "trash", ""))
                || !config.beaconRangePermission.equals(getString(permissions, "beaconrange", ""))
                || !config.stonecutterPermission.equals(getString(permissions, "stonecutter", ""))
                || !config.loomPermission.equals(getString(permissions, "loom", ""))
                || !config.grindstonePermission.equals(getString(permissions, "grindstone", ""))
                || !config.woodcutterPermission.equals(getString(permissions, "woodcutter", ""))
                || !config.homePermission.equals(getString(permissions, "home", ""))
                || !config.sethomePermission.equals(getString(permissions, "sethome", ""))
                || !config.delhomePermission.equals(getString(permissions, "delhome", ""))
                || !config.homesPermission.equals(getString(permissions, "homes", ""))
                || !config.setDefaultHomePermission.equals(getString(permissions, "setdefaulthome", ""))
                || !config.tpaPermission.equals(getString(permissions, "tpa", ""))
                || !config.tpacceptPermission.equals(getString(permissions, "tpaccept", ""))
                || !config.tpdenyPermission.equals(getString(permissions, "tpdeny", ""))
                || !config.updatePermission.equals(getString(permissions, "update", ""))
                || getInt(obj, "max_beacon_range_bonus_chunks", -1) != config.maxBeaconRangeBonusChunks
                || getBoolean(enabledCommands, "ec", false) != config.ecEnabled
                || getBoolean(enabledCommands, "craft", false) != config.craftEnabled
                || getBoolean(enabledCommands, "anvil", false) != config.anvilEnabled
                || getBoolean(enabledCommands, "trash", false) != config.trashEnabled
                || getBoolean(enabledCommands, "cartography", false) != config.cartographyEnabled
                || getBoolean(enabledCommands, "smithing", false) != config.smithingEnabled
                || getBoolean(enabledCommands, "stonecutter", false) != config.stonecutterEnabled
                || getBoolean(enabledCommands, "loom", false) != config.loomEnabled
                || getBoolean(enabledCommands, "grindstone", false) != config.grindstoneEnabled
                || getBoolean(enabledCommands, "woodcutter", false) != config.woodcutterEnabled
                || getBoolean(enabledCommands, "home_commands", false) != config.homeCommandsEnabled
                || getBoolean(enabledCommands, "tpa_commands", false) != config.tpaCommandsEnabled
                || getBoolean(obj, "tree_feller_enabled", false) != config.treeFellerEnabled
                || getBoolean(obj, "vein_miner_enabled", false) != config.veinMinerEnabled
                || getBoolean(obj, "villager_infinite_trading", true) != config.villagerInfiniteTrading
                || getBoolean(obj, "tree_feller_require_sneak", false) != config.treeFellerRequireSneak
                || getBoolean(obj, "vein_miner_require_sneak", false) != config.veinMinerRequireSneak
                || getBoolean(obj, "tree_feller_require_axe", false) != config.treeFellerRequireAxe
                || getBoolean(obj, "vein_miner_require_pickaxe", false) != config.veinMinerRequirePickaxe
                || getInt(obj, "max_homes_per_player", Integer.MIN_VALUE) != config.maxHomesPerPlayer
                || getInt(obj, "max_tree_blocks", -1) != config.maxTreeBlocks
                || getInt(obj, "max_vein_blocks", -1) != config.maxVeinBlocks;
    }

    private void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("rows", rows);
            obj.addProperty("rows_note", ROWS_NOTE);
            obj.addProperty("woodcutter_note", WOODCUTTER_NOTE);
            obj.addProperty("updater_note", UPDATER_NOTE);
            obj.addProperty("tree_feller_note", TREE_FELLER_NOTE);
            obj.addProperty("vein_miner_note", VEIN_MINER_NOTE);
            obj.addProperty("villager_trading_note", VILLAGER_TRADING_NOTE);
            obj.addProperty("max_beacon_range_bonus_chunks", maxBeaconRangeBonusChunks);
            obj.addProperty("tree_feller_enabled", treeFellerEnabled);
            obj.addProperty("vein_miner_enabled", veinMinerEnabled);
            obj.addProperty("villager_infinite_trading", villagerInfiniteTrading);
            obj.addProperty("tree_feller_require_sneak", treeFellerRequireSneak);
            obj.addProperty("vein_miner_require_sneak", veinMinerRequireSneak);
            obj.addProperty("tree_feller_require_axe", treeFellerRequireAxe);
            obj.addProperty("vein_miner_require_pickaxe", veinMinerRequirePickaxe);
            obj.addProperty("max_homes_per_player", maxHomesPerPlayer);
            obj.addProperty("max_tree_blocks", maxTreeBlocks);
            obj.addProperty("max_vein_blocks", maxVeinBlocks);

            JsonObject permissions = new JsonObject();
            permissions.addProperty("ec", ecPermission);
            permissions.addProperty("reload", reloadPermission);
            permissions.addProperty("craft", craftPermission);
            permissions.addProperty("anvil", anvilPermission);
            permissions.addProperty("trash", trashPermission);
            permissions.addProperty("beaconrange", beaconRangePermission);
            permissions.addProperty("stonecutter", stonecutterPermission);
            permissions.addProperty("loom", loomPermission);
            permissions.addProperty("grindstone", grindstonePermission);
            permissions.addProperty("woodcutter", woodcutterPermission);
            permissions.addProperty("home", homePermission);
            permissions.addProperty("sethome", sethomePermission);
            permissions.addProperty("delhome", delhomePermission);
            permissions.addProperty("homes", homesPermission);
            permissions.addProperty("setdefaulthome", setDefaultHomePermission);
            permissions.addProperty("tpa", tpaPermission);
            permissions.addProperty("tpaccept", tpacceptPermission);
            permissions.addProperty("tpdeny", tpdenyPermission);
            permissions.addProperty("update", updatePermission);
            obj.add("permissions", permissions);

            JsonObject enabledCommands = new JsonObject();
            enabledCommands.addProperty("ec", ecEnabled);
            enabledCommands.addProperty("craft", craftEnabled);
            enabledCommands.addProperty("anvil", anvilEnabled);
            enabledCommands.addProperty("trash", trashEnabled);
            enabledCommands.addProperty("cartography", cartographyEnabled);
            enabledCommands.addProperty("smithing", smithingEnabled);
            enabledCommands.addProperty("stonecutter", stonecutterEnabled);
            enabledCommands.addProperty("loom", loomEnabled);
            enabledCommands.addProperty("grindstone", grindstoneEnabled);
            enabledCommands.addProperty("woodcutter", woodcutterEnabled);
            enabledCommands.addProperty("home_commands", homeCommandsEnabled);
            enabledCommands.addProperty("tpa_commands", tpaCommandsEnabled);
            obj.add("enabled_commands", enabledCommands);

            Files.writeString(file, GSON.toJson(obj));
        } catch (IOException ignored) {
        }
    }

    private static int clampRows(int rows) {
        if (rows < MIN_ROWS) {
            return MIN_ROWS;
        }
        if (rows > MAX_ROWS) {
            return MAX_ROWS;
        }
        return rows;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
    }
}
