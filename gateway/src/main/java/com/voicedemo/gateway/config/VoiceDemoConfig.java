package com.voicedemo.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModeProperties.class)
public class VoiceDemoConfig {
}

