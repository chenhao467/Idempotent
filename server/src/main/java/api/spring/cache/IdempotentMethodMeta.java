package api.spring.cache;


import api.annotation.Idempotent;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 幂等方法元信息缓存对象
 */
@Data
public class IdempotentMethodMeta {
	/** 方法 */
	private Method method;
	/** 幂等性注解 */
	private Idempotent idempotent;
	/** 类名 */
	private String className;
	/** 方法名称 */
	private String methodName;
	/** 参数类型名称 */
	private List<String> paramTypeNames;
}