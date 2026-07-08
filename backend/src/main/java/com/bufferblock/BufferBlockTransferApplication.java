package com.bufferblock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BufferBlockTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(BufferBlockTransferApplication.class, args);
    }
}
