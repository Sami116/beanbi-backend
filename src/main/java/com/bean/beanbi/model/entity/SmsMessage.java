package com.bean.beanbi.model.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * @author sami
 */
@Data
public class SmsMessage implements Serializable {

//    /**
//     * 手机号码
//     */
//    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 短信
     */
    private String code;

    public SmsMessage(String email, String code) {
        this.email = email;
        this.code = code;
    }

    public SmsMessage() {
    }
}