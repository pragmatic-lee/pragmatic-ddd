package io.pragmatic.ddd.action;



public class Action {
    private final String actionCode;
    private final String description;

    public Action(String actionCode, String description) {
        this.actionCode = actionCode;
        this.description = description;
    }

    public String getActionCode() {
        return actionCode;
    }

    public String getDescription() {
        return description;
    }

    public static Action build(String actionCode) {
        return new Action(actionCode, "");
    }

    public static Action build(String actionCode, String description) {
        return new Action(actionCode, description);
    }
}
