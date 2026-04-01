package ar.edu.uadexplorenow.data.network;

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

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return chain.proceed(request);
        }
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(false));
            String token = result.getToken();
            if (token == null || token.isEmpty()) {
                return chain.proceed(request);
            }
            HttpUrl url = request.url().newBuilder()
                    .addQueryParameter("auth", token)
                    .build();
            return chain.proceed(request.newBuilder().url(url).build());
        } catch (Exception e) {
            return chain.proceed(request);
        }
    }
}
