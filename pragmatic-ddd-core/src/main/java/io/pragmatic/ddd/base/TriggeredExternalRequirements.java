package io.pragmatic.ddd.base;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部需求收集器：与 TriggeredEvents 同构，承载一次工作单元内聚合声明的外部需求。
 *
 * @author wizard-lee
 */
final class TriggeredExternalRequirements {

    private final List<IExternalRequirement> requirements = new ArrayList<>();

    /** 收集一项外部需求。 */
    void collect(IExternalRequirement requirement) {
        this.requirements.add(requirement);
    }

    /** 返回已声明的外部需求（只读，按声明顺序）。 */
    List<IExternalRequirement> getRequirements() {
        return List.copyOf(this.requirements);
    }

    /** 清空全部需求，由 clearWorkUnitState 调用。 */
    void clear() {
        this.requirements.clear();
    }
}
