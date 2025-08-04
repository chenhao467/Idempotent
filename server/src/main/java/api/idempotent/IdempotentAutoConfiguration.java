package api.idempotent;



import api.idempotent.spring.IdempotentProperties;
import api.idempotent.spring.aspect.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 幂等自动配置
 *
 * @author liu
 * @date 2025/07/17
 */
@AutoConfiguration
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class IdempotentAutoConfiguration {


	/**
	 * 切面 拦截处理所有 @Idempotent
	 *
	 * @return Aspect
	 */
	@Bean(name = "wechatIdempotentAspect")
	public IdempotentAspect idempotentAspect() {
		return new IdempotentAspect();
	}

	/**
	 * 提供默认的IdempotentProperties（当用户未配置时）
	 */
	@Bean
	@ConditionalOnMissingBean(IdempotentProperties.class)
	public IdempotentProperties defaultIdempotentProperties() {
		return new IdempotentProperties(); // 使用构造器中的默认值
	}
}
