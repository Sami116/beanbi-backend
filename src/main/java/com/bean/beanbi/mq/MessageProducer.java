package com.bean.beanbi.mq;

import com.bean.beanbi.constant.MQConstant;
import com.bean.beanbi.model.entity.SmsMessage;
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

    /**
     * 向消息队列发送邮箱验证码任务
     * @param message
     */
    public void sendMessage(SmsMessage message) {
        rabbitTemplate.convertAndSend(MQConstant.EXCHANGE_EMAIL_CODE_TASK,MQConstant.ROUTING_KEY_EMAIL_CODE_TASK,message);
    }
}
