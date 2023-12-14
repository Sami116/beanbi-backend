package com.bean.beanbi.config;

import com.bean.beanbi.constant.CommonConstant;
import com.bean.beanbi.constant.MQConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author sami
 */
@Configuration
public class RabbitMQConfig {


    // 声明转发异步任务消息的交换机
    @Bean
    public Exchange asyncTaskExchange() {
        return new DirectExchange(MQConstant.EXCHANGE_ASYNC_TASK, true, false);
    }

    // 声明异步任务消息队列
    @Bean
    public Queue asyncTaskQueue() {
        return new Queue(MQConstant.QUEUE_ASYNC_TASK, true, false, false);
    }

    // 绑定交换机与队列
    @Bean
    public Binding bindingAsyncTaskQueue() {
        return new Binding(MQConstant.QUEUE_ASYNC_TASK, Binding.DestinationType.QUEUE, MQConstant.EXCHANGE_ASYNC_TASK,
                MQConstant.ROUTING_KEY_ASYNC_TASK, null);
    }


    // 声明转发邮箱验证码任务的交换机
    @Bean
    public Exchange emailCodeTaskExchange() {
        return new DirectExchange(MQConstant.EXCHANGE_EMAIL_CODE_TASK,true,false);
    }

    // 声明邮箱验证码任务消息队列
    @Bean
    public Queue emailCodeTaskQueue() {
        return new Queue(MQConstant.QUEUE_EMAIL_CODE_TASK, true, false, false);
    }

    // 绑定交换机与队列
    @Bean
    public Binding bindingEmailCodeTaskQueue() {
        return new Binding(MQConstant.QUEUE_EMAIL_CODE_TASK, Binding.DestinationType.QUEUE, MQConstant.EXCHANGE_EMAIL_CODE_TASK,
                MQConstant.ROUTING_KEY_EMAIL_CODE_TASK, null);
    }
}
