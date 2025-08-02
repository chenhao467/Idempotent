package api.spring.aspect;

import api.RequestUtils;

import api.annotation.Idempotent;
import api.assign.RsaUtil;
import api.spring.IdempotentProperties;

import api.spring.cache.IdempotentMethodCache;
import api.spring.cache.IdempotentMethodMeta;
import api.spring.exception.IdempotentException;
import api.spring.monitor.RedisDelayedDeleteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 幂等方面
 *
 * @author liu
 * @date 2025/07/17
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String KEY_PREFIX = "idempotent:";
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
	@Resource
	private IdempotentMethodCache methodCache;
    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private IdempotentProperties idempotentProperties;

	@Resource
	private RedisDelayedDeleteService redisDelayedDeleteService;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
		//判断是否需要验签
		if(idempotent.enableSignVerify()){
			verifySign();
		}
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String className = signature.getDeclaringTypeName();
		String methodName = signature.getName();
		List<String> paramTypeNames = Arrays.stream(signature.getParameterTypes())
				.map(Class::getName).collect(Collectors.toList());

		IdempotentMethodMeta meta = methodCache.getMeta(className, methodName, paramTypeNames);
		if (ObjectUtils.isEmpty(meta)) {
			throw new IdempotentException("没找到对应类和方法的元数据 " + className + "#" + methodName);
		}
		Idempotent cachedAnno = meta.getIdempotent();


		// 生成Redis幂等键
		String redisKey = buildKey(joinPoint,meta);

		// 尝试设置Redis键值（原子操作）
		Boolean isAbsent = redisTemplate.opsForValue().setIfAbsent(
				redisKey,
				"1", //占位值
				cachedAnno.expireTime(),
				cachedAnno.timeUnit()
		);
		// 重复请求处理
		if (Boolean.FALSE.equals(isAbsent)) {
			log.warn("重复请求触发幂等拦截, key: {}", redisKey);
			throw new IdempotentException(cachedAnno.info());
		}else{
			redisTemplate.opsForValue().set(redisKey, "", cachedAnno.expireTime(), cachedAnno.timeUnit());
		}

		try {
			// 执行业务方法
			Object result = joinPoint.proceed();

			// 执行成功后不马上删除Key，让其自然过期 （如果配置）
			if (idempotent.delKey()) {
				//无论否成功，都加入延迟删除任务，防止数据丢失
				redisDelayedDeleteService.addDelayDeleteTask(redisKey, cachedAnno.delayCheckSeconds());
				log.debug("业务完成删除幂等键, key: {}", redisKey);
			}
			return result;
		} catch (Throwable e) {
			// 异常时立即删除Key（如果配置）
			if (cachedAnno.delKey()) {
				redisTemplate.delete(redisKey);
				//无论否成功，都加入延迟删除任务，防止数据丢失
				redisDelayedDeleteService.addDelayDeleteTask(redisKey, cachedAnno.delayCheckSeconds());
				log.debug("业务异常删除幂等键, key: {}", redisKey);
			}
			throw new IdempotentException(e);
		}
	}

	private String buildKey(ProceedingJoinPoint joinPoint, IdempotentMethodMeta meta){
        String rawKeyContent;
		//从缓存中拿，避免反射
        Method method = meta.getMethod();
		Idempotent idempotent = meta.getIdempotent();
        HttpServletRequest currentRequest = RequestUtils.getCurrentRequest();
        // 1. 使用自定义SPEL表达式
        if (StringUtils.hasText(idempotent.key())) {
            rawKeyContent = parseSpel(method, joinPoint.getArgs(), idempotent.key());
        }
        // 2. 自动生成默认Key：类名+方法名+参数哈希
        else {
			//同样的，从缓存里拿
            String className = meta.getClassName();
            String methodName = meta.getMethodName();
			System.out.println();
			int paramsHash = Arrays.toString(joinPoint.getArgs()).hashCode();
			rawKeyContent = String.format("%s.%s:%d", className, methodName, paramsHash);
        }

        // 获取请求体数据
        Map<String, Object> requestBody = RequestUtils.getRequestBody(currentRequest);

        // 标准化处理请求体并追加
        if (!requestBody.isEmpty()) {
            String normalizedRequestBody = normalizeRequestBody(requestBody);
            rawKeyContent += normalizedRequestBody;
        }


        // 获取 当前请求 IP
        String ipAddress = RequestUtils.getIpAddress(currentRequest);
		//新增：端口
		String port = String.valueOf(currentRequest.getRemotePort());



        String defTokenHeader = idempotent.tokenHeader();
        if (!StringUtils.hasText(defTokenHeader) || defTokenHeader.isEmpty()) {
            defTokenHeader = idempotentProperties.getTokenHeader();
        }

        String tokenValue = RequestUtils.getHeaderWithVariants(currentRequest, defTokenHeader);


        // 生成32字节SHA-256哈希值
		return KEY_PREFIX + tokenValue + ":" + ipAddress + ":" + port + ":" + generateSha256(rawKeyContent);
	}

    /**
     * 标准化请求体:
     * 1. 过滤空值项
     * 2. 按键名不区分大小写排序
     * 3. 使用标准JSON格式序列化
     */
    private String normalizeRequestBody(Map<String, Object> requestBody) {
        // 创建有序Map（按字段名排序）
        Map<String, Object> sortedMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // 过滤空值项并收集到有序Map中
        requestBody.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> sortedMap.put(entry.getKey(), entry.getValue()));

        // 使用JSON序列化（假设项目中有JSON处理能力）
        try {
            return new ObjectMapper().writeValueAsString(sortedMap);
        } catch (JsonProcessingException e) {
            log.warn("请求体序列化失败，将使用原始toString: {}", e.getMessage());
            return sortedMap.toString();
        }
    }

    /**
     * 生成32字节SHA-256哈希值（64字符十六进制字符串）
     */
    private String generateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256是标准算法，理论上不会发生
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 解析SPEL表达式
     */
    private String parseSpel(Method method, Object[] args, String spel) {
        Expression expression = parser.parseExpression(spel);
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, args, nameDiscoverer
        );
        return expression.getValue(context, String.class);
    }
	/**
	 * 签名验证的核心逻辑，由 idempotentHandler 调用。
	 *
	 */
	private void verifySign() throws Exception {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		Map<String, String> paramsToSign = getAllParams(request);

		String clientSign = paramsToSign.remove("sign");
		if (clientSign == null) {
			throw new IdempotentException("签名校验失败：缺少'sign'参数");
		}

		// 1. 构造待签名字符串（如 key1=value1&key2=value2...）
		String dataToVerify = RsaUtil.buildSignContent(paramsToSign);

		// 2. 验签
		PublicKey publicKey = RsaUtil.getPublicKey(idempotentProperties.getPublicKey()); // 从配置中获取公钥
		boolean verifyResult = RsaUtil.verify(dataToVerify, clientSign, publicKey);

		log.debug("验签原文: {}, 客户端签名: {}", dataToVerify, clientSign);

		if (!verifyResult) {
			throw new IdempotentException("签名校验失败，参数可能被篡改");
		}
		log.info("API签名验证通过。");
	}

	private Map<String, String> getAllParams(HttpServletRequest request) {
		Map<String, String> params = new HashMap<>();
		// 1. 先取表单参数
		request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));

		// 2. 如果是JSON，读取body
		if (request.getContentType() != null && request.getContentType().contains("application/json")) {
			try {
				String body = getRequestBody(request);
				if (body != null && !body.isEmpty()) {
					ObjectMapper mapper = new ObjectMapper();
					Map<String, Object> jsonMap = mapper.readValue(body, Map.class);
					jsonMap.forEach((k, v) -> params.put(k, v == null ? "" : v.toString()));
				}
			} catch (Exception e) {
				throw new IdempotentException(e);
			}
		}
		return params;
	}
	//避免只能单次获取
	private String getRequestBody(HttpServletRequest request) throws IOException {
		if (request instanceof ContentCachingRequestWrapper) {
			ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
			byte[] buf = wrapper.getContentAsByteArray();
			return new String(buf, wrapper.getCharacterEncoding());
		} else {
			// 兼容未包装的情况
			StringBuilder sb = new StringBuilder();
			BufferedReader reader = request.getReader();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		}
	}
}