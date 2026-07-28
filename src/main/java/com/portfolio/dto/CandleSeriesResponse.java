package com.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record CandleSeriesResponse(
        Long stockId,
        String symbol,
        String interval,
        String source,
        LocalDate asOf,
        List<CandleResponse> candles
) {
}
