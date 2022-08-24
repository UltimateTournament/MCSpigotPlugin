package co.tangia.sdk;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface TangiaApi {
    @POST("/game/login")
    Call<GameLoginResp> login(@Body GameLoginReq req);

    @POST("/game/interactions/poll")
    Call<InteractionEventsResp> pollEvents(@Header("Authorization") String auth, @Body InteractionEventsReq req);

    @POST("/game/interactions/ack")
    Call<Void> ackEvents(@Header("Authorization") String auth, @Body AckInteractionEventsReq req);

    @POST("/game/interactions/stop_playing")
    Call<Void> notifyStopPlaying(@Header("Authorization") String auth);
}

