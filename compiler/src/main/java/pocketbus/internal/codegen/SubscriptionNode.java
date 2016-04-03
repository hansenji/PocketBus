package pocketbus.internal.codegen;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.type.TypeMirror;

public class SubscriptionNode {
    private final TypeMirror typeMirror;
    private final List<SubscriptionNode> children = new ArrayList<>();

    public SubscriptionNode(TypeMirror typeMirror) {
        this.typeMirror = typeMirror;
    }

    public void addChild(SubscriptionNode child) {
        children.add(child);
    }

    public TypeMirror getTypeMirror() {
        return typeMirror;
    }

    public List<SubscriptionNode> getChildren() {
        return children;
    }
}
