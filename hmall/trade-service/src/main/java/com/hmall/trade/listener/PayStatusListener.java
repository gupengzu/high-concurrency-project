package com.hmall.trade.listener;

import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;

@Component
@RequiredArgsConstructor
public class PayStatusListener {

  private final IOrderService orderService;

  @RabbitListener(bindings = @QueueBinding(value = @Queue(name = "trade.pay.success.queue", durable = "true"), exchange = @Exchange(name = "pay.direct"), key = "pay.success"))
  public void listenPaySuccess(Long orderId, @Header("spring_returned_message_correlation") String messageId) {
    // 你可以用 messageId 做幂等校验或日志记录
    //这里查询数据库有没有这个id，如果有就不处理，没有就处理
    //但是这个方法很麻烦，如果有别的方法，就用别的方法
    orderService.markOrderPaySuccess(orderId);
  }
}