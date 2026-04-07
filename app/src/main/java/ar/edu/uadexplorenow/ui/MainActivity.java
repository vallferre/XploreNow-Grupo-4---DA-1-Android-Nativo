package ar.edu.uadexplorenow.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import ar.edu.uadexplorenow.R;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Pantalla principal tras el login: aloja el {@link androidx.navigation.fragment.NavHostFragment}
 * (explorar → detalle según {@code nav_graph.xml}).
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
