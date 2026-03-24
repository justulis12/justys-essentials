package xyz.justys.ec;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.justys.ec.mixin.TradeOfferAccessor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class EnderChestCommandMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("justys-essentials");
    private final AtomicReference<ModConfig> configRef = new AtomicReference<>();
    private final HomeStorage homeStorage = new HomeStorage(LOGGER);
    private final PlayerDataStorage playerDataStorage = new PlayerDataStorage(LOGGER);
    private final SpawnStorage spawnStorage = new SpawnStorage(LOGGER);
    private final MailStorage mailStorage = new MailStorage(LOGGER);
    private final TpaManager tpaManager = new TpaManager();
    private final Map<UUID, BackLocation> backLocations = new HashMap<>();
    private final Map<UUID, List<ItemStack>> trashUndo = new HashMap<>();
    private final Set<UUID> afkPlayers = new HashSet<>();
    private final Map<UUID, Integer> playtimeTickCounter = new HashMap<>();
    private final Map<UUID, Integer> afkIdleTickCounter = new HashMap<>();
    private final Map<UUID, BlockPos> afkLastPositions = new HashMap<>();
    private final SuggestionProvider<ServerCommandSource> homeSuggestionProvider = (context, builder) -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            for (HomeStorage.HomeEntry home : homeStorage.getHomes(player.getUuid())) {
                builder.suggest(home.name());
            }
        }
        return builder.buildFuture();
    };
    private final SuggestionProvider<ServerCommandSource> warpSuggestionProvider = (context, builder) -> {
        for (HomeStorage.WarpEntry warp : homeStorage.getWarps()) {
            builder.suggest(warp.name());
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        configRef.set(ModConfig.load());
        ModUpdater.preparePendingUpdate(LOGGER);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            applyWorldConfig(server);
            LOGGER.info("Active beacon range bonus: {} chunks ({} blocks).",
                    BeaconRangeState.get(server).getBonusChunks(), BeaconRangeState.get(server).getBonusChunks() * 16);
            ModUpdater.runStartupUpdateCheck(LOGGER);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> playerDataStorage.flush());
        ServerTickEvents.END_SERVER_TICK.register(server -> tickOnlinePlayers(server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            playerDataStorage.recordPlayerName(player);
            playtimeTickCounter.putIfAbsent(player.getUuid(), 0);
            afkIdleTickCounter.put(player.getUuid(), 0);
            afkLastPositions.put(player.getUuid(), player.getBlockPos());
            int unreadMail = mailStorage.getUnreadCount(player.getUuid());
            if (unreadMail > 0) {
                player.sendMessage(Text.literal("You have " + unreadMail + " unread mail message" + (unreadMail == 1 ? "" : "s") + ". Use /mail to view them.").formatted(Formatting.GOLD), false);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            playerDataStorage.recordPlayerName(player);
            playtimeTickCounter.remove(player.getUuid());
            afkIdleTickCounter.remove(player.getUuid());
            afkLastPositions.remove(player.getUuid());
            afkPlayers.remove(player.getUuid());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, net.minecraft.entity.damage.DamageSource damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
                playerDataStorage.recordDeath(player);
            }
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != net.minecraft.util.Hand.MAIN_HAND) {
                return net.minecraft.util.ActionResult.PASS;
            }
            if (!configRef.get().isVillagerInfiniteTrading()) {
                return net.minecraft.util.ActionResult.PASS;
            }
            if (!(entity instanceof MerchantEntity merchant) || !supportsInfiniteTrading(merchant)) {
                return net.minecraft.util.ActionResult.PASS;
            }

            merchant.getOffers().forEach(this::resetMerchantOffer);
            return net.minecraft.util.ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isEcEnabled()) {
                dispatcher.register(CommandManager.literal("ec")
                        .requires(source -> hasPermission(source, config.getEcPermission(), true))
                        .executes(context -> openEnderChest(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("justys")
                        .executes(context -> showJustysHelp(context.getSource()))
                        .then(literal("help")
                                .executes(context -> showJustysHelp(context.getSource())))
                .then(literal("reload")
                                .requires(source -> hasPermission(source, configRef.get().getReloadPermission(), 4))
                                .executes(context -> reloadConfig(context.getSource())))
                        .then(literal("update")
                                .requires(source -> hasPermission(source, configRef.get().getUpdatePermission(), 4))
                                .executes(context -> updateMod(context.getSource())))
                        .then(literal("doctor")
                                .requires(source -> configRef.get().isExtraEnabled("doctor")
                                        && hasPermission(source, configRef.get().getExtraPermission("doctor"), 4))
                                .executes(context -> runDoctor(context.getSource())))
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
                        .executes(context -> openTrash(context.getSource().getPlayer()))
                        .then(literal("undo")
                                .executes(context -> undoTrash(context.getSource().getPlayer()))));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isCartographyEnabled()) {
                dispatcher.register(CommandManager.literal("cartography")
                        .requires(source -> hasPermission(source, config.getCartographyPermission(), true))
                        .executes(context -> openCartography(context.getSource().getPlayer())));
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
            if (config.isSmithingEnabled()) {
                dispatcher.register(CommandManager.literal("smithing")
                        .requires(source -> hasPermission(source, config.getSmithingPermission(), true))
                        .executes(context -> openSmithing(context.getSource().getPlayer())));
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

                dispatcher.register(CommandManager.literal("homeset")
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
                        .executes(context -> openHomesMenu(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("delhome")
                        .requires(source -> hasPermission(source, config.getDelhomePermission(), true))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(homeSuggestionProvider)
                                .executes(context -> deleteHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("homedel")
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

                dispatcher.register(CommandManager.literal("homedefault")
                        .requires(source -> hasPermission(source, config.getSetDefaultHomePermission(), true))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(homeSuggestionProvider)
                                .executes(context -> setDefaultHome(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("back")
                        .requires(source -> hasPermission(source, config.getBackPermission(), true))
                        .executes(context -> goBack(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("warp")
                        .requires(source -> hasPermission(source, config.getWarpPermission(), true))
                        .executes(context -> openWarpsMenu(context.getSource().getPlayer()))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(warpSuggestionProvider)
                                .executes(context -> goWarp(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("warps")
                        .requires(source -> hasPermission(source, config.getWarpPermission(), true))
                        .executes(context -> openWarpsMenu(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("setwarp")
                        .requires(source -> hasPermission(source, config.getSetwarpPermission(), 4))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> setWarp(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name")))));

                dispatcher.register(CommandManager.literal("delwarp")
                        .requires(source -> hasPermission(source, config.getDelwarpPermission(), 4))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(warpSuggestionProvider)
                                .executes(context -> deleteWarp(StringArgumentType.getString(context, "name"), context.getSource()))));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();
            if (config.isTpaCommandsEnabled()) {
                dispatcher.register(CommandManager.literal("tpa")
                        .requires(source -> hasPermission(source, config.getTpaPermission(), true))
                        .executes(context -> openTpaMenu(context.getSource().getPlayer()))
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

                dispatcher.register(CommandManager.literal("tpatoggle")
                        .requires(source -> hasPermission(source, config.getTpatogglePermission(), true))
                        .executes(context -> toggleTpa(context.getSource().getPlayer())));

                dispatcher.register(CommandManager.literal("tpacancel")
                        .requires(source -> hasPermission(source, config.getTpacancelPermission(), true))
                        .executes(context -> cancelTpa(context.getSource().getPlayer())));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("enderpeek")
                    .requires(source -> hasPermission(source, configRef.get().getEnderpeekPermission(), 4))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(context -> openEnderPeek(
                                    context.getSource().getPlayer(),
                                    EntityArgumentType.getPlayer(context, "player")))));

            dispatcher.register(CommandManager.literal("invsee")
                    .requires(source -> hasPermission(source, configRef.get().getInvseePermission(), 4))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(context -> openInventoryPeek(
                                    context.getSource().getPlayer(),
                                    EntityArgumentType.getPlayer(context, "player")))));
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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("condense")
                    .requires(source -> hasPermission(source, configRef.get().getCondensePermission(), true))
                    .executes(context -> condenseInventory(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("afk")
                    .requires(source -> hasPermission(source, configRef.get().getAfkPermission(), true))
                    .executes(context -> toggleAfk(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("playtime")
                    .requires(source -> hasPermission(source, configRef.get().getPlaytimePermission(), true))
                    .executes(context -> showPlaytime(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("playtimetop")
                    .requires(source -> hasPermission(source, configRef.get().getPlaytimePermission(), true))
                    .executes(context -> showPlaytimeTop(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("lastdeath")
                    .requires(source -> hasPermission(source, configRef.get().getLastdeathPermission(), true))
                    .executes(context -> showLastDeath(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("deathcompass")
                    .requires(source -> hasPermission(source, configRef.get().getDeathcompassPermission(), true))
                    .executes(context -> giveDeathCompass(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("coinflip")
                    .requires(source -> hasPermission(source, configRef.get().getCoinflipPermission(), true))
                    .executes(context -> coinFlip(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("whereami")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showWhereAmI(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("coords")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showWhereAmI(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("biome")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showBiome(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("light")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showLight(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("moonphase")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showMoonPhase(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("weather")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showWeather(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("time")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showTime(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("day")
                    .requires(source -> hasPermission(source, configRef.get().getDayPermission(), 2))
                    .executes(context -> setWorldTime(context.getSource(), 1000L, "day")));

            dispatcher.register(CommandManager.literal("night")
                    .requires(source -> hasPermission(source, configRef.get().getNightPermission(), 2))
                    .executes(context -> setWorldTime(context.getSource(), 13000L, "night")));

            dispatcher.register(CommandManager.literal("seed")
                    .requires(source -> hasPermission(source, configRef.get().getSeedPermission(), 2))
                    .executes(context -> showSeed(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("slimechunk")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showSlimeChunk(context.getSource().getPlayer())));

            dispatcher.register(CommandManager.literal("chunkinfo")
                    .requires(source -> hasPermission(source, configRef.get().getWorldinfoPermission(), true))
                    .executes(context -> showChunkInfo(context.getSource().getPlayer())));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig config = configRef.get();

            if (config.isExtraEnabled("spawn")) {
                dispatcher.register(CommandManager.literal("spawn")
                        .requires(source -> hasPermission(source, config.getExtraPermission("spawn"), true))
                        .executes(context -> goSpawn(context.getSource().getPlayer())));
            }

            if (config.isExtraEnabled("setspawn")) {
                dispatcher.register(CommandManager.literal("setspawn")
                        .requires(source -> hasPermission(source, config.getExtraPermission("setspawn"), 4))
                        .executes(context -> setSpawn(context.getSource().getPlayer())));
            }

            if (config.isExtraEnabled("rtp")) {
                dispatcher.register(CommandManager.literal("rtp")
                        .requires(source -> hasPermission(source, config.getExtraPermission("rtp"), true))
                        .executes(context -> randomTeleport(context.getSource().getPlayer())));
            }

            if (config.isTpaCommandsEnabled() && config.isExtraEnabled("tpahere")) {
                dispatcher.register(CommandManager.literal("tpahere")
                        .requires(source -> hasPermission(source, config.getExtraPermission("tpahere"), true))
                        .executes(context -> openTpaHereMenu(context.getSource().getPlayer()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> requestTeleportHere(
                                        context.getSource().getPlayer(),
                                        EntityArgumentType.getPlayer(context, "player")))));
            }

            if (config.isExtraEnabled("mail")) {
                dispatcher.register(CommandManager.literal("mail")
                        .requires(source -> hasPermission(source, config.getExtraPermission("mail"), true))
                        .executes(context -> showMailInbox(context.getSource().getPlayer()))
                        .then(literal("inbox")
                                .executes(context -> showMailInbox(context.getSource().getPlayer())))
                        .then(literal("send")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> sendMail(
                                                        context.getSource().getPlayer(),
                                                        EntityArgumentType.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "message"))))))
                        .then(literal("read")
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> readMail(
                                                context.getSource().getPlayer(),
                                                IntegerArgumentType.getInteger(context, "id")))))
                        .then(literal("delete")
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> deleteMail(
                                                context.getSource().getPlayer(),
                                                IntegerArgumentType.getInteger(context, "id")))))
                        .then(literal("clear")
                                .executes(context -> clearMail(context.getSource().getPlayer()))));
            }

            if (config.isExtraEnabled("sort")) {
                dispatcher.register(CommandManager.literal("sort")
                        .requires(source -> hasPermission(source, config.getExtraPermission("sort"), true))
                        .executes(context -> sortCurrentInventory(context.getSource().getPlayer())));
            }

            if (config.isExtraEnabled("seen")) {
                dispatcher.register(CommandManager.literal("seen")
                        .requires(source -> hasPermission(source, config.getExtraPermission("seen"), true))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(context -> showSeen(
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "player")))));
            }

            if (config.isExtraEnabled("packup")) {
                dispatcher.register(CommandManager.literal("packup")
                        .requires(source -> hasPermission(source, config.getExtraPermission("packup"), true))
                        .executes(context -> packUpItems(context.getSource().getPlayer())));
            }

            if (config.isExtraEnabled("top")) {
                dispatcher.register(CommandManager.literal("top")
                        .requires(source -> hasPermission(source, config.getExtraPermission("top"), true))
                        .executes(context -> goTop(context.getSource().getPlayer())));
            }

            if (config.isExtraEnabled("near")) {
                dispatcher.register(CommandManager.literal("near")
                        .requires(source -> hasPermission(source, config.getExtraPermission("near"), true))
                        .executes(context -> showNear(context.getSource().getPlayer())));
            }
        });
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
        playerDataStorage.reload();
        spawnStorage.reload();
        mailStorage.reload();
        applyWorldConfig(source.getServer());
        source.sendFeedback(() -> Text.literal("Justys' Essentials config reloaded. rows=" + config.getRows()
                + ", max_rows=" + ModConfig.MAX_ROWS
                + ", max_beacon_range_bonus_chunks=" + config.getMaxBeaconRangeBonusChunks()
                + ", ec_enabled=" + config.isEcEnabled()
                + ", craft_enabled=" + config.isCraftEnabled()
                + ", anvil_enabled=" + config.isAnvilEnabled()
                + ", trash_enabled=" + config.isTrashEnabled()
                + ", cartography_enabled=" + config.isCartographyEnabled()
                + ", smithing_enabled=" + config.isSmithingEnabled()
                + ", stonecutter_enabled=" + config.isStonecutterEnabled()
                + ", loom_enabled=" + config.isLoomEnabled()
                + ", grindstone_enabled=" + config.isGrindstoneEnabled()
                + ", woodcutter_enabled=" + config.isWoodcutterEnabled()
                + ", home_commands_enabled=" + config.isHomeCommandsEnabled()
                + ", tpa_commands_enabled=" + config.isTpaCommandsEnabled()
                + ", tree_feller_enabled=" + config.isTreeFellerEnabled()
                + ", vein_miner_enabled=" + config.isVeinMinerEnabled()
                + ", villager_infinite_trading=" + config.isVillagerInfiniteTrading()
                + ", tree_feller_require_sneak=" + config.isTreeFellerRequireSneak()
                + ", vein_miner_require_sneak=" + config.isVeinMinerRequireSneak()
                + ", max_homes_per_player=" + config.getMaxHomesPerPlayer()
                + ", max_tree_blocks=" + config.getMaxTreeBlocks()
                + ", max_vein_blocks=" + config.getMaxVeinBlocks()
                + ", anti_enderman_grief=" + config.isAntiEndermanGrief()
                + ", anti_creeper_grief=" + config.isAntiCreeperGrief()
                + ", disable_phantoms=" + config.isDisablePhantoms()
                + ", no_fire_spread=" + config.isNoFireSpread()
                + " (restart required for command registration changes)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private boolean supportsInfiniteTrading(MerchantEntity merchant) {
        return merchant instanceof VillagerEntity || merchant instanceof WanderingTraderEntity;
    }

    private void resetMerchantOffer(net.minecraft.village.TradeOffer offer) {
        TradeOfferAccessor accessor = (TradeOfferAccessor) offer;
        accessor.justys$setUses(0);
        accessor.justys$setMaxUses(Integer.MAX_VALUE);
        accessor.justys$setDemandBonus(0);
    }

    private int showJustysHelp(ServerCommandSource source) {
        ModConfig config = configRef.get();
        source.sendFeedback(() -> Text.literal("Justys' Essentials commands:"), false);

        if (config.isEcEnabled()) {
            source.sendFeedback(() -> Text.literal("/ec"), false);
        }
        source.sendFeedback(() -> Text.literal("/justys help"), false);
        source.sendFeedback(() -> Text.literal("/justys reload"), false);
        source.sendFeedback(() -> Text.literal("/justys update"), false);

        if (config.isCraftEnabled()) {
            source.sendFeedback(() -> Text.literal("/craft"), false);
        }
        if (config.isAnvilEnabled()) {
            source.sendFeedback(() -> Text.literal("/anvil"), false);
        }
        if (config.isTrashEnabled()) {
            source.sendFeedback(() -> Text.literal("/trash, /trash undo"), false);
        }
        if (config.isCartographyEnabled()) {
            source.sendFeedback(() -> Text.literal("/cartography"), false);
        }
        if (config.isSmithingEnabled()) {
            source.sendFeedback(() -> Text.literal("/smithing"), false);
        }
        if (config.isStonecutterEnabled()) {
            source.sendFeedback(() -> Text.literal("/stonecutter"), false);
        }
        if (config.isLoomEnabled()) {
            source.sendFeedback(() -> Text.literal("/loom"), false);
        }
        if (config.isGrindstoneEnabled()) {
            source.sendFeedback(() -> Text.literal("/grindstone"), false);
        }
        if (config.isWoodcutterEnabled() && FabricLoader.getInstance().isModLoaded("nemos_woodcutter")) {
            source.sendFeedback(() -> Text.literal("/woodcutter"), false);
        }

        source.sendFeedback(() -> Text.literal("/beaconrange [get|reset|<chunks>]"), false);
        source.sendFeedback(() -> Text.literal("/enderpeek <player> (op), /invsee <player> (op)"), false);

        if (config.isHomeCommandsEnabled()) {
            source.sendFeedback(() -> Text.literal("/sethome [name], /homeset [name]"), false);
            source.sendFeedback(() -> Text.literal("/home [name], /homes"), false);
            source.sendFeedback(() -> Text.literal("/delhome <name>, /homedel <name>"), false);
            source.sendFeedback(() -> Text.literal("/setdefaulthome <name>, /homedefault <name>"), false);
            source.sendFeedback(() -> Text.literal("/warp [name], /warps"), false);
            source.sendFeedback(() -> Text.literal("/setwarp <name>, /delwarp <name> (op)"), false);
            source.sendFeedback(() -> Text.literal("/back"), false);
        }
        if (config.isTpaCommandsEnabled()) {
            source.sendFeedback(() -> Text.literal("/tpa [player], /tpaccept, /tpdeny"), false);
            source.sendFeedback(() -> Text.literal("/tpatoggle, /tpacancel"), false);
        }
        source.sendFeedback(() -> Text.literal("/condense, /afk, /playtime, /playtimetop, /lastdeath, /deathcompass, /coinflip"), false);
        source.sendFeedback(() -> Text.literal("/whereami, /coords, /biome, /light, /moonphase, /weather, /time, /slimechunk, /chunkinfo"), false);
        source.sendFeedback(() -> Text.literal("/day, /night, /seed (op)"), false);
        if (config.isExtraEnabled("spawn")) {
            source.sendFeedback(() -> Text.literal("/spawn"), false);
        }
        if (config.isExtraEnabled("setspawn")) {
            source.sendFeedback(() -> Text.literal("/setspawn (op)"), false);
        }
        if (config.isExtraEnabled("rtp")) {
            source.sendFeedback(() -> Text.literal("/rtp"), false);
        }
        if (config.isTpaCommandsEnabled() && config.isExtraEnabled("tpahere")) {
            source.sendFeedback(() -> Text.literal("/tpahere [player]"), false);
        }
        if (config.isExtraEnabled("mail")) {
            source.sendFeedback(() -> Text.literal("/mail, /mail send <player> <message>, /mail read <id>, /mail delete <id>, /mail clear"), false);
        }
        if (config.isExtraEnabled("sort")) {
            source.sendFeedback(() -> Text.literal("/sort"), false);
        }
        if (config.isExtraEnabled("seen")) {
            source.sendFeedback(() -> Text.literal("/seen <player>"), false);
        }
        if (config.isExtraEnabled("doctor")) {
            source.sendFeedback(() -> Text.literal("/justys doctor (op)"), false);
        }
        if (config.isExtraEnabled("packup")) {
            source.sendFeedback(() -> Text.literal("/packup"), false);
        }
        if (config.isExtraEnabled("top")) {
            source.sendFeedback(() -> Text.literal("/top"), false);
        }
        if (config.isExtraEnabled("near")) {
            source.sendFeedback(() -> Text.literal("/near"), false);
        }

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
            ModConfig config = configRef.get();
            int homeLimit = config.getMaxHomesPerPlayer();
            if (homeLimit >= 0 && !player.hasPermissionLevel(4) && homeStorage.getHomes(player.getUuid()).size() >= homeLimit) {
                player.sendMessage(Text.literal("You have reached the home limit of " + homeLimit + "."), false);
                return 0;
            }

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

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
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

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
        Optional<String> error = homeStorage.teleportToHome(player, name);
        if (error.isPresent()) {
            player.sendMessage(Text.literal(error.get()), false);
            return 0;
        }

        player.sendMessage(Text.literal("Teleported to home '" + name + "'."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int openHomesMenu(ServerPlayerEntity player) {
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

        int rows = Math.min(6, Math.max(1, (homes.size() + 8) / 9));
        SimpleInventory menu = new SimpleInventory(rows * 9);
        Map<Integer, Runnable> actions = new HashMap<>();

        for (int i = 0; i < Math.min(menu.size(), homes.size()); i++) {
            HomeStorage.HomeEntry home = homes.get(i);
            ItemStack icon = new ItemStack(isDefaultHome(player, home) ? Items.RECOVERY_COMPASS : Items.RED_BED);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(home.name()).formatted(Formatting.AQUA));
            menu.setStack(i, icon);
            actions.put(i, () -> goHome(player, home.name()));
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createActionMenuHandler(syncId, playerInv, menu, rows, actions),
                Text.literal("Homes")
        ));
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

    private boolean isDefaultHome(ServerPlayerEntity player, HomeStorage.HomeEntry home) {
        return homeStorage.getDefaultHome(player.getUuid())
                .map(defaultHome -> defaultHome.name().equalsIgnoreCase(home.name()))
                .orElse(false);
    }

    private int setWarp(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        try {
            if (!homeStorage.addWarp(player, name)) {
                player.sendMessage(Text.literal("Warp '" + name + "' already exists."), false);
                return 0;
            }
            player.sendMessage(Text.literal("Set warp '" + name + "' at your current location."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to save warp '{}' for {}.", name, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to save warp. Check the server log."), false);
            return 0;
        }
    }

    private int deleteWarp(String name, ServerCommandSource source) {
        try {
            if (!homeStorage.deleteWarp(name)) {
                source.sendError(Text.literal("Warp '" + name + "' does not exist."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Deleted warp '" + name + "'."), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to delete warp '{}'.", name, e);
            source.sendError(Text.literal("Failed to delete warp. Check the server log."));
            return 0;
        }
    }

    private int goWarp(ServerPlayerEntity player, String name) {
        if (player == null) {
            return 0;
        }

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
        Optional<String> error = homeStorage.teleportToWarp(player, name);
        if (error.isPresent()) {
            player.sendMessage(Text.literal(error.get()), false);
            return 0;
        }

        player.sendMessage(Text.literal("Teleported to warp '" + name + "'."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int openWarpsMenu(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<HomeStorage.WarpEntry> warps = homeStorage.getWarps().stream()
                .sorted(Comparator.comparing(HomeStorage.WarpEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (warps.isEmpty()) {
            player.sendMessage(Text.literal("There are no warps set."), false);
            return Command.SINGLE_SUCCESS;
        }

        int rows = Math.min(6, Math.max(1, (warps.size() + 8) / 9));
        SimpleInventory menu = new SimpleInventory(rows * 9);
        Map<Integer, Runnable> actions = new HashMap<>();

        for (int i = 0; i < Math.min(menu.size(), warps.size()); i++) {
            HomeStorage.WarpEntry warp = warps.get(i);
            ItemStack icon = new ItemStack(Items.ENDER_PEARL);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(warp.name()).formatted(Formatting.LIGHT_PURPLE));
            menu.setStack(i, icon);
            actions.put(i, () -> goWarp(player, warp.name()));
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createActionMenuHandler(syncId, playerInv, menu, rows, actions),
                Text.literal("Warps")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int requestTeleport(ServerPlayerEntity requester, ServerPlayerEntity target) {
        return requestTeleport(requester, target, TpaManager.RequestType.TO_TARGET);
    }

    private int requestTeleportHere(ServerPlayerEntity requester, ServerPlayerEntity target) {
        return requestTeleport(requester, target, TpaManager.RequestType.HERE);
    }

    private int requestTeleport(ServerPlayerEntity requester, ServerPlayerEntity target, TpaManager.RequestType type) {
        if (requester == null || target == null) {
            return 0;
        }

        TpaManager.TpaResult result = tpaManager.createRequest(requester, target, type);
        if (!result.isSuccess()) {
            requester.sendMessage(Text.literal(result.errorMessage()), false);
            return 0;
        }

        if (type == TpaManager.RequestType.HERE) {
            requester.sendMessage(Text.literal("Sent a teleport-here request to " + target.getName().getString() + "."), false);
        } else {
            requester.sendMessage(Text.literal("Sent a teleport request to " + target.getName().getString() + "."), false);
        }
        target.sendMessage(buildTpaRequestMessage(requester, type), false);
        return Command.SINGLE_SUCCESS;
    }

    private int openTpaMenu(ServerPlayerEntity player) {
        return openTpaMenu(player, TpaManager.RequestType.TO_TARGET, "TPA Menu");
    }

    private int openTpaHereMenu(ServerPlayerEntity player) {
        return openTpaMenu(player, TpaManager.RequestType.HERE, "TPA Here");
    }

    private int openTpaMenu(ServerPlayerEntity player, TpaManager.RequestType type, String title) {
        if (player == null) {
            return 0;
        }

        List<ServerPlayerEntity> targets = player.getServer().getPlayerManager().getPlayerList().stream()
                .filter(target -> !target.getUuid().equals(player.getUuid()))
                .sorted(Comparator.comparing(target -> target.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("No other players are online to request teleportation to."), false);
            return Command.SINGLE_SUCCESS;
        }

        int rows = Math.min(6, Math.max(1, (targets.size() + 8) / 9));
        SimpleInventory menu = new SimpleInventory(rows * 9);
        Map<Integer, Runnable> actions = new HashMap<>();

        for (int i = 0; i < Math.min(menu.size(), targets.size()); i++) {
            ServerPlayerEntity target = targets.get(i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, new ProfileComponent(target.getGameProfile()));
            head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(target.getName().getString()).formatted(Formatting.AQUA));
            menu.setStack(i, head);
            actions.put(i, () -> requestTeleport(player, target, type));
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createActionMenuHandler(syncId, playerInv, menu, rows, actions),
                Text.literal(title)
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleTpa(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        boolean disabled = tpaManager.toggleDisabled(player.getUuid());
        player.sendMessage(Text.literal(disabled ? "Teleport requests disabled." : "Teleport requests enabled."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int cancelTpa(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Optional<TpaManager.TpaRequest> cancelled = tpaManager.cancelForRequester(player.getUuid());
        if (cancelled.isEmpty()) {
            player.sendMessage(Text.literal("You have no pending teleport request to cancel."), false);
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(cancelled.get().targetUuid());
            if (target != null) {
                target.sendMessage(Text.literal(player.getName().getString() + " canceled their teleport request."), false);
            }
        }
        player.sendMessage(Text.literal("Canceled your teleport request."), false);
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

        if (request.get().type() == TpaManager.RequestType.HERE) {
            storeBackLocation(target, target.getServerWorld(), target.getBlockPos());
            target.teleport(requester.getServerWorld(), requester.getX(), requester.getY(), requester.getZ(), java.util.Collections.emptySet(), requester.getYaw(), requester.getPitch(), true);
            requester.sendMessage(Text.literal(target.getName().getString() + " accepted your teleport-here request."), false);
            target.sendMessage(Text.literal("Accepted teleport-here request from " + requester.getName().getString() + "."), false);
        } else {
            storeBackLocation(requester, requester.getServerWorld(), requester.getBlockPos());
            requester.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), java.util.Collections.emptySet(), target.getYaw(), target.getPitch(), true);
            requester.sendMessage(Text.literal(target.getName().getString() + " accepted your teleport request."), false);
            target.sendMessage(Text.literal("Accepted teleport request from " + requester.getName().getString() + "."), false);
        }
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

    private Text buildTpaRequestMessage(ServerPlayerEntity requester, TpaManager.RequestType type) {
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

        String prefix = type == TpaManager.RequestType.HERE
                ? requester.getName().getString() + " wants you to teleport to them. "
                : requester.getName().getString() + " wants to teleport to you. ";

        return Text.literal(prefix)
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

    private int undoTrash(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<ItemStack> stacks = trashUndo.remove(player.getUuid());
        if (stacks == null || stacks.isEmpty()) {
            player.sendMessage(Text.literal("You have nothing to undo from trash."), false);
            return 0;
        }

        for (ItemStack stack : stacks) {
            player.getInventory().offerOrDrop(stack.copy());
        }
        player.sendMessage(Text.literal("Restored your most recently trashed items."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int condenseInventory(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Map<Item, Item> recipes = new LinkedHashMap<>();
        recipes.put(Items.IRON_INGOT, Items.IRON_BLOCK);
        recipes.put(Items.GOLD_INGOT, Items.GOLD_BLOCK);
        recipes.put(Items.DIAMOND, Items.DIAMOND_BLOCK);
        recipes.put(Items.EMERALD, Items.EMERALD_BLOCK);
        recipes.put(Items.REDSTONE, Items.REDSTONE_BLOCK);
        recipes.put(Items.COAL, Items.COAL_BLOCK);
        recipes.put(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK);
        recipes.put(Items.COPPER_INGOT, Items.COPPER_BLOCK);
        recipes.put(Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK);
        recipes.put(Items.WHEAT, Items.HAY_BLOCK);
        recipes.put(Items.QUARTZ, Items.QUARTZ_BLOCK);

        int crafted = 0;
        for (Map.Entry<Item, Item> entry : recipes.entrySet()) {
            crafted += condenseItem(player, entry.getKey(), entry.getValue());
        }

        if (crafted == 0) {
            player.sendMessage(Text.literal("Nothing in your inventory can be condensed right now."), false);
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Text.literal("Condensed " + crafted + " stack groups in your inventory."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int condenseItem(ServerPlayerEntity player, Item input, Item output) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(input)) {
                total += stack.getCount();
            }
        }

        int crafts = total / 9;
        if (crafts <= 0) {
            return 0;
        }

        int remainingToRemove = crafts * 9;
        for (int i = 0; i < player.getInventory().size() && remainingToRemove > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(input)) {
                continue;
            }

            int remove = Math.min(remainingToRemove, stack.getCount());
            stack.decrement(remove);
            remainingToRemove -= remove;
        }

        int outputCount = crafts;
        while (outputCount > 0) {
            int stackSize = Math.min(outputCount, output.getMaxCount());
            player.getInventory().offerOrDrop(new ItemStack(output, stackSize));
            outputCount -= stackSize;
        }
        return crafts;
    }

    private int toggleAfk(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        boolean afk;
        if (!afkPlayers.add(player.getUuid())) {
            afkPlayers.remove(player.getUuid());
            afk = false;
        } else {
            afk = true;
        }
        afkIdleTickCounter.put(player.getUuid(), 0);
        afkLastPositions.put(player.getUuid(), player.getBlockPos());

        Text message = Text.literal(player.getName().getString() + (afk ? " is now AFK." : " is no longer AFK."))
                .formatted(afk ? Formatting.YELLOW : Formatting.GREEN);
        player.getServer().getPlayerManager().broadcast(message, false);
        return Command.SINGLE_SUCCESS;
    }

    private int showPlaytime(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        long seconds = playerDataStorage.getPlaytimeSeconds(player.getUuid());
        player.sendMessage(Text.literal("Playtime: " + formatDuration(seconds)), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showPlaytimeTop(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<PlayerDataStorage.PlaytimeEntry> top = playerDataStorage.getTopPlaytimes(10);
        if (top.isEmpty()) {
            player.sendMessage(Text.literal("No playtime has been recorded yet."), false);
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Text.literal("Playtime Top 10:").formatted(Formatting.GOLD), false);
        for (int i = 0; i < top.size(); i++) {
            PlayerDataStorage.PlaytimeEntry entry = top.get(i);
            player.sendMessage(Text.literal((i + 1) + ". " + entry.name() + " - " + formatDuration(entry.seconds())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int showLastDeath(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Optional<PlayerDataStorage.DeathEntry> death = playerDataStorage.getLastDeath(player.getUuid());
        if (death.isEmpty()) {
            player.sendMessage(Text.literal("No death location has been recorded yet."), false);
            return 0;
        }

        PlayerDataStorage.DeathEntry entry = death.get();
        player.sendMessage(Text.literal("Last death: " + entry.world() + " " + entry.x() + ", " + entry.y() + ", " + entry.z()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int giveDeathCompass(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Optional<PlayerDataStorage.DeathEntry> death = playerDataStorage.getLastDeath(player.getUuid());
        if (death.isEmpty()) {
            player.sendMessage(Text.literal("No death location has been recorded yet."), false);
            return 0;
        }

        PlayerDataStorage.DeathEntry entry = death.get();
        RegistryKey<World> worldKey = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of(entry.world()));
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.of(GlobalPos.create(worldKey, new BlockPos(entry.x(), entry.y(), entry.z()))), true));
        compass.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Death Compass").formatted(Formatting.RED));
        player.getInventory().offerOrDrop(compass);
        player.sendMessage(Text.literal("Gave you a compass pointing to your last death."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int coinFlip(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        String result = new Random().nextBoolean() ? "Heads" : "Tails";
        Text message = Text.literal(player.getName().getString() + " flipped " + result + ".").formatted(Formatting.GOLD);
        player.getServer().getPlayerManager().broadcast(message, false);
        return Command.SINGLE_SUCCESS;
    }

    private int showWhereAmI(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        BlockPos pos = player.getBlockPos();
        player.sendMessage(Text.literal("You are at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " in " + player.getServerWorld().getRegistryKey().getValue()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showBiome(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        String biome = player.getServerWorld().getBiome(player.getBlockPos()).getKey()
                .map(key -> key.getValue().toString())
                .orElse("unknown");
        player.sendMessage(Text.literal("Biome: " + biome), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showLight(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        BlockPos pos = player.getBlockPos();
        int total = player.getServerWorld().getLightLevel(pos);
        int block = player.getServerWorld().getLightLevel(LightType.BLOCK, pos);
        int sky = player.getServerWorld().getLightLevel(LightType.SKY, pos);
        player.sendMessage(Text.literal("Light: total=" + total + ", block=" + block + ", sky=" + sky), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showMoonPhase(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        player.sendMessage(Text.literal("Moon phase: " + player.getServerWorld().getMoonPhase()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showWeather(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        String weather = player.getServerWorld().isThundering() ? "thundering"
                : player.getServerWorld().isRaining() ? "raining"
                : "clear";
        player.sendMessage(Text.literal("Weather: " + weather), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showTime(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        long timeOfDay = player.getServerWorld().getTimeOfDay() % 24000L;
        player.sendMessage(Text.literal("Time of day: " + timeOfDay), false);
        return Command.SINGLE_SUCCESS;
    }

    private int setWorldTime(ServerCommandSource source, long time, String label) {
        source.getWorld().setTimeOfDay(time);
        source.sendFeedback(() -> Text.literal("Set time to " + label + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private int showSeed(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        player.sendMessage(Text.literal("World seed: " + player.getServerWorld().getSeed()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showSlimeChunk(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        ChunkPos chunkPos = player.getChunkPos();
        boolean slime = isSlimeChunk(player.getServerWorld().getSeed(), chunkPos.x, chunkPos.z);
        player.sendMessage(Text.literal("Chunk " + chunkPos.x + ", " + chunkPos.z + (slime ? " is" : " is not") + " a slime chunk."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showChunkInfo(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        ChunkPos chunkPos = player.getChunkPos();
        int regionX = chunkPos.x >> 5;
        int regionZ = chunkPos.z >> 5;
        player.sendMessage(Text.literal("Chunk: " + chunkPos.x + ", " + chunkPos.z + " | Region: " + regionX + ", " + regionZ), false);
        return Command.SINGLE_SUCCESS;
    }

    private int setSpawn(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        try {
            spawnStorage.setSpawn(player);
            player.sendMessage(Text.literal("Set server spawn at your current location."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to set spawn.", e);
            player.sendMessage(Text.literal("Failed to set spawn. Check the server log."), false);
            return 0;
        }
    }

    private int goSpawn(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
        Optional<String> error = spawnStorage.teleportToSpawn(player);
        if (error.isPresent()) {
            player.sendMessage(Text.literal(error.get()), false);
            return 0;
        }

        player.sendMessage(Text.literal("Teleported to spawn."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int randomTeleport(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        var world = player.getServerWorld();
        var random = world.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = player.getBlockX() + random.nextInt(10_001) - 5_000;
            int z = player.getBlockZ() + random.nextInt(10_001) - 5_000;
            Optional<net.minecraft.util.math.Vec3d> safeTarget = TeleportHelper.findSurfaceTeleportTarget(world, x, z);
            if (safeTarget.isEmpty()) {
                continue;
            }

            storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
            net.minecraft.util.math.Vec3d destination = safeTarget.get();
            player.teleport(world, destination.x, destination.y, destination.z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
            player.sendMessage(Text.literal("Randomly teleported you to " + x + ", " + z + "."), false);
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Text.literal("Failed to find a safe random teleport location. Try again."), false);
        return 0;
    }

    private int sendMail(ServerPlayerEntity sender, ServerPlayerEntity recipient, String message) {
        if (sender == null || recipient == null) {
            return 0;
        }
        if (sender.getUuid().equals(recipient.getUuid())) {
            sender.sendMessage(Text.literal("You cannot mail yourself."), false);
            return 0;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            sender.sendMessage(Text.literal("Mail message cannot be empty."), false);
            return 0;
        }

        try {
            MailStorage.MailMessage mail = mailStorage.send(sender, recipient, trimmed);
            sender.sendMessage(Text.literal("Sent mail #" + mail.id() + " to " + recipient.getName().getString() + "."), false);
            recipient.sendMessage(Text.literal("You received new mail from " + sender.getName().getString() + ". Use /mail to view it.").formatted(Formatting.GOLD), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to send mail from {} to {}.", sender.getName().getString(), recipient.getName().getString(), e);
            sender.sendMessage(Text.literal("Failed to send mail. Check the server log."), false);
            return 0;
        }
    }

    private int showMailInbox(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<MailStorage.MailMessage> inbox = mailStorage.getInbox(player.getUuid());
        if (inbox.isEmpty()) {
            player.sendMessage(Text.literal("Your mailbox is empty."), false);
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Text.literal("Mailbox:").formatted(Formatting.GOLD), false);
        inbox.stream().limit(10).forEach(mail -> {
            String status = mail.read() ? "" : " [new]";
            player.sendMessage(Text.literal("#" + mail.id() + " from " + mail.senderName() + " - " + formatRelativeTime(mail.sentAtMs()) + status), false);
        });
        if (inbox.size() > 10) {
            player.sendMessage(Text.literal("Showing 10 of " + inbox.size() + " messages."), false);
        }
        player.sendMessage(Text.literal("Use /mail read <id> to read one."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int readMail(ServerPlayerEntity player, int id) {
        if (player == null) {
            return 0;
        }

        try {
            Optional<MailStorage.MailMessage> mail = mailStorage.getMessage(player.getUuid(), id);
            if (mail.isEmpty()) {
                player.sendMessage(Text.literal("Mail #" + id + " was not found."), false);
                return 0;
            }

            MailStorage.MailMessage message = mail.get();
            player.sendMessage(Text.literal("Mail #" + message.id() + " from " + message.senderName() + ":").formatted(Formatting.GOLD), false);
            player.sendMessage(Text.literal(message.message()), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to read mail #{} for {}.", id, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to read mail. Check the server log."), false);
            return 0;
        }
    }

    private int deleteMail(ServerPlayerEntity player, int id) {
        if (player == null) {
            return 0;
        }

        try {
            if (!mailStorage.delete(player.getUuid(), id)) {
                player.sendMessage(Text.literal("Mail #" + id + " was not found."), false);
                return 0;
            }
            player.sendMessage(Text.literal("Deleted mail #" + id + "."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to delete mail #{} for {}.", id, player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to delete mail. Check the server log."), false);
            return 0;
        }
    }

    private int clearMail(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        try {
            int removed = mailStorage.clear(player.getUuid());
            player.sendMessage(Text.literal(removed == 0 ? "Your mailbox was already empty." : "Deleted " + removed + " mail messages."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            LOGGER.error("Failed to clear mail for {}.", player.getName().getString(), e);
            player.sendMessage(Text.literal("Failed to clear mail. Check the server log."), false);
            return 0;
        }
    }

    private int sortCurrentInventory(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Inventory target = getLookedAtSortableInventory(player).orElse(player.getInventory());
        int size = target == player.getInventory() ? 36 : target.size();

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = target.getStack(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) {
            player.sendMessage(Text.literal("There is nothing to sort."), false);
            return Command.SINGLE_SUCCESS;
        }

        stacks.sort(Comparator
                .comparing((ItemStack stack) -> Registries.ITEM.getId(stack.getItem()).toString())
                .thenComparing(stack -> stack.getName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemStack::getCount, Comparator.reverseOrder()));

        for (int i = 0; i < size; i++) {
            target.setStack(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < stacks.size(); i++) {
            target.setStack(i, stacks.get(i));
        }
        target.markDirty();
        player.currentScreenHandler.sendContentUpdates();
        player.playerScreenHandler.sendContentUpdates();
        player.sendMessage(Text.literal(target == player.getInventory() ? "Sorted your inventory." : "Sorted the container you were looking at."), false);
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Inventory> getLookedAtSortableInventory(ServerPlayerEntity player) {
        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return Optional.empty();
        }

        BlockPos pos = blockHitResult.getBlockPos();
        var world = player.getServerWorld();
        var state = world.getBlockState(pos);

        if (state.getBlock() instanceof ChestBlock) {
            Inventory inventory = ChestBlock.getInventory((ChestBlock) state.getBlock(), state, world, pos, true);
            if (inventory != null) {
                return Optional.of(inventory);
            }
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory inventory && !(blockEntity instanceof ChestBlockEntity)) {
            return Optional.of(inventory);
        }

        return Optional.empty();
    }

    private int showSeen(ServerPlayerEntity viewer, String name) {
        if (viewer == null) {
            return 0;
        }

        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return 0;
        }

        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) {
            viewer.sendMessage(Text.literal(online.getName().getString() + " is online in " + online.getServerWorld().getRegistryKey().getValue()
                    + ". Playtime: " + formatDuration(playerDataStorage.getPlaytimeSeconds(online.getUuid()))), false);
            return Command.SINGLE_SUCCESS;
        }

        Optional<PlayerDataStorage.SeenEntry> seen = playerDataStorage.findByName(name);
        if (seen.isEmpty()) {
            viewer.sendMessage(Text.literal("No player data found for '" + name + "'."), false);
            return 0;
        }

        PlayerDataStorage.SeenEntry entry = seen.get();
        viewer.sendMessage(Text.literal(entry.name() + " was last seen " + formatRelativeTime(entry.lastSeenAtMs())
                + ". Playtime: " + formatDuration(entry.playtimeSeconds())), false);
        return Command.SINGLE_SUCCESS;
    }

    private int runDoctor(ServerCommandSource source) {
        SpawnStorage.SpawnPoint spawn = spawnStorage.getSpawn().orElse(null);
        source.sendFeedback(() -> Text.literal("Justys' Essentials doctor:"), false);
        source.sendFeedback(() -> Text.literal("Config: " + net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("justys-essentials.json")), false);
        source.sendFeedback(() -> Text.literal("Home storage: " + homeStorage.getStorageFile()), false);
        source.sendFeedback(() -> Text.literal("Player data: " + playerDataStorage.getStorageFile()), false);
        source.sendFeedback(() -> Text.literal("Mail storage: " + mailStorage.getStorageFile()), false);
        source.sendFeedback(() -> Text.literal("Spawn storage: " + spawnStorage.getStorageFile()), false);
        source.sendFeedback(() -> Text.literal("Current jar: " + ModUpdater.getCurrentJarPath()), false);
        source.sendFeedback(() -> Text.literal("Spawn set: " + (spawn != null ? spawn.world() + " " + spawn.x() + ", " + spawn.y() + ", " + spawn.z() : "no")), false);
        source.sendFeedback(() -> Text.literal("Visual Workbench loaded: " + FabricLoader.getInstance().isModLoaded("visualworkbench")), false);
        source.sendFeedback(() -> Text.literal("Easy Anvils loaded: " + FabricLoader.getInstance().isModLoaded("easyanvils")), false);
        source.sendFeedback(() -> Text.literal("Nemo's Woodcutter loaded: " + FabricLoader.getInstance().isModLoaded("nemos_woodcutter")), false);
        return Command.SINGLE_SUCCESS;
    }

    private int packUpItems(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<ItemEntity> items = player.getServerWorld().getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(8.0), entity -> entity.isAlive() && !entity.getStack().isEmpty());
        if (items.isEmpty()) {
            player.sendMessage(Text.literal("There are no nearby dropped items to collect."), false);
            return Command.SINGLE_SUCCESS;
        }

        int movedStacks = 0;
        for (ItemEntity item : items) {
            ItemStack stack = item.getStack().copy();
            if (stack.isEmpty()) {
                continue;
            }
            player.getInventory().offerOrDrop(stack);
            item.discard();
            movedStacks++;
        }

        player.sendMessage(Text.literal("Picked up " + movedStacks + " nearby item stack" + (movedStacks == 1 ? "" : "s") + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int goTop(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        Optional<net.minecraft.util.math.Vec3d> safeTarget = TeleportHelper.findSurfaceTeleportTarget(player.getServerWorld(), player.getBlockX(), player.getBlockZ());
        if (safeTarget.isEmpty()) {
            player.sendMessage(Text.literal("Could not find a safe top location here."), false);
            return 0;
        }

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
        net.minecraft.util.math.Vec3d destination = safeTarget.get();
        player.teleport(player.getServerWorld(), destination.x, destination.y, destination.z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
        player.sendMessage(Text.literal("Teleported you to the top."), false);
        return Command.SINGLE_SUCCESS;
    }

    private int showNear(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        List<ServerPlayerEntity> nearby = player.getServer().getPlayerManager().getPlayerList().stream()
                .filter(other -> !other.getUuid().equals(player.getUuid()))
                .filter(other -> other.getServerWorld() == player.getServerWorld())
                .filter(other -> other.squaredDistanceTo(player) <= 128 * 128)
                .sorted(Comparator.comparingDouble(other -> other.squaredDistanceTo(player)))
                .toList();

        if (nearby.isEmpty()) {
            player.sendMessage(Text.literal("No players are nearby."), false);
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Text.literal("Nearby players:").formatted(Formatting.GOLD), false);
        for (ServerPlayerEntity other : nearby) {
            int distance = (int) Math.sqrt(other.squaredDistanceTo(player));
            player.sendMessage(Text.literal(other.getName().getString() + " - " + distance + " blocks"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        long mixed = seed
                + (long) (chunkX * chunkX * 4987142)
                + (long) (chunkX * 5947611)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711)
                ^ 987234911L;
        return new Random(mixed).nextInt(10) == 0;
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours + "h " + minutes + "m " + remainder + "s";
    }

    private String formatRelativeTime(long timestampMs) {
        long deltaSeconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (deltaSeconds < 60) {
            return deltaSeconds + "s ago";
        }
        if (deltaSeconds < 3600) {
            return (deltaSeconds / 60) + "m ago";
        }
        if (deltaSeconds < 86_400) {
            return (deltaSeconds / 3600) + "h ago";
        }
        return (deltaSeconds / 86_400) + "d ago";
    }

    private void tickOnlinePlayers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            int ticks = playtimeTickCounter.getOrDefault(uuid, 0) + 1;
            if (ticks >= 20) {
                playerDataStorage.addPlaytime(player, 1);
                ticks = 0;
            }
            playtimeTickCounter.put(uuid, ticks);

            BlockPos currentPos = player.getBlockPos();
            BlockPos lastPos = afkLastPositions.get(uuid);
            if (lastPos == null || !lastPos.equals(currentPos)) {
                afkLastPositions.put(uuid, currentPos);
                afkIdleTickCounter.put(uuid, 0);
                if (afkPlayers.remove(uuid)) {
                    server.getPlayerManager().broadcast(Text.literal(player.getName().getString() + " is no longer AFK.")
                            .formatted(Formatting.GREEN), false);
                }
            } else {
                int idleTicks = afkIdleTickCounter.getOrDefault(uuid, 0) + 1;
                afkIdleTickCounter.put(uuid, idleTicks);
                if (idleTicks >= 20 * 60 && afkPlayers.add(uuid)) {
                    server.getPlayerManager().broadcast(Text.literal(player.getName().getString() + " is now AFK.")
                            .formatted(Formatting.YELLOW), false);
                }
            }
        }

        if (server.getTicks() % 1200 == 0) {
            playerDataStorage.flush();
        }
    }

    private void applyWorldConfig(MinecraftServer server) {
        boolean doFireTick = !configRef.get().isNoFireSpread();
        server.getGameRules().get(GameRules.DO_FIRE_TICK).set(doFireTick, server);
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

        if (FabricLoader.getInstance().isModLoaded("visualworkbench")) {
            BlockPos workbenchPos = findNearbyBlockWithScreen(player, path -> path.equals("crafting_table"));
            if (workbenchPos == null) {
                player.sendMessage(Text.literal("Visual Workbench is installed. /craft needs a nearby crafting table to stay compatible."), false);
                return 0;
            }

            var factory = player.getServerWorld().getBlockState(workbenchPos).createScreenHandlerFactory(player.getServerWorld(), workbenchPos);
            if (factory == null) {
                player.sendMessage(Text.literal("The nearby crafting table could not be opened."), false);
                return 0;
            }

            player.openHandledScreen(factory);
            return Command.SINGLE_SUCCESS;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new CraftingScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.crafting")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private BlockPos findNearbyBlockWithScreen(ServerPlayerEntity player, java.util.function.Predicate<String> pathMatcher) {
        BlockPos center = player.getBlockPos();
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(center, 24, 8, 24)) {
            var state = player.getServerWorld().getBlockState(pos);
            String path = Registries.BLOCK.getId(state.getBlock()).getPath();
            if (!pathMatcher.test(path)) {
                continue;
            }
            if (state.createScreenHandlerFactory(player.getServerWorld(), pos) == null) {
                continue;
            }

            double distanceSq = pos.getSquaredDistance(center);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestPos = pos.toImmutable();
            }
        }

        return bestPos;
    }

    private int openAnvil(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        if (FabricLoader.getInstance().isModLoaded("easyanvils")) {
            BlockPos anvilPos = findNearbyBlockWithScreen(player, path -> path.contains("anvil"));
            if (anvilPos == null) {
                player.sendMessage(Text.literal("Easy Anvils is installed. /anvil needs a nearby anvil to stay compatible."), false);
                return 0;
            }

            var factory = player.getServerWorld().getBlockState(anvilPos).createScreenHandlerFactory(player.getServerWorld(), anvilPos);
            if (factory == null) {
                player.sendMessage(Text.literal("The nearby anvil could not be opened."), false);
                return 0;
            }

            player.openHandledScreen(factory);
            return Command.SINGLE_SUCCESS;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.repair")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openCartography(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new CartographyTableScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.cartography_table")
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
                List<ItemStack> deletedStacks = new ArrayList<>();
                for (int i = 0; i < size(); i++) {
                    ItemStack stack = getStack(i);
                    if (!stack.isEmpty()) {
                        deletedStacks.add(stack.copy());
                    }
                }
                if (!deletedStacks.isEmpty() && player instanceof ServerPlayerEntity serverPlayer) {
                    trashUndo.put(serverPlayer.getUuid(), deletedStacks);
                }
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

    private int openSmithing(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new SmithingScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY),
                Text.translatable("container.upgrade")
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

    private int openEnderPeek(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (viewer == null || target == null) {
            return 0;
        }

        ModConfig config = configRef.get();
        int rows = config.getRows();
        EnderChestInventory ender = target.getEnderChestInventory();
        MinecraftServer server = target.getServer();
        if (server == null) {
            return 0;
        }

        ExtraEnderChestState state = ExtraEnderChestState.get(server);
        int storedSize = state.getStoredSize(target.getUuid());
        int storedRows = Math.min(ModConfig.MAX_ROWS, Math.max(ModConfig.MIN_ROWS, storedSize / 9));
        if (state.hasItemsBeyond(target.getUuid(), rows * 9)) {
            rows = storedRows;
        }

        DefaultedList<ItemStack> saved = state.getOrCreate(target.getUuid(), rows * 9);
        for (int i = 0; i < Math.min(27, saved.size()); i++) {
            saved.set(i, ender.getStack(i).copy());
        }

        SimpleInventory editable = new SimpleInventory(saved.toArray(new ItemStack[0])) {
            @Override
            public void markDirty() {
                super.markDirty();
                state.set(target.getUuid(), toList());
                for (int i = 0; i < Math.min(27, size()); i++) {
                    ender.setStack(i, getStack(i).copy());
                }
            }

            @Override
            public void onClose(PlayerEntity player) {
                super.onClose(player);
                state.set(target.getUuid(), toList());
                for (int i = 0; i < Math.min(27, size()); i++) {
                    ender.setStack(i, getStack(i).copy());
                }
            }

            private DefaultedList<ItemStack> toList() {
                DefaultedList<ItemStack> list = DefaultedList.ofSize(size(), ItemStack.EMPTY);
                for (int i = 0; i < size(); i++) {
                    list.set(i, getStack(i));
                }
                return list;
            }
        };
        int finalRows = rows;
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createGenericHandler(syncId, playerInv, editable, finalRows),
                Text.literal(target.getName().getString() + "'s Ender Chest")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int openInventoryPeek(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (viewer == null || target == null) {
            return 0;
        }

        ItemStack[] copied = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            copied[i] = target.getInventory().getStack(i).copy();
        }

        Inventory editable = new SimpleInventory(copied) {
            @Override
            public void markDirty() {
                super.markDirty();
                syncToTarget();
            }

            @Override
            public void onClose(PlayerEntity player) {
                syncToTarget();
                super.onClose(player);
            }

            private void syncToTarget() {
                for (int i = 0; i < 36; i++) {
                    target.getInventory().setStack(i, getStack(i).copy());
                }
                target.playerScreenHandler.sendContentUpdates();
            }
        };
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> createGenericHandler(syncId, playerInv, editable, 4),
                Text.literal(target.getName().getString() + "'s Inventory")
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int goBack(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        BackLocation backLocation = backLocations.get(player.getUuid());
        if (backLocation == null) {
            player.sendMessage(Text.literal("You have no back location saved."), false);
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }

        net.minecraft.server.world.ServerWorld world = server.getWorld(backLocation.worldKey());
        if (world == null) {
            player.sendMessage(Text.literal("Your back location world is not loaded."), false);
            return 0;
        }

        Optional<net.minecraft.util.math.Vec3d> safeTarget = TeleportHelper.findSafeTeleportTarget(world, backLocation.pos());
        if (safeTarget.isEmpty()) {
            player.sendMessage(Text.literal("Your back location is obstructed or unsafe."), false);
            return 0;
        }

        storeBackLocation(player, player.getServerWorld(), player.getBlockPos());
        net.minecraft.util.math.Vec3d destination = safeTarget.get();
        player.teleport(world, destination.x, destination.y, destination.z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
        player.sendMessage(Text.literal("Teleported to your last saved location."), false);
        return Command.SINGLE_SUCCESS;
    }

    private void storeBackLocation(ServerPlayerEntity player, net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.BlockPos pos) {
        backLocations.put(player.getUuid(), new BackLocation(world.getRegistryKey(), pos.toImmutable()));
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

    private GenericContainerScreenHandler createReadOnlyGenericHandler(int syncId,
                                                                       net.minecraft.entity.player.PlayerInventory playerInv,
                                                                       Inventory inv,
                                                                       int rows) {
        return new GenericContainerScreenHandler(resolveGenericType(rows), syncId, playerInv, inv, rows) {
            @Override
            public ItemStack quickMove(PlayerEntity player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                if (slotIndex >= 0 && slotIndex < inv.size()) {
                    return;
                }
                super.onSlotClick(slotIndex, button, actionType, player);
            }
        };
    }

    private GenericContainerScreenHandler createActionMenuHandler(int syncId,
                                                                  net.minecraft.entity.player.PlayerInventory playerInv,
                                                                  Inventory inv,
                                                                  int rows,
                                                                  Map<Integer, Runnable> actions) {
        return new GenericContainerScreenHandler(resolveGenericType(rows), syncId, playerInv, inv, rows) {
            @Override
            public ItemStack quickMove(PlayerEntity player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                if (slotIndex >= 0 && slotIndex < inv.size()) {
                    Runnable action = actions.get(slotIndex);
                    if (action != null) {
                        action.run();
                    }
                    return;
                }
                super.onSlotClick(slotIndex, button, actionType, player);
            }
        };
    }

    private ScreenHandlerType<?> resolveGenericType(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X3;
        };
    }

    private record BackLocation(net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
                                net.minecraft.util.math.BlockPos pos) {
    }
}
