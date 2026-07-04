package ar.edu.uadexplorenow.ui.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Detecta la transicion de sin conexion a con conexion mientras una pantalla esta visible,
 * para poder re-sincronizar datos (reservas, actividades) sin que el usuario tenga que
 * salir y volver a entrar a la pantalla.
 */
public final class NetworkReconnectObserver {

    public interface Listener {
        void onNetworkRestored();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasConnected;

    @MainThread
    public void start(@NonNull Context context, @NonNull Listener listener) {
        stop(context);
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return;
        }

        wasConnected = connectivityManager.getActiveNetwork() != null;
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                boolean justReconnected = !wasConnected;
                wasConnected = true;
                if (justReconnected) {
                    mainHandler.post(listener::onNetworkRestored);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                wasConnected = false;
            }
        };
        networkCallback = callback;
        connectivityManager.registerDefaultNetworkCallback(callback);
    }

    @MainThread
    public void stop(@NonNull Context context) {
        ConnectivityManager.NetworkCallback callback = networkCallback;
        if (callback == null) {
            return;
        }
        networkCallback = null;
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (IllegalArgumentException alreadyUnregistered) {
            // El callback ya no estaba registrado, no hay nada que hacer.
        }
    }

    @Nullable
    private ConnectivityManager getConnectivityManager(@NonNull Context context) {
        return (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
