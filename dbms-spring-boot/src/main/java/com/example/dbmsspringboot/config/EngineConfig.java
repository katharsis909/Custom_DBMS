package com.example.dbmsspringboot.config;

import dbmscli.DbmsCliEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {
    @Bean
    public DbmsCliEngine dbmsCliEngine() {
        return new DbmsCliEngine();
    }
}
