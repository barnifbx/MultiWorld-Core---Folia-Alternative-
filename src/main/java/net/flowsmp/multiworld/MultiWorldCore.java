package net.flowsmp.multiworld;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.flowsmp.multiworld.command.MultiWorldCommand;
import net.flowsmp.multiworld.listener.PortalListener;
import net.flowsmp.multiworld.listener.WorldRulesListener;
import net.flowsmp.multiworld.util.Msg;
import net.flowsmp.multiworld.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * MultiWorld-Core — a Multiverse-Core style world manager for Folia.
 * Created by the FlowSMP Team AKA FlowStudios.
 */
public final class MultiWorldCore extends JavaPlugin {
    private WorldManager worlds;
    private Msg msg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        msg = new Msg(this);
        msg.reload();
        worlds = new WorldManager(this);
        worlds.read();
        worlds.startup();

        getServer().getPluginManager().registerEvents(new WorldRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        MultiWorldCommand cmd = new MultiWorldCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e ->
                e.registrar().register("multiworld", "MultiWorld-Core world manager", List.of("mw", "mwc", "mv"), cmd));

        getLogger().info("MultiWorld-Core enabled — managing " + worlds.all().size() + " worlds. Created by the FlowSMP Team AKA FlowStudios");
    }

    @Override
    public void onDisable() {
        if (worlds != null) worlds.save();
    }

    public void reloadAll() {
        reloadConfig();
        msg.reload();
        worlds.read();
        worlds.all().forEach(worlds::applySettings);
    }

    public WorldManager worlds() {
        return worlds;
    }

    public Msg msg() {
        return msg;
    }
}
