package ar.edu.uadexplorenow.di;

import android.content.Context;

import androidx.room.Room;

import javax.inject.Singleton;

import ar.edu.uadexplorenow.data.local.db.AppDatabase;
import ar.edu.uadexplorenow.data.local.db.CachedActivityDao;
import ar.edu.uadexplorenow.data.local.db.CachedReservationDao;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class StorageModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "xplorenow_cache")
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    @Singleton
    public CachedActivityDao provideCachedActivityDao(AppDatabase database) {
        return database.cachedActivityDao();
    }

    @Provides
    @Singleton
    public CachedReservationDao provideCachedReservationDao(AppDatabase database) {
        return database.cachedReservationDao();
    }
}
