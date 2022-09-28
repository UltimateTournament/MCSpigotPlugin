package co.tangia.sdk;

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
    private final EventHandler eventHandler;

    public TangiaSDK(String gameID, String gameVersion, EventHandler eventHandler) {
        this(gameID, gameVersion, PROD_URL, eventHandler);
    }

    public TangiaSDK(String gameID, String gameVersion, String baseUrl, EventHandler eventHandler) {
        this.gameID = gameID;
        this.gameVersion = gameVersion;
        this.api = createApi(baseUrl);
        this.eventHandler = eventHandler;
    }

    public void login(String creatorCode) throws IOException, InvalidLoginException {
        var call = api.login(new GameLoginReq(gameID, creatorCode));
        var res = execWithRetries(call);
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
        var call = api.ackEvents(this.sessionKey, new AckInteractionEventsReq(results));
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
        var failsafeCall = FailsafeCall.with(retryPolicy).compose(call);
        return failsafeCall.execute();
    }

    private static TangiaApi createApi(String baseUrl) {
        var retrofit = new Retrofit.Builder()
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
            var stopCall = api.notifyStopPlaying(sessionKey);
            try {
                Response<Void> stopResp = execWithRetries(stopCall);
                if (!stopResp.isSuccessful())
                    System.out.println("WARN: couldn't notify stop playing");
            } catch (IOException e) {
                System.out.println("WARN: couldn't notify stop playing: " + e.getMessage());
            }
        }

        private void pollEvents() throws InterruptedException {
            var acks = new LinkedList<EventResult>();
            while (true) {
                var ack = eventAckQueue.poll();
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
            var eventsCall = api.pollEvents(sessionKey, new InteractionEventsReq(gameVersion));
            Response<InteractionEventsResp> eventsResp = null;
            try {
                eventsResp = execWithRetries(eventsCall);
            } catch (IOException e) {
                System.out.println("WARN: error when polling events: " + e.getMessage());
                return;
            }
            if (!eventsResp.isSuccessful()) {
                System.out.println("WARN: couldn't get events");
                if (eventsResp.code() == 401) {
                    System.out.println("WARN: session became invalid - stopping event polling");
                    stopped = true;
                    return;
                }
                Thread.sleep(200);
                return;
            }
            var body = eventsResp.body();
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
                try {
                    eventHandler.handleTangiaEvent(e);
                } catch (Exception ex) {
                    System.out.println("WARN: error when polling events: " + ex.getMessage());
                }
            }
        }

        public void stopPolling() {
            this.stopped = true;
        }
    }
}
