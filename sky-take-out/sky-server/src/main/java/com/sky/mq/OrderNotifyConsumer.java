package com.sky.mq;

import com.alibaba.fastjson.JSONObject;
import com.sky.config.RabbitMQConfiguration;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单通知消息消费者
 * 收到 MQ 消息后通过 WebSocket 推送给商家端
 */
@Component
@Slf4j
public class OrderNotifyConsumer {

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 监听订单通知队列，收到消息后通过 WebSocket 推送给商家
     */
    @RabbitListener(queues = RabbitMQConfiguration.ORDER_NOTIFY_QUEUE)
    public void handleOrderNotify(String message) {
        log.info("收到订单通知消息: {}", message);
        try {
            JSONObject msg = JSONObject.parseObject(message);
            log.info("WebSocket推送通知: type={}, orderId={}",
                    msg.getInteger("type"), msg.getLong("orderId"));
            webSocketServer.sendToAllClient(message);
            log.info("WebSocket推送完毕");
        } catch (Exception e) {
            log.error("WebSocket推送通知失败", e);
            // MQ 会重试，不需要额外处理
            throw e;
        }
    }
}
