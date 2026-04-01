package ar.edu.uadexplorenow.data.network;

import ar.edu.uadexplorenow.data.model.ActivityRtdbDto;

import com.google.gson.JsonElement;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * REST de Firebase Realtime Database (rutas terminadas en {@code .json}).
 */
public interface RealtimeDatabaseApi {

    @GET("activities.json")
    Call<JsonElement> getActivities();

    @GET("activities/{id}.json")
    Call<ActivityRtdbDto> getActivity(@Path("id") String id);
}
