package ar.edu.uadexplorenow;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Pantalla principal tras el login: aloja el {@link androidx.navigation.fragment.NavHostFragment}
 * (explorar → detalle según {@code nav_graph.xml}).
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
