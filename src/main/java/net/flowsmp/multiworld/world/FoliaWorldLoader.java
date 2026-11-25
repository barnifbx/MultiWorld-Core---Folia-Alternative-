/*
 * MultiWorld-Core — Created by the FlowSMP Team AKA FlowStudios
 *
 * The world creation sequence in this file is adapted from MoreFoWorld
 * (https://github.com/Folia-Inquisitors/MoreFoWorld, MIT License, Copyright (c) 2023 Folia Inquisitors),
 * because Folia stubs out Bukkit#createWorld ("not implemented properly yet").
 */
package net.flowsmp.multiworld.world;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.JsonOps;
import io.papermc.paper.world.PaperWorldLoader;
import io.papermc.paper.world.migration.WorldFolderMigration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class FoliaWorldLoader {
    private FoliaWorldLoader() {
    }

    /** Folder the world is stored in (new 26.x layout). */
    public static Path dimensionPath(String name) {
        DedicatedServer console = ((CraftServer) Bukkit.getServer()).getServer();
        ResourceKey<net.minecraft.world.level.Level> key = PaperWorldLoader.dimensionKey(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return console.storageSource.getDimensionPath(key);
    }

    /**
     * Creates or loads a world. Must run on the global region thread (or during startup).
     */
    public static World load(WorldCreator creator) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        DedicatedServer console = craftServer.getServer();

        String name = creator.name();
        ChunkGenerator chunkGenerator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        if (chunkGenerator == null) chunkGenerator = craftServer.getGenerator(name);
        if (biomeProvider == null) biomeProvider = craftServer.getBiomeProvider(name);

        ResourceKey<LevelStem> actualDimension = switch (creator.environment()) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        };

        final ResourceKey<net.minecraft.world.level.Level> dimensionKey = PaperWorldLoader.dimensionKey(creator.key());
        WorldLoader.DataLoadContext context = console.worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        net.minecraft.core.Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);

        final LevelStem configuredStem = console.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(actualDimension);
        if (configuredStem == null) throw new IllegalStateException("Missing configured level stem " + actualDimension);

        try {
            WorldFolderMigration.migrateApiWorld(console.storageSource, console.registryAccess(), name, actualDimension, dimensionKey);
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to migrate legacy world " + name, ex);
        }

        PaperWorldLoader.LoadedWorldData loadedWorldData = PaperWorldLoader.loadWorldData(console, dimensionKey, name);
        final PrimaryLevelData primaryLevelData = (PrimaryLevelData) console.getWorldData();

        WorldGenSettings worldGenSettings = LevelStorageSource.readExistingSavedData(console.storageSource, dimensionKey, console.registryAccess(), WorldGenSettings.TYPE)
                .result()
                .orElse(null);
        if (worldGenSettings == null) {
            WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), creator.bonusChest());
            String genSettings = creator.generatorSettings();
            if (genSettings.isEmpty()) {
                if (creator.type() == WorldType.FLAT) {
                    RegistryAccess ra = console.registryAccess();
                    genSettings = FlatLevelGeneratorSettings.CODEC.encodeStart(ra.createSerializationContext(JsonOps.INSTANCE), FlatLevelGeneratorSettings.getDefault(
                            ra.lookupOrThrow(Registries.BIOME),
                            ra.lookupOrThrow(Registries.STRUCTURE_SET),
                            ra.lookupOrThrow(Registries.PLACED_FEATURE)
                    )).getOrThrow().toString();
                } else {
                    genSettings = "{}";
                }
            }
            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse(genSettings), creator.type().name().toLowerCase(Locale.ROOT));
            WorldDimensions worldDimensions = properties.create(context.datapackWorldgen());
            WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
            if (complete.dimensions().getValue(actualDimension) == null) {
                throw new IllegalStateException("Missing generated level stem " + actualDimension + " for world " + name);
            }
            worldGenSettings = new WorldGenSettings(worldOptions, worldDimensions);
            registryAccess = complete.dimensionsRegistryAccess();
            loadedWorldData.levelOverrides().setHardcore(creator.hardcore());
            loadedWorldData = new PaperWorldLoader.LoadedWorldData(
                    loadedWorldData.bukkitName(), loadedWorldData.uuid(), loadedWorldData.pdc(), loadedWorldData.levelOverrides());
        }
        final WorldGenSettings genSettingsFinal = worldGenSettings;
        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);

        if (console.options.has("forceUpgrade")) {
            net.minecraft.server.Main.forceUpgrade(console.storageSource, DataFixers.getDataFixer(), console.options.has("eraseCache"), () -> true, registryAccess, console.options.has("recreateRegionFiles"));
        }

        long biomeZoomSeed = BiomeManager.obfuscateSeed(genSettingsFinal.options().seed());
        LevelStem customStem = genSettingsFinal.dimensions().get(actualDimension).orElse(null);
        if (customStem == null) customStem = contextLevelStemRegistry.getValue(actualDimension);
        if (customStem == null) throw new IllegalStateException("Missing level stem for world " + name + " using key " + actualDimension);

        WorldInfo worldInfo = new CraftWorldInfo(loadedWorldData.bukkitName(), CraftNamespacedKey.fromMinecraft(dimensionKey.identifier()), genSettingsFinal.options().seed(), primaryLevelData.enabledFeatures(), creator.environment(), customStem.type().value(), customStem.generator(), craftServer.getHandle().getServer().registryAccess(), loadedWorldData.uuid());
        if (biomeProvider == null && chunkGenerator != null) biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);

        final SavedDataStorage savedDataStorage = new SavedDataStorage(console.storageSource.getDimensionPath(dimensionKey).resolve(LevelResource.DATA.id()), console.getFixerUpper(), console.registryAccess());
        savedDataStorage.set(WorldGenSettings.TYPE, new WorldGenSettings(genSettingsFinal.options(), genSettingsFinal.dimensions()));
        List<CustomSpawner> spawners = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(savedDataStorage));

        ServerLevel serverLevel = new ServerLevel(
                console,
                console.executor,
                console.storageSource,
                genSettingsFinal,
                dimensionKey,
                customStem,
                primaryLevelData.isDebugWorld(),
                biomeZoomSeed,
                creator.environment() == World.Environment.NORMAL ? spawners : ImmutableList.of(),
                true,
                actualDimension,
                creator.environment(),
                chunkGenerator,
                biomeProvider,
                savedDataStorage,
                loadedWorldData
        );

        console.addLevel(serverLevel);
        console.initWorld(serverLevel, creator);
        serverLevel.setSpawnSettings(true);
        console.prepareLevel(serverLevel);
        return serverLevel.getWorld();
    }
}
