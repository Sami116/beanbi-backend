package com.bean.beanbi.mq;

import com.bean.beanbi.common.ErrorCode;
import com.bean.beanbi.constant.MQConstant;
import com.bean.beanbi.exception.BusinessException;
import com.bean.beanbi.manager.AiManager;
import com.bean.beanbi.model.entity.Chart;
import com.bean.beanbi.model.entity.SmsMessage;
import com.bean.beanbi.service.ChartService;
import com.bean.beanbi.utils.SendEmaiMessageUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author sami
 */
@Component
@Slf4j
public class MessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiManager aiManager;


    @RabbitListener(queues = {MQConstant.QUEUE_ASYNC_TASK}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (StringUtils.isBlank(message)) {
            // 先拒绝消息
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息为空");
        }
        log.info("message received: " + message);
        // 先修改图表状态为 ”执行中“，执行成功后修改为 “已完成”，保存执行结果，执行失败修改为 “失败”，记录失败信息。
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
        }
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus("running");
        boolean res = chartService.updateById(updateChart);
        if (!res) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chartId, "更改图表状态为“执行中”失败");
            return;
        }
        // 重新构造用户输入
        String userInput = getUserInput(chart);
        // 调用星火大模型
        String result = aiManager.doChat(userInput.toString());

        String[] splits = result.split("%%%%");
        if (splits.length < 3) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "AI 生成错误");
            return;
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        updateChart.setGenChart(genChart);
        updateChart.setGenResult(genResult);
        updateChart.setStatus("succeed");
        boolean updateResult = chartService.updateById(updateChart);
        if (!updateResult) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更改图表状态为“成功”失败");
        }
        // 任务执行完成后确认该消息
        channel.basicAck(deliveryTag, false);

    }

    private void handleChartUpdateError(Long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error(execMessage + "," + chartId);
        }
    }

    private String getUserInput(Chart chart) {
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();
        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ", 请使用" + chartType + "图表类型";
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        return userInput.toString();
    }

    /**
     * 处理发送邮箱验证码的任务
     * @param message
     * @param channel
     * @param deliveryTag
     */
    @RabbitListener(queues = {MQConstant.QUEUE_EMAIL_CODE_TASK},ackMode = "MANUAL")
    public void sendEmailCode(SmsMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null) {
            // 如果消息为空，先拒绝消息
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息为空");
        }
        log.info("监听到消息啦，内容是："+ message);
        SendEmaiMessageUtil sendEmaiMessageUtil = new SendEmaiMessageUtil();
        String targetEmail = message.getEmail();
        try {
            sendEmaiMessageUtil.sendMessage(targetEmail, stringRedisTemplate);
        } catch (EmailException e) {
            // 如果验证码发送失败，拒绝消息
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"验证码发送失败");
        }
        // 任务执行完成后确认该消息
        channel.basicAck(deliveryTag,false);

    }
}
