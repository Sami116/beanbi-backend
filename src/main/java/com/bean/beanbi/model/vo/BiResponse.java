package com.bean.beanbi.model.vo;

import lombok.Data;

/**
 * BI 的返回结果
 *
 * @author sami
 */
@Data
public class BiResponse {
    private String genChart;
    private String genResult;
    private Long chartId;
    /**
     * 图表状态
     */
    private String status;

    /**
     * 执行信息
     */
    private String execMessage;
}
