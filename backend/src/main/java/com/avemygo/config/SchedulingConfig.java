package com.avemygo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务调度（@Scheduled 生效的前提）。
 *
 * 2026-11-05 加开关：测试 profile 里关掉（app.scheduling.enabled=false）。
 * 原因：会话状态翻转任务会在用例插数据的同时 UPDATE 同几张表，
 * 全量跑时稳定复现 MySQL 死锁（"Deadlock found when trying to get lock"），
 * 而单跑该测试类必过——纯属后台任务与用例抢行锁，与被测逻辑无关。
 * 生产不配该属性，默认开启。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
