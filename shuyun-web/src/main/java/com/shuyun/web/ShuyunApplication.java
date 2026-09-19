package com.shuyun.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 蜀韵·智游 后端启动类
 */
@SpringBootApplication(scanBasePackages = "com.shuyun")
@MapperScan("com.shuyun.mapper")
public class ShuyunApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShuyunApplication.class, args);
    }
}
