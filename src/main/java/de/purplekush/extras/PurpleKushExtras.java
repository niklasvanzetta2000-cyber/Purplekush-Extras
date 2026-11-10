package de.purplekush.extras;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PurpleKush-Extras
 *  1. Schoener Chat:  Name: Nachricht  (ohne spitze Klammern)
 *  2. Tabliste:       Name ☠ 5  (Tode, farbig nach Anzahl)
 *  3. Schlafen:       nur ein Drittel der Spieler muss schlafen
 */
public final class PurpleKushExtras extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("sleep.enabled", true)) {
            for (World world : Bukkit.getWorlds()) {
                applySleepPercentage(world);
            }
        }

        if (getConfig().getBoolean("tab.enabled", true)) {
            // Regelmaessig aktualisieren, damit die Anzeige immer stimmt
            Bukkit.getScheduler().runTaskTimer(this, this::refreshAllTabNames, 40L, 100L);
        }
        getLogger().info("PurpleKush-Extras aktiviert.");
    }

    // ------------------------------------------------------------------
    // 1. Chat
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        TextColor nameColor = parseColor(getConfig().getString("chat.name-color", "light_purple"), NamedTextColor.LIGHT_PURPLE);
        TextColor sepColor = parseColor(getConfig().getString("chat.separator-color", "gray"), NamedTextColor.GRAY);
        TextColor msgColor = parseColor(getConfig().getString("chat.message-color", "white"), NamedTextColor.WHITE);

        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.text()
                        .append(sourceDisplayName.colorIfAbsent(nameColor))
                        .append(Component.text(": ", sepColor))
                        .append(message.colorIfAbsent(msgColor))
                        .build()));
    }

    // ------------------------------------------------------------------
    // 2. Tabliste mit Toden
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> updateTabName(event.getPlayer()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Statistik wird erst nach dem Tod hochgezaehlt -> kurz warten
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(this, () -> updateTabName(player), 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> updateTabName(player), 5L);
    }

    private void refreshAllTabNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabName(player);
        }
    }

    private void updateTabName(Player player) {
        if (!player.isOnline() || !getConfig().getBoolean("tab.enabled", true)) {
            return;
        }
        int deaths = player.getStatistic(Statistic.DEATHS);
        String symbol = getConfig().getString("tab.symbol", "☠");

        Component name = player.displayName();
        player.playerListName(Component.text()
                .append(name)
                .append(Component.text(" " + symbol + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(deaths, deathColor(deaths)))
                .build());
    }

    private TextColor deathColor(int deaths) {
        if (deaths <= getConfig().getInt("tab.green-until", 4)) {
            return NamedTextColor.GREEN;
        }
        if (deaths <= getConfig().getInt("tab.yellow-until", 14)) {
            return NamedTextColor.YELLOW;
        }
        if (deaths <= getConfig().getInt("tab.gold-until", 29)) {
            return NamedTextColor.GOLD;
        }
        return NamedTextColor.RED;
    }

    // ------------------------------------------------------------------
    // 3. Schlafen: nur 1/3 der Spieler
    // ------------------------------------------------------------------

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (getConfig().getBoolean("sleep.enabled", true)) {
            applySleepPercentage(event.getWorld());
        }
    }

    /**
     * Setzt die Spielregel "playersSleepingPercentage" fuer die Welt.
     * Der Regelname hat sich in neuen Minecraft-Versionen geaendert, daher
     * werden beide Schreibweisen probiert.
     */
    private void applySleepPercentage(World world) {
        int percent = getConfig().getInt("sleep.percentage", 33);
        String dimension = switch (world.getEnvironment()) {
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };

        String[] ruleNames = {"players_sleeping_percentage", "playersSleepingPercentage"};
        boolean ok = false;
        for (String rule : ruleNames) {
            String command = "execute in " + dimension + " run gamerule " + rule + " " + percent;
            if (world.getEnvironment() == World.Environment.NORMAL
                    || world.getEnvironment() == World.Environment.NETHER
                    || world.getEnvironment() == World.Environment.THE_END) {
                ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
            if (ok) {
                break;
            }
        }
        if (ok) {
            getLogger().info("Schlaf-Anteil in Welt '" + world.getName() + "' auf " + percent + "% gesetzt.");
        } else {
            getLogger().warning("Konnte den Schlaf-Anteil fuer '" + world.getName() + "' nicht setzen. "
                    + "Bitte manuell: /gamerule players_sleeping_percentage " + percent);
        }
    }

    // ------------------------------------------------------------------

    private static TextColor parseColor(String value, TextColor fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (value.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(value);
            return hex != null ? hex : fallback;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase());
        return named != null ? named : fallback;
    }
}
