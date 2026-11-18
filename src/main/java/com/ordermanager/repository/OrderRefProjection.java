package com.ordermanager.repository;

import java.time.Instant;

public interface OrderRefProjection {

    Long getId();

    Long getUserId();

    long getQuantity();

    Instant getCreatedAt();

    long getFulfilledQuantity();
}
