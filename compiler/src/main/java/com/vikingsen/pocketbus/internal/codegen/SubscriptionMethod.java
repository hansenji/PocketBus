package com.vikingsen.pocketbus.internal.codegen;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class SubscriptionMethod {
    private final TypeMirror eventType;
    private final String name;
    private final com.vikingsen.pocketbus.ThreadMode threadMode;
    private int index;

    public SubscriptionMethod(ExecutableElement executableElement, com.vikingsen.pocketbus.ThreadMode threadMode) {
        this.name = executableElement.getSimpleName().toString();
        this.eventType = executableElement.getParameters().get(0).asType();
        this.threadMode = threadMode;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getEventType() {
        return eventType;
    }

    public com.vikingsen.pocketbus.ThreadMode getThreadMode() {
        return threadMode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SubscriptionMethod) {
            SubscriptionMethod otherSubMethod = (SubscriptionMethod) obj;
            return eventType.equals(otherSubMethod.eventType) && threadMode == otherSubMethod.threadMode;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = eventType.hashCode();
        result = 31 * result + threadMode.hashCode();
        return result;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
