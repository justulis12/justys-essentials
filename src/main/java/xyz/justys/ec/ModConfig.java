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
    private static final String ROWS_NOTE = "Rows are clamped to the supported range 3-6. Maximum supported ender chest rows: 6.";
    private static final String WOODCUTTER_NOTE = "/woodcutter only registers and works when Nemo's Woodcutter is installed.";
    private static final String UPDATER_NOTE = "/justys update checks the official Justys' Essentials GitHub releases, downloads a newer version if one exists, and stages it to replace the current jar automatically after the server fully stops.";
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
    private final boolean craftEnabled;
    private final boolean anvilEnabled;
    private final boolean trashEnabled;
    private final boolean stonecutterEnabled;
    private final boolean loomEnabled;
    private final boolean grindstoneEnabled;
    private final boolean woodcutterEnabled;
    private final boolean homeCommandsEnabled;
    private final boolean tpaCommandsEnabled;
    private ModConfig(int rows, String ecPermission, String reloadPermission, String craftPermission,
                      String anvilPermission, String trashPermission, String beaconRangePermission,
                      String stonecutterPermission, String loomPermission, String grindstonePermission,
                      String woodcutterPermission, String homePermission, String sethomePermission,
                      String delhomePermission, String homesPermission, String setDefaultHomePermission,
                      String tpaPermission, String tpacceptPermission, String tpdenyPermission,
                      String updatePermission, int maxBeaconRangeBonusChunks, boolean craftEnabled,
                      boolean anvilEnabled, boolean trashEnabled, boolean stonecutterEnabled,
                      boolean loomEnabled, boolean grindstoneEnabled, boolean woodcutterEnabled,
                      boolean homeCommandsEnabled, boolean tpaCommandsEnabled) {
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
        this.craftEnabled = craftEnabled;
        this.anvilEnabled = anvilEnabled;
        this.trashEnabled = trashEnabled;
        this.stonecutterEnabled = stonecutterEnabled;
        this.loomEnabled = loomEnabled;
        this.grindstoneEnabled = grindstoneEnabled;
        this.woodcutterEnabled = woodcutterEnabled;
        this.homeCommandsEnabled = homeCommandsEnabled;
        this.tpaCommandsEnabled = tpaCommandsEnabled;
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

    public boolean isCraftEnabled() {
        return craftEnabled;
    }

    public boolean isAnvilEnabled() {
        return anvilEnabled;
    }

    public boolean isTrashEnabled() {
        return trashEnabled;
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
                    return config;
                }
            } catch (IOException ignored) {
            }
        }

        ModConfig cfg = defaults();
        cfg.save(file);
        return cfg;
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
                getBoolean(enabledCommands, "craft", true),
                getBoolean(enabledCommands, "anvil", true),
                getBoolean(enabledCommands, "trash", true),
                getBoolean(enabledCommands, "stonecutter", true),
                getBoolean(enabledCommands, "loom", true),
                getBoolean(enabledCommands, "grindstone", true),
                getBoolean(enabledCommands, "woodcutter", true),
                getBoolean(enabledCommands, "home_commands", true),
                getBoolean(enabledCommands, "tpa_commands", true)
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
                true,
                true,
                true,
                true,
                true,
                true
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
                || getBoolean(enabledCommands, "craft", false) != config.craftEnabled
                || getBoolean(enabledCommands, "anvil", false) != config.anvilEnabled
                || getBoolean(enabledCommands, "trash", false) != config.trashEnabled
                || getBoolean(enabledCommands, "stonecutter", false) != config.stonecutterEnabled
                || getBoolean(enabledCommands, "loom", false) != config.loomEnabled
                || getBoolean(enabledCommands, "grindstone", false) != config.grindstoneEnabled
                || getBoolean(enabledCommands, "woodcutter", false) != config.woodcutterEnabled
                || getBoolean(enabledCommands, "home_commands", false) != config.homeCommandsEnabled
                || getBoolean(enabledCommands, "tpa_commands", false) != config.tpaCommandsEnabled;
    }

    private void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("rows", rows);
            obj.addProperty("rows_note", ROWS_NOTE);
            obj.addProperty("woodcutter_note", WOODCUTTER_NOTE);
            obj.addProperty("updater_note", UPDATER_NOTE);
            obj.addProperty("max_beacon_range_bonus_chunks", maxBeaconRangeBonusChunks);

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
            enabledCommands.addProperty("craft", craftEnabled);
            enabledCommands.addProperty("anvil", anvilEnabled);
            enabledCommands.addProperty("trash", trashEnabled);
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
