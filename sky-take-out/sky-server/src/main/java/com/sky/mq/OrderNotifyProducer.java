package com.sky.mq;

import com.alibaba.fastjson.JSONObject;
import com.sky.config.RabbitMQConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单通知消息生产者
 * 将 WebSocket 推送从业务方法中解耦，改为异步发送 MQ 消息
 */
@Component
@Slf4j
public class OrderNotifyProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单通知消息
     *
     * @param type    消息类型：1=来单提醒 2=催单提醒 3=订单状态变更
     * @param orderId 订单ID
     * @param content 通知内容
     */
    public void sendNotify(Integer type, Long orderId, String content) {
        JSONObject message = new JSONObject();
        message.put("type", type);
        message.put("orderId", orderId);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());

        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE,
                RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY,
                message.toJSONString()
        );

        log.info("订单通知消息已发送: type={}, orderId={}", type, orderId);
    }
}
