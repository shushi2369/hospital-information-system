package com.his;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.his.modules.**.mapper")
@EnableScheduling
public class HisApplication {
    public static void main(String[] args) {
        SpringApplication.run(HisApplication.class, args);
    }
}
