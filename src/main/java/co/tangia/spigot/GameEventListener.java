package co.tangia.spigot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.slf4j.LoggerFactory;

public class GameEventListener implements Listener {
    private final TangiaPlugin spigot;

    public GameEventListener(TangiaPlugin spigot) {
        this.spigot = spigot;
    }

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GameEventListener.class.getCanonicalName());

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (spigot.playerSDKs.get(id) != null) {
            return;
        }
        ModPersistenceData.PlayerSession session = ModPersistence.data.sessions().get(id);
        if (session != null) {
            try {
                spigot.restoreSession(event.getPlayer(), session.sessionToken());
                event.getPlayer().sendMessage("We've logged you back into your Tangia account");
            } catch (Exception e) {
                LOGGER.error("failed to use persisted session", e);
                event.getPlayer().sendMessage("We couldn't log you back into your Tangia account!");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        spigot.logout(player, false);
    }
}
