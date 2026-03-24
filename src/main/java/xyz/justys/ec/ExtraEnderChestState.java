package xyz.justys.ec;

import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExtraEnderChestState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("enderchest-command");
    private static final String STATE_KEY = "enderchest_command_extra";
    private static final int MIN_SIZE = ModConfig.MIN_ROWS * 9;
    private static final int MAX_SIZE = ModConfig.MAX_ROWS * 9;
    private final Map<UUID, DefaultedList<ItemStack>> data = new HashMap<>();

    public DefaultedList<ItemStack> getOrCreate(UUID uuid, int size) {
        DefaultedList<ItemStack> list = data.get(uuid);
        int normalizedSize = normalizeSize(size);
        if (list == null) {
            list = DefaultedList.ofSize(normalizedSize, ItemStack.EMPTY);
            data.put(uuid, list);
            markDirty();
            return list;
        }

        if (list.size() < normalizedSize) {
            DefaultedList<ItemStack> resized = DefaultedList.ofSize(normalizedSize, ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                resized.set(i, list.get(i));
            }
            list = resized;
            data.put(uuid, list);
            markDirty();
        } else if (list.size() > normalizedSize && !hasItemsBeyond(uuid, normalizedSize)) {
            DefaultedList<ItemStack> resized = DefaultedList.ofSize(normalizedSize, ItemStack.EMPTY);
            for (int i = 0; i < normalizedSize; i++) {
                resized.set(i, list.get(i));
            }
            list = resized;
            data.put(uuid, list);
            markDirty();
        }
        return list;
    }

    public void set(UUID uuid, DefaultedList<ItemStack> list) {
        data.put(uuid, list);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList players = new NbtList();
        for (Map.Entry<UUID, DefaultedList<ItemStack>> entry : data.entrySet()) {
            NbtCompound playerTag = new NbtCompound();
            playerTag.putUuid("uuid", entry.getKey());
            playerTag.putInt("size", entry.getValue().size());
            Inventories.writeNbt(playerTag, entry.getValue(), registryLookup);
            players.add(playerTag);
        }
        nbt.put("players", players);
        return nbt;
    }

    public int getStoredSize(UUID uuid) {
        DefaultedList<ItemStack> list = data.get(uuid);
        return list == null ? MIN_SIZE : list.size();
    }

    public boolean hasItemsBeyond(UUID uuid, int size) {
        DefaultedList<ItemStack> list = data.get(uuid);
        if (list == null) {
            return false;
        }

        int normalizedSize = normalizeSize(size);
        for (int i = normalizedSize; i < list.size(); i++) {
            if (!list.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int normalizeSize(int size) {
        return Math.max(MIN_SIZE, size);
    }

    public static ExtraEnderChestState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ExtraEnderChestState state = new ExtraEnderChestState();
        NbtList players = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound playerTag = players.getCompound(i);
            UUID uuid = playerTag.getUuid("uuid");
            int size = playerTag.contains("size") ? playerTag.getInt("size") : 27;
            size = normalizeSize(size);
            if (size > MAX_SIZE) {
                LOGGER.warn("Stored ender chest data for player {} has {} slots, but only {} rows ({} slots) are supported. Extra slots will remain saved but are inaccessible in-game.",
                        uuid, size, ModConfig.MAX_ROWS, MAX_SIZE);
            }
            DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
            Inventories.readNbt(playerTag, list, registryLookup);
            state.data.put(uuid, list);
        }
        return state;
    }

    public static ExtraEnderChestState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(
                        ExtraEnderChestState::new,
                        ExtraEnderChestState::fromNbt,
                        null
                ),
                STATE_KEY
        );
    }
}
