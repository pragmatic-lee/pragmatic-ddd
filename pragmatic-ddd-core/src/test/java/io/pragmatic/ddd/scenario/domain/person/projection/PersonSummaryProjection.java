package io.pragmatic.ddd.scenario.domain.person.projection;

/**
 * 人员概要投影：列表页轻量字段。
 *
 * @author wizard-lee
 */
public final class PersonSummaryProjection implements PersonProjection {

    private final Long id;

    private final long version;

    private final String name;

    private final String departmentId;

    private final String status;

    public PersonSummaryProjection(Long id, long version, String name, String departmentId, String status) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.departmentId = departmentId;
        this.status = status;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public long version() {
        return version;
    }

    public String name() {
        return name;
    }

    public String departmentId() {
        return departmentId;
    }

    public String status() {
        return status;
    }
}
