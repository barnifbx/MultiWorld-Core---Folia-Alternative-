/*
 * MultiWorld-Core — Created by the FlowSMP Team AKA FlowStudios
 *
 * Loader for Folia 1.21.4. The world creation sequence is adapted from MoreFoWorld
 * (https://github.com/Folia-Inquisitors/MoreFoWorld, MIT License, Copyright (c) 2023 Folia Inquisitors).
 * Before 1.21.11 Folia needs the spawn area preloaded, so worlds are only created during startup.
 */
package net.flowsmp.multiworld.world;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class FoliaWorldLoader {
    public static final boolean RUNTIME_SAFE = false;
    public static final String TARGET = "1.21.4";

    private FoliaWorldLoader() {
    }

    public static Path dimensionPath(String name) {
        return new File(Bukkit.getWorldContainer(), name).toPath();
    }

    public static World load(WorldCreator creator) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        DedicatedServer console = craftServer.getServer();
        String name = creator.name();
        ChunkGenerator generator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        File folder = new File(craftServer.getWorldContainer(), name);
        if (folder.exists() && !folder.isDirectory()) throw new IllegalStateException("World folder " + name + " is not a directory");
        if (generator == null) generator = craftServer.getGenerator(name);
        if (biomeProvider == null) biomeProvider = craftServer.getBiomeProvider(name);

        ResourceKey<LevelStem> actualDimension = switch (creator.environment()) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Illegal dimension");
        };

        LevelStorageSource.LevelStorageAccess levelStorageAccess;
        try {
            levelStorageAccess = LevelStorageSource.createDefault(craftServer.getWorldContainer().toPath()).validateAndCreateAccess(name, actualDimension);
        } catch (IOException | ContentValidationException ex) {
            throw new RuntimeException(ex);
        }
        Dynamic<?> dataTag;
        if (levelStorageAccess.hasWorldData()) {
            net.minecraft.world.level.storage.LevelSummary summary;
            try {
                dataTag = levelStorageAccess.getDataTag();
                summary = levelStorageAccess.getSummary(dataTag);
            } catch (NbtException | ReportedNbtException | IOException ex) {
                try {
                    dataTag = levelStorageAccess.getDataTagFallback();
                    summary = levelStorageAccess.getSummary(dataTag);
                } catch (NbtException | ReportedNbtException | IOException ex2) {
                    throw new IllegalStateException("World data of " + name + " is corrupted", ex2);
                }
                levelStorageAccess.restoreLevelDataFromOld();
            }
            if (summary.requiresManualConversion()) throw new IllegalStateException("World " + name + " must be converted in an older version first");
            if (!summary.isCompatible()) throw new IllegalStateException("World " + name + " was created by an incompatible version");
        } else {
            dataTag = null;
        }

        PrimaryLevelData primaryLevelData;
        WorldLoader.DataLoadContext context = console.worldLoader;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        net.minecraft.core.Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        if (dataTag != null) {
            LevelDataAndDimensions data = LevelStorageSource.getLevelDataAndDimensions(dataTag, context.dataConfiguration(), contextLevelStemRegistry, context.datapackWorldgen());
            primaryLevelData = (PrimaryLevelData) data.worldData();
            registryAccess = data.dimensions().dimensionsRegistryAccess();
        } else {
            WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), false);
            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse(creator.generatorSettings().isEmpty() ? "{}" : creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
            LevelSettings levelSettings = new LevelSettings(name, GameType.SURVIVAL, creator.hardcore(), Difficulty.EASY, false,
                    new GameRules(context.dataConfiguration().enabledFeatures()), context.dataConfiguration());
            WorldDimensions worldDimensions = properties.create(context.datapackWorldgen());
            WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
            Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());
            primaryLevelData = new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);
            registryAccess = complete.dimensionsRegistryAccess();
        }
        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        primaryLevelData.customDimensions = contextLevelStemRegistry;
        primaryLevelData.checkName(name);
        primaryLevelData.setModdedInfo(console.getServerModName(), console.getModdedStatus().shouldReportAsModified());
        long seed = BiomeManager.obfuscateSeed(primaryLevelData.worldGenOptions().seed());
        List<CustomSpawner> spawners = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(primaryLevelData));
        LevelStem customStem = contextLevelStemRegistry.getValue(actualDimension);
        WorldInfo worldInfo = new CraftWorldInfo(primaryLevelData, levelStorageAccess, creator.environment(), customStem.type().value(), customStem.generator(), console.registryAccess());
        if (biomeProvider == null && generator != null) biomeProvider = generator.getDefaultBiomeProvider(worldInfo);
        if (console.options.has("forceUpgrade")) {
            net.minecraft.server.Main.forceUpgrade(levelStorageAccess, DataFixers.getDataFixer(), console.options.has("eraseCache"), () -> true, registryAccess, console.options.has("recreateRegionFiles"));
        }
        ResourceKey<net.minecraft.world.level.Level> worldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(creator.key().namespace(), creator.key().value()));

        ServerLevel serverLevel = new ServerLevel(console, console.executor, levelStorageAccess, primaryLevelData, worldKey, customStem,
                console.progressListenerFactory.create(primaryLevelData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS)),
                primaryLevelData.isDebugWorld(), seed, creator.environment() == World.Environment.NORMAL ? spawners : ImmutableList.of(), true,
                console.overworld().getRandomSequences(), creator.environment(), generator, biomeProvider);
        console.addLevel(serverLevel);
        int radius = 1024 >> 4;
        serverLevel.randomSpawnSelection = new ChunkPos(serverLevel.getChunkSource().randomState().sampler().findSpawnPosition());
        for (int x = -radius; x <= radius; ++x) {
            for (int z = -radius; z <= radius; ++z) {
                ChunkPos pos = new ChunkPos(x, z);
                serverLevel.chunkSource.addTicketAtLevel(net.minecraft.server.level.TicketType.UNKNOWN, pos, ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL, pos);
            }
        }
        serverLevel.setSpawnSettings(true);
        console.prepareLevels(serverLevel.getChunkSource().chunkMap.progressListener, serverLevel);
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addWorld(serverLevel);
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(serverLevel.getWorld()));
        return serverLevel.getWorld();
    }
}
