package com.avemygo.assistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 流式输出线程池：Web 请求线程立即返回 SseEmitter，真正的生成在池线程里做。
 * 独立小池（2-4 线程）避免把 Tomcat 工作线程长期占用；队列满时拒绝而不是无限堆积。
 */
@Configuration
public class AssistantStreamConfig {

    @Bean("assistantStreamExecutor")
    public ThreadPoolTaskExecutor assistantStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("assistant-sse-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
