package com.hmdp.hmdianping02;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hmdp.hmdianping02.mapper")
public class HmDianping02Application {

    public static void main(String[] args) {
        SpringApplication.run(HmDianping02Application.class, args);
    }

}
