package ar.edu.uadexplorenow.data.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RealtimeRetrofitClient {

    private static volatile RealtimeDatabaseApi api;

    private RealtimeRetrofitClient() {}

    public static RealtimeDatabaseApi getApi() {
        if (api == null) {
            synchronized (RealtimeRetrofitClient.class) {
                if (api == null) {
                    OkHttpClient ok = new OkHttpClient.Builder()
                            .addInterceptor(new FirebaseAuthQueryInterceptor())
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();

                    Gson gson = new GsonBuilder().create();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(RealtimeApiConfig.BASE_URL)
                            .client(ok)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                    api = retrofit.create(RealtimeDatabaseApi.class);
                }
            }
        }
        return api;
    }

    public static Gson gson() {
        return new GsonBuilder().create();
    }
}
