package com.hmall.api.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.hmall.common.utils.UserContext;
import com.hmall.api.client.fallback.ItemClientFallback;

public class DefaultFeignConfig {
  @Bean
  public RequestInterceptor userInfoRequestInterceptor(){
    System.out.println("开始执行Feign拦截器");
      return new RequestInterceptor() {
          @Override
          public void apply(RequestTemplate template) {
              // 获取登录用户
              Long userId = UserContext.getUser();
              System.out.println("Feign传递用户ID：" + userId);
              if(userId == null) {
                  // 如果为空则直接跳过
                  return;
              }
              // 如果不为空则放入请求头中，传递给下游微服务
              template.header("user-info", userId.toString());
          }
      };
  }

    @Bean
    public Logger.Level fullFeignLoggerLevel() {
        return Logger.Level.FULL;
    }
    @Bean
    public ItemClientFallback itemClientFallback() {
        return new ItemClientFallback();
    }
}
