package io.pragmatic.ddd.visual.application;

public class MockCommandService {

    @CommandServiceVisual(name = "测试", description = "测试描述")
    public void update() {

    }

    @CommandServiceVisual(name = "测试1", description = "测试描述2")
    public void add() {

    }

    @ReadServiceVisual(name = "测试读",description = "测试读")
    public Object getById(){
        return "";
    }
}
