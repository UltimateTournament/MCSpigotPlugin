package co.tangia.spigot;

import co.tangia.sdk.EventResult;
import co.tangia.sdk.InteractionEvent;
import co.tangia.sdk.InvalidLoginException;
import co.tangia.sdk.TangiaSDK;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TangiaPlugin extends JavaPlugin {
    public final String tangiaUrl = "STAGING".equals(System.getenv("TANGIA_ENV")) ? TangiaSDK.STAGING_URL : TangiaSDK.PROD_URL;
    private static final Logger LOGGER = LoggerFactory.getLogger(TangiaPlugin.class.getName());
    public final Map<UUID, TangiaSDK> playerSDKs = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        LOGGER.info("Tangia plugin starting");
        this.getCommand("tangia").setExecutor(new TangiaCommand(this));
        getServer().getPluginManager().registerEvents(new GameEventListener(this), this);
        ModPersistence.load();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        LOGGER.info("Tangia plugin stopping");
    }

    public void login(Player player, String key) throws InvalidLoginException, IOException {
        LOGGER.info("Spigot.login");
        UUID playerID = player.getUniqueId();
        TangiaSDK sdk = new TangiaSDK(this.tangiaUrl, "1.19.2", "MC Spigot", (errMsg)-> {
            player.sendMessage("Your Tangia login expired");
            logout(player, true);
        }, (s, event)-> processEvent(player, s, event));
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

    private void processEvent(Player p, TangiaSDK sdk, InteractionEvent e) {
        // Process the interaction event
        Gson gson = new Gson();
        EventComponent event = gson.fromJson(e.Metadata, EventComponent.class);
        Player player = Bukkit.getPlayer(p.getUniqueId()); // in case the callback gets called after player left
        if (player == null) {
            sdk.ackEventAsync(new EventResult(e.EventID, false, "player not in game"));
            return;
        }
        try {
            var delayAck = false;
            if (event.commands != null) {
                var firstCommand = true;
                for (CommandComponent cmd : event.commands) {
                    final var shouldAck = firstCommand;
                    if (firstCommand) {
                        firstCommand = false;
                    }
                    delayAck = true;
                    cmd = new CommandComponent(cmd.command, e.BuyerName, player.getName(), cmd.delayTicks);
                    System.out.println("Running command: " + cmd.getCommand());
                    String commandString = cmd.getCommand();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        var success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandString);
                        if (success && shouldAck) {
                            sdk.ackEventAsync(new EventResult(e.EventID, true, null));
                        }
                        if (!success) {
                            LOGGER.warn("COMMAND FAILED: {}", commandString);
                        }
                    }, cmd.delayTicks);
                }
            }
            if (event.messages != null) {
                for (MessageComponent msg : event.messages) {
                    String msgString = msg.message.replaceAll("\\$DISPLAYNAME", e.BuyerName).replaceAll("\\$PLAYERNAME", player.getName());
                    System.out.println("Running message: " + msgString);
                    boolean sendToAll = msg.toAllPlayers;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (sendToAll) {
                            Bukkit.broadcastMessage(msgString);
                            return;
                        }
                        player.sendMessage(msgString);
                    }, msg.delayTicks);
                }
            }
            if (!delayAck) {
                sdk.ackEventAsync(new EventResult(e.EventID, true, null));
            }
        } catch (Exception ex) {
            sdk.ackEventAsync(new EventResult(e.EventID, false, "exception"));
        }
    }

    public void logout(Player player, boolean removeSession) {
        UUID id = player.getUniqueId();
        synchronized (playerSDKs) {
            TangiaSDK sdk = playerSDKs.get(id);
            if (sdk != null) {
                sdk.stopEventPolling();
                sdk.logout();
                playerSDKs.remove(id);
            }
            if (removeSession) {
                ModPersistence.data.sessions().remove(id);
                ModPersistence.store();
            }
        }
    }
}
