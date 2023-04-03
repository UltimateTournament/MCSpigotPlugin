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
import java.util.*;

public final class TangiaPlugin extends JavaPlugin {
  public final String tangiaUrl = "STAGING".equals(System.getenv("TANGIA_ENV")) ? TangiaSDK.STAGING_URL : TangiaSDK.PROD_URL;
  private static final Logger LOGGER = LoggerFactory.getLogger(TangiaPlugin.class.getCanonicalName());
  public final Map<UUID, TangiaSDK> playerSDKs = new HashMap<>();

  private static final String MOD_VERSION = "1.19.4";

  record EventReceival(InteractionEvent event, long receivedAt) {

  }
  private final Map<UUID, Deque<EventReceival>> lastEvents = new HashMap<>();

  static {
    if (System.getenv("TANGIA_LOGS") == null) {
      LOGGER.info("Disabling logging for Tangia. To re-enable set the env var TANGIA_LOGS=1");
      try {
        org.apache.logging.log4j.core.config.Configurator.setLevel("co.tangia", org.apache.logging.log4j.Level.ERROR);
      } catch (Exception ex) {
        LOGGER.error("failed to set log level", ex);
      }
    }
  }

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
    var playerID = player.getUniqueId();
    var sdk = new TangiaSDK(this.tangiaUrl, MOD_VERSION, "MC Spigot", (errMsg) -> {
      player.sendMessage("Your Tangia login expired");
      logout(player, true);
    }, (s, event) -> processEvent(player, s, event));
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

  public void restoreSession(Player player, String sessionKey) {
    LOGGER.info("Spigot.restoreSession");
    var playerID = player.getUniqueId();
    var sdk = new TangiaSDK(this.tangiaUrl, MOD_VERSION, "MC Spigot", (errMsg) -> {
      player.sendMessage("Your Tangia login expired");
      logout(player, true);
    }, (s, event) -> processEvent(player, s, event));
    sdk.setSessionKey(sessionKey);
    synchronized (this.playerSDKs) {
      if (this.playerSDKs.get(playerID) != null)
        this.playerSDKs.get(playerID).stopEventPolling();
      this.playerSDKs.put(playerID, sdk);
    }
    sdk.startEventPolling();
  }

  private void processEvent(Player p, TangiaSDK sdk, InteractionEvent e) {
    // Process the interaction event
    var gson = new Gson();
    var event = gson.fromJson(e.Metadata, EventComponent.class);
    var player = Bukkit.getPlayer(p.getUniqueId()); // in case the callback gets called after player left
    if (player == null) {
      sdk.ackEventAsync(new EventResult(e.EventID, false, "player not in game"));
      return;
    }
    synchronized (lastEvents) {
      var playerLastEvents = lastEvents.computeIfAbsent(p.getUniqueId(), k -> new LinkedList<>());
      playerLastEvents.add(new EventReceival(e, System.currentTimeMillis()));
      if (playerLastEvents.size() > 15) {
        playerLastEvents.removeFirst();
      }
    }
    try {
      var delayAck = false;
      if (event.commands != null) {
        var firstCommand = true;
        for (var cmd : event.commands) {
          final var shouldAck = firstCommand;
          if (firstCommand) {
            firstCommand = false;
          }
          delayAck = true;
          cmd = new CommandComponent(cmd.command, e.BuyerName, player.getName(), cmd.delayTicks);
          System.out.println("Running command: " + cmd.getCommand());
          var commandString = cmd.getCommand();
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
        for (var msg : event.messages) {
          var msgString = msg.message.replaceAll("\\$DISPLAYNAME", e.BuyerName).replaceAll("\\$PLAYERNAME", player.getName());
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
    var id = player.getUniqueId();
    synchronized (playerSDKs) {
      var sdk = playerSDKs.get(id);
      playerSDKs.remove(id);
      if (sdk != null) {
        sdk.stopEventPolling();
        if (removeSession) {
          sdk.logout();
        }
      }
      if (removeSession) {
        ModPersistence.data.sessions().remove(id);
        ModPersistence.store();
      }
    }
  }

  public void holdEvents(Player player) {
    var id = player.getUniqueId();
    synchronized (playerSDKs) {
      var sdk = playerSDKs.get(id);
      if (sdk == null) {
        return;
      }
      sdk.stopEventPolling();
    }
  }

  public void resumeEvents(Player player) {
    var id = player.getUniqueId();
    TangiaSDK sdk;
    synchronized (playerSDKs) {
      sdk = playerSDKs.get(id);
    }
    if (sdk == null) {
      return;
    }
    Deque<EventReceival> events;
    synchronized (lastEvents) {
      events = lastEvents.get(id);
      if (events != null) {
        var now = System.currentTimeMillis();
        for (var er : events) {
          if (er.event.DeathReplaySecs > 0 && now - er.receivedAt < er.event.DeathReplaySecs * 1_000) {
            processEvent(player, sdk, er.event);
          }
        }
      }
    }
    sdk.startEventPolling();
  }
}
