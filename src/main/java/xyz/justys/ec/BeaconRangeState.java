package xyz.justys.ec;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public class BeaconRangeState extends PersistentState {
    private static final String STATE_KEY = "enderchest_command_beacon_range";
    private int bonusChunks;

    public int getBonusChunks() {
        return bonusChunks;
    }

    public void setBonusChunks(int bonusChunks) {
        this.bonusChunks = Math.max(0, bonusChunks);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putInt("bonus_chunks", bonusChunks);
        return nbt;
    }

    public static BeaconRangeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BeaconRangeState state = new BeaconRangeState();
        state.bonusChunks = Math.max(0, nbt.getInt("bonus_chunks"));
        return state;
    }

    public static BeaconRangeState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(
                        BeaconRangeState::new,
                        BeaconRangeState::fromNbt,
                        null
                ),
                STATE_KEY
        );
    }
}
