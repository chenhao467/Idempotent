package api;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 请求实用工具
 *
 * @author Acer
 * @date 2024/12/24
 */
@Log4j2
public class RequestUtils {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 获取当前请求
	 *
	 * @return {@link HttpServletRequest }
	 */
	public static HttpServletRequest getCurrentRequest() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attrs != null) {
			return attrs.getRequest();
		} else {
			throw new IllegalStateException("当前线程中不存在请求上下文");
		}
	}

	/**
	 * 获取Headers
	 *
	 * @param request 请求
	 * @return {@link Map }<{@link String }, {@link String }>
	 */
	public static Map<String, String> getHeadersInfo(HttpServletRequest request) {
		Map<String, String> map = new HashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = headerNames.nextElement();
			String value = request.getHeader(key);
			map.put(key, value);
		}
		return map;
	}


	/**
	 * 获取请求参数，返回一个Map
	 *
	 * @param request HttpServletRequest对象
	 * @return 包含请求参数的Map
	 */
	public static Map<String, Object> getParameters(HttpServletRequest request) {
		Map<String, Object> paramMap = new HashMap<>();
		Enumeration<String> parameterNames = request.getParameterNames();
		while (parameterNames.hasMoreElements()) {
			String paramName = parameterNames.nextElement();
			String paramValue = request.getParameter(paramName);
			paramMap.put(paramName, paramValue);
		}
		return paramMap;
	}

	public static String getIpAddress() {
		HttpServletRequest currentRequest = getCurrentRequest();
		return getIpAddress(currentRequest);
	}

	/**
	 * 获取ip地址
	 *
	 * @param request 请求
	 * @return {@link String }
	 */
	public static String getIpAddress(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_CLIENT_IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_X_FORWARDED_FOR");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		// 本机访问
		if ("localhost".equalsIgnoreCase(ip) || "127.0.0.1".equalsIgnoreCase(ip) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(ip)) {
			// 根据网卡取本机配置的IP
			InetAddress inet;
			try {
				inet = InetAddress.getLocalHost();
				ip = inet.getHostAddress();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		// 对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
		if (null != ip && ip.length() > 15) {
			if (ip.indexOf(",") > 15) {
				ip = ip.substring(0, ip.indexOf(","));
			}
		}

		if (ip != null && ip.contains(",")) {
			String[] split = ip.split(",");
			ip = split[0];
		}
		return ip;
	}

	/**
	 * 获取mac地址
	 *
	 * @return {@link String }
	 * @throws Exception 例外
	 */
	public static String getMacAddress() throws Exception {
		// 取mac地址
		byte[] macAddressBytes = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
		// 下面代码是把mac地址拼装成String
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < macAddressBytes.length; i++) {
			if (i != 0) {
				sb.append("-");
			}
			// mac[i] & 0xFF 是为了把byte转化为正整数
			String s = Integer.toHexString(macAddressBytes[i] & 0xFF);
			sb.append(s.length() == 1 ? 0 + s : s);
		}
		return sb.toString().trim().toUpperCase();
	}

	/**
	 * 获取请求正文
	 *
	 * @param request 请求
	 * @return {@link Map }
	 * @throws IOException 读取请求数据时可能抛出的异常
	 */
	@SneakyThrows
	public static Map<String, Object> getRequestBody(HttpServletRequest request) {
		Map<String, Object> resultMap = new HashMap<>();
		String contentType = request.getContentType();

		if (contentType != null) {
			if (contentType.contains("application/json")) {
				resultMap.putAll(getJsonData(request));
			} else if (contentType.contains("application/x-www-form-urlencoded")) {
				resultMap.putAll(getFormData(request));
			}
		}

		return resultMap;
	}

	private static Map<String, Object> getJsonData(HttpServletRequest request) throws IOException {
		// 读取请求体内容为字符串
		String jsonBody = request.getReader().lines().collect(Collectors.joining());

		// 2. 处理空请求体
		if (jsonBody == null || jsonBody.trim().isEmpty()) {
			return new HashMap<>();
		}


		ObjectMapper objectMapper = new ObjectMapper();
		// 3. 解析JSON为通用对象
		Object parsed = objectMapper.readValue(jsonBody, Object.class);
		// 4. 处理不同JSON结构
		if (parsed instanceof Collection) {
			List temp = (List) parsed;
			// 数组类型特殊处理
			Map<String, Object> result = new HashMap<>();
			for (int i = 0; i < temp.size(); i++) {
				result.put(String.valueOf(i), temp.get(i));
			}
			return result;
		}

		// 使用Jackson库解析JSON字符串
		return objectMapper.readValue(jsonBody, new TypeReference<Map<String, Object>>() {
		});
	}


	private static Map<String, String> getFormData(HttpServletRequest request) {
		Map<String, String> formData = new HashMap<>();


		try {
			String body = new BufferedReader(new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))
					.lines().collect(Collectors.joining(System.lineSeparator()));

			StringTokenizer tokenizer = new StringTokenizer(body, "&");
			while (tokenizer.hasMoreTokens()) {
				String pair = tokenizer.nextToken();
				int idx = pair.indexOf('=');
				if (idx > 0) {
					String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
					String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
					formData.put(key, value);
				}
			}
		} catch (IOException e) {
			e.printStackTrace(); // Handle errors appropriately in production use
		}

		return formData;
	}

	/**
	 * 从HttpServletRequest中获取请求体
	 *
	 * @param request HttpServletRequest对象
	 * @return 请求体的内容
	 * @throws IOException 如果读取请求体时发生I/O错误
	 */
	public static String getBody(HttpServletRequest request) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		try (BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
			stringBuilder.append(bufferedReader.lines().collect(Collectors.joining(System.lineSeparator())));
		}
		return stringBuilder.toString();
	}


	/**
	 * 尝试多种变体格式从Request中获取Header值
	 *
	 * @param request   HTTP请求对象
	 * @param headerKey 原始Header Key
	 * @return 找到的第一个有效Header值，未找到返回null
	 */
	public static String getHeaderWithVariants(HttpServletRequest request, String headerKey) {
		if (!StringUtils.hasText(headerKey)) {
			return null;
		}

		// 定义所有可能的Header Key变体
		Set<String> possibleKeys = generateHeaderKeyVariants(headerKey);

		// 按顺序尝试所有可能Key
		for (String key : possibleKeys) {
			String value = request.getHeader(key);
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}

	/**
	 * 生成指定Header Key的所有可能变体格式
	 *
	 * @param headerKey 原始Header Key
	 * @return 包含所有唯一变体的有序集合
	 */
	private static Set<String> generateHeaderKeyVariants(String headerKey) {
		Set<String> variants = new LinkedHashSet<>();

		// 1. 原始Key
		variants.add(headerKey);

		// 2. 大小写变体
		variants.add(headerKey.toLowerCase());
		variants.add(headerKey.toUpperCase());
		variants.add(capitalizeEachWord(headerKey, '-'));

		// 3. 分隔符变体（横线转下划线）
		if (headerKey.contains("-")) {
			String underscoreKey = headerKey.replace('-', '_');
			variants.add(underscoreKey);
			variants.add(underscoreKey.toLowerCase());
			variants.add(underscoreKey.toUpperCase());
			variants.add(capitalizeEachWord(underscoreKey, '_'));
		}

		// 4. 分隔符变体（下划线转横线）- 仅当原始key包含下划线时
		if (headerKey.contains("_")) {
			String dashKey = headerKey.replace('_', '-');
			variants.add(dashKey);
			variants.add(dashKey.toLowerCase());
			variants.add(dashKey.toUpperCase());
			variants.add(capitalizeEachWord(dashKey, '-'));
		}

		return variants;
	}

	// 辅助方法：将横线分隔的单词首字母大写（e.g., "olink-cdn-token" -> "Olink-Cdn-Token"）
	private static String capitalizeEachWord(String str, char delimiter) {
		if (!StringUtils.hasText(str)) {
			return str;
		}
		String[] parts = str.split(Pattern.quote(String.valueOf(delimiter)));
		return Arrays.stream(parts)
				.map(part -> part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase())
				.collect(Collectors.joining(String.valueOf(delimiter)));
	}


}
