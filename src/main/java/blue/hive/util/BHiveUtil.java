package blue.hive.util;

import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import blue.hive.exception.BHiveRuntimeException;
import blue.hive.jackson.BHiveObjectMapper;
import blue.hive.spring.context.ApplicationContextHolder;
import blue.hive.spring.validation.BHiveFieldError;
import blue.hive.spring.validation.BHiveObjectError;

/**
 * Common Helper Class
 *
 * 주요 오픈소스의 Helper에서 제공되지 않는 일반적인 공통 함수 모음
 *
 * @author DongMan Kwon <dmkwon@intellicode.co.kr>
 */
public class BHiveUtil {

	private static final Logger logger = LoggerFactory.getLogger(BHiveUtil.class);

	/**
	 * Spring Active Profiles 획득
	 */
	public static List<String> getActiveProfiles() {
		ApplicationContext applicationContext = ApplicationContextHolder.getApplicationContext();
		Environment environment = applicationContext.getEnvironment();
		List<String> activeProfiles = Lists.newArrayList(environment.getActiveProfiles());
		if(CollectionUtils.isEmpty(activeProfiles)) {
			activeProfiles = Lists.newArrayList(environment.getDefaultProfiles());
		}
		return activeProfiles;
	}

	/**
	 * Spring Active Profiles 획득 - 서버환경: local, dev, stage, real 중의 한개 값
	 * Kwonyul 추가 : 각 개발자 개별 local-name profile도 가능하도록 수정함
	 */
	public static String getActiveServerProfile() {
		List<String> activeProfiles = getActiveProfiles();
		if (activeProfiles != null 
				&& activeProfiles.size() > 0) {
			for (String activeProfile : activeProfiles) {
				if("local".equals(activeProfile)) {
					return "local"; 
				}
				if("dev".equals(activeProfile)) {
					return "dev"; 
				}
				if("real".equals(activeProfile)) {
					return "real"; 
				}
				if("aws".equals(activeProfile)) {
					return "aws"; 
				}
				if("test".equals(activeProfile)) {
					return "test"; 
				}
			}
			
			if(activeProfiles.get(0).startsWith("local-")) {
				logger.warn("getActiveProfile : Developer Local Profile - " + activeProfiles.get(0));
				return activeProfiles.get(0);
			} 
		} 
		
		logger.warn("getActiveProfile FAILED");
		return null;
	}

	/** Spring Active Profile이 local인지 확인 */
	public static boolean isLocalProfile() {
		return "local".equals(getActiveServerProfile());
	}
	/** Spring Active Profile이 dev인지 확인 */
	public static boolean isDevProfile() {
		return "dev".equals(getActiveServerProfile());
	}	
	/** Spring Active Profile이 real인지 확인 */
	public static boolean isRealProfile() {
		return "real".equals(getActiveServerProfile());
	}	
	/** Spring Active Profile이 aws인지 확인 */
	public static boolean isAwsProfile() {
		return "aws".equals(getActiveServerProfile());
	}	
	/** Spring Active Profile이 test인지 확인 */
	public static boolean isTestProfile() {
		return "test".equals(getActiveServerProfile());
	}	


	//MethodName///////////////////////////////////////////////////////////////////////
	/**
	 * 현재메소드명 획득
	 */
	public static String getCurrentMethodName() {
		return getStackTraceMethodName(0);
	}
	/**
	 * 현재메소드명 또는 호출자의 메소드명을 획득
	 * @param upperDepth 0이면 현재메소드명, 1이상이면 상위 호출 depth 메소드명
	 * @return 메소드명
	 */
	public static String getStackTraceMethodName(final int upperDepth) {
		final StackTraceElement[] steList = Thread.currentThread().getStackTrace();
		int length = steList.length;
		int startIndex = 0;
		for (int i=0; i<length; i++) {
			String methodName = steList[i].getMethodName();
			if(NOT_USER_METHODS.contains(methodName)) {
				startIndex++;
			} else {
				break;
			}
		}
		//[DUMP TEST]
		//for (int i=0; i<length; i++) {
		//	String methodName = steList[i].getMethodName();
		//	logger.debug("{} - {}", i, methodName);
		//}
		return steList[startIndex + upperDepth].getMethodName();
	}
	private static List<String> NOT_USER_METHODS = Arrays.asList("getStackTrace", "getCurrentMethodName", "getStackTraceMethodName");

	//JSON 처리////////////////////////////////////////////////////////////////////////
	/**
	 * 주어진 객체를 JSON문자열로 Serialize (Jackson2 ObjectMapper 사용)
	 * @param object 변환할 객체
	 * @return JSON 문자열
	 *
	 * @see http://wiki.fasterxml.com/JacksonJsonViews
	 * @see http://www.jroller.com/RickHigh/entry/working_with_jackson_json_views
	 */
	public static String toJson(Object object) {
		return toJson(object, null);
	}

	/**
	 * 주어진 객체를 JSON문자열로 Serialize (Jackson2 ObjectMapper 사용)
	 * @param object 변환할 객체
	 * @param serializationView Serialization에 사용할 View Class (<code>@JsonView</code> 참고)
	 * @return JSON 문자열
	 *
	 * @see http://wiki.fasterxml.com/JacksonJsonViews
	 * @see http://www.jroller.com/RickHigh/entry/working_with_jackson_json_views
	 */
	public static String toJson(Object object, Class<?> serializationView) {
		String json;
		ObjectMapper mapper = new BHiveObjectMapper();
		try {
			if(serializationView != null) {
				json = mapper.writerWithView(serializationView).writeValueAsString(object);
			} else {
				json = mapper.writeValueAsString(object);
			}
		} catch (JsonProcessingException e) {
			throw new BHiveRuntimeException("Failed to write as json", e);
		}
		return json.replace("/", "\\/"); //StringEscapeUtils.escapeJson
	}

	/**
	 * 주어진 Json문자열을 객체 변환
	 * @param json JSON문자열
	 * @param valueType 획득할 객체타입
	 * @return 변환된 객체
	 */
	public static <T> T fromJson(String json, Class<T> valueType) {
		ObjectMapper mapper = new BHiveObjectMapper();
		try {
			T value = mapper.readValue(json, valueType);
			return value;
		} catch (Exception e) {
			throw new BHiveRuntimeException("Failed to read from json", e);
		}
	}

	/**
	 * 예외 메시지를 획득 (옵션에 따라 Throwable의 Cause의 메시지도 포함)
	 * @param ex 메시지를 가져올 Throwable 예외
	 * @param includeExceptionClassSimpleName 예외 클래스의 SimpleName을 포함?
	 * @param includeCauseMessage Cuase의 메시지도 포함할 것인가?
	 * @return ExceptionSimpleName: 메시지 (- CauseExceptionSimpleName: 메시지 - ...)
	 */
	public static String getMessageOfException(Throwable ex, boolean includeExceptionClassSimpleName, boolean includeCauseMessage) {
		String message = ((includeExceptionClassSimpleName)? ex.getClass().getSimpleName() + ": " : "") + ex.getMessage();
		if(includeCauseMessage) {
			Throwable innerEx = ex.getCause();
			if(innerEx != null) {
				return message + " - " + getMessageOfException(innerEx, includeExceptionClassSimpleName, includeCauseMessage);
			}
		}
		return message;
	}

	/**
	 * 예외 또는 예외 내부 Cause를 뒤져서 원하는 Type의 예외를 찾는다.
	 * @param ex 예외
	 * @param requiredType 예외 또는 예외내부Cause에서 찾아볼 예외 타입
	 * @return
	 */
	public static <T extends Exception> T findInnerException(Throwable ex, Class<? extends Throwable> requiredType) {
		//		if(ex.getClass() == requiredType) {
		//			return (T) ex;
		//		}
		Throwable cause =  ex.getCause();
		if(cause == null) {
			return null;
		}
		return findInnerException(cause, requiredType);
	}

	/**
	 * 예외 또는 예외 내부 Cause를 뒤져서 원하는 Type이 있는지를 확인한다.
	 * @param ex 예외
	 * @param requiredType 예외 또는 예외내부Cause에서 찾아볼 예외 타입
	 * @return
	 */
	public static Boolean hasInnerException(Throwable ex, Class<? extends Throwable> requiredType) {
		return findInnerException(ex, requiredType) != null;
	}

	/**
	 * 에러 메시지 획득
	 * @param errors 에러정보 객체 (BindingResult등...)
	 * @param messageSource 메시지소스. 에러메시지 변환에 사용
	 * @param locale 로케일. 에러메시지 국제화시 현재 로케일 (null이면 LocaleContextHolder에서 취득)
	 * @param seperator 에러메시지간 구분자
	 * @return 에러메시지
	 */
	public static String buildErrorMessage(Errors errors, MessageSource messageSource, String seperator) {
		StringBuilder sb = new StringBuilder();

		//List<BHiveObjectError> ableObjectErrors = new ArrayList<BHiveObjectError>();
		List<ObjectError> globalErrors = errors.getGlobalErrors();

		for (ObjectError objectError : globalErrors) {
			BHiveObjectError ableObjectError = new BHiveObjectError(objectError);
			//ableObjectErrors.add(ableObjectError);
			sb.append(ableObjectError.getMessage() + seperator);
		}

		//List<BHiveFieldError> ableFieldErrors = new ArrayList<BHiveFieldError>();
		List<FieldError> fieldErrors = errors.getFieldErrors();
		for (FieldError fieldError : fieldErrors) {
			BHiveFieldError ableFieldError = new BHiveFieldError(fieldError, messageSource);
			//ableFieldErrors.add(ableFieldError);
			sb.append(ableFieldError.getMessage() + seperator);
		}

		return sb.toString();
	}

	private static String key = "";
	protected final static String initialVector = "1234567890123456";



	public static String encode(byte[] data) {
		//
		//  trap door
		//
		if (data == null) {	
			return null;
		}
		//
		//  declaration
		//
		byte[] result = null;
		String iv = initialVector;
		Cipher cipher = null;
		SecretKeySpec keyspec = null;
		IvParameterSpec ivspec = null;
		//
		//  encode
		//
		try {
			keyspec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
			ivspec  = new IvParameterSpec(iv.getBytes("UTF-8"));
			cipher  = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, keyspec, ivspec);
			result = cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
		//
		//  done
		//
		String resStr = Base64.encodeBase64String(result);
		return resStr;
	}

	public static byte[] decode(byte[] data) {
		//
		//  trap door
		//
		if (data.length == 0) {
			return null;
		}
		//
		//  declaration
		//
		byte[] result = null;
		String iv = initialVector;
		Cipher cipher = null;
		SecretKeySpec keyspec = null;
		IvParameterSpec ivspec = null;
		//
		//
		//
		try {
			data = Base64.decodeBase64(data);
			keyspec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
			ivspec  = new IvParameterSpec(iv.getBytes("UTF-8"));
			cipher  = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE,  keyspec, ivspec);
			result = cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
		//
		//
		//
		return result;
	}

}