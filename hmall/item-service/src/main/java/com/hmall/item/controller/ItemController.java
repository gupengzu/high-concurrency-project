
package com.hmall.item.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.IItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

@Api(tags = "商品管理相关接口")
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;
    private final RedisTemplate<String, Object> redisTemplate;

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
        List<Long> missIds = new java.util.ArrayList<>();
        // 1. 先批量查 Redis
        List<Object> cachedList = redisTemplate.opsForValue().multiGet(
                ids.stream().map(id -> "item:" + id).collect(java.util.stream.Collectors.toList()));
        for (int i = 0; i < ids.size(); i++) {
            Object obj = cachedList.get(i);
            if (obj != null) {
                result.add((ItemDTO) obj);
            } else {
                missIds.add(ids.get(i));
            }
        }
        // 2. 查数据库并回写 Redis
        if (!missIds.isEmpty()) {
            List<ItemDTO> dbList = itemService.queryItemByIds(missIds);
            for (ItemDTO item : dbList) {
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
        // 1. 先查 Redis
        ItemDTO itemDTO = (ItemDTO) redisTemplate.opsForValue().get(redisKey);
        if (itemDTO != null) {
            System.out.println("从Redis中获取的商品: " + itemDTO);
            return itemDTO;
        }
        // 2. 查数据库
        itemDTO = BeanUtils.copyBean(itemService.getById(id), ItemDTO.class);
        // 3. 写入 Redis，设置过期时间（如1小时）
        if (itemDTO != null) {
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
    }

    @ApiOperation("更新商品")
    @PutMapping
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        itemService.updateById(BeanUtils.copyBean(item, Item.class));
    }

    @ApiOperation("根据id删除商品")
    @DeleteMapping("{id}")
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.removeById(id);
    }

    @ApiOperation("批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items) {
        itemService.deductStock(items);
    }
}
