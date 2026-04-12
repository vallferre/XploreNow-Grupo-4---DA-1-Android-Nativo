package ar.edu.uadexplorenow.di;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import ar.edu.uadexplorenow.data.local.TokenManager;
import ar.edu.uadexplorenow.data.network.FirebaseAuthQueryInterceptor;
import ar.edu.uadexplorenow.data.network.RealtimeApiConfig;
import ar.edu.uadexplorenow.data.network.RealtimeDatabaseApi;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    private static final String TAG = "NetworkModule";

    private NetworkModule() {}

    @Provides
    @Singleton
    static Gson provideGson() {
        return new GsonBuilder().create();
    }

    @Provides
    @Singleton
    static OkHttpClient provideOkHttpClient(TokenManager tokenManager) {
        return new OkHttpClient.Builder()
                // Interceptor 1: Authorization Bearer (TokenManager — README clase 4b)
                .addInterceptor(chain -> {
                    String token = tokenManager.getToken();
                    Request request = chain.request();
                    if (token != null) {
                        request = request.newBuilder()
                                .addHeader("Authorization", "Bearer " + token)
                                .build();
                        Log.d(TAG, "Authorization header agregado: Bearer " + token);
                    } else {
                        Log.d(TAG, "Sin token — request sin Authorization header");
                    }
                    return chain.proceed(request);
                })
                // Interceptor 2: ?auth=<Firebase ID token> para Firebase Realtime Database
                .addInterceptor(new FirebaseAuthQueryInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Provides
    @Singleton
    static Retrofit provideRetrofit(OkHttpClient okHttpClient, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(RealtimeApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    @Provides
    @Singleton
    static RealtimeDatabaseApi provideRealtimeDatabaseApi(Retrofit retrofit) {
        return retrofit.create(RealtimeDatabaseApi.class);
    }
}
