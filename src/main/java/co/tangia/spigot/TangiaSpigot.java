package co.tangia.spigot;

import co.tangia.sdk.InvalidLoginException;
import co.tangia.sdk.ModPersistence;
import co.tangia.sdk.ModPersistenceData;
import co.tangia.sdk.TangiaSDK;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TangiaSpigot extends JavaPlugin {
    public final String tangiaUrl = "STAGING".equals(System.getenv("TANGIA_ENV")) ? TangiaSDK.STAGING_URL : TangiaSDK.PROD_URL;
    public final String gameID = System.getenv("GAME_ID") == null ? TangiaSDK.GAME_ID : System.getenv("GAME_ID");

    public final Map<UUID, TangiaSDK> playerSDKs = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        System.out.println("Tangia plugin starting");
        this.getCommand("tangia").setExecutor(new TangiaCommand(this));
        getServer().getPluginManager().registerEvents(new TangiaEventListener(this), this);
        ModPersistence.load();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        System.out.println("Tangia plugin stopping");
    }

    public void login(Player player, String key) throws InvalidLoginException, IOException {
        System.out.println("Spigot.login");
        if (this.gameID == null) {
            System.out.println("WARN: TANGIA_GAME_ID not set");
            throw new InvalidLoginException();
        }
        UUID playerID = player.getUniqueId();
        TangiaSDK sdk = new TangiaSDK(this.gameID, "0.0.1", this.tangiaUrl, playerID, this);
        sdk.login(key);
        synchronized (this.playerSDKs) {
            if (this.playerSDKs.get(playerID) != null)
                this.playerSDKs.get(playerID).stopEventPolling();
            this.playerSDKs.put(playerID, sdk);
        }
        sdk.startEventPolling();
        ModPersistence.data.sessions().put(player.getUniqueId(), new ModPersistenceData.PlayerSession(sdk.getSessionKey()));
        ModPersistence.store();
    }

    public void logout(Player player, boolean removeSession) {
        UUID id = player.getUniqueId();
        synchronized (playerSDKs) {
            TangiaSDK sdk = playerSDKs.get(id);
            if (sdk != null) {
                sdk.stopEventPolling();
                playerSDKs.remove(id);
            }
            if (removeSession) {
                ModPersistence.data.sessions().remove(id);
                ModPersistence.store();
            }
        }
    }
}
