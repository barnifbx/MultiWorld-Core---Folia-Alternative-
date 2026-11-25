package net.flowsmp.multiworld.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.flowsmp.multiworld.MultiWorldCore;
import net.flowsmp.multiworld.listener.WorldRulesListener;
import net.flowsmp.multiworld.util.Msg;
import net.flowsmp.multiworld.world.FoliaWorldLoader;
import net.flowsmp.multiworld.world.WorldEntry;
import net.flowsmp.multiworld.world.WorldManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MultiWorldCommand implements BasicCommand {
    private static final String ADMIN = "multiworld.admin";
    private static final List<String> SUBS = List.of("help", "list", "info", "who", "tp", "spawn", "setspawn", "create", "import", "load",
            "remove", "delete", "confirm", "modify", "gamerule", "link", "unlink", "reload");
    private static final List<String> PROPS = List.of("alias", "gamemode", "difficulty", "pvp", "hunger", "weather", "animals", "monsters",
            "hidden", "access-permission", "respawn-world", "auto-load");

    private final MultiWorldCore plugin;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(String world, long until) {
    }

    public MultiWorldCommand(MultiWorldCore plugin) {
        this.plugin = plugin;
    }

    private Msg msg() {
        return plugin.msg();
    }

    private WorldManager worlds() {
        return plugin.worlds();
    }

    @Override
    public void execute(CommandSourceStack src, String[] args) {
        CommandSender s = src.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String[] a = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (sub) {
                case "list", "ls" -> list(s);
                case "info" -> info(s, a);
                case "who" -> who(s, a);
                case "tp", "teleport" -> tp(s, a);
                case "spawn" -> spawn(s, a);
                case "setspawn" -> setSpawn(s);
                case "create" -> create(s, a, false);
                case "import" -> create(s, a, true);
                case "load" -> load(s, a);
                case "remove" -> remove(s, a);
                case "delete" -> delete(s, a);
                case "confirm" -> confirm(s);
                case "modify", "set" -> modify(s, a);
                case "gamerule" -> gamerule(s, a);
                case "link" -> link(s, a);
                case "unlink" -> unlink(s, a);
                case "reload" -> {
                    if (!admin(s)) return;
                    plugin.reloadAll();
                    msg().send(s, "reload");
                }
                default -> help(s);
            }
        } catch (Exception ex) {
            s.sendMessage(Msg.color("&cError: " + ex.getMessage()));
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Command failed", ex);
        }
    }

    // ───────── helpers
    private boolean admin(CommandSender s) {
        if (s.hasPermission(ADMIN)) return true;
        msg().send(s, "no-permission");
        return false;
    }

    private boolean perm(CommandSender s, String p) {
        if (s.hasPermission(p) || s.hasPermission(ADMIN)) return true;
        msg().send(s, "no-permission");
        return false;
    }

    private void usage(CommandSender s, String u) {
        msg().send(s, "usage", "usage", u);
    }

    private WorldEntry entryOrMsg(CommandSender s, String name) {
        WorldEntry e = worlds().get(name);
        if (e == null) msg().send(s, "unknown-world", "world", name);
        return e;
    }

    private World loadedOrMsg(CommandSender s, String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) msg().send(s, "unknown-world", "world", name);
        return w;
    }

    private void teleport(Player p, Location loc) {
        p.getScheduler().run(plugin, t -> p.teleportAsync(loc), null);
    }

    private static String onOff(boolean b) {
        return b ? "ᴏɴ" : "ᴏꜰꜰ";
    }

    // ───────── help / list / info / who
    private void help(CommandSender s) {
        s.sendMessage(msg().get("help-title"));
        String[][] lines = {
                {"/mw list", "All worlds"}, {"/mw info [world]", "World settings"}, {"/mw who [world]", "Players in a world"},
                {"/mw tp <world> [player]", "Teleport to a world"}, {"/mw spawn [player]", "Go to this world's spawn"},
                {"/mw setspawn", "Set this world's spawn"},
                {"/mw create <name> <normal|nether|end> [-t type] [-s seed] [-g generator] [-n]", "Create a world (-n = no structures)"},
                {"/mw import <name> <normal|nether|end> [-g generator]", "Load an uploaded world folder"},
                {"/mw load <world>", "Load a world that is not loaded"}, {"/mw remove <world>", "Stop managing a world (keeps files)"},
                {"/mw delete <world>", "Delete a world on the next restart"},
                {"/mw modify <world> <property> <value>", "Change a world setting"},
                {"/mw gamerule <world> <rule> <value>", "Set a gamerule"},
                {"/mw link <nether|end> <from> <to>", "Link portals between worlds"}, {"/mw unlink <nether|end> <world>", "Remove a portal link"},
                {"/mw reload", "Reload config, messages and worlds.yml"}};
        for (String[] l : lines) s.sendMessage(msg().get("help-line", "cmd", l[0], "desc", l[1]));
    }

    private void list(CommandSender s) {
        if (!perm(s, "multiworld.command.list")) return;
        boolean seeHidden = s.hasPermission("multiworld.seehidden") || s.hasPermission(ADMIN);
        List<WorldEntry> shown = worlds().all().stream().filter(e -> seeHidden || !e.hidden).toList();
        s.sendMessage(msg().get("list-title", "count", shown.size()));
        for (WorldEntry e : shown) {
            World w = e.world();
            String status = (w == null ? msg().raw("list-unloaded") : "") + (e.hidden ? msg().raw("list-hidden") : "");
            Component line = msg().get("list-line", "world", e.display(), "env", envName(e.environment),
                    "players", w == null ? 0 : w.getPlayerCount(), "status", status);
            if (w != null) line = msg().clickable(line, "/mw tp " + e.name, "click-hover-tp", "world", e.display());
            s.sendMessage(line);
        }
    }

    private static String envName(World.Environment env) {
        return switch (env) {
            case NETHER -> "ɴᴇᴛʜᴇʀ";
            case THE_END -> "ᴇɴᴅ";
            default -> "ɴᴏʀᴍᴀʟ";
        };
    }

    private String currentWorld(CommandSender s, String[] a) {
        if (a.length > 0) return a[0];
        if (s instanceof Player p) return p.getWorld().getName();
        return null;
    }

    private void info(CommandSender s, String[] a) {
        if (!perm(s, "multiworld.command.info")) return;
        String name = currentWorld(s, a);
        if (name == null) {
            usage(s, "/mw info <world>");
            return;
        }
        WorldEntry e = entryOrMsg(s, name);
        if (e == null) return;
        World w = e.world();
        s.sendMessage(msg().get("info-title", "world", e.display()));
        Object[][] rows = {
                {"ɴᴀᴍᴇ", e.name}, {"ʟᴏᴀᴅᴇᴅ", onOff(w != null)}, {"ᴇɴᴠɪʀᴏɴᴍᴇɴᴛ", envName(e.environment)},
                {"ᴛʏᴘᴇ", e.builtin ? "server world" : e.type + (e.generator != null ? " (" + e.generator + ")" : "")},
                {"ѕᴇᴇᴅ", w != null ? w.getSeed() : e.seed}, {"ᴘʟᴀʏᴇʀѕ", w != null ? w.getPlayerCount() : 0},
                {"ɢᴀᴍᴇᴍᴏᴅᴇ", e.gamemode == null ? "not enforced" : e.gamemode.name().toLowerCase(Locale.ROOT)},
                {"ᴅɪꜰꜰɪᴄᴜʟᴛʏ", e.difficulty != null ? e.difficulty.name().toLowerCase(Locale.ROOT) : w != null ? w.getDifficulty().name().toLowerCase(Locale.ROOT) : "-"},
                {"ᴘᴠᴘ", onOff(e.pvp)}, {"ʜᴜɴɢᴇʀ", onOff(e.hunger)}, {"ᴡᴇᴀᴛʜᴇʀ", onOff(e.weather)},
                {"ᴀɴɪᴍᴀʟѕ", onOff(e.animals)}, {"ᴍᴏɴѕᴛᴇʀѕ", onOff(e.monsters)}, {"ʜɪᴅᴅᴇɴ", onOff(e.hidden)},
                {"ᴀᴄᴄᴇѕѕ ᴘᴇʀᴍɪѕѕɪᴏɴ", e.access ? "multiworld.access." + e.name : "ᴏꜰꜰ"},
                {"ʀᴇѕᴘᴀᴡɴ ᴡᴏʀʟᴅ", e.respawnWorld == null ? "-" : e.respawnWorld},
                {"ɴᴇᴛʜᴇʀ ᴘᴏʀᴛᴀʟѕ", e.netherLink == null ? "vanilla" : "→ " + e.netherLink},
                {"ᴇɴᴅ ᴘᴏʀᴛᴀʟѕ", e.endLink == null ? "vanilla" : "→ " + e.endLink},
                {"ᴀᴜᴛᴏ-ʟᴏᴀᴅ", onOff(e.autoLoad || e.builtin)}};
        for (Object[] r : rows) s.sendMessage(msg().get("info-line", "label", r[0], "value", r[1] == null ? "random" : r[1]));
    }

    private void who(CommandSender s, String[] a) {
        if (!perm(s, "multiworld.command.who")) return;
        String name = currentWorld(s, a);
        if (name == null) {
            usage(s, "/mw who <world>");
            return;
        }
        World w = loadedOrMsg(s, name);
        if (w == null) return;
        List<String> names = Bukkit.getOnlinePlayers().stream().filter(p -> p.getWorld().equals(w)).map(Player::getName).sorted().toList();
        WorldEntry e = worlds().get(w);
        s.sendMessage(msg().get("who-title", "world", e != null ? e.display() : w.getName(), "count", names.size()));
        s.sendMessage(names.isEmpty() ? msg().get("who-none") : msg().get("who-line", "players", String.join("&7, &f", names)));
    }

    // ───────── tp / spawn
    private void tp(CommandSender s, String[] a) {
        if (!perm(s, "multiworld.command.tp")) return;
        if (a.length == 0) {
            usage(s, "/mw tp <world> [player]");
            return;
        }
        World w = loadedOrMsg(s, a[0]);
        if (w == null) return;
        Player target;
        if (a.length > 1) {
            if (!perm(s, "multiworld.command.tp.others")) return;
            target = Bukkit.getPlayerExact(a[1]);
            if (target == null) {
                msg().send(s, "unknown-player", "player", a[1]);
                return;
            }
        } else if (s instanceof Player p) target = p;
        else {
            msg().send(s, "players-only");
            return;
        }
        WorldEntry e = worlds().get(w);
        String display = e != null ? e.display() : w.getName();
        if (e != null && target == s && !WorldRulesListener.canEnter(target, e)) {
            msg().send(s, "no-access", "world", display);
            return;
        }
        teleport(target, worlds().spawnOf(w));
        if (target == s) msg().send(s, "teleport", "world", display);
        else msg().send(s, "teleport-other", "player", target.getName(), "world", display);
    }

    private void spawn(CommandSender s, String[] a) {
        if (!perm(s, "multiworld.command.spawn")) return;
        Player target;
        if (a.length > 0) {
            if (!perm(s, "multiworld.command.spawn.others")) return;
            target = Bukkit.getPlayerExact(a[0]);
            if (target == null) {
                msg().send(s, "unknown-player", "player", a[0]);
                return;
            }
        } else if (s instanceof Player p) target = p;
        else {
            msg().send(s, "players-only");
            return;
        }
        World w = target.getWorld();
        WorldEntry e = worlds().get(w);
        teleport(target, worlds().spawnOf(w));
        msg().send(s, target == s ? "teleport" : "teleport-other", "player", target.getName(), "world", e != null ? e.display() : w.getName());
    }

    private void setSpawn(CommandSender s) {
        if (!admin(s)) return;
        if (!(s instanceof Player p)) {
            msg().send(s, "players-only");
            return;
        }
        Location l = p.getLocation();
        WorldEntry e = worlds().get(l.getWorld());
        if (e == null) {
            msg().send(s, "unknown-world", "world", l.getWorld().getName());
            return;
        }
        e.spawn = new double[]{l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()};
        worlds().save();
        worlds().applySettings(e);
        msg().send(s, "setspawn", "world", e.display());
    }

    // ───────── create / import / load
    private void create(CommandSender s, String[] a, boolean isImport) {
        if (!admin(s)) return;
        if (a.length < 2) {
            usage(s, isImport ? "/mw import <name> <normal|nether|end> [-g generator]" : "/mw create <name> <normal|nether|end> [-t type] [-s seed] [-g generator] [-n]");
            return;
        }
        String name = a[0].toLowerCase(Locale.ROOT);
        if (!WorldManager.NAME.matcher(name).matches()) {
            msg().send(s, "invalid-name");
            return;
        }
        if (worlds().get(name) != null || Bukkit.getWorld(name) != null) {
            msg().send(s, "already-exists", "world", name);
            return;
        }
        if (worlds().isPendingDelete(name)) {
            msg().send(s, "already-exists", "world", name);
            return;
        }
        World.Environment env = WorldEntry.parseEnv(a[1]);
        if (env == null) {
            msg().send(s, "invalid-env", "value", a[1]);
            return;
        }
        WorldEntry e = new WorldEntry(name);
        e.environment = env;
        for (int i = 2; i < a.length; i++) {
            String f = a[i].toLowerCase(Locale.ROOT);
            String v = i + 1 < a.length ? a[i + 1] : null;
            switch (f) {
                case "-t" -> {
                    if (v == null || !WorldManager.TYPES.contains(v.toLowerCase(Locale.ROOT))) {
                        msg().send(s, "invalid-type", "value", v, "types", String.join(", ", WorldManager.TYPES));
                        return;
                    }
                    e.type = v.toLowerCase(Locale.ROOT);
                    i++;
                }
                case "-s" -> {
                    if (v == null) return;
                    try {
                        e.seed = Long.parseLong(v);
                    } catch (NumberFormatException ex) {
                        e.seed = (long) v.hashCode();
                    }
                    i++;
                }
                case "-g" -> {
                    if (v == null) return;
                    e.generator = v;
                    i++;
                }
                case "-n" -> e.structures = false;
                default -> {
                }
            }
        }
        if (isImport && !folderExists(name)) {
            msg().send(s, "import-missing", "world", name);
            return;
        }
        worlds().put(e);
        worlds().save();
        startLoad(s, e);
    }

    private boolean folderExists(String name) {
        if (new File(Bukkit.getWorldContainer(), name + "/level.dat").exists() || new File(Bukkit.getWorldContainer(), name + "/region").isDirectory()) return true;
        try {
            return Files.isDirectory(FoliaWorldLoader.dimensionPath(name));
        } catch (Throwable t) {
            return false;
        }
    }

    private void startLoad(CommandSender s, WorldEntry e) {
        if (!plugin.getConfig().getBoolean("runtime-loading", true) || !FoliaWorldLoader.RUNTIME_SAFE) {
            msg().send(s, "create-restart", "world", e.name);
            return;
        }
        msg().send(s, "create-start", "world", e.name);
        worlds().load(e).whenComplete((w, err) -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                msg().send(s, "create-failed", "world", e.name, "error", c.getClass().getSimpleName());
                return;
            }
            s.sendMessage(msg().clickable(msg().prefixed("create-done", "world", e.display()), "/mw tp " + e.name, "click-hover-tp", "world", e.display()));
        });
    }

    private void load(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length == 0) {
            usage(s, "/mw load <world>");
            return;
        }
        WorldEntry e = entryOrMsg(s, a[0]);
        if (e == null) return;
        if (e.world() != null) {
            msg().send(s, "already-loaded", "world", e.name);
            return;
        }
        e.autoLoad = true;
        worlds().save();
        startLoad(s, e);
    }

    // ───────── remove / delete
    private boolean isMain(String name) {
        World main = worlds().mainWorld();
        return main != null && main.getName().equalsIgnoreCase(name);
    }

    private void remove(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length == 0) {
            usage(s, "/mw remove <world>");
            return;
        }
        WorldEntry e = entryOrMsg(s, a[0]);
        if (e == null) return;
        if (isMain(e.name) || e.builtin) {
            msg().send(s, "cannot-delete-main");
            return;
        }
        worlds().remove(e.name);
        worlds().save();
        msg().send(s, "remove-done", "world", e.name);
    }

    private void delete(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length == 0) {
            usage(s, "/mw delete <world>");
            return;
        }
        WorldEntry e = entryOrMsg(s, a[0]);
        if (e == null) return;
        if (isMain(e.name) || e.builtin) {
            msg().send(s, "cannot-delete-main");
            return;
        }
        pending.put(key(s), new Pending(e.name, System.currentTimeMillis() + 15_000));
        s.sendMessage(msg().clickable(msg().prefixed("delete-confirm", "world", e.name), "/mw confirm", "click-hover-confirm"));
    }

    private static String key(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId().toString() : "console:" + s.getName();
    }

    private void confirm(CommandSender s) {
        if (!admin(s)) return;
        Pending p = pending.remove(key(s));
        if (p == null || p.until() < System.currentTimeMillis()) {
            msg().send(s, "confirm-nothing");
            return;
        }
        WorldEntry e = worlds().get(p.world());
        if (e == null) return;
        World w = e.world();
        if (w != null) worlds().evacuate(w);
        // unlink every world that pointed here
        for (WorldEntry o : worlds().all()) {
            if (e.name.equals(o.netherLink)) o.netherLink = null;
            if (e.name.equals(o.endLink)) o.endLink = null;
            if (e.name.equals(o.respawnWorld)) o.respawnWorld = null;
        }
        worlds().remove(e.name);
        worlds().markDelete(e.name);
        worlds().save();
        msg().send(s, "delete-done", "world", e.name);
    }

    // ───────── modify
    private void modify(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) {
            usage(s, "/mw modify <world> <property> <value>");
            return;
        }
        WorldEntry e = entryOrMsg(s, a[0]);
        if (e == null) return;
        String prop = a[1].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        Boolean bool = switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "allow" -> true;
            case "false", "off", "no", "deny" -> false;
            default -> null;
        };
        boolean ok = true;
        switch (prop) {
            case "alias" -> e.alias = value.equalsIgnoreCase("none") ? null : value;
            case "gamemode", "mode" -> {
                GameMode gm = WorldEntry.enumOrNull(GameMode.class, value);
                if (gm == null && !value.equalsIgnoreCase("none")) ok = false;
                else e.gamemode = gm;
            }
            case "difficulty", "diff" -> {
                Difficulty d = WorldEntry.enumOrNull(Difficulty.class, value);
                if (d == null && !value.equalsIgnoreCase("none")) ok = false;
                else e.difficulty = d;
            }
            case "pvp" -> ok = setBool(bool, v -> e.pvp = v);
            case "hunger" -> ok = setBool(bool, v -> e.hunger = v);
            case "weather" -> ok = setBool(bool, v -> e.weather = v);
            case "animals" -> ok = setBool(bool, v -> e.animals = v);
            case "monsters" -> ok = setBool(bool, v -> e.monsters = v);
            case "hidden" -> ok = setBool(bool, v -> e.hidden = v);
            case "access-permission", "access" -> ok = setBool(bool, v -> e.access = v);
            case "auto-load", "autoload" -> ok = setBool(bool, v -> e.autoLoad = v);
            case "respawn-world", "respawn" -> {
                if (value.equalsIgnoreCase("none")) e.respawnWorld = null;
                else if (worlds().get(value) == null) ok = false;
                else e.respawnWorld = worlds().get(value).name;
            }
            default -> {
                msg().send(s, "modify-unknown", "properties", String.join(", ", PROPS));
                return;
            }
        }
        if (!ok) {
            msg().send(s, "modify-bad-value", "value", value, "property", prop);
            return;
        }
        worlds().save();
        worlds().applySettings(e);
        if (prop.startsWith("game") || prop.equals("mode")) enforceAll(e);
        msg().send(s, "modify-done", "property", prop, "world", e.display(), "value", value);
    }

    private interface BoolSetter {
        void set(boolean v);
    }

    private static boolean setBool(Boolean b, BoolSetter setter) {
        if (b == null) return false;
        setter.set(b);
        return true;
    }

    private void enforceAll(WorldEntry e) {
        if (e.gamemode == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getScheduler().run(plugin, t -> {
                if (p.getWorld().getName().equalsIgnoreCase(e.name) && !p.hasPermission("multiworld.gamemode.bypass")) p.setGameMode(e.gamemode);
            }, null);
        }
    }

    // ───────── gamerule
    @SuppressWarnings({"unchecked", "deprecation"})
    private void gamerule(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) {
            usage(s, "/mw gamerule <world> <rule> <value>");
            return;
        }
        World w = loadedOrMsg(s, a[0]);
        if (w == null) return;
        GameRule<?> rule = GameRule.getByName(a[1]);
        if (rule == null) {
            msg().send(s, "gamerule-unknown", "rule", a[1]);
            return;
        }
        Object value;
        if (rule.getType() == Boolean.class) {
            if (!a[2].equalsIgnoreCase("true") && !a[2].equalsIgnoreCase("false")) {
                msg().send(s, "modify-bad-value", "value", a[2], "property", a[1]);
                return;
            }
            value = Boolean.parseBoolean(a[2]);
        } else {
            try {
                value = Integer.parseInt(a[2]);
            } catch (NumberFormatException ex) {
                msg().send(s, "modify-bad-value", "value", a[2], "property", a[1]);
                return;
            }
        }
        Object v = value;
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> w.setGameRule((GameRule<Object>) rule, v));
        msg().send(s, "gamerule-done", "rule", a[1], "world", w.getName(), "value", a[2]);
    }

    // ───────── portal links
    private void link(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3 || !(a[0].equalsIgnoreCase("nether") || a[0].equalsIgnoreCase("end"))) {
            usage(s, "/mw link <nether|end> <from> <to>");
            return;
        }
        WorldEntry from = entryOrMsg(s, a[1]);
        if (from == null) return;
        WorldEntry to = entryOrMsg(s, a[2]);
        if (to == null) return;
        boolean nether = a[0].equalsIgnoreCase("nether");
        if (nether) {
            from.netherLink = to.name;
            to.netherLink = from.name;
        } else {
            from.endLink = to.name;
            if (to.environment == World.Environment.THE_END) to.endLink = from.name;
        }
        worlds().save();
        msg().send(s, "link-done", "type", nether ? "Nether" : "End", "from", from.display(), "to", to.display());
    }

    private void unlink(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2 || !(a[0].equalsIgnoreCase("nether") || a[0].equalsIgnoreCase("end"))) {
            usage(s, "/mw unlink <nether|end> <world>");
            return;
        }
        WorldEntry e = entryOrMsg(s, a[1]);
        if (e == null) return;
        if (a[0].equalsIgnoreCase("nether")) e.netherLink = null;
        else e.endLink = null;
        worlds().save();
        msg().send(s, "unlink-done", "type", a[0].equalsIgnoreCase("nether") ? "Nether" : "End", "world", e.display());
    }

    // ───────── tab completion
    @Override
    public Collection<String> suggest(CommandSourceStack src, String[] args) {
        CommandSender s = src.getSender();
        boolean admin = s.hasPermission(ADMIN);
        List<String> worldNames = worlds().names();
        List<String> loaded = Bukkit.getWorlds().stream().map(World::getName).toList();
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            out.addAll(admin ? SUBS : List.of("help", "list", "info", "who", "tp", "spawn"));
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            int i = args.length - 1;
            switch (sub) {
                case "tp", "teleport" -> out.addAll(i == 1 ? loaded : i == 2 ? players : List.of());
                case "spawn" -> out.addAll(i == 1 ? players : List.of());
                case "info", "who", "load", "remove", "delete" -> out.addAll(i == 1 ? worldNames : List.of());
                case "create", "import" -> {
                    if (i == 1) out.add("<name>");
                    else if (i == 2) out.addAll(List.of("normal", "nether", "end"));
                    else if (args[i - 1].equalsIgnoreCase("-t")) out.addAll(WorldManager.TYPES);
                    else if (args[i - 1].equalsIgnoreCase("-s")) out.add("<seed>");
                    else if (args[i - 1].equalsIgnoreCase("-g")) out.add("<Plugin:id>");
                    else out.addAll(sub.equals("import") ? List.of("-g") : List.of("-t", "-s", "-g", "-n"));
                }
                case "modify", "set" -> {
                    if (i == 1) out.addAll(worldNames);
                    else if (i == 2) out.addAll(PROPS);
                    else if (i == 3) out.addAll(valuesFor(args[2].toLowerCase(Locale.ROOT), worldNames));
                }
                case "gamerule" -> {
                    if (i == 1) out.addAll(loaded);
                    else if (i == 2) out.addAll(ruleNames());
                    else if (i == 3) out.addAll(List.of("true", "false"));
                }
                case "link" -> {
                    if (i == 1) out.addAll(List.of("nether", "end"));
                    else if (i <= 3) out.addAll(worldNames);
                }
                case "unlink" -> {
                    if (i == 1) out.addAll(List.of("nether", "end"));
                    else if (i == 2) out.addAll(worldNames);
                }
                default -> {
                }
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }

    @SuppressWarnings("deprecation")
    private static List<String> ruleNames() {
        return Arrays.stream(GameRule.values()).map(GameRule::getName).sorted().toList();
    }

    private static List<String> valuesFor(String prop, List<String> worldNames) {
        return switch (prop) {
            case "gamemode", "mode" -> List.of("survival", "creative", "adventure", "spectator", "none");
            case "difficulty", "diff" -> List.of("peaceful", "easy", "normal", "hard", "none");
            case "respawn-world", "respawn" -> Stream.concat(worldNames.stream(), Stream.of("none")).toList();
            case "alias" -> List.of("<name>", "none");
            default -> List.of("true", "false");
        };
    }
}
