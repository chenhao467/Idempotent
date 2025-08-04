package api.idempotent.spring.monitor;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class RedisDelayedDeleteService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final String DELAY_DELETE_ZSET = "idempotent:delay:delete";
    
    /**
     * 添加延迟删除任务
     */
    public void addDelayDeleteTask(String key, long delaySeconds) {
        try {
            long executeTime = System.currentTimeMillis() + delaySeconds * 1000;
            redisTemplate.opsForZSet().add(DELAY_DELETE_ZSET, key, executeTime);  // expire时间如果不需要可传0
            log.debug("添加延迟删除任务, key: {}, 执行时间: {}", key, executeTime);
        } catch (Exception e) {
            log.error("添加延迟删除任务失败, key: {}", key, e);
        }
    }


    /**
     * 定时扫描并执行删除任务
     */
    @Scheduled(fixedDelay = 2000) // 每两秒扫描一次
    public void processDelayedDeletes() {
        try {
            long now = System.currentTimeMillis();
            // 获取所有到期的任务
            Set<Object> keys = redisTemplate.opsForZSet().rangeByScore(
                DELAY_DELETE_ZSET, 0, now
            );

            if (keys != null && !keys.isEmpty()) {
                for (Object key : keys) {
                        String strKey = key.toString();
                    try {
                        // 删除实际的key
                        Boolean exists = redisTemplate.hasKey(strKey);
                        if (Boolean.TRUE.equals(exists)) {
                            redisTemplate.delete(strKey);
                            log.info("延迟删除成功, key: {}", key);
                        }

                        // 从延迟队列中移除
                        redisTemplate.opsForZSet().remove(DELAY_DELETE_ZSET, key);
                    } catch (Exception e) {
                        log.error("处理延迟删除任务失败, key: {}", key, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描延迟删除任务失败", e);
        }
    }
}