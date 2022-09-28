package co.tangia.spigot;

import co.tangia.sdk.ModPersistence;
import co.tangia.sdk.ModPersistenceData;
import co.tangia.sdk.TangiaSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class GameEventListener implements Listener {
    private final TangiaPlugin spigot;
    public GameEventListener(TangiaPlugin spigot) {
        this.spigot = spigot;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (spigot.playerSDKs.get(id) != null) {
            return;
        }
        ModPersistenceData.PlayerSession session = ModPersistence.data.sessions().get(id);
        if (session != null) {
            TangiaSDK sdk = new TangiaSDK(spigot.gameID, "0.0.1", spigot.tangiaUrl, eventHandler);
            sdk.setSessionKey(session.sessionToken());
            spigot.playerSDKs.put(id, sdk);
            sdk.startEventPolling();
            System.out.println("Tangia session restored for Player with UUID {} " + id);
            event.getPlayer().sendMessage("We've logged you back into your Tangia account");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        spigot.logout(player, false);
    }
}
