package co.tangia.spigot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameEventListener implements Listener {
    private final TangiaPlugin spigot;

    public GameEventListener(TangiaPlugin spigot) {
        this.spigot = spigot;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (spigot.playerSDKs.get(id) != null) {
            return;
        }
        ModPersistenceData.PlayerSession session = ModPersistence.data.sessions().get(id);
        if (session != null) {
            try {
                spigot.login(event.getPlayer(), session.sessionToken());
                event.getPlayer().sendMessage("We've logged you back into your Tangia account");
            } catch (Exception e) {
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
