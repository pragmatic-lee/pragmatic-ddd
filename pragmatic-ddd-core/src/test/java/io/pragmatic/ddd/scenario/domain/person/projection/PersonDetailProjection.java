package io.pragmatic.ddd.scenario.domain.person.projection;

import io.pragmatic.ddd.scenario.domain.person.model.valueobject.Address;

import java.util.List;

/**
 * 人员详情投影：全量字段，含地址与标签。
 *
 * @author wizard-lee
 */
public final class PersonDetailProjection implements PersonProjection {

    private final Long id;

    private final long version;

    private final String name;

    private final String gender;

    private final String age;

    private final String idCard;

    private final String email;

    private final String phone;

    private final String avatarUrl;

    private final String departmentId;

    private final String position;

    private final String employeeNo;

    private final String status;

    private final List<String> tags;

    private final int level;

    private final Address address;

    public PersonDetailProjection(Long id, long version, String name, String gender, String age, String idCard,
            String email, String phone, String avatarUrl, String departmentId, String position,
            String employeeNo, String status, List<String> tags, int level, Address address) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.idCard = idCard;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.departmentId = departmentId;
        this.position = position;
        this.employeeNo = employeeNo;
        this.status = status;
        this.tags = tags;
        this.level = level;
        this.address = address;
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

    public String gender() {
        return gender;
    }

    public String age() {
        return age;
    }

    public String idCard() {
        return idCard;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public String departmentId() {
        return departmentId;
    }

    public String position() {
        return position;
    }

    public String employeeNo() {
        return employeeNo;
    }

    public String status() {
        return status;
    }

    public List<String> tags() {
        return tags;
    }

    public int level() {
        return level;
    }

    public Address address() {
        return address;
    }
}
