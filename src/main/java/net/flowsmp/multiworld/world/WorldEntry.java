package net.flowsmp.multiworld.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Settings of one managed world (stored in worlds.yml). */
public final class WorldEntry {
    public final String name;
    public World.Environment environment = World.Environment.NORMAL;
    public String type = "normal";          // normal, flat, amplified, large_biomes, void
    public Long seed;
    public String generator;                // "Plugin" or "Plugin:id"
    public boolean structures = true;
    public boolean builtin;                 // loaded by the server itself (world, world_nether, ...)
    public boolean autoLoad = true;

    public String alias;
    public GameMode gamemode;               // null = do not enforce
    public Difficulty difficulty;           // null = do not change
    public boolean pvp = true;
    public boolean hunger = true;
    public boolean weather = true;
    public boolean animals = true;
    public boolean monsters = true;
    public boolean hidden;
    public boolean access;                  // needs multiworld.access.<world>
    public String respawnWorld;
    public String netherLink;
    public String endLink;
    public double[] spawn;                  // x y z yaw pitch

    public WorldEntry(String name) {
        this.name = name;
    }

    public World world() {
        return Bukkit.getWorld(name);
    }

    public String display() {
        return alias == null || alias.isBlank() ? name : alias;
    }

    public Location spawnLocation(World w) {
        if (spawn != null && spawn.length == 5) return new Location(w, spawn[0], spawn[1], spawn[2], (float) spawn[3], (float) spawn[4]);
        return w.getSpawnLocation();
    }

    public void save(ConfigurationSection s) {
        s.set("environment", environment.name());
        s.set("type", type);
        s.set("seed", seed);
        s.set("generator", generator);
        s.set("structures", structures);
        s.set("builtin", builtin);
        s.set("auto-load", autoLoad);
        s.set("alias", alias);
        s.set("gamemode", gamemode == null ? null : gamemode.name());
        s.set("difficulty", difficulty == null ? null : difficulty.name());
        s.set("pvp", pvp);
        s.set("hunger", hunger);
        s.set("weather", weather);
        s.set("animals", animals);
        s.set("monsters", monsters);
        s.set("hidden", hidden);
        s.set("access-permission", access);
        s.set("respawn-world", respawnWorld);
        s.set("portals.nether", netherLink);
        s.set("portals.end", endLink);
        if (spawn != null) {
            s.set("spawn.x", spawn[0]); s.set("spawn.y", spawn[1]); s.set("spawn.z", spawn[2]);
            s.set("spawn.yaw", spawn[3]); s.set("spawn.pitch", spawn[4]);
        } else s.set("spawn", null);
    }

    public static WorldEntry load(String name, ConfigurationSection s) {
        WorldEntry e = new WorldEntry(name);
        e.environment = parseEnv(s.getString("environment", "NORMAL"));
        e.type = s.getString("type", "normal").toLowerCase(Locale.ROOT);
        e.seed = s.contains("seed") && s.get("seed") != null ? s.getLong("seed") : null;
        e.generator = s.getString("generator");
        e.structures = s.getBoolean("structures", true);
        e.builtin = s.getBoolean("builtin", false);
        e.autoLoad = s.getBoolean("auto-load", true);
        e.alias = s.getString("alias");
        e.gamemode = enumOrNull(GameMode.class, s.getString("gamemode"));
        e.difficulty = enumOrNull(Difficulty.class, s.getString("difficulty"));
        e.pvp = s.getBoolean("pvp", true);
        e.hunger = s.getBoolean("hunger", true);
        e.weather = s.getBoolean("weather", true);
        e.animals = s.getBoolean("animals", true);
        e.monsters = s.getBoolean("monsters", true);
        e.hidden = s.getBoolean("hidden", false);
        e.access = s.getBoolean("access-permission", false);
        e.respawnWorld = s.getString("respawn-world");
        e.netherLink = s.getString("portals.nether");
        e.endLink = s.getString("portals.end");
        if (s.isConfigurationSection("spawn")) {
            e.spawn = new double[]{s.getDouble("spawn.x"), s.getDouble("spawn.y"), s.getDouble("spawn.z"), s.getDouble("spawn.yaw"), s.getDouble("spawn.pitch")};
        }
        return e;
    }

    public static World.Environment parseEnv(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "normal", "overworld", "world" -> World.Environment.NORMAL;
            case "nether", "the_nether", "hell" -> World.Environment.NETHER;
            case "end", "the_end", "theend" -> World.Environment.THE_END;
            default -> null;
        };
    }

    public static <E extends Enum<E>> E enumOrNull(Class<E> c, String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("off")) return null;
        try {
            return Enum.valueOf(c, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
