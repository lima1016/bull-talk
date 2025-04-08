package com.lima.consoleservice.domain.stock.controller;

import com.lima.consoleservice.domain.stock.service.StockService;
import com.lima.consoleservice.domain.user.model.response.AuthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

  private final StockService stockService;

  public StockController(StockService stockService) {
    this.stockService = stockService;
  }

  @GetMapping("/get/analysis/stock")
  public AuthResponse getAnalysisStock()  {
    String symbol = "MSFT";
    return stockService.getAnalysisStock(symbol);
  }
}
