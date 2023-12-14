package com.bean.beanbi.constant;

/**
 * 消息队列常量
 *
 * @author sami
 */
public interface MQConstant {
    String EXCHANGE_ASYNC_TASK = "exchange_async_task";
    String EXCHANGE_EMAIL_CODE_TASK = "exchange_email_code";
    String QUEUE_ASYNC_TASK = "queue_async_task";
    String QUEUE_EMAIL_CODE_TASK = "queue_email_code";
    String ROUTING_KEY_ASYNC_TASK = "async_task";
    String ROUTING_KEY_EMAIL_CODE_TASK = "email_code_task";
}
