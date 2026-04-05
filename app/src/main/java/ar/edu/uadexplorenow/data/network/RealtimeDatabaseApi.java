package ar.edu.uadexplorenow.data.network;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;

import com.google.gson.JsonElement;

import ar.edu.uadexplorenow.data.model.UserRtdbDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.Map;

/**
 * REST de Firebase Realtime Database (rutas terminadas en {@code .json}).
 */
public interface RealtimeDatabaseApi {

    @GET("activities.json")
    Call<JsonElement> getActivities();

    @GET("activities/{id}.json")
    Call<ActivityRtdbDto> getActivity(@Path("id") String id);

    @GET("users/{uid}.json")
    Call<UserRtdbDto> getUser(@Path("uid") String uid);

    @PUT("users/{uid}.json")
    Call<UserRtdbDto> putUser(@Path("uid") String uid, @Body UserRtdbDto user);

    @PATCH("users/{uid}.json")
    Call<Void> patchUser(@Path("uid") String uid, @Body Map<String, Object> updates);
}
