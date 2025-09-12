package com.hmall.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("trade-service")
public interface TradeClient {
    @PutMapping("/orders/pay-success")
    void markOrderPaySuccess(@RequestParam("orderId") Long orderId);
}