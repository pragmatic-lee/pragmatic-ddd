package io.pragmatic.ddd.visual.application;


import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.visual.MockEntity;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.out;

public class CommandParserTest {


    @Test
    public void commandTest() {

        ApplicationServiceParser commandParser = new ApplicationServiceParser();
        commandParser.registerApplicationService(MockEntity.class, new MockCommandFinder());

        List<ApplicationDescriptor> parser = commandParser.parser(MockEntity.class);

        assert parser.size() == 2;

        out.println(JSON.toJSONString(parser));

    }

    static class MockCommandFinder implements IApplicationServiceFinder {

        @Override
        public <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls) {

            return Stream.of(MockCommandService.class).collect(Collectors.toList());
        }
    }
}

