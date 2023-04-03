package co.tangia.sdk;

import dev.failsafe.RetryPolicy;
import dev.failsafe.retrofit.FailsafeCall;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TangiaSDK {
  public static final String PROD_URL = "https://api.tangia.co/";
  public static final String STAGING_URL = "https://api.tangia-staging.co/";

  private String sessionKey;
  private EventPoller eventPoller = new EventPoller();
  private final String versionInfo;
  private final ArrayBlockingQueue<InteractionEvent> eventQueue = new ArrayBlockingQueue<>(100);
  private final ArrayBlockingQueue<EventResult> eventAckQueue = new ArrayBlockingQueue<>(100);
  private final Set<String> handledEventIds = new HashSet<>();
  private final TangiaApi api;
  private final String integrationInfo;
  private final Consumer<String> sessionFailCallback;
  private final BiConsumer<TangiaSDK, InteractionEvent> eventCallback;

  private static final Logger LOGGER = LoggerFactory.getLogger(TangiaSDK.class.getName());

  public TangiaSDK(String baseUrl, String versionInfo, String integrationInfo, Consumer<String> sessionFailCallback, BiConsumer<TangiaSDK, InteractionEvent> eventCallback) {
    this.versionInfo = versionInfo;
    this.integrationInfo = integrationInfo;
    this.sessionFailCallback = sessionFailCallback;
    this.eventCallback = eventCallback;
    this.api = createApi(baseUrl, versionInfo, integrationInfo);
  }

  public void login(String creatorCode) throws IOException, InvalidLoginException {
    var call = api.login(new IntegrationLoginReq(versionInfo, creatorCode));
    var res = execWithRetries(call);
    if (!res.isSuccessful() || res.body() == null)
      throw new InvalidLoginException(res.toString());
    this.sessionKey = res.body().AccountKey;
  }

  public void logout() {
    var call = api.logout(sessionKey);
    Response<Void> res;
    try {
      res = execWithRetries(call);
      if (!res.isSuccessful()) {
        LOGGER.warn("logout failed: code {}", res.code());
      }
    } catch (IOException e) {
      LOGGER.warn("logout failed", e);
    }
  }

  public void startEventPolling() {
    try {
      eventPoller.start();
    } catch (IllegalThreadStateException ex) {
      LOGGER.info("ignoring double start of event loop");
    }
  }

  public void stopEventPolling() {
    eventPoller.stopPolling();
    eventPoller = new EventPoller();
  }

  public InteractionEvent popEventQueue() {
    return eventQueue.poll();
  }

  public void ackEvent(String eventID) throws IOException, InvalidRequestException {
    var call = api.ackEvent(this.sessionKey, eventID);
    Response<Void> res = execWithRetries(call);
    if (!res.isSuccessful())
      throw new InvalidRequestException();
  }

  public void nackEvent(String eventID) throws IOException, InvalidRequestException {
    var call = api.nackEvent(this.sessionKey, eventID);
    Response<Void> res = execWithRetries(call);
    if (!res.isSuccessful())
      throw new InvalidRequestException();
  }

  public void ackEventAsync(EventResult e) {
    if (e.Executed) {
      LOGGER.info("ack-ing event {} success:{}", e.EventID, true);
    } else {
      LOGGER.warn("nack-ing event {} success:{}, {}", e.EventID, false, e.Message);
    }
    if (!eventAckQueue.offer(e)) {
      LOGGER.warn("ack-queue is full!");
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

  private static TangiaApi createApi(String baseUrl, String versionInfo, String integrationInfo) {
    OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
    httpClient.addInterceptor(chain -> {
      Request request = chain.request()
          .newBuilder()
          .addHeader("tangia-integration", integrationInfo)
          .addHeader("tangia-version", versionInfo)
          .build();
      return chain.proceed(request);
    });
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient.build())
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
        LOGGER.warn("got interrupted, will stop event polling");
      }
    }

    private void pollEvents() throws InterruptedException {
      while (true) {
        var event = eventAckQueue.poll();
        if (event == null)
          break;
        try {
          if (event.Executed) {
            ackEvent(event.EventID);
          } else {
            nackEvent(event.EventID);
          }
        } catch (Exception e) {
          LOGGER.warn("couldn't ack events: " + e);
        }
      }
      var eventsCall = api.pollEvents(sessionKey);
      Response<InteractionEventsResp> eventsResp = null;
      try {
        eventsResp = execWithRetries(eventsCall);
      } catch (IOException e) {
        LOGGER.warn("error when polling events: " + e.getMessage());
      }
      if (eventsResp == null || !eventsResp.isSuccessful()) {
        LOGGER.warn("couldn't get events: {}", eventsResp);
        if (eventsResp != null && eventsResp.code() == 401) {
          LOGGER.warn("login invalid. Stopping event polling");
          this.stopPolling();
          if (sessionFailCallback != null) {
            sessionFailCallback.accept("session key got unauthorized");
          }
          return;
        }
        long sleepMS = 1000;
        if (eventsResp != null && eventsResp.code() == 429) {
          sleepMS = 3000;
        }
        Thread.sleep(sleepMS);
        return;
      }
      // as the above is a long-poll we might hang there for long enough to miss a stop and a start, so better check twice
      if (stopped)
        return;
      var body = eventsResp.body();
      if (body == null || body.ActionExecutions == null || body.ActionExecutions.length == 0) {
        LOGGER.debug("no events");
        Thread.sleep(50);
        return;
      }
      var gotNewEvents = false;
      for (var ae : body.ActionExecutions) {
        // we'll receive events until they get ack'ed/rejected
        if (handledEventIds.contains(ae.Body.EventID))
          continue;
        gotNewEvents = true;
        handledEventIds.add(ae.Body.EventID);
        if (eventCallback != null) {
          eventCallback.accept(TangiaSDK.this, ae.Body);
        } else {
          eventQueue.put(ae.Body);
        }
      }
      // if we're only getting known (but un-acked) events then we have to throttle down
      // as the long-polling won't do it for us
      if (body.ActionExecutions.length > 0 && !gotNewEvents) {
        Thread.sleep(1000);
      }
    }

    public void stopPolling() {
      this.stopped = true;
    }
  }
}
