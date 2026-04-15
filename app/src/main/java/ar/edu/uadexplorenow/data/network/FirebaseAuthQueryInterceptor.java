package ar.edu.uadexplorenow.data.network;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Añade {@code ?auth=<ID_TOKEN>} a las peticiones REST de Realtime Database cuando hay sesión.
 */
public final class FirebaseAuthQueryInterceptor implements Interceptor {

    private static final String TAG = "FirebaseAuthQuery";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Request sin FirebaseUser. URL=" + request.url());
            return chain.proceed(request);
        }
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(false));
            String token = result.getToken();
            if (token == null || token.isEmpty()) {
                Log.w(TAG, "Firebase ID token vacio para uid=" + user.getUid() + ". URL=" + request.url());
                return chain.proceed(request);
            }
            HttpUrl url = request.url().newBuilder()
                    .addQueryParameter("auth", token)
                    .build();
            Log.d(TAG, "Auth query agregado para uid=" + user.getUid() + ". URL=" + url);
            return chain.proceed(request.newBuilder().url(url).build());
        } catch (Exception e) {
            Log.e(TAG, "No se pudo obtener el Firebase ID token. URL=" + request.url(), e);
            return chain.proceed(request);
        }
    }
}
