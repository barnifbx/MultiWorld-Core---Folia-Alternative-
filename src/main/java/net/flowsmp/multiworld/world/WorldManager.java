package net.flowsmp.multiworld.world;

import net.flowsmp.multiworld.MultiWorldCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class WorldManager {
    public static final Pattern NAME = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    public static final List<String> TYPES = List.of("normal", "flat", "amplified", "large_biomes", "void");

    private final MultiWorldCore plugin;
    private final File file;
    private final Map<String, WorldEntry> entries = new LinkedHashMap<>();
    private final List<String> pendingDelete = new ArrayList<>();

    public WorldManager(MultiWorldCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
    }

    // ───────── storage
    public synchronized void read() {
        entries.clear();
        pendingDelete.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ws = y.getConfigurationSection("worlds");
        if (ws != null) for (String k : ws.getKeys(false)) {
            ConfigurationSection s = ws.getConfigurationSection(k);
            if (s != null) entries.put(k.toLowerCase(Locale.ROOT), WorldEntry.load(k, s));
        }
        pendingDelete.addAll(y.getStringList("pending-delete"));
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MultiWorld-Core — Created by the FlowSMP Team AKA FlowStudios",
                "Edit with /mw modify while the server runs, or edit this file and use /mw reload."));
        for (WorldEntry e : entries.values()) e.save(y.createSection("worlds." + e.name));
        y.set("pending-delete", pendingDelete);
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save worlds.yml", ex);
        }
    }

    // ───────── lookup
    public synchronized WorldEntry get(String name) {
        return name == null ? null : entries.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized WorldEntry get(World w) {
        return w == null ? null : get(w.getName());
    }

    public synchronized Collection<WorldEntry> all() {
        return entries.values().stream().sorted(Comparator.comparing(e -> e.name)).toList();
    }

    public synchronized List<String> names() {
        return entries.values().stream().map(e -> e.name).sorted().toList();
    }

    public synchronized void put(WorldEntry e) {
        entries.put(e.name.toLowerCase(Locale.ROOT), e);
    }

    public synchronized void remove(String name) {
        entries.remove(name.toLowerCase(Locale.ROOT));
    }

    public synchronized void markDelete(String name) {
        if (!pendingDelete.contains(name)) pendingDelete.add(name);
    }

    public synchronized boolean isPendingDelete(String name) {
        return pendingDelete.contains(name);
    }

    // ───────── startup
    /** Runs during plugin enable (startup thread): deletes queued worlds, registers server worlds and loads managed worlds. */
    public void startup() {
        for (String name : new ArrayList<>(pendingDelete)) {
            if (Bukkit.getWorld(name) != null) {
                plugin.getLogger().warning("World " + name + " is still loaded and was not deleted.");
                continue;
            }
            deleteFolders(name);
            pendingDelete.remove(name);
            plugin.getLogger().info("Deleted world " + name);
        }
        for (World w : Bukkit.getWorlds()) {
            if (get(w) != null) continue;
            WorldEntry e = new WorldEntry(w.getName());
            e.environment = w.getEnvironment();
            e.builtin = true;
            e.pvp = w.getPVP();
            put(e);
        }
        for (WorldEntry e : all()) {
            if (e.world() != null || !e.autoLoad || e.builtin) continue;
            try {
                FoliaWorldLoader.load(creator(e));
                plugin.getLogger().info("Loaded world " + e.name);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Could not load world " + e.name, t);
            }
        }
        save();
        for (WorldEntry e : all()) if (e.world() != null) applySettings(e);
    }

    // ───────── runtime
    /** Creates / loads a world on the global region thread. */
    public CompletableFuture<World> load(WorldEntry e) {
        CompletableFuture<World> f = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                World existing = e.world();
                World w = existing != null ? existing : FoliaWorldLoader.load(creator(e));
                applySettings(e);
                f.complete(w);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Could not load world " + e.name, t);
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    public WorldCreator creator(WorldEntry e) {
        WorldCreator c = new WorldCreator(e.name);
        c.environment(e.environment);
        c.seed(e.seed != null ? e.seed : ThreadLocalRandom.current().nextLong());
        c.generateStructures(e.structures);
        switch (e.type) {
            case "flat" -> c.type(WorldType.FLAT);
            case "amplified" -> c.type(WorldType.AMPLIFIED);
            case "large_biomes" -> c.type(WorldType.LARGE_BIOMES);
            default -> c.type(WorldType.NORMAL);
        }
        if ("void".equals(e.type)) {
            c.generator(new VoidGenerator());
        } else if (e.generator != null && !e.generator.isBlank()) {
            ChunkGenerator gen = WorldCreator.getGeneratorForName(e.name, e.generator, Bukkit.getConsoleSender());
            if (gen != null) c.generator(gen);
            BiomeProvider bp = WorldCreator.getBiomeProviderForName(e.name, e.generator, Bukkit.getConsoleSender());
            if (bp != null) c.biomeProvider(bp);
        }
        return c;
    }

    /** Applies world-wide settings (global region thread). Player settings are handled by listeners. */
    public void applySettings(WorldEntry e) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            World w = e.world();
            if (w == null) return;
            if (e.difficulty != null) w.setDifficulty(e.difficulty);
            w.setPVP(e.pvp);
            if (!e.weather) {
                w.setStorm(false);
                w.setThundering(false);
            }
            if (e.spawn != null) w.setSpawnLocation(e.spawnLocation(w));
        });
    }

    /** Where players go when sent to a world. */
    public Location spawnOf(World w) {
        WorldEntry e = get(w);
        return e != null ? e.spawnLocation(w) : w.getSpawnLocation();
    }

    public World mainWorld() {
        return Bukkit.getWorlds().getFirst();
    }

    /** Moves everyone out of a world (to the main world spawn). */
    public void evacuate(World w) {
        World main = mainWorld();
        if (main == null || main.equals(w)) return;
        Location target = spawnOf(main);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getScheduler().run(plugin, t -> {
                if (p.getWorld().equals(w)) p.teleportAsync(target);
            }, null);
        }
    }

    private void deleteFolders(String name) {
        List<Path> paths = new ArrayList<>();
        try {
            paths.add(FoliaWorldLoader.dimensionPath(name));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve storage path of " + name, t);
        }
        paths.add(new File(Bukkit.getWorldContainer(), name).toPath());
        for (Path p : paths) {
            if (!Files.isDirectory(p)) continue;
            if (p.toAbsolutePath().normalize().equals(Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize())) continue;
            try (Stream<Path> s = Files.walk(p)) {
                s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not delete " + p, ex);
            }
        }
    }
}
