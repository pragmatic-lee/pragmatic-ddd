package io.pragmatic.ddd.acl;

/**
 * 防腐层本地数据转换异常（请求转换、响应转换、查重键提取、已存在记录转换）。
 * 转换失败属于本地问题，不可重试，应由调用方修复入参或映射而非重试。
 *
 * @author wizard-lee
 */
public class AclConversionException extends AclException {

    public AclConversionException(String message, Throwable cause) {
        super(message, cause);
    }

    public AclConversionException(String message) {
        super(message);
    }
}
