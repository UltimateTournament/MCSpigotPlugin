package co.tangia.spigot;

import co.tangia.sdk.InvalidLoginException;
import com.google.gson.Gson;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TangiaCommand implements CommandExecutor {
    // This method is called, when somebody uses our command
    public TangiaPlugin spigot;
    private static final Logger LOGGER = LoggerFactory.getLogger(TangiaCommand.class.getName());
    public TangiaCommand(TangiaPlugin spigot) {
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

    public static class ModPersistence {
        private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
        private static final Gson gson = new Gson();
        private static final String fileName = "./tangia-persistence.json";

        public static ModPersistenceData data = new ModPersistenceData(new HashMap<>());

        private ModPersistence() {
        }

        public static void store() {
            executor.execute(ModPersistence::storeData);
        }

        private static void storeData() {
            try (FileWriter fw = new FileWriter(fileName, false)) {
                fw.write(gson.toJson(data));
            } catch (IOException e) {
                System.out.println("WARN: couldn't store data " + e);
            }
        }

        public static void load() {
            try (FileReader fr = new FileReader(fileName)) {
                data = gson.fromJson(fr, ModPersistenceData.class);
                if (data == null) {
                    data = new ModPersistenceData(new HashMap<>());
                }
                if (data.sessions() == null) {
                    data.setSessions(new HashMap<>());
                }
            } catch (IOException e) {
                System.out.println("WARN: couldn't load data " + e);
            }
        }
    }

    public static final class ModPersistenceData {
        private HashMap<UUID, PlayerSession> sessions;

        public ModPersistenceData(HashMap<UUID, PlayerSession> sessions) {
            this.sessions = sessions;
        }

        public HashMap<UUID, PlayerSession> sessions() {
            return sessions;
        }

        public void setSessions(HashMap<UUID, PlayerSession> sessions) {
            this.sessions = sessions;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            ModPersistenceData that = (ModPersistenceData) obj;
            return Objects.equals(this.sessions, that.sessions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessions);
        }

        @Override
        public String toString() {
            return "ModPersistenceData[" +
                "sessions=" + sessions + ']';
        }

        public static final class PlayerSession {
            private String sessionToken;

            public PlayerSession(String sessionToken) {
                this.sessionToken = sessionToken;
            }

            public String sessionToken() {
                return sessionToken;
            }

            public void setSessionToken(String sessionToken) {
                this.sessionToken = sessionToken;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                PlayerSession that = (PlayerSession) obj;
                return Objects.equals(this.sessionToken, that.sessionToken);
            }

            @Override
            public int hashCode() {
                return Objects.hash(sessionToken);
            }

            @Override
            public String toString() {
                return "PlayerSession[" +
                    "sessionToken=" + sessionToken + ']';
            }

        }
    }
}