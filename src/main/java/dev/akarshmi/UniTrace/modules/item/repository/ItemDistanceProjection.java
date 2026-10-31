package dev.akarshmi.UniTrace.modules.item.repository;

import java.util.UUID;

/** One candidate row from the pgvector search: item id plus cosine DISTANCE (0 = identical, lower is closer). */
public interface ItemDistanceProjection {

    UUID getId();

    Double getDistance();
}
