package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.typehandler.demo.ChannelEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.ColorEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.StatusEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.UserProfile;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * TypeHandler 真实库集成测试契约接口（与具体数据库无关）。
 *
 * <p>仅声明方法签名，SQL 由同包同名 {@code TypeHandlerDemoMapper.xml} 提供。
 * 各列通过 XML 的 typeHandler 属性绑定到对应的 TypeHandler 实现。</p>
 *
 * @author wizard-lee
 */
public interface TypeHandlerDemoMapper {

    void insert(TypeHandlerDemoRow row);

    TypeHandlerDemoRow selectById(@Param("id") long id);

    void clear();
}
