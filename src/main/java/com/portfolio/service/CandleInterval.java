package com.portfolio.service;

import java.util.Locale;

public enum CandleInterval {
    DAILY(120, 260),
    WEEKLY(52, 104),
    MONTHLY(60, 120);

    private final int defaultLimit;
    private final int maxLimit;

    CandleInterval(int defaultLimit, int maxLimit) {
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    public static CandleInterval parse(String value) {
        try {
            return CandleInterval.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Unsupported candle interval: " + value);
        }
    }

    public int resolveLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? defaultLimit : requestedLimit;
        if (limit < 1 || limit > maxLimit) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + maxLimit + " for " + name()
            );
        }
        return limit;
    }
}
