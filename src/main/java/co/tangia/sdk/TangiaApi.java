package co.tangia.sdk;

import retrofit2.Call;
import retrofit2.http.*;

public interface TangiaApi {
    @POST("/v2/actions/login")
    Call<LoginResp> login(@Body IntegrationLoginReq req);

    @POST("/v2/actions/logout")
    Call<Void> logout(@Header("Authorization") String auth);

    @GET("/v2/actions/pending")
    Call<InteractionEventsResp> pollEvents(@Header("Authorization") String auth);

    @POST("/v2/actions/ack/{actionExecutionID}")
    Call<Void> ackEvent(@Header("Authorization") String auth, @Path("actionExecutionID") String actionExecutionID);

    @POST("/v2/actions/nack/{actionExecutionID}")
    Call<Void> nackEvent(@Header("Authorization") String auth, @Path("actionExecutionID") String actionExecutionID);

}

