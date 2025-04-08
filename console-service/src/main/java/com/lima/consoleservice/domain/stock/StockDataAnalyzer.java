package com.lima.consoleservice.domain.stock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class StockDataAnalyzer {

  public static BarSeries convertToTimeSeries(List<Map<String, Object>> stockData, String symbol) {
    // BarSeries 생성
    BarSeries series = new BaseBarSeries(symbol);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 데이터 시간순으로 정렬
    stockData.sort(Comparator.comparing(data ->
        LocalDateTime.parse((String) data.get("timestamp"), formatter)));

    ZonedDateTime lastEndTime = null;

    for (Map<String, Object> data : stockData) {
      // 타임스탬프 변환
      String timestampStr = (String) data.get("timestamp");
      LocalDateTime dateTime = LocalDateTime.parse(timestampStr, formatter);
      ZonedDateTime endTime = dateTime.atZone(ZoneId.systemDefault());

      // 가격 데이터 추출
      double open = Double.parseDouble(data.get("open").toString());
      double high = Double.parseDouble(data.get("high").toString());
      double low = Double.parseDouble(data.get("low").toString());
      double close = Double.parseDouble(data.get("close").toString());
      double volume = Double.parseDouble(data.get("volume").toString());

      // 이전 endTime과 비교해 중복이거나 이전 시간이라면 건너뛰기
      if (lastEndTime != null && !endTime.isAfter(lastEndTime)) {
        System.out.println("Skipping bar with end time " + endTime + " as it is not after " + lastEndTime);
        continue;
      }

      // Bar 객체 생성
      BaseBar bar = BaseBar.builder()
          .timePeriod(Duration.ofMinutes(5))
          .endTime(endTime)
          .openPrice(series.numOf(open))
          .highPrice(series.numOf(high))
          .lowPrice(series.numOf(low))
          .closePrice(series.numOf(close))
          .volume(series.numOf(volume))
          .build();

      series.addBar(bar);
      lastEndTime = endTime; // 마지막 종료 시간 갱신
    }

    return series;
  }

  // 이동평균 교차 전략 분석 (매수와 매도 시점을 결정하는 거래 전략을 분석하는 방법)
  // 단기 이동평균선(SMA, Short Moving Average)과 장기 이동평균선(LMA, Long Moving Average)의 교차점을 기준으로 매매 신호를 생성하고, 그 결과를 평가하는 로직을 구현한 것입니다.
  public static void analyzeSMA(BarSeries series) {
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

    // Bar = 10 * 5 = 50분의 이동평균
    SMAIndicator shortSma = new SMAIndicator(closePrice, 10);
    // Bar = 30 * 5 = 150분의 이동평균
    SMAIndicator longSma = new SMAIndicator(closePrice, 30);

    // 전략 생성: 단기 이평선이 장기 이평선을 상향 돌파할 때 매수
    Rule buyingRule = new CrossedUpIndicatorRule(shortSma, longSma);
    // 전략 생성: 단기 이평선이 장기 이평선을 하향 돌파할 때 매도
    Rule sellingRule = new CrossedDownIndicatorRule(shortSma, longSma);

    Strategy strategy = new BaseStrategy(buyingRule, sellingRule);

    // 전략 테스트
    BarSeriesManager seriesManager = new BarSeriesManager(series);
    TradingRecord tradingRecord = seriesManager.run(strategy);

    // 결과 분석
    System.out.println("시작 인덱스: " + series.getBeginIndex() + ", 종료 인덱스: " + series.getEndIndex());
    System.out.println("거래 횟수: " + tradingRecord.getPositionCount());

    // 각 거래 정보 출력
    for (Position position : tradingRecord.getPositions()) {
      Bar entryBar = series.getBar(position.getEntry().getIndex());
      Bar exitBar = series.getBar(position.getExit().getIndex());

      System.out.println("매수: " + entryBar.getDateName() + " 가격: " + position.getEntry().getAmount());
      System.out.println("매도: " + exitBar.getDateName() + " 가격: " + position.getExit().getAmount());

      Num profit = position.getGrossProfit();
      System.out.println("수익: " + profit + " (" + profit.multipliedBy(series.numOf(100)) + "%)");
      System.out.println("-----------------------");
    }
  }

  /**
   * RSI 지표를 이용한 과매수/과매도 분석
   */
  public static void analyzeRSI(BarSeries series) {
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    RSIIndicator rsi = new RSIIndicator(closePrice, 14);

    // RSI가 30 이하일 때 매수 (과매도 상태)
    Rule buyingRule = new UnderIndicatorRule(rsi, 30);
    // RSI가 70 이상일 때 매도 (과매수 상태)
    Rule sellingRule = new OverIndicatorRule(rsi, 70);

    Strategy strategy = new BaseStrategy(buyingRule, sellingRule);

    // 전략 테스트
    BarSeriesManager seriesManager = new BarSeriesManager(series);
    TradingRecord tradingRecord = seriesManager.run(strategy);

    System.out.println("\n== RSI 분석 결과 ==");
    System.out.println("거래 횟수: " + tradingRecord.getPositionCount());

    // 현재 RSI 값 확인
    int lastIndex = series.getEndIndex();
    Num currentRsi = rsi.getValue(lastIndex);
    System.out.println("현재 RSI: " + currentRsi);

    if (currentRsi.doubleValue() < 30) {
      System.out.println("현재 과매도 상태입니다. 매수 신호가 발생했습니다.");
    } else if (currentRsi.doubleValue() > 70) {
      System.out.println("현재 과매수 상태입니다. 매도 신호가 발생했습니다.");
    } else {
      System.out.println("현재 RSI는 중립 구간에 있습니다.");
    }
  }

  /**
   * 향후 가격 추세 예측 (간단한 선형 회귀 기반)
   */
  public static void predictTrend(BarSeries series, int predictDays) {
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    int lastIndex = series.getEndIndex();

    // 마지막 N개 데이터로 선형 회귀 계산
    int n = Math.min(20, series.getBarCount());
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < n; i++) {
      int idx = lastIndex - n + 1 + i;
      double x = i;
      double y = closePrice.getValue(idx).doubleValue();

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    double xMean = sumX / n;
    double yMean = sumY / n;

    // 회귀 계수 계산
    double slope = (sumXY - sumX * yMean) / (sumX2 - sumX * xMean);
    double intercept = yMean - slope * xMean;

    // 예측
    System.out.println("\n== 가격 추세 예측 ==");
    double lastPrice = closePrice.getValue(lastIndex).doubleValue();
    System.out.println("현재 가격: " + lastPrice);

    for (int i = 1; i <= predictDays; i++) {
      double predictedPrice = intercept + slope * (n + i - 1);
      System.out.println(i + "일 후 예상 가격: " + String.format("%.2f", predictedPrice) +
          " (변화율: " + String.format("%.2f", (predictedPrice - lastPrice) / lastPrice * 100) + "%)");
    }

    if (slope > 0) {
      System.out.println("현재 추세: 상승 ▲");
    } else {
      System.out.println("현재 추세: 하락 ▼");
    }
  }
}
