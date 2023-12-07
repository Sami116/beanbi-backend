package com.bean.beanbi.mq;

import com.bean.beanbi.constant.MQConstant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author sami
 */

@Component
public class MessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(String message) {
        rabbitTemplate.convertAndSend(MQConstant.EXCHANGE_ASYNC_TASK,MQConstant.ROUTING_KEY_ASYNC_TASK,message);
    }
}
