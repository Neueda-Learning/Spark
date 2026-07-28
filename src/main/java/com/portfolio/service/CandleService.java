package com.portfolio.service;

import com.portfolio.dto.CandleSeriesResponse;

public interface CandleService {

    CandleSeriesResponse getCandles(Long stockId, CandleInterval interval, int limit);
}
