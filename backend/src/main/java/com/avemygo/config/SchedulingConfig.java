package com.avemygo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务调度（@Scheduled 生效的前提）。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
