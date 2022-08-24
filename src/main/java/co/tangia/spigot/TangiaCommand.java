package co.tangia.spigot;

import co.tangia.sdk.InvalidLoginException;
import co.tangia.sdk.ModPersistence;
import co.tangia.sdk.ModPersistenceData;
import co.tangia.sdk.TangiaSDK;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TangiaCommand implements CommandExecutor {
    // This method is called, when somebody uses our command
    public TangiaSpigot spigot;
    public TangiaCommand(TangiaSpigot spigot) {
        this.spigot = spigot;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            // Login or logout
            if (args[0].equals("login")) {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + "/tangia login [code]");
                }
                System.out.println("trying to login player");
//                try {
//                    if (spigot.gameID == null) {
//                        System.out.println("WARN: TANGIA_GAME_ID not set");
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
                    System.out.println("WARN: failed to login: " + ex);
                    player.sendMessage("We couldn't log you in");
                } catch (Exception ex) {
                    System.out.println("WARN: failed to login: " + ex);
                    player.sendMessage("We couldn't log you in");
                    return false;
                }
                return true;
            } else if (args[0].equals("logout")) {
                System.out.println("trying to logout player");
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