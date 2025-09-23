package com.hmall.cart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
public class RedisTest {

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Test
  public void testRedisSetAndGet() {
    // 写入数据
    redisTemplate.opsForValue().set("test:key", "hello redis");
    // 读取数据
    Object value = redisTemplate.opsForValue().get("test:key");
    System.out.println("Redis读取结果: " + value);
    // 断言
    assert "hello redis".equals(value);
  }
}
