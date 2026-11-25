package net.flowsmp.multiworld.listener;

import net.flowsmp.multiworld.MultiWorldCore;
import net.flowsmp.multiworld.world.WorldEntry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.EnumSet;
import java.util.Set;

/** Per-world rules: gamemode, access, PvP, hunger, weather, mob spawning, respawn world, join spawn. */
public final class WorldRulesListener implements Listener {
    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN, CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.MOUNT, CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.TRAP);
    private final MultiWorldCore plugin;

    public WorldRulesListener(MultiWorldCore plugin) {
        this.plugin = plugin;
    }

    private WorldEntry entry(World w) {
        return plugin.worlds().get(w);
    }

    // ───────── access + gamemode
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to.getWorld() == null || to.getWorld().equals(e.getFrom().getWorld())) return;
        WorldEntry we = entry(to.getWorld());
        if (we != null && !canEnter(e.getPlayer(), we)) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "no-access", "world", we.display());
        }
    }

    public static boolean canEnter(Player p, WorldEntry we) {
        return !we.access || p.hasPermission("multiworld.access." + we.name) || p.hasPermission("multiworld.access.*");
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        enforceGamemode(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String joinWorld = plugin.getConfig().getString("join.world", "");
        boolean firstOnly = plugin.getConfig().getBoolean("join.first-join-only", true);
        if (joinWorld != null && !joinWorld.isBlank() && (!firstOnly || !p.hasPlayedBefore())) {
            World w = plugin.getServer().getWorld(joinWorld);
            if (w != null) {
                Location target = plugin.worlds().spawnOf(w);
                p.getScheduler().runDelayed(plugin, t -> p.teleportAsync(target), null, 2L);
                return;
            }
        }
        enforceGamemode(p);
    }

    private void enforceGamemode(Player p) {
        WorldEntry we = entry(p.getWorld());
        if (we == null || we.gamemode == null || p.hasPermission("multiworld.gamemode.bypass")) return;
        p.getScheduler().run(plugin, t -> {
            if (p.getGameMode() != we.gamemode) p.setGameMode(we.gamemode);
        }, null);
    }

    // ───────── pvp
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Entity d = e.getDamager();
        Player attacker = d instanceof Player p ? p : d instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || attacker.equals(e.getEntity())) return;
        WorldEntry we = entry(e.getEntity().getWorld());
        if (we != null && !we.pvp) e.setCancelled(true);
    }

    // ───────── hunger
    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WorldEntry we = entry(p.getWorld());
        if (we != null && !we.hunger && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    // ───────── weather
    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        WorldEntry we = entry(e.getWorld());
        if (we != null && !we.weather && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        WorldEntry we = entry(e.getWorld());
        if (we != null && !we.weather && e.toThunderState()) e.setCancelled(true);
    }

    // ───────── spawning
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!NATURAL.contains(e.getSpawnReason())) return;
        WorldEntry we = entry(e.getLocation().getWorld());
        if (we == null) return;
        if (!we.monsters && e.getEntity() instanceof Monster) e.setCancelled(true);
        else if (!we.animals && e.getEntity() instanceof Animals) e.setCancelled(true);
    }

    /** Bed / anchor check that works on every supported API version without loading chunks. */
    private static boolean hasRespawnPoint(Player p) {
        for (String m : new String[]{"getPotentialRespawnLocation", "getRespawnLocation"}) {
            try {
                java.lang.reflect.Method method;
                Object loc;
                try {
                    method = p.getClass().getMethod(m, boolean.class);
                    loc = method.invoke(p, false);
                } catch (NoSuchMethodException ex) {
                    method = p.getClass().getMethod(m);
                    if (m.equals("getRespawnLocation")) continue; // would load and validate chunks
                    loc = method.invoke(p);
                }
                return loc != null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    // ───────── respawn (Folia's respawn API is unreliable, so players are moved right after respawning)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        World died = p.getWorld();
        WorldEntry we = entry(died);
        if (we == null) return;
        World target;
        if (we.respawnWorld != null && !we.respawnWorld.isBlank()) target = plugin.getServer().getWorld(we.respawnWorld);
        else if (!we.builtin) target = died;
        else return;
        if (target == null) return;
        if (plugin.getConfig().getBoolean("respawn.use-bed", true) && hasRespawnPoint(p)) return;
        Location loc = plugin.worlds().spawnOf(target);
        p.getScheduler().runAtFixedRate(plugin, task -> {
            if (p.isDead()) return;
            task.cancel();
            p.teleportAsync(loc);
        }, null, 1L, 1L);
    }
}
