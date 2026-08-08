package com.sky.task;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.utils.RedisLockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 * 使用 Redis 分布式锁防止多实例重复执行
 */

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理超时订单（每分钟触发，通过分布式锁保证只有一个实例执行）
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        String lockKey = "lock:task:processTimeoutOrder";
        String requestId = RedisLockUtil.tryLock(redisTemplate, lockKey, Duration.ofSeconds(55));

        if (requestId == null) {
            log.debug("获取分布式锁失败，跳过本次超时订单处理（其他实例正在执行）");
            return;
        }

        try {
            log.info("定时处理超时订单：{}", LocalDateTime.now());
            LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
            List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

            if (ordersList != null && !ordersList.isEmpty()) {
                for (Orders orders : ordersList) {
                    orders.setStatus(Orders.CANCELLED);
                    orders.setCancelReason("订单超时，自动取消");
                    orders.setCancelTime(LocalDateTime.now());
                    orderMapper.update(orders);
                }
                log.info("本次取消超时订单数量: {}", ordersList.size());
            }
        } finally {
            RedisLockUtil.unlock(redisTemplate, lockKey, requestId);
        }
    }

    /**
     * 处理配送完成订单（每天凌晨一点触发）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        String lockKey = "lock:task:processDeliveryOrder";
        String requestId = RedisLockUtil.tryLock(redisTemplate, lockKey, Duration.ofMinutes(5));

        if (requestId == null) {
            log.debug("获取分布式锁失败，跳过本次配送订单处理");
            return;
        }

        try {
            log.info("定时处理配送订单：{}", LocalDateTime.now());
            LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
            List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
            if (ordersList != null && !ordersList.isEmpty()) {
                for (Orders orders : ordersList) {
                    orders.setStatus(Orders.COMPLETED);
                    orderMapper.update(orders);
                }
                log.info("本次完成配送订单数量: {}", ordersList.size());
            }
        } finally {
            RedisLockUtil.unlock(redisTemplate, lockKey, requestId);
        }
    }
}
