package io.pragmatic.ddd.mybatis.id;

/**
 * id_segment 表映射 POJO。注：无 start_id 字段——起始值仅在插行时作为 current_max_id 初值，不落库。
 *
 * @author wizard-lee
 */
public class IdSegmentEntity {

    private String bizKey;
    private long currentMaxId;
    private int step;
    private long version;
    private String remark;

    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public long getCurrentMaxId() { return currentMaxId; }
    public void setCurrentMaxId(long currentMaxId) { this.currentMaxId = currentMaxId; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
