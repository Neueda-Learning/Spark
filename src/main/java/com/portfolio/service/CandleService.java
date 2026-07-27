package com.portfolio.service;

import com.portfolio.dto.CandleSeriesResponse;

public interface CandleService {

    CandleSeriesResponse getWeeklyCandles(Long stockId, int weeks);
}
