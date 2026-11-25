package net.flowsmp.multiworld.util;

import net.flowsmp.multiworld.MultiWorldCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Messages from messages.yml, with &-codes and &#RRGGBB hex colors. */
public final class Msg {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();
    private final MultiWorldCore plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    public Msg(MultiWorldCore plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        var in = plugin.getResource("messages.yml");
        if (in != null) defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public String raw(String key, Object... ph) {
        String s = cfg.getString(key, defaults.getString(key, key));
        for (int i = 0; i + 1 < ph.length; i += 2) s = s.replace("{" + ph[i] + "}", String.valueOf(ph[i + 1]));
        return s;
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s);
    }

    public Component get(String key, Object... ph) {
        return color(raw(key, ph));
    }

    public Component prefixed(String key, Object... ph) {
        return color(raw("prefix") + raw(key, ph));
    }

    public void send(CommandSender to, String key, Object... ph) {
        to.sendMessage(prefixed(key, ph));
    }

    /** A clickable line: hover text from messages.yml key "click-hover-…". */
    public Component clickable(Component text, String command, String hoverKey, Object... ph) {
        return text.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(get(hoverKey, ph)));
    }
}
