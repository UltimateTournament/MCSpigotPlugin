package co.tangia.spigot;

import co.tangia.sdk.InvalidLoginException;
import co.tangia.sdk.TangiaSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GameEventListener implements Listener {
    private final TangiaPlugin spigot;
    private static final Logger LOGGER = LoggerFactory.getLogger(GameEventListener.class.getName());

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
