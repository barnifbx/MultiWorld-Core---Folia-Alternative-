package net.flowsmp.multiworld.listener;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import net.flowsmp.multiworld.MultiWorldCore;
import net.flowsmp.multiworld.world.WorldEntry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Linked nether / end portals between worlds (Folia does not route portals of custom worlds).
 * Set up with /mw link nether|end &lt;from&gt; &lt;to&gt;. Worlds without a link keep vanilla behaviour.
 */
public final class PortalListener implements Listener {
    private final MultiWorldCore plugin;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> arrived = new ConcurrentHashMap<>();
    private final Map<UUID, long[]> standing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDenied = new ConcurrentHashMap<>();

    public PortalListener(MultiWorldCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInside(EntityInsideBlockEvent e) {
        Material type = e.getBlock().getType();
        if (type != Material.NETHER_PORTAL && type != Material.END_PORTAL) return;
        World from = e.getEntity().getWorld();
        WorldEntry we = plugin.worlds().get(from);
        if (we == null) return;
        String link = type == Material.NETHER_PORTAL ? we.netherLink : we.endLink;
        if (link == null || link.isBlank()) return;
        World to = Bukkit.getWorld(link);
        if (to == null) return;
        e.setCancelled(true); // never let vanilla route a linked portal
        if (!(e.getEntity() instanceof Player p)) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = arrived.get(id);
        if (until != null && until > now) {
            arrived.put(id, now + 1500); // still standing in the portal they arrived in
            return;
        }
        if (busy.contains(id)) return;
        if (type == Material.NETHER_PORTAL && p.getGameMode() != GameMode.CREATIVE) {
            long[] s = standing.computeIfAbsent(id, k -> new long[]{now, now});
            if (s[1] < now - 500) s[0] = now;
            s[1] = now;
            long delay = plugin.getConfig().getLong("portals.nether-delay-ticks", 80) * 50L;
            if (now - s[0] < delay) return;
        }
        WorldEntry target = plugin.worlds().get(to);
        if (target != null && !WorldRulesListener.canEnter(p, target)) {
            Long last = lastDenied.get(id);
            if (last == null || last < now - 3000) {
                lastDenied.put(id, now);
                plugin.msg().send(p, "no-access", "world", target.display());
            }
            return;
        }

        busy.add(id);
        standing.remove(id);
        CompletableFuture<Location> dest;
        if (type == Material.END_PORTAL) {
            dest = to.getEnvironment() == World.Environment.THE_END ? endPlatform(to) : CompletableFuture.completedFuture(plugin.worlds().spawnOf(to));
        } else {
            Location fromLoc = p.getLocation();
            double ratio = factor(from) / factor(to);
            dest = netherPortal(to, clamp(to, fromLoc.getX() * ratio), clamp(to, fromLoc.getZ() * ratio));
        }
        dest.whenComplete((loc, err) -> {
            if (err != null || loc == null) {
                busy.remove(id);
                if (err != null) plugin.getLogger().warning("Portal teleport failed: " + err);
                return;
            }
            p.getScheduler().run(plugin, t -> p.teleportAsync(loc).whenComplete((ok, ex) -> {
                busy.remove(id);
                arrived.put(id, System.currentTimeMillis() + 1500);
            }), () -> busy.remove(id));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        busy.remove(id);
        arrived.remove(id);
        standing.remove(id);
        lastDenied.remove(id);
    }

    private static double factor(World w) {
        return w.getEnvironment() == World.Environment.NETHER ? 8.0 : 1.0;
    }

    private static double clamp(World w, double v) {
        WorldBorder b = w.getWorldBorder();
        double half = Math.max(16, b.getSize() / 2 - 16);
        double c = 0; // border centers are almost always 0; keep it simple and safe
        return Math.max(c - half, Math.min(c + half, v));
    }

    private CompletableFuture<Void> loadArea(World w, int bx, int bz) {
        List<CompletableFuture<?>> l = new ArrayList<>();
        int cx = bx >> 4, cz = bz >> 4;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.add(w.getChunkAtAsync(cx + dx, cz + dz));
        return CompletableFuture.allOf(l.toArray(new CompletableFuture[0]));
    }

    private <T> CompletableFuture<T> onRegion(World w, int x, int z, java.util.function.Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        loadArea(w, x, z).whenComplete((v, err) -> Bukkit.getRegionScheduler().execute(plugin, w, x >> 4, z >> 4, () -> {
            try {
                f.complete(task.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }));
        return f;
    }

    private CompletableFuture<Location> endPlatform(World w) {
        return onRegion(w, 100, 0, () -> {
            for (int x = 98; x <= 102; x++) for (int z = -2; z <= 2; z++) {
                w.getBlockAt(x, 48, z).setType(Material.OBSIDIAN, false);
                for (int y = 49; y <= 51; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
            return new Location(w, 100.5, 49, 0.5, 90f, 0f);
        });
    }

    private CompletableFuture<Location> netherPortal(World w, double dx, double dz) {
        int x = (int) Math.floor(dx), z = (int) Math.floor(dz);
        return onRegion(w, x, z, () -> {
            Location found = findPortal(w, x, z);
            return found != null ? found : buildPortal(w, x, z);
        });
    }

    private int maxY(World w) {
        return w.getEnvironment() == World.Environment.NETHER ? Math.min(w.getMaxHeight(), 128) - 1 : w.getMaxHeight() - 1;
    }

    private Location findPortal(World w, int x, int z) {
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        int min = w.getMinHeight(), max = maxY(w);
        for (int ox = -16; ox <= 16; ox++) for (int oz = -16; oz <= 16; oz++) for (int y = min; y <= max; y++) {
            Block b = w.getBlockAt(x + ox, y, z + oz);
            if (b.getType() != Material.NETHER_PORTAL) continue;
            if (b.getRelative(0, -1, 0).getType() == Material.NETHER_PORTAL) continue; // bottom block only
            double d = ox * ox + oz * oz + (y - 64) * (y - 64) * 0.01;
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        if (best == null) return null;
        boolean axisX = !(best.getBlockData() instanceof Orientable o) || o.getAxis() == org.bukkit.Axis.X;
        int[][] fronts = axisX ? new int[][]{{0, 1}, {0, -1}} : new int[][]{{1, 0}, {-1, 0}};
        for (int[] f : fronts) {
            Block feet = best.getRelative(f[0], 0, f[1]);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                float yaw = f[1] == 1 ? 0f : f[1] == -1 ? 180f : f[0] == 1 ? -90f : 90f;
                Location l = feet.getLocation().add(0.5, 0, 0.5);
                l.setYaw(yaw);
                l.setPitch(0f);
                return l;
            }
        }
        return best.getLocation().add(0.5, 0, 0.5);
    }

    private Location buildPortal(World w, int x, int z) {
        int y = safeY(w, x, z);
        // clear a 4x4x4 room and lay an obsidian floor
        for (int ox = -1; ox <= 2; ox++) for (int oz = -1; oz <= 1; oz++) {
            w.getBlockAt(x + ox, y - 1, z + oz).setType(Material.OBSIDIAN, false);
            for (int oy = 0; oy <= 3; oy++) w.getBlockAt(x + ox, y + oy, z + oz).setType(Material.AIR, false);
        }
        // frame (4 wide, 5 tall) on the z plane
        for (int ox = -1; ox <= 2; ox++) {
            w.getBlockAt(x + ox, y - 1, z).setType(Material.OBSIDIAN, false);
            w.getBlockAt(x + ox, y + 3, z).setType(Material.OBSIDIAN, false);
        }
        for (int oy = 0; oy <= 2; oy++) {
            w.getBlockAt(x - 1, y + oy, z).setType(Material.OBSIDIAN, false);
            w.getBlockAt(x + 2, y + oy, z).setType(Material.OBSIDIAN, false);
        }
        Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
        portal.setAxis(org.bukkit.Axis.X);
        for (int ox = 0; ox <= 1; ox++) for (int oy = 0; oy <= 2; oy++) w.getBlockAt(x + ox, y + oy, z).setBlockData(portal, false);
        return new Location(w, x + 1.0, y, z + 1.5, 0f, 0f);
    }

    private int safeY(World w, int x, int z) {
        int min = w.getMinHeight() + 2, max = maxY(w) - 4;
        if (w.getEnvironment() == World.Environment.NETHER) {
            for (int y = 32; y <= Math.min(max, 110); y++) {
                if (w.getBlockAt(x, y - 1, z).getType().isSolid() && w.getBlockAt(x, y, z).isEmpty()
                        && w.getBlockAt(x, y + 1, z).isEmpty() && w.getBlockAt(x, y + 2, z).isEmpty()) return y;
            }
            return Math.max(min, Math.min(max, 70));
        }
        int top = w.getHighestBlockYAt(x, z) + 1;
        return Math.max(min, Math.min(max, top <= w.getMinHeight() + 1 ? 64 : top));
    }
}
