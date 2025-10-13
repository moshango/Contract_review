package com.example.Contract_review.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Web客户端配置
 */
@Configuration
public class WebClientConfig {

    @Autowired
    private AIServiceConfig aiServiceConfig;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 设置超时时间（毫秒）
        factory.setConnectTimeout(60000);  // 60秒连接超时
        factory.setReadTimeout(120000);    // 120秒读取超时

        // 如果配置了代理，则使用代理；否则明确禁用代理
        if (aiServiceConfig.getProxy().isEnabled()) {
            String proxyHost = aiServiceConfig.getProxy().getHost();
            int proxyPort = aiServiceConfig.getProxy().getPort();
            String proxyType = aiServiceConfig.getProxy().getType();

            Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType)
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;

            Proxy proxy = new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
            factory.setProxy(proxy);
        } else {
            // 明确禁用代理，不使用系统代理设置
            factory.setProxy(Proxy.NO_PROXY);
        }

        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加错误处理器
        restTemplate.setErrorHandler(new AIServiceErrorHandler());

        return restTemplate;
    }
}