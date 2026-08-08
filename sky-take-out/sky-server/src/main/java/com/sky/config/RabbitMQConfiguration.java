package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 *
 * 队列说明：
 * - order.notify.queue：订单通知队列（来单提醒、催单等），消费者收到后通过 WebSocket 推送给商家
 */
@Configuration
@Slf4j
public class RabbitMQConfiguration {

    // ========== 订单通知 ==========

    public static final String ORDER_NOTIFY_QUEUE = "order.notify.queue";
    public static final String ORDER_NOTIFY_EXCHANGE = "order.notify.exchange";
    public static final String ORDER_NOTIFY_ROUTING_KEY = "order.notify";

    /**
     * 订单通知队列（持久化，不自动删除）
     */
    @Bean
    public Queue orderNotifyQueue() {
        return QueueBuilder.durable(ORDER_NOTIFY_QUEUE).build();
    }

    /**
     * 订单通知交换机（Direct 模式）
     */
    @Bean
    public DirectExchange orderNotifyExchange() {
        return new DirectExchange(ORDER_NOTIFY_EXCHANGE);
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding orderNotifyBinding() {
        return BindingBuilder
                .bind(orderNotifyQueue())
                .to(orderNotifyExchange())
                .with(ORDER_NOTIFY_ROUTING_KEY);
    }

    // ========== 通用配置 ==========

    /**
     * 消息转换器：使用 JSON 序列化（替代默认的 Java 序列化）
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
