package com.lima.consoleservice.domain.stock.service;

import com.lima.consoleservice.common.connection.ElasticsearchConnection;
import com.lima.consoleservice.common.exception.ErrorCode;
import com.lima.consoleservice.domain.stock.StockDataAnalyzer;
import com.lima.consoleservice.domain.user.model.response.AuthResponse;
import com.lima.consoleservice.schedule.log.params.Function;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

@Service
public class StockService {

  private final ElasticsearchConnection elasticsearchConnection;

  public StockService() {
    this.elasticsearchConnection = ElasticsearchConnection.getInstance();
  }

  public AuthResponse getAnalysisStock(String symbol) {
    String intraday = Function.TIME_SERIES_WEEKLY.name().toLowerCase();
    List<Map<String, Object>> maps = elasticsearchConnection.searchMatch(intraday, "timestamp",
        "2025-03-06");
    // 데이터를 TA4J 시계열로 변환
    BarSeries series = StockDataAnalyzer.convertToTimeSeries(maps, symbol);

    // 가격 추세 예측
    StockDataAnalyzer.predictTrend(series, 50);
    // RSI 과매수/과매도 분석
//    StockDataAnalyzer.analyzeRSI(series);

    // 이동평균 교차 전략 분석
    StockDataAnalyzer.analyzeSMA(series);
    return new AuthResponse(ErrorCode.SUCCESS.getMessage(), "");
  }
}
