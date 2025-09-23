package com.hmall.cart.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.cart.config.CartProperties;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;
import com.hmall.api.dto.ItemDTO;
import com.hmall.cart.mapper.CartMapper;
import com.hmall.cart.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import com.hmall.api.client.ItemClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    // private final IItemService itemService;
    private final DiscoveryClient discoveryClient;
    private final ItemClient itemClient;
    private final CartProperties cartProperties;
    private final RedisTemplate redisTemplate;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否已经存在
        if (checkItemExists(cartFormDTO.getItemId(), userId)) {
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库
        save(cart);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        List<Cart> carts = lambdaQuery().eq(Cart::getUserId, UserContext.getUser()).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }

    private void handleCartItems(List<CartVO> vos) {
        // TODO 1.获取商品id
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        // 2.批量查 Redis
        List<String> redisKeys = itemIds.stream().map(id -> "item:" + id).collect(Collectors.toList());
        List<Object> cachedList = redisTemplate.opsForValue().multiGet(redisKeys);

        List<ItemDTO> items = new java.util.ArrayList<>();
        List<Long> missIds = new java.util.ArrayList<>();
        int idx = 0;
        for (Long id : itemIds) {
            Object obj = cachedList.get(idx++);
            if (obj != null) {
                items.add((ItemDTO) obj);
            } else {
                missIds.add(id);
            }
        }
        // 3.查数据库并回写 Redis
        if (!missIds.isEmpty()) {
            List<ItemDTO> dbItems = itemClient.queryItemByIds(missIds);
            for (ItemDTO item : dbItems) {
                redisTemplate.opsForValue().set("item:" + item.getId(), item, 1, java.util.concurrent.TimeUnit.HOURS);
                items.add(item);
            }
        }

        if (CollUtils.isEmpty(items)) {
            throw new BadRequestException("购物车中商品不存在");
        }
        // 4.转为 id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        // 5.写入vo
        for (CartVO v : vos) {
            ItemDTO item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
    }

    private void checkCartsFull(Long userId) {
        long count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= cartProperties.getMaxAmount()) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", cartProperties.getMaxAmount()));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        long count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}
