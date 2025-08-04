package api.idempotent.assign;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

public class RsaUtil {
	/**
	 * 验签
	 * @param data 待验签字符串
	 * @param sign Base64 编码后的签名
	 * @param publicKey 公钥（Base64编码）
	 * @return 是否验签通过
	 */
	public static boolean verify(String data, String sign, PublicKey publicKey) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initVerify(publicKey);
			signature.update(data.getBytes(StandardCharsets.UTF_8));

			return signature.verify(Base64.getDecoder().decode(sign));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static String buildSignContent(Map<String, String> params) {
		return params.entrySet()
				.stream()
				.filter(entry -> entry.getValue() != null && !"".equals(entry.getValue()))
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("&"));
	}

	public static PublicKey getPublicKey(String base64PublicKey) throws Exception {
		byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
		X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePublic(spec);
	}
}
