package io.pragmatic.ddd.visual;

public class MockValueObject {

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isYes() {
        return yes;
    }

    public void setYes(boolean yes) {
        this.yes = yes;
    }

    private String name;
    private boolean yes;
}
