package api.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性注解
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Idempotent {

	/**
	 * <p>如果是实体类的话,默认拦截不会生效. objects.toString()会返回不同地址.</p>
	 * 幂等操作的唯一标识，使用spring el表达式 用#来引用方法参数
	 *
	 * @return Spring-EL expression
	 */
	String key() default "";

	/**
	 * 令牌标识符
	 * <p>从请求头中获取令牌值作为幂等标识</p>
	 *
	 * @return 请求头中的令牌字段名
	 */
	String tokenHeader() default "";

	/**
	 * 有效期 默认：1 (有效期要大于程序执行时间)
	 *
	 * @return expireTime
	 */
	int expireTime() default 1;

	/**
	 * 时间单位 默认：秒
	 *
	 * @return TimeUnit
	 */
	TimeUnit timeUnit() default TimeUnit.SECONDS;

	/**
	 * 提示信息
	 *
	 * @return String
	 */
	String info() default "重复请求，请稍后重试";

	/**
	 * 是否在业务完成后删除key
	 *
	 * @return boolean
	 */
	boolean delKey() default false;

	/**
	 * 幂等验证类型（新增） - 默认 宽泛模式
	 *
	 * @return IdempotentType
	 */
	IdempotentType type() default IdempotentType.LENIENT;


	/**
	 * 延迟检查时间（秒）（新增）
	 */
	long delayCheckSeconds() default 10;


	/**
	 * 幂等类型枚举（新增内部枚举）
	 */
	enum IdempotentType {
		/**
		 * 严格模式：相同请求参数视为重复请求
		 */
		STRICT,

		/**
		 * 宽松模式：仅验证请求标识符(ID/token)的重复性
		 */
		LENIENT
	}
	boolean enableSignVerify() default false;
}