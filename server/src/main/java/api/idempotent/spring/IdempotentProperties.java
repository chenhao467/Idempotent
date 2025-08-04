package api.idempotent.spring;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 幂等性配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "idempotent")
public class IdempotentProperties {

	/** 扫描包 */
	private List<String> scanPackages;
	/** 密钥 */
	private String publicKey;
	/**
	 * 默认令牌请求头名称
	 */
	private String tokenHeader = "token";

}