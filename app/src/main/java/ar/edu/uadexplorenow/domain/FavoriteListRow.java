package ar.edu.uadexplorenow.domain;

import androidx.annotation.NonNull;

/** Fila de la lista "Mis favoritos" con flags de novedad respecto a la última visita guardada. */
public final class FavoriteListRow {

    public enum SpotsNovelty {
        NONE,
        /** Hay más cupos que en la línea base; usar {@link #spotsIncreaseDelta}. */
        INCREASED,
        /** Antes había cupos y ahora no hay disponibles. */
        SOLD_OUT
    }

    @NonNull
    public final ActivityItem activity;
    public final boolean priceNovelty;
    @NonNull
    public final SpotsNovelty spotsNovelty;
    /** Cupos que subieron respecto a la línea base; solo si {@link #spotsNovelty} es {@link SpotsNovelty#INCREASED}. */
    public final long spotsIncreaseDelta;
    /**
     * Precio guardado en el favorito ({@code last_seen_price}) cuando hay {@link #priceNovelty};
     * si no hay novedad de precio, el valor no se usa.
     */
    public final double favoritedPrice;
    /** {@code true} = subió respecto a {@link #favoritedPrice}; solo relevante si {@link #priceNovelty}. */
    public final boolean priceWentUp;

    public FavoriteListRow(
            @NonNull ActivityItem activity,
            boolean priceNovelty,
            @NonNull SpotsNovelty spotsNovelty,
            long spotsIncreaseDelta,
            double favoritedPrice,
            boolean priceWentUp
    ) {
        this.activity = activity;
        this.priceNovelty = priceNovelty;
        this.spotsNovelty = spotsNovelty;
        this.spotsIncreaseDelta = spotsIncreaseDelta;
        this.favoritedPrice = favoritedPrice;
        this.priceWentUp = priceWentUp;
    }
}
