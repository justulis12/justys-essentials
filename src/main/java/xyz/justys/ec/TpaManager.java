package xyz.justys.ec;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TpaManager {
    private static final long REQUEST_TIMEOUT_MS = 60_000L;

    private final Map<UUID, TpaRequest> incomingByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByRequester = new HashMap<>();

    public synchronized TpaResult createRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
        cleanupExpired();

        if (requester.getUuid().equals(target.getUuid())) {
            return TpaResult.error("You cannot send a teleport request to yourself.");
        }
        if (targetByRequester.containsKey(requester.getUuid())) {
            return TpaResult.error("You already have a pending teleport request.");
        }
        if (incomingByTarget.containsKey(target.getUuid())) {
            return TpaResult.error(target.getName().getString() + " already has a pending teleport request.");
        }

        TpaRequest request = new TpaRequest(requester.getUuid(), requester.getName().getString(), target.getUuid(), System.currentTimeMillis());
        incomingByTarget.put(target.getUuid(), request);
        targetByRequester.put(requester.getUuid(), target.getUuid());
        return TpaResult.success(request);
    }

    public synchronized Optional<TpaRequest> getRequestForTarget(UUID targetUuid) {
        cleanupExpired();
        return Optional.ofNullable(incomingByTarget.get(targetUuid));
    }

    public synchronized void clearForTarget(UUID targetUuid) {
        TpaRequest removed = incomingByTarget.remove(targetUuid);
        if (removed != null) {
            targetByRequester.remove(removed.requesterUuid());
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        incomingByTarget.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue().createdAtMs() > REQUEST_TIMEOUT_MS;
            if (expired) {
                targetByRequester.remove(entry.getValue().requesterUuid());
            }
            return expired;
        });
    }

    public record TpaRequest(UUID requesterUuid, String requesterName, UUID targetUuid, long createdAtMs) {
    }

    public record TpaResult(TpaRequest request, String errorMessage) {
        public static TpaResult success(TpaRequest request) {
            return new TpaResult(request, null);
        }

        public static TpaResult error(String message) {
            return new TpaResult(null, message);
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }
    }
}
