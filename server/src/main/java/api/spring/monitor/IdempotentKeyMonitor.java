package api.spring.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import jakarta.annotation.Resource;
import java.util.Set;

@Component
@Slf4j
public class IdempotentKeyMonitor {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    // 每小时执行一次
    @Scheduled(cron = "0 0 * * * ?")
    public void scanAndCleanIdempotentKeys() {
        Set<String> keys = redisTemplate.keys("idempotent:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            Long expire = redisTemplate.getExpire(key);
            // expire == -1 表示没有设置过期时间
            // expire == -2 表示key不存在
            if (expire == null || expire == -1) {
                // 发现异常key，进行删除和告警
                redisTemplate.delete(key);
                log.error("发现未设置过期时间的幂等key并已删除: " + key);
            }
        }
    }
}