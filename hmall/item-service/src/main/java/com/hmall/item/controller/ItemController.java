
package com.hmall.item.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.IItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Api(tags = "商品管理相关接口")
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Cache<Long, ItemDTO> itemCache;

    @ApiOperation("分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
        // 1.分页查询
        Page<Item> result = itemService.page(query.toMpPage("update_time", false));
        // 2.封装并返回
        return PageDTO.of(result, ItemDTO.class);
    }

    @ApiOperation("根据id批量查询商品")
    @GetMapping
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") List<Long> ids) {
        List<ItemDTO> result = new java.util.ArrayList<>();
        List<Long> jvmMissIds = new java.util.ArrayList<>();

        // 1. 先批量查JVM缓存
        for (Long id : ids) {
            ItemDTO itemDTO = itemCache.getIfPresent(id);
            if (itemDTO != null) {
                result.add(itemDTO);
            } else {
                jvmMissIds.add(id);
            }
        }

        // 2. 对JVM缓存未命中的，查Redis
        List<Long> redisMissIds = new java.util.ArrayList<>();
        if (!jvmMissIds.isEmpty()) {
            List<Object> cachedList = redisTemplate.opsForValue().multiGet(
                    jvmMissIds.stream().map(id -> "item:" + id).collect(java.util.stream.Collectors.toList()));
            if (cachedList != null) {
                for (int i = 0; i < jvmMissIds.size(); i++) {
                    Object obj = cachedList.get(i);
                    if (obj != null) {
                        ItemDTO itemDTO = (ItemDTO) obj;
                        result.add(itemDTO);
                        // 回写到JVM缓存
                        itemCache.put(jvmMissIds.get(i), itemDTO);
                    } else {
                        redisMissIds.add(jvmMissIds.get(i));
                    }
                }
            } else {
                // 如果Redis返回null，说明所有商品都需要查数据库
                redisMissIds.addAll(jvmMissIds);
            }
        }

        // 3. 对Redis缓存未命中的，查数据库并回写缓存
        if (!redisMissIds.isEmpty()) {
            List<ItemDTO> dbList = itemService.queryItemByIds(redisMissIds);
            for (ItemDTO item : dbList) {
                // 回写到JVM缓存
                itemCache.put(item.getId(), item);
                // 回写到Redis
                String redisKey = "item:" + item.getId();
                redisTemplate.opsForValue().set(redisKey, item, 1, java.util.concurrent.TimeUnit.HOURS);
                result.add(item);
            }
        }
        return result;
    }

    @ApiOperation("根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        String redisKey = "item:" + id;

        // 1. 先查JVM缓存（Caffeine）
        ItemDTO itemDTO = itemCache.getIfPresent(id);
        if (itemDTO != null) {
            System.out.println("从JVM缓存中获取的商品: " + itemDTO);
            return itemDTO;
        }

        // 2. 再查 Redis
        itemDTO = (ItemDTO) redisTemplate.opsForValue().get(redisKey);
        if (itemDTO != null) {
            System.out.println("从Redis中获取的商品: " + itemDTO);
            // 回写到JVM缓存
            itemCache.put(id, itemDTO);
            return itemDTO;
        }

        // 3. 查数据库
        itemDTO = BeanUtils.copyBean(itemService.getById(id), ItemDTO.class);
        if (itemDTO != null) {
            // 4. 写入JVM缓存
            itemCache.put(id, itemDTO);
            // 5. 写入Redis，设置过期时间（如1小时）
            redisTemplate.opsForValue().set(redisKey, itemDTO, 1, java.util.concurrent.TimeUnit.HOURS);
        }
        return itemDTO;
    }

    @ApiOperation("新增商品")
    @PostMapping
    public void saveItem(@RequestBody ItemDTO item) {
        // 新增
        itemService.save(BeanUtils.copyBean(item, Item.class));
    }

    @ApiOperation("更新商品状态")
    @PutMapping("/status/{id}/{status}")
    public void updateItemStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        Item item = new Item();
        item.setId(id);
        item.setStatus(status);
        itemService.updateById(item);

        // 清除相关缓存
        evictCache(id);
    }

    @ApiOperation("更新商品")
    @PutMapping
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        itemService.updateById(BeanUtils.copyBean(item, Item.class));

        // 清除相关缓存
        evictCache(item.getId());
    }

    @ApiOperation("根据id删除商品")
    @DeleteMapping("{id}")
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.removeById(id);

        // 清除相关缓存
        evictCache(id);
    }

    @ApiOperation("批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items) {
        itemService.deductStock(items);

        // 清除相关商品的缓存（因为库存变化了）
        for (OrderDetailDTO item : items) {
            evictCache(item.getItemId());
        }
    }

    /**
     * 清除指定商品的缓存
     * 
     * @param itemId 商品ID
     */
    private void evictCache(Long itemId) {
        // 清除JVM缓存
        itemCache.invalidate(itemId);
        // 清除Redis缓存
        String redisKey = "item:" + itemId;
        redisTemplate.delete(redisKey);
        System.out.println("已清除商品ID为 " + itemId + " 的缓存");

        // 清除Nginx本地缓存
        try {
            RestTemplate restTemplate = new RestTemplate();
            // 这里假设nginx监听18080端口，按实际情况修改
            String url = "http://localhost:18080/api/cache/item/purge?id=" + itemId;
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            System.err.println("清除Nginx缓存失败: " + e.getMessage());
        }
    }
}
