package io.pragmatic.ddd.base.id;

/**
 * 生成器定义：描述一个 bizKey 对应的 ID 空间如何初始化与格式化。
 * 作为生成器的初始化配置，可由配置 / 建表初值提供。
 *
 * @author wizard-lee
 */
public class IdGeneratorDefinition {

    /** 业务键（渠道），全局唯一。 */
    private String bizKey;
    /** 起始位置：该渠道第一个 ID（默认 1）。仅用于计算建表初值，不落库。 */
    private long startId = 1;
    /** 号段步长：每次申请多少连续 ID（默认 1000）。 */
    private int step = 1000;
    /** ID 类型。 */
    private IdType idType = IdType.LONG;
    /** 可选格式：如 "ORD-%08d"，仅 idType=STRING 时生效。 */
    private String format;
    /** 可选描述。 */
    private String description;

    public IdGeneratorDefinition() {
    }

    public IdGeneratorDefinition(String bizKey, long startId, int step,
                                 IdType idType, String format, String description) {
        this.bizKey = bizKey;
        this.startId = startId;
        this.step = step;
        this.idType = idType;
        this.format = format;
        this.description = description;
    }

    // ===== getters / setters =====
    /** 返回业务键（渠道），全局唯一。 */
    public String getBizKey() { return bizKey; }
    /** 设置业务键（渠道）。 */
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    /** 返回起始 ID（默认 1），仅用于计算建表初值，不落库。 */
    public long getStartId() { return startId; }
    /** 设置起始 ID。 */
    public void setStartId(long startId) { this.startId = startId; }
    /** 返回号段步长（默认 1000）。 */
    public int getStep() { return step; }
    /** 设置号段步长。 */
    public void setStep(int step) { this.step = step; }
    /** 返回 ID 类型。 */
    public IdType getIdType() { return idType; }
    /** 设置 ID 类型。 */
    public void setIdType(IdType idType) { this.idType = idType; }
    /** 返回可选格式（如 "ORD-%08d"），仅 STRING 类型生效。 */
    public String getFormat() { return format; }
    /** 设置可选格式。 */
    public void setFormat(String format) { this.format = format; }
    /** 返回可选描述。 */
    public String getDescription() { return description; }
    /** 设置可选描述。 */
    public void setDescription(String description) { this.description = description; }
}
