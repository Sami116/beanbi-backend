package com.bean.beanbi.manager;

import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import io.github.briqt.spark4j.model.request.SparkRequest;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 与AI对话能力
 *
 * @author sami
 */
@Component
public class AiManager {

    @Resource
    private SparkClient sparkClient;


    /**
     * AI 的设定
     */
    private static final String PRECONDITION = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容： \n" +
            "分析需求：\n" +
            "{数据分析的需求或者目标}\n" +
            "原始数据：\n" +
            "{csv格式的原始数据，用,作为分隔符}\n" +
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释等内容）\n" +
            "%%%% （ 前面的四个百分号起到分隔符的作用，请不要忽略）\n" +
            "{前端 Echarts V5 的 option 配置对象json格式代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
            "%%%% （ 前面的四个百分号起到分隔符的作用，请不要忽略）\n" +
            "{明确的数据分析结论，越详细越好，不要生成多余的注释}\n";


    /**
     * AI 对话
     *
     * @param message
     * @return
     */
    public String doChat(String message) {

        String content = PRECONDITION + message;

        List<SparkMessage> messages = new ArrayList<>();
        messages.add(SparkMessage.userContent(content));
        // 构造请求
        SparkRequest sparkRequest = SparkRequest.builder()
                // 消息列表
                .messages(messages)
                // 模型回答的tokens的最大长度,非必传,取值为[1,4096],默认为2048
                .maxTokens(4096)
                // 核采样阈值。用于决定结果随机性,取值越高随机性越强即相同的问题得到的不同答案的可能性越高 非必传,取值为[0,1],默认为0.5
                .temperature(0.2)
                // 指定请求版本，默认使用最新2.0版本
                .apiVersion(SparkApiVersion.V3_0)
                .build();
        // 同步调用
        SparkSyncChatResponse chatResponse = sparkClient.chatSync(sparkRequest);
        return chatResponse.getContent();
    }
}
