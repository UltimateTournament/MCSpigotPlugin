package co.tangia.spigot;

import co.tangia.sdk.InvalidLoginException;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TangiaCommand implements CommandExecutor {
    // This method is called, when somebody uses our command
    public TangiaPlugin spigot;
    private static final Logger LOGGER = LoggerFactory.getLogger(TangiaCommand.class.getCanonicalName());
    public TangiaCommand(TangiaPlugin spigot) {
        this.spigot = spigot;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            // Login or logout
            if (args[0].equals("login")) {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + "/tangia login [code]");
                }
                LOGGER.info("trying to login player");
//                try {
//                    if (spigot.gameID == null) {
//                        LOGGER.warn("TANGIA_GAME_ID not set");
//                        throw new InvalidLoginException();
//                    }
//                    UUID playerID = player.getUniqueId();
//                    TangiaSDK sdk = new TangiaSDK(spigot.gameID, "0.0.1", spigot.tangiaUrl, playerID);
//                    sdk.login(args[1]);
//                    synchronized (spigot.playerSDKs) {
//                        if (spigot.playerSDKs.get(playerID) != null)
//                            spigot.playerSDKs.get(playerID).stopEventPolling();
//                        spigot.playerSDKs.put(playerID, sdk);
//                    }
//                    sdk.startEventPolling();
//                    ModPersistence.data.sessions().put(uuid, new ModPersistenceData.PlayerSession(sdk.getSessionKey()));
//                    ModPersistence.store();
//                } catch (Exception e) {
//
//                }
                try {
                    spigot.login(player, args[1]);
                    player.sendMessage("Logged in to Tangia!");
                } catch (InvalidLoginException ex) {
                    LOGGER.warn("failed to login: " + ex);
                    player.sendMessage("We couldn't log you in");
                } catch (Exception ex) {
                    LOGGER.warn("failed to login: " + ex);
                    player.sendMessage("We couldn't log you in");
                    return false;
                }
                return true;
            } else if (args[0].equals("logout")) {
                LOGGER.info("trying to logout player");
                spigot.logout(player, true);
                player.sendMessage("You've been logged out of Tangia");
            } else {
                player.sendMessage(ChatColor.RED + "Unknown command: " + args[0]);
                return false;
            }

            return true;
        }
        return false;
    }

}