package xyz.justys.ec;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class EnderChestCommandMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("justys-essentials");
    private final AtomicReference<ModConfig> configRef = new AtomicReference<>();
    private final HomeStorage homeStorage = new HomeStorage(LOGGER);
    private final TpaManager tpaManager = new TpaManager();
    private final SuggestionProvider<ServerCommandSource> homeSuggestionProvider = (context, builder) -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            for (HomeStorage.HomeEntry home : homeStorage.getHomes(player.getUuid())) {
                builder.suggest(home.name());
            }
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        configRef.set(ModConfig.load());
        ModUpdater.preparePendingUpdate(LOGGER);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Active beacon range bonus: {} chunks ({} blocks).",
                    BeaconRangeState.get(server).getBonusChunks(), BeaconRangeState.get(server).getBonusChunks() * 16);
            ModUpdater.runStartupUpdateCheck(LOGGER);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("ec")
                        .requires(source -> hasPermission(source, configRef.get().getEcPermission(), true))
                        .executes(context -> openEnderChest(context.getSource().getPlayer()))
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("justys")
                        .then(literal("reload")
                                .requires(source -> hasPermission(source, configRef.get().getReloadPermission(), 4))
                                .executes(context -> reloadConfig(context.getSource())))
                        .then(literal("update")
                                .requires(source -> hasPermission(source, configRef.get().getUpdatePermission(), 4))
                                .executes(context -> updateMod(context.getSource())))
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isCraftEnabled()) {
                dispatcher.register(CommandManager.literal("craft")
                        .requires(source -> hasPermission(source, config.getCraftPermission(), true))
                        .executes(context -> openCraftingTable(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isAnvilEnabled()) {
                dispatcher.register(CommandManager.literal("anvil")
                        .requires(source -> hasPermission(source, config.getAnvilPermission(), true))
                        .executes(context -> openAnvil(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isTrashEnabled()) {
                dispatcher.register(CommandManager.literal("trash")
                        .requires(source -> hasPermission(source, config.getTrashPermission(), true))
                        .executes(context -> openTrash(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isStonecutterEnabled()) {
                dispatcher.register(CommandManager.literal("stonecutter")
                        .requires(source -> hasPermission(source, config.getStonecutterPermission(), true))
                        .executes(context -> openStonecutter(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isLoomEnabled()) {
                dispatcher.register(CommandManager.literal("loom")
                        .requires(source -> hasPermission(source, config.getLoomPermission(), true))
                        .executes(context -> openLoom(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isGrindstoneEnabled()) {
                dispatcher.register(CommandManager.literal("grindstone")
                        .requires(source -> hasPermission(source, config.getGrindstonePermission(), true))
                        .executes(context -> openGrindstone(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isWoodcutterEnabled() && FabricLoader.getInstance().isModLoaded("nemos_woodcutter")) {
                dispatcher.register(CommandManager.literal("woodcutter")
                        .requires(source -> hasPermission(source, config.getWoodcutterPermission(), true))
                        .executes(context -> openWoodcutter(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isHomeCommandsEnabled()) {
                dispatcher.register(CommandManager.literal("sethome")
                        .requires(source -> hasPermission(source, config.getSethomePermission(), true))
                        .executes(context -> setHome(context.getSource().getPlayer(), "home"))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> setHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("home")
                        .requires(source -> hasPermission(source, config.getHomePermission(), true))
                        .executes(context -> goHome(context.getSource().getPlayer()))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(homeSuggestionProvider)
                                .executes(context -> goHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("homes")
                        .requires(source -> hasPermission(source, config.getHomesPermission(), true))
                        .executes(context -> listHomes(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("delhome")
                        .requires(source -> hasPermission(source, config.getDelhomePermission(), true))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(homeSuggestionProvider)
                                .executes(context -> deleteHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("setdefaulthome")
                        .requires(source -> hasPermission(source, config.getSetDefaultHomePermission(), true))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(homeSuggestionProvider)
                                .executes(context -> setDefaultHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isTpaCommandsEnabled()) {
                dispatcher.register(CommandManager.literal("tpa")
                        .requires(source -> hasPermission(source, config.getTpaPermission(), true))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> requestTeleport(
                                        context.getSource().getPlayer(),
                                        EntityArgumentType.getPlayer(context, "player")))));

                dispatcher.register(CommandManager.literal("tpaccept")
                        .requires(source -> hasPermission(source, config.getTpacceptPermission(), true))
                        .executes(context -> acceptTeleport(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("tpdeny")
                        .requires(source -> hasPermission(source, config.getTpdenyPermission(), true))
                        .executes(context -> denyTeleport(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("beaconrange")
                        .requires(source -> hasPermission(source, configRef.get().getBeaconRangePermission(), 4))
                        .executes(context -> getBeaconRange(context.getSource()))
                        .then(literal("get")
                                .executes(context -> getBeaconRange(context.getSource())))
                        .then(literal("reset")
                                .executes(context -> resetBeaconRange(context.getSource())))
                        .then(CommandManager.argument("chunks", IntegerArgumentType.integer(0))
                                .executes(context -> setBeaconRange(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "chunks"))))
        ));
    }

    private LiteralArgumentBuilder<ServerCommandSource> literal(String name) {
        return CommandManager.literal(name);
    }

    private boolean hasPermission(ServerCommandSource source, String node, boolean fallback) {
        return Permissions.check(source, node, fallback);
    }

    private boolean hasPermission(ServerCommandSource source, String node, int fallbackPermissionLevel) {
        return Permissions.check(source, node, fallbackPermissionLevel);
    }

    private int reloadConfig(ServerCommandSource source) {
        ModConfig config = ModConfig.load();
        configRef.set(config);
        homeStorage.reload();
        source.sendFeedback(() -> Text.literal("Justys' Essentials config reloaded. rows=" + config.getRows()
                + ", max_rows=" + ModConfig.MAX_ROWS
                + ", max_beacon_range_bonus_chunks=" + config.getMaxBeaconRangeBonusChunks()
                + ", craft_enabled=" + config.isCraftEnabled()
                + ", anvil_enabled=" + config.isAnvilEnabled()
                + ", trash_enabled=" + config.isTrashEnabled()
                + ", stonecutter_enabled=" + config.isStonecutterEnabled()
                + ", loom_enabled=" + config.isLoomEnabled()
                + ", grindstone_enabled=" + config.isGrindstoneEnabled()
                + ", woodcutter_enabled=" + config.isWoodcutterEnabled()
                + ", home_commands_enabled=" + config.isHomeCommandsEnabled()
                + ", tpa_commands_enabled=" + config.isTpaCommandsEnabled()
                + " (restart required for command registration changes)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private int updateMod(ServerCommandSource source) {
        try {
            String result = ModUpdater.checkAndStageUpdate(LOGGER);
            source.sendFeedback(() -> Text.literal(result), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException e) {
            source.sendError(Text.literal(e.getMessage()));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Failed to check or stage a Justys' Essentials update.", e);
            source.sendError(Text.literal("Failed to check or stage update. Check the server log."));
            return 0;
        }
    }

    private int getBeaconRange(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        BeaconRangeState state = BeaconRangeState.get(server);
        int bonusChunks = state.getBonusChunks();
        source.sendFeedback(() -> Text.literal("Beacon range bonus is " + bonusChunks + " chunks (" + (bonusChunks * 16) + " blocks)."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int setHome(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        try {
            boolean alreadyExists = !homeStorage.addHome(player, name);
            if (alreadyExists) {
                player.sendMessage(Text.literal("Home '" + name + "' already exists."), false);
                return 0;
            }

            player.sendMessage(Text.literal("Set home '" + name + "' at your current location."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to save home '{}' for {}.", name, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to save home. Check the server log."), false);
            return 0;
        }
    }

    private int goHome(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Optional<String> error = homeStorage.teleportToDefaultHome(player);
        if (error.isPresent()) {
            player.sendMessage(Text.literal(error.get()), false);
            return 0;
        }

        player.sendMessage(Text.literal("Teleported to your default home."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int goHome(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        Optional<String> error = homeStorage.teleportToHome(player, name);
        if (error.isPresent()) {
            player.sendMessage(Text.literal(error.get()), false);
            return 0;
        }

        player.sendMessage(Text.literal("Teleported to home '" + name + "'."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int listHomes(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        var homes = homeStorage.getHomes(player.getUuid()).stream()
                .sorted(Comparator.comparing(HomeStorage.HomeEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (homes.isEmpty()) {
            player.sendMessage(Text.literal("You have no homes set."), false);
            return Command.SINGLE_SUCCESS;
        }

        String joined = homes.stream()
                .map(home -> home.name() + " (" + home.world() + " " + home.x() + ", " + home.y() + ", " + home.z() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        player.sendMessage(Text.literal("Homes: " + joined), false);
        return Command.SINGLE_SUCCESS;
    }

    private int deleteHome(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        try {
            boolean deleted = homeStorage.deleteHome(player.getUuid(), name);
            if (!deleted) {
                player.sendMessage(Text.literal("Home '" + name + "' does not exist."), false);
                return 0;
            }

            player.sendMessage(Text.literal("Deleted home '" + name + "'."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to delete home '{}' for {}.", name, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to delete home. Check the server log."), false);
            return 0;
        }
    }

    private int setDefaultHome(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        try {
            boolean set = homeStorage.setDefaultHome(player.getUuid(), name);
            if (!set) {
                player.sendMessage(Text.literal("Home '" + name + "' does not exist."), false);
                return 0;
            }

            player.sendMessage(Text.literal("Default home set to '" + name + "'."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to set default home '{}' for {}.", name, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to set default home. Check the server log."), false);
            return 0;
        }
    }

    private int requestTeleport(ServerPlayerEntity requester, ServerPlayerEntity target) {
        if (requester == null || target == null) {
            return 0;
        }

        TpaManager.TpaResult result = tpaManager.createRequest(requester, target);
        if (!result.isSuccess()) {
            requester.sendMessage(Text.literal(result.errorMessage()), false);
            return 0;
        }

        requester.sendMessage(Text.literal("Sent a teleport request to " + target.getName().getString() + "."), false);
        target.sendMessage(buildTpaRequestMessage(requester), false);
        return Command.SINGLE_SUCCESS;
    }

    private int acceptTeleport(ServerPlayerEntity target) {
        if (target == null) {
            return 0;
        }

        Optional<TpaManager.TpaRequest> request = tpaManager.getRequestForTarget(target.getUuid());
        if (request.isEmpty()) {
            target.sendMessage(Text.literal("You have no pending teleport requests."), false);
            return 0;
        }

        MinecraftServer server = target.getServer();
        if (server == null) {
            return 0;
        }

        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.get().requesterUuid());
        tpaManager.clearForTarget(target.getUuid());
        if (requester == null) {
            target.sendMessage(Text.literal("That player is no longer online."), false);
            return 0;
        }

        requester.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), java.util.Collections.emptySet(), target.getYaw(), target.getPitch(), true);
        requester.sendMessage(Text.literal(target.getName().getString() + " accepted your teleport request."), false);
        target.sendMessage(Text.literal("Accepted teleport request from " + requester.getName().getString() + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int denyTeleport(ServerPlayerEntity target) {
        if (target == null) {
            return 0;
        }

        Optional<TpaManager.TpaRequest> request = tpaManager.getRequestForTarget(target.getUuid());
        if (request.isEmpty()) {
            target.sendMessage(Text.literal("You have no pending teleport requests."), false);
            return 0;
        }

        MinecraftServer server = target.getServer();
        ServerPlayerEntity requester = server == null ? null : server.getPlayerManager().getPlayer(request.get().requesterUuid());
        tpaManager.clearForTarget(target.getUuid());

        if (requester != null) {
            requester.sendMessage(Text.literal(target.getName().getString() + " denied your teleport request."), false);
        }
        target.sendMessage(Text.literal("Denied teleport request from " + request.get().requesterName() + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private Text buildTpaRequestMessage(ServerPlayerEntity requester) {
        MutableText acceptButton = Text.literal("[Accept]")
                .formatted(Formatting.GREEN)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Accept teleport request"))));

        MutableText denyButton = Text.literal("[Deny]")
                .formatted(Formatting.RED)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Deny teleport request"))));

        return Text.literal(requester.getName().getString() + " wants to teleport to you. ")
                .append(acceptButton)
                .append(Text.literal(" "))
                .append(denyButton);
    }

    private int resetBeaconRange(ServerCommandSource source) {
        return setBeaconRange(source, 0);
    }

    private int setBeaconRange(ServerCommandSource source, int requestedChunks) {
        ModConfig config = configRef.get();
        int maxChunks = config.getMaxBeaconRangeBonusChunks();
        if (requestedChunks > maxChunks) {
            source.sendError(Text.literal("Beacon range bonus cannot exceed " + maxChunks + " chunks."));
            return 0;
        }

        BeaconRangeState state = BeaconRangeState.get(source.getServer());
        state.setBonusChunks(requestedChunks);
        LOGGER.info("Beacon range bonus set to {} chunks ({} blocks) by {}.", requestedChunks, requestedChunks * 16, source.getName());
        source.sendFeedback(() -> Text.literal("Beacon range bonus set to " + requestedChunks + " chunks (" + (requestedChunks * 16) + " blocks)."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int openEnderChest(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        ModConfig config = configRef.get();
        int rows = config.getRows();

        EnderChestInventory ender = player.getEnderChestInventory();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }

        ExtraEnderChestState state = ExtraEnderChestState.get(server);
        int storedSize = state.getStoredSize(player.getUuid());
        int storedRows = Math.min(ModConfig.MAX_ROWS, Math.max(ModConfig.MIN_ROWS, storedSize / 9));
        if (storedSize > ModConfig.MAX_ROWS * 9) {
            LOGGER.warn("Player {} has {} saved ender chest slots, but the command mod only supports up to {} rows ({} slots). Extra slots are inaccessible until data is cleaned up.",
                    player.getGameProfile().getName(), storedSize, ModConfig.MAX_ROWS, ModConfig.MAX_ROWS * 9);
        }
        if (state.hasItemsBeyond(player.getUuid(), rows * 9)) {
            rows = storedRows;
        }

        DefaultedList<ItemStack> saved = state.getOrCreate(player.getUuid(), rows * 9);
        for (int i = 0; i < Math.min(27, saved.size()); i++) {
            saved.set(i, ender.getStack(i).copy());
        }

        SimpleInventory inv = new SimpleInventory(saved.toArray(new ItemStack[0])) {
            @Override
            public void markDirty() {
                super.markDirty();
                state.set(player.getUuid(), toList());
            }

            @Override
            public void onClose(PlayerEntity playerEntity) {
                super.onClose(playerEntity);
                ender.onClose(playerEntity);
                for (int i = 0; i < Math.min(27, this.size()); i++) {
                    ender.setStack(i, this.getStack(i).copy());
                }
                state.set(player.getUuid(), toList());
            }

            private DefaultedList<ItemStack> toList() {
                DefaultedList<ItemStack> list = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
                for (int i = 0; i < this.size(); i++) {
                    list.set(i, this.getStack(i));
                }
                return list;
            }
        };

        int finalRows = rows;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> {
                    ender.onOpen(player);
                    return createGenericHandler(syncId, playerInv, inv, finalRows);
                },
                Text.translatable("container.enderchest")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openCraftingTable(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new CraftingScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.crafting")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openAnvil(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.repair")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openTrash(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Inventory trashInventory = new SimpleInventory(27) {
            @Override
            public void onClose(PlayerEntity player) {
                clear();
                super.onClose(player);
            }
        };

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createGenericHandler(syncId, playerInv, trashInventory, 3),
                Text.literal("Trash")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openStonecutter(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new StonecutterScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.stonecutter")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openLoom(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new LoomScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.loom")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openGrindstone(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new GrindstoneScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.grindstone_title")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openWoodcutter(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        try {
            Class<?> menuClass = Class.forName("com.nemonotfound.nemos.woodcutter.screen.WoodcutterMenu");
            Constructor<?> constructor = menuClass.getConstructor(int.class, net.minecraft.entity.player.PlayerInventory.class);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> {
                        try {
                            return (net.minecraft.screen.ScreenHandler) constructor.newInstance(syncId, playerInv);
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException("Failed to create Nemo's Woodcutter menu", e);
                        }
                    },
                    Text.literal("Woodcutter")
            ));
            return Command.SINGLE_SUCCESS;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to open /woodcutter even though Nemo's Woodcutter is loaded.", e);
            player.sendMessage(Text.literal("Woodcutter command failed to open. Check the server log."), false);
            return 0;
        }
    }

    private GenericContainerScreenHandler createGenericHandler(int syncId,
                                                               net.minecraft.entity.player.PlayerInventory playerInv,
                                                               Inventory inv,
                                                               int rows) {
        ScreenHandlerType<?> type = switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X3;
        };
        return new GenericContainerScreenHandler(type, syncId, playerInv, inv, rows);
    }
}
