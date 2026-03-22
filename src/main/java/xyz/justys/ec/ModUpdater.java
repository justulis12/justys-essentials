package xyz.justys.ec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModUpdater {
    private static final String MOD_ID = "justys_essentials";
    private static final String USER_AGENT = "Justys-Essentials-Updater";
    private static final String GITHUB_OWNER = "justulis12";
    private static final String GITHUB_REPO = "justys-essentials";
    private static final String ASSET_PREFIX = "justys-essentials-";
    private static final AtomicBoolean AUTO_APPLY_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicBoolean UPDATE_CHECK_RUNNING = new AtomicBoolean(false);

    private ModUpdater() {
    }

    public static void preparePendingUpdate(Logger logger) {
        PendingUpdate pending = findPendingUpdate(logger);
        if (pending == null) {
            return;
        }

        logger.info("Detected staged Justys' Essentials update at {}. Scheduling automatic replacement after shutdown.", pending.pendingJar());
        if (schedulePendingReplacement(pending, logger)) {
            logger.info("Automatic replacement helper is ready. The staged jar will replace the current jar after this server process fully stops.");
        } else {
            logger.warn("Failed to schedule automatic replacement helper. The staged update will remain pending until replacement succeeds.");
        }
    }

    public static void runStartupUpdateCheck(Logger logger) {
        if (!UPDATE_CHECK_RUNNING.compareAndSet(false, true)) {
            return;
        }

        Thread updateThread = new Thread(() -> {
            try {
                if (findPendingUpdate(logger) != null) {
                    logger.info("Skipping automatic Justys' Essentials update check because a staged update is already waiting to be applied.");
                    return;
                }

                String result = checkAndStageUpdate(logger);
                logger.info("Startup update check result: {}", result);
            } catch (Exception e) {
                logger.error("Automatic Justys' Essentials update check failed.", e);
            } finally {
                UPDATE_CHECK_RUNNING.set(false);
            }
        }, "justys-essentials-update-check");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    public static String checkAndStageUpdate(Logger logger) throws IOException, InterruptedException {
        PendingUpdate pending = findPendingUpdate(logger);
        if (pending != null) {
            logger.info("A staged Justys' Essentials update is already present at {}.", pending.pendingJar());
            return "A Justys' Essentials update is already staged and will be applied after the server stops.";
        }

        logger.info("Checking GitHub releases for a Justys' Essentials update.");
        ReleaseInfo release = fetchLatestRelease();
        String currentVersion = getCurrentVersion();
        int comparison = compareVersions(currentVersion, release.version);

        if (comparison >= 0) {
            logger.info("Justys' Essentials is up to date. current={}, latest={}", currentVersion, release.version);
            return "Justys' Essentials is up to date (" + currentVersion + ").";
        }

        logger.info("Update available for Justys' Essentials. current={}, latest={}, asset={}", currentVersion, release.version, release.assetName);
        stageReleaseDownload(release, logger);
        return "Update available: " + release.version + " (current: " + currentVersion + "). It will be installed after the server stops and take effect on the next restart.";
    }

    private static ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest releaseRequest = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> releaseResponse = client.send(releaseRequest, HttpResponse.BodyHandlers.ofString());
        if (releaseResponse.statusCode() != 200) {
            throw new IOException("GitHub latest release lookup failed with HTTP " + releaseResponse.statusCode() + ".");
        }

        JsonObject release = JsonParser.parseString(releaseResponse.body()).getAsJsonObject();
        String latestTag = normalizeVersion(getString(release, "tag_name"));
        if (latestTag.isBlank()) {
            throw new IOException("Latest GitHub release has no tag name.");
        }

        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) {
            throw new IOException("Latest GitHub release has no assets.");
        }

        for (JsonElement assetElement : assets) {
            JsonObject asset = assetElement.getAsJsonObject();
            String assetName = getString(asset, "name");
            String downloadUrl = getString(asset, "browser_download_url");
            if (assetName.endsWith(".jar") && !assetName.contains("sources") && assetName.startsWith(ASSET_PREFIX) && !downloadUrl.isBlank()) {
                return new ReleaseInfo(latestTag, assetName, downloadUrl);
            }
        }

        throw new IOException("Latest GitHub release has no matching mod jar asset.");
    }

    private static void stageReleaseDownload(ReleaseInfo release, Logger logger) throws IOException, InterruptedException {
        PendingUpdate pending = createPendingUpdate(release);
        Path pendingJar = pending.pendingJar();
        Path tempJar = pendingJar.resolveSibling(pendingJar.getFileName() + ".tmp");
        Files.createDirectories(pendingJar.getParent());

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(release.downloadUrl))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<InputStream> downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (downloadResponse.statusCode() != 200) {
            throw new IOException("Asset download failed with HTTP " + downloadResponse.statusCode() + ".");
        }

        logger.info("Downloading Justys' Essentials update asset {}.", release.assetName);
        try (InputStream inputStream = downloadResponse.body()) {
            Files.copy(inputStream, tempJar, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(tempJar, pendingJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        writePendingMetadata(release.version, release.assetName, pending.metadataPath());
        logger.info("Staged Justys' Essentials update {} at {}.", release.assetName, pendingJar);

        if (!schedulePendingReplacement(pending, logger)) {
            throw new IOException("Downloaded update, but failed to schedule automatic replacement after shutdown.");
        }
        logger.info("Automatic replacement helper scheduled successfully for the staged Justys' Essentials update.");
    }

    private static void writePendingMetadata(String version, String assetName, Path metadataPath) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", version);
        metadata.addProperty("asset_name", assetName);
        metadata.addProperty("pending_file", assetName + ".pending");
        Files.writeString(metadataPath, metadata.toString());
    }

    public static Path getCurrentJarPath() {
        ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Could not resolve current mod container for " + MOD_ID + "."));
        return container.getOrigin().getPaths().stream()
                .filter(Files::isRegularFile)
                .min(Comparator.comparing(Path::toString))
                .orElseThrow(() -> new IllegalStateException("Could not resolve current mod jar path for " + MOD_ID + "."));
    }

    private static String getCurrentVersion() {
        ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Could not resolve current mod container for " + MOD_ID + "."));
        return normalizeVersion(container.getMetadata().getVersion().getFriendlyString());
    }

    private static PendingUpdate createPendingUpdate(ReleaseInfo release) {
        Path currentJar = getCurrentJarPath();
        Path jarDirectory = currentJar.getParent();
        Path targetJar = jarDirectory.resolve(release.assetName);
        Path pendingJar = jarDirectory.resolve(release.assetName + ".pending");
        Path metadataPath = jarDirectory.resolve(release.assetName + ".pending.json");
        return new PendingUpdate(currentJar, pendingJar, targetJar, metadataPath);
    }

    private static PendingUpdate findPendingUpdate(Logger logger) {
        Path currentJar = getCurrentJarPath();
        Path jarDirectory = currentJar.getParent();
        List<Path> pendingJars = new ArrayList<>();

        try (var stream = Files.list(jarDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(ASSET_PREFIX))
                    .filter(path -> path.getFileName().toString().endsWith(".jar.pending"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(pendingJars::add);
        } catch (IOException e) {
            logger.error("Failed to scan for pending Justys' Essentials updates in {}.", jarDirectory, e);
            return null;
        }

        if (pendingJars.isEmpty()) {
            return null;
        }
        if (pendingJars.size() > 1) {
            logger.warn("Multiple pending Justys' Essentials updates were found. Using the first one: {}", pendingJars.get(0));
        }

        Path pendingJar = pendingJars.get(0);
        String targetFileName = pendingJar.getFileName().toString().replaceFirst("\\.pending$", "");
        Path targetJar = pendingJar.resolveSibling(targetFileName);
        Path metadataPath = pendingJar.resolveSibling(pendingJar.getFileName().toString() + ".json");
        return new PendingUpdate(currentJar, pendingJar, targetJar, metadataPath);
    }

    private static boolean schedulePendingReplacement(PendingUpdate pending, Logger logger) {
        if (!Files.exists(pending.pendingJar())) {
            return false;
        }
        if (!AUTO_APPLY_SCHEDULED.compareAndSet(false, true)) {
            return true;
        }

        try {
            ProcessBuilder builder = isWindows()
                    ? new ProcessBuilder(buildWindowsCommand(pending))
                    : new ProcessBuilder(buildPosixCommand(pending));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.start();
            return true;
        } catch (IOException e) {
            AUTO_APPLY_SCHEDULED.set(false);
            logger.error("Failed to start the Justys' Essentials automatic replacement helper.", e);
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static List<String> buildPosixCommand(PendingUpdate pending) {
        long currentPid = ProcessHandle.current().pid();
        String script = "while kill -0 \"$1\" 2>/dev/null; do sleep 1; done; " +
                "rm -f \"$3\" \"$2\"; " +
                "mv \"$4\" \"$3\" && " +
                "rm -f \"$5\"";

        return List.of(
                "sh",
                "-c",
                script,
                "justys-updater",
                Long.toString(currentPid),
                pending.currentJar().toString(),
                pending.targetJar().toString(),
                pending.pendingJar().toString(),
                pending.metadataPath().toString()
        );
    }

    private static List<String> buildWindowsCommand(PendingUpdate pending) {
        long currentPid = ProcessHandle.current().pid();
        String command =
                "$pidToWait=" + currentPid + ";" +
                "$current='" + escapePowerShell(pending.currentJar().toString()) + "';" +
                "$target='" + escapePowerShell(pending.targetJar().toString()) + "';" +
                "$pending='" + escapePowerShell(pending.pendingJar().toString()) + "';" +
                "$meta='" + escapePowerShell(pending.metadataPath().toString()) + "';" +
                "while (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 1 };" +
                "Remove-Item $target,$current -Force -ErrorAction SilentlyContinue;" +
                "Move-Item $pending $target -Force;" +
                "Remove-Item $pending,$meta -Force -ErrorAction SilentlyContinue;";

        return List.of(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command
        );
    }

    private static String escapePowerShell(String value) {
        return value.replace("'", "''");
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < length; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "0";
            String rightPart = i < rightParts.length ? rightParts[i] : "0";
            int result = compareVersionPart(leftPart, rightPart);
            if (result != 0) {
                return result;
            }
        }

        return 0;
    }

    private static int compareVersionPart(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);

        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }

        if (leftNumeric) {
            return 1;
        }
        if (rightNumeric) {
            return -1;
        }

        return left.compareToIgnoreCase(right);
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private record ReleaseInfo(String version, String assetName, String downloadUrl) {
    }

    private record PendingUpdate(Path currentJar, Path pendingJar, Path targetJar, Path metadataPath) {
    }
}
