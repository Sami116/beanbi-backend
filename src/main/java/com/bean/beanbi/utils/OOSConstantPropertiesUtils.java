package com.bean.beanbi.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


/**
 * @author sami
 */

@Configuration
@ConfigurationProperties(prefix = "aliyun.oss.file")
@Data
public class OOSConstantPropertiesUtils implements InitializingBean {


    private String endpoint;

    private String keyid;

    private String keysecret;

    private String bucketname;

    //定义公开静态常量
    public static String END_POINT;
    public static String ACCESS_KEY_ID;
    public static String ACCESS_KEY_SECRET;
    public static String BUCKET_NAME;


    @Override
    public void afterPropertiesSet() throws Exception {
        END_POINT = endpoint;
        ACCESS_KEY_ID = keyid;
        ACCESS_KEY_SECRET = keysecret;
        BUCKET_NAME = bucketname;
    }
}
