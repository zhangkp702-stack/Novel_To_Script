package com.zkp.my12306.ntc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan({
        "com.zkp.my12306.ntc.mapper",
        "com.zkp.my12306.ntc.llm.dao.mapper",
        "com.zkp.my12306.ntc.script.dao.mapper"
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

}
