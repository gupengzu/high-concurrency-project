package com.hmall.cart.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@Api(tags = "Redis测试接口")
@RestController
@RequestMapping("/redis-test")
public class RedisTestController {

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @ApiOperation("设置Redis键值")
  @GetMapping("/set")
  public String setValue(@RequestParam String key, @RequestParam String value) {
    redisTemplate.opsForValue().set(key, value);
    return "OK";
  }

  @ApiOperation("获取Redis键值")
  @GetMapping("/get")
  public String getValue(@RequestParam String key) {
    Object value = redisTemplate.opsForValue().get(key);
    return value != null ? value.toString() : "null";
  }
}