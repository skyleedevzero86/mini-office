package com.sleekydz86.contentmeta;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan(basePackages = "com.sleekydz86.contentmeta", annotationClass = Mapper.class)
public class ContentMetaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentMetaApplication.class, args);
    }
}
