package com.example.Contract_review.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Aspose Words 授权配置
 * 
 * 在应用启动时自动注册Aspose Words许可
 * 适用于 aspose-words:24.12:jdk17 版本
 */
@Slf4j
@Component
public class StartupRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.registerWord2412();
        log.info("====================================");
        log.info("Aspose Words 授权已注册！");
        log.info("版本: 24.12 (JDK17)");
        log.info("====================================");
    }

    /**
     * 注册 aspose-words:24.12:jdk17 版本的许可
     * 
     * 通过反射修改内部字段绕过许可验证
     */
    private void registerWord2412() {
        try {
            // 步骤1: 获取并初始化 zzod 类实例
            Class<?> zzodClass = Class.forName("com.aspose.words.zzod");
            Constructor<?> constructors = zzodClass.getDeclaredConstructors()[0];
            constructors.setAccessible(true);
            Object instance = constructors.newInstance(null, null);
            
            // 设置许可标志位
            Field zzWws = zzodClass.getDeclaredField("zzWws");
            zzWws.setAccessible(true);
            zzWws.set(instance, 1);
            
            Field zzVZC = zzodClass.getDeclaredField("zzVZC");
            zzVZC.setAccessible(true);
            zzVZC.set(instance, 1);

            // 步骤2: 初始化 zz83 类
            Class<?> zz83Class = Class.forName("com.aspose.words.zz83");
            constructors.setAccessible(true);
            constructors.newInstance(null, null);

            // 步骤3: 将许可实例添加到许可列表
            Field zzZY4 = zz83Class.getDeclaredField("zzZY4");
            zzZY4.setAccessible(true);
            ArrayList<Object> zzwPValue = new ArrayList<>();
            zzwPValue.add(instance);
            zzZY4.set(null, zzwPValue);

            // 步骤4: 设置全局许可参数
            Class<?> zzXuRClass = Class.forName("com.aspose.words.zzXuR");
            Field zzWE8 = zzXuRClass.getDeclaredField("zzWE8");
            zzWE8.setAccessible(true);
            zzWE8.set(null, 128);
            
            Field zzZKj = zzXuRClass.getDeclaredField("zzZKj");
            zzZKj.setAccessible(true);
            zzZKj.set(null, false);

            log.info("✓ Aspose Words 授权注册成功");
        } catch (Exception e) {
            log.error("✗ Aspose Words 授权注册失败", e);
            throw new RuntimeException("Aspose Words 初始化失败", e);
        }
    }
}

