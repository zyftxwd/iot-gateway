package com.iot.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 工业物联网平台启动类
 */
@SpringBootApplication // Spring Boot 核心注解：开启自动配置、组件扫描+
@EnableScheduling
@MapperScan("com.iot.backend.mapper") // 关键：告诉 MyBatis 扫描哪个包下的 Mapper 接口
public class IiotBackendApplication {

    public static void main(String[] args) {
        // 启动 Spring 应用
        SpringApplication.run(IiotBackendApplication.class, args);
        System.out.println("==========================================");
        System.out.println("   工业物联网后端系统启动成功！口径：8080   ");
        System.out.println("==========================================");
    }
}