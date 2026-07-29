package io.pragmatic.ddd.base.id;

/**
 * 生成器定义：描述一个 bizKey 对应的 ID 空间如何初始化与格式化。
 * 作为生成器的「出生证明」，可由配置 / 建表初值提供。
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
    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public long getStartId() { return startId; }
    public void setStartId(long startId) { this.startId = startId; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public IdType getIdType() { return idType; }
    public void setIdType(IdType idType) { this.idType = idType; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
