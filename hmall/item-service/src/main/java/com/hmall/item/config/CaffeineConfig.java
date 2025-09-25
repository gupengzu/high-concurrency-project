package com.hmall.item.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmall.item.domain.dto.ItemDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine缓存配置类
 * 提供JVM级别的本地缓存，作为Redis缓存的前置缓存
 * 
 * @author 虎哥
 * @since 2023-05-05
 */
@Configuration
public class CaffeineConfig {

  /**
   * 商品信息缓存
   * 缓存ItemDTO对象，用于快速查询商品信息
   */
  @Bean
  public Cache<Long, ItemDTO> itemCache() {
    return Caffeine.newBuilder()
        // 初始容量
        .initialCapacity(100)
        // 最大容量
        .maximumSize(10_000)
        // 写入后过期时间
        .expireAfterWrite(30, TimeUnit.MINUTES)
        // 访问后过期时间
        .expireAfterAccess(10, TimeUnit.MINUTES)
        // 启用统计
        .recordStats()
        .build();
  }
}
