package api.idempotent.spring.cache;

import api.idempotent.annotation.Idempotent;
import api.idempotent.spring.IdempotentProperties;
import api.idempotent.spring.exception.IdempotentException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class IdempotentMethodCache {

	private final Map<String, IdempotentMethodMeta> methodMetaMap = new ConcurrentHashMap<>();
	private final IdempotentProperties idempotentProperties;
	public IdempotentMethodCache(IdempotentProperties idempotentProperties) {
		this.idempotentProperties = idempotentProperties;
	}


	@PostConstruct
	public void init() {
		List<String> scanPackages = idempotentProperties.getScanPackages();
		if (CollectionUtils.isEmpty(scanPackages)) {
			throw new IdempotentException("包路径配置为空");
		}

		log.info("开始扫描幂等方法，扫描路径: {}", scanPackages);
		for (String basePackage : scanPackages) {
			Reflections reflections = new Reflections(basePackage, Scanners.MethodsAnnotated);
			Set<Method> methods = reflections.getMethodsAnnotatedWith(Idempotent.class);

			for (Method method : methods) {
				Idempotent idempotent = method.getAnnotation(Idempotent.class);
				Class<?> clazz = method.getDeclaringClass();

				String key = buildMethodKey(clazz, method);
				IdempotentMethodMeta meta = new IdempotentMethodMeta();
				meta.setMethod(method);
				meta.setIdempotent(idempotent);
				meta.setClassName(clazz.getName());
				meta.setMethodName(method.getName());
				meta.setParamTypeNames(Arrays.stream(method.getParameterTypes())
						.map(Class::getName).collect(Collectors.toList()));
				methodMetaMap.put(key, meta);
				log.debug("缓存幂等方法: {}", key);
			}
		}
		log.info("幂等方法扫描完成，共缓存 {} 个方法。", methodMetaMap.size());
	}

    public IdempotentMethodMeta getMeta(String className, String methodName, List<String> paramTypeNames) {
        String key = buildMethodKey(className, methodName, paramTypeNames);
        return methodMetaMap.get(key);
    }

    private String buildMethodKey(Class<?> clazz, Method method) {
        return buildMethodKey(clazz.getName(), method.getName(),
                Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.toList()));
    }

    private String buildMethodKey(String className, String methodName, List<String> paramTypeNames) {
        return className + "#" + methodName + "#" + String.join(",", paramTypeNames);
    }
}