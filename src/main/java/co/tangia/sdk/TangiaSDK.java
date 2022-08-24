package co.tangia.sdk;

import co.tangia.spigot.TangiaSpigot;
import com.google.gson.Gson;
import dev.failsafe.RetryPolicy;
import dev.failsafe.retrofit.FailsafeCall;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

public class TangiaSDK {
    public static final String PROD_URL = "https://api.tangia.co/";
    public static final String STAGING_URL = "https://tangia.staging.ultimatearcade.io/";
    public static final String GAME_ID = "game_VztAa76k7wc1KdXCW8PJiQ";

    private String sessionKey;
    private EventPoller eventPoller = new EventPoller();
    private final String gameID;
    private final String gameVersion;
    private final ArrayBlockingQueue<EventResult> eventAckQueue = new ArrayBlockingQueue<>(100);
    private final Set<String> handledEventIds = new HashSet<>();
    private final TangiaApi api;

    private UUID playerUUID;

    public TangiaSDK(String gameID, String gameVersion) {
        this(gameID, gameVersion, PROD_URL, null, null);
    }

    private TangiaSpigot spigot;

    public TangiaSDK(String gameID, String gameVersion, String baseUrl, UUID playerUUID, TangiaSpigot spigot) {
        this.gameID = gameID;
        this.gameVersion = gameVersion;
        this.api = createApi(baseUrl);
        this.playerUUID = playerUUID;
        this.spigot = spigot;
    }

    public void login(String creatorCode) throws IOException, InvalidLoginException {
        Call call = api.login(new GameLoginReq(gameID, creatorCode));
        Response<GameLoginResp> res = execWithRetries(call);
        if (!res.isSuccessful() || res.body() == null)
            throw new InvalidLoginException();
        this.sessionKey = res.body().SessionID;
        System.out.println("Login successful!");
    }

    public void startEventPolling() {
        eventPoller.start();
    }

    public void stopEventPolling() {
        eventPoller.stopPolling();
        eventPoller = new EventPoller();
    }

    public void ackEvents(EventResult[] results) throws IOException, InvalidRequestException {
        Call call = api.ackEvents(this.sessionKey, new AckInteractionEventsReq(results));
        Response<Void> res = execWithRetries(call);
        if (!res.isSuccessful())
            throw new InvalidRequestException();
    }

    public void ackEventAsync(EventResult e) {
        if (!eventAckQueue.offer(e)) {
            System.out.println("WARN: ack-queue is full!");
        }
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    private <T> Response<T> execWithRetries(Call<T> call) throws IOException {
        RetryPolicy<Response<T>> retryPolicy = RetryPolicy.ofDefaults();
        FailsafeCall failsafeCall = FailsafeCall.with(retryPolicy).compose(call);
        return failsafeCall.execute();
    }

    private static TangiaApi createApi(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(TangiaApi.class);
    }

    private class EventPoller extends Thread {
        private boolean stopped = false;

        @Override
        public void run() {
            super.run();
            try {
                while (!stopped) {
                    pollEvents();
                }
            } catch (InterruptedException ex) {
                System.out.println("WARN: got interrupted, will stop event polling");
            }
            Call stopCall = api.notifyStopPlaying(sessionKey);
            try {
                Response<Void> stopResp = execWithRetries(stopCall);
                if (!stopResp.isSuccessful())
                    System.out.println("WARN: couldn't notify stop playing");
            } catch (IOException e) {
                System.out.println("WARN: couldn't notify stop playing: " + e.getMessage());
            }
        }

        private void pollEvents() throws InterruptedException {
            LinkedList acks = new LinkedList<EventResult>();
            while (true) {
                EventResult ack = eventAckQueue.poll();
                if (ack == null)
                    break;
                acks.add(ack);
            }
            if (acks.size() > 0) {
                try {
                    EventResult[] ackArr = (EventResult[]) acks.toArray(new EventResult[0]);
                    ackEvents(ackArr);
                } catch (Exception e) {
                    System.out.println("WARN: couldn't ack events: " + e);
                }
            }
            Call eventsCall = api.pollEvents(sessionKey, new InteractionEventsReq(gameVersion));
            Response<InteractionEventsResp> eventsResp = null;
            try {
                eventsResp = execWithRetries(eventsCall);
            } catch (IOException e) {
                System.out.println("WARN: error when polling events: " + e.getMessage());
            }
            if (eventsResp == null || !eventsResp.isSuccessful()) {
                System.out.println("WARN: couldn't get events");
                Thread.sleep(200);
                return;
            }
            InteractionEventsResp body = eventsResp.body();
            if (body == null || body.Events == null || body.Events.length == 0) {
                System.out.println("DEBUG: no events");
                Thread.sleep(50);
                return;
            }
            for (InteractionEvent e : body.Events) {
                System.out.println("DEBUG: got events");
                // we'll receive events until they get ack'ed/rejected
                if (handledEventIds.contains(e.EventID))
                    continue;
                handledEventIds.add(e.EventID);
                // Process the interaction event
                Gson gson = new Gson();
                EventComponent event = gson.fromJson(e.Metadata, EventComponent.class);
                try {
                    if (event.commands != null) {
                        for (CommandComponent cmd : event.commands) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player == null) {
                                ackEventAsync(new EventResult(e.EventID, false, "player not in game"));
                                continue;
                            }
                            cmd = new CommandComponent(cmd.command, e.BuyerName, player.getName().toString(), cmd.delayTicks);
                            System.out.println("Running command: " + cmd.getCommand());
                            String commandString = cmd.getCommand();
                            Bukkit.getScheduler().runTaskLater(spigot, new Runnable() {
                                @Override
                                public void run() {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandString);
                                }
                            }, cmd.delayTicks);
                        }
                    }
                    if (event.messages != null) {
                        for (MessageComponent msg : event.messages) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player == null) {
                                ackEventAsync(new EventResult(e.EventID, false, "player not in game"));
                                continue;
                            }
                            String msgString = msg.message.replaceAll("\\$DISPLAYNAME", e.BuyerName).replaceAll("\\$PLAYERNAME", player.getName());
                            System.out.println("Running message: " + msgString);
                            boolean sendToAll = msg.toAllPlayers;
                            Bukkit.getScheduler().runTaskLater(spigot, new Runnable() {
                                @Override
                                public void run() {
                                    if (sendToAll) {
                                        Bukkit.broadcastMessage(msgString);
                                        return;
                                    }
                                    player.sendMessage(msgString);
                                }
                            }, msg.delayTicks);
                        }
                    }
                    ackEventAsync(new EventResult(e.EventID, true, null));
                } catch (Exception ex) {
                    ackEventAsync(new EventResult(e.EventID, false, "exception"));
                }
            }
        }

        public void stopPolling() {
            this.stopped = true;
        }
    }
}
