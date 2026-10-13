package com.example.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitListenerConfig {

    // Declara orders.queue al iniciar, para que OrderListener tenga una cola que escuchar
    @Bean
    public Queue ordersQueue() {
        return QueueBuilder.durable("orders.queue").build();
    }

    @Bean(name = "orderListenerFactory")
    public SimpleRabbitListenerContainerFactory orderListenerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }

    @Bean(name = "paymentListenerFactory")
    public SimpleRabbitListenerContainerFactory paymentListenerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(1);
        return factory;
    }
}
