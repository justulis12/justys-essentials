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

public final class MailStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = FabricLoader.getInstance().getGameDir().resolve("justysessentials");
    private static final Path STORAGE_FILE = STORAGE_DIR.resolve("mail.json");

    private final Logger logger;
    private MailData data;

    public MailStorage(Logger logger) {
        this.logger = logger;
        this.data = loadOrCreate();
    }

    public synchronized void reload() {
        this.data = loadOrCreate();
    }

    public synchronized Path getStorageFile() {
        return STORAGE_FILE;
    }

    public synchronized int getUnreadCount(UUID recipientUuid) {
        return (int) getInbox(recipientUuid).stream().filter(message -> !message.read()).count();
    }

    public synchronized List<MailMessage> getInbox(UUID recipientUuid) {
        String recipient = recipientUuid.toString();
        return data.messages.stream()
                .filter(message -> message.recipientUuid.equals(recipient))
                .sorted(Comparator.comparingLong((StoredMessage message) -> message.sentAtMs).reversed())
                .map(StoredMessage::toRecord)
                .toList();
    }

    public synchronized MailMessage send(ServerPlayerEntity sender, ServerPlayerEntity recipient, String content) throws IOException {
        StoredMessage message = new StoredMessage();
        message.id = ++data.nextId;
        message.recipientUuid = recipient.getUuid().toString();
        message.recipientName = recipient.getName().getString();
        message.senderUuid = sender.getUuid().toString();
        message.senderName = sender.getName().getString();
        message.message = content;
        message.sentAtMs = System.currentTimeMillis();
        data.messages.add(message);
        save();
        return message.toRecord();
    }

    public synchronized Optional<MailMessage> getMessage(UUID recipientUuid, long id) throws IOException {
        StoredMessage message = findMessage(recipientUuid, id);
        if (message == null) {
            return Optional.empty();
        }
        if (!message.read) {
            message.read = true;
            save();
        }
        return Optional.of(message.toRecord());
    }

    public synchronized boolean delete(UUID recipientUuid, long id) throws IOException {
        StoredMessage message = findMessage(recipientUuid, id);
        if (message == null) {
            return false;
        }
        data.messages.remove(message);
        save();
        return true;
    }

    public synchronized int clear(UUID recipientUuid) throws IOException {
        String recipient = recipientUuid.toString();
        int before = data.messages.size();
        data.messages.removeIf(message -> message.recipientUuid.equals(recipient));
        int removed = before - data.messages.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    private StoredMessage findMessage(UUID recipientUuid, long id) {
        String recipient = recipientUuid.toString();
        return data.messages.stream()
                .filter(message -> message.recipientUuid.equals(recipient) && message.id == id)
                .findFirst()
                .orElse(null);
    }

    private MailData loadOrCreate() {
        try {
            Files.createDirectories(STORAGE_DIR);
            if (!Files.exists(STORAGE_FILE)) {
                MailData fresh = new MailData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            String raw = Files.readString(STORAGE_FILE);
            if (raw.isBlank()) {
                MailData fresh = new MailData();
                Files.writeString(STORAGE_FILE, GSON.toJson(fresh));
                return fresh;
            }

            MailData loaded = GSON.fromJson(raw, MailData.class);
            if (loaded == null) {
                return new MailData();
            }
            if (loaded.messages == null) {
                loaded.messages = new ArrayList<>();
            }
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load Justys' Essentials mail storage from {}. Using empty data.", STORAGE_FILE, e);
            return new MailData();
        }
    }

    private void save() throws IOException {
        Files.createDirectories(STORAGE_DIR);
        Files.writeString(STORAGE_FILE, GSON.toJson(data));
    }

    private static final class MailData {
        private long nextId = 0L;
        private List<StoredMessage> messages = new ArrayList<>();
    }

    private static final class StoredMessage {
        private long id;
        private String recipientUuid = "";
        private String recipientName = "";
        private String senderUuid = "";
        private String senderName = "";
        private String message = "";
        private long sentAtMs = 0L;
        private boolean read = false;

        private MailMessage toRecord() {
            return new MailMessage(id, recipientUuid, recipientName, senderUuid, senderName, message, sentAtMs, read);
        }
    }

    public record MailMessage(long id, String recipientUuid, String recipientName, String senderUuid,
                              String senderName, String message, long sentAtMs, boolean read) {
    }
}
