package pocketbus.internal.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import pocketbus.Registry;

public class RegistryProcessor {
    private final Elements elements;

    public RegistryProcessor(Elements elements) {
        this.elements = elements;
    }

    public RegistryGenerator findAndParseTarget(RoundEnvironment roundEnv, Map<TypeElement, SubscriptionGenerator> subscriptionMap) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Registry.class);
        if (elements.isEmpty()) {
            if (!subscriptionMap.isEmpty()) {
                throw new IllegalStateException("Missing @Registry. Please annotate 1 class with @Registry");
            }
            return null;
        }
        if (elements.size() > 1) {
            throw new IllegalStateException("Only 1 @Registry allowed");
        }
        Element element = elements.iterator().next();
        if (element instanceof TypeElement) {
            TypeElement typeElement = (TypeElement) element;
            String packageName = getPackageName(typeElement);
            RegistryGenerator generator = new RegistryGenerator(typeElement, packageName);
            generator.setSubscriptionTrees(generateSubscriptionTrees(subscriptionMap));
            return generator;
        } else {
            throw new IllegalStateException("Element is invalid: " + element);
        }
    }

    private List<SubscriptionNode> generateSubscriptionTrees(Map<TypeElement, SubscriptionGenerator> subscriptionMap) {
        Map<String, SubscriptionNode> nodeMap = new HashMap<>();
        List<SubscriptionNode> roots = new ArrayList<>();
        for (Map.Entry<TypeElement, SubscriptionGenerator> entry : subscriptionMap.entrySet()) {
            String adapterName = entry.getValue().getAdapterName();
            SubscriptionNode node = new SubscriptionNode(entry.getKey().asType());
            nodeMap.put(adapterName, node);
        }
        for (SubscriptionGenerator generator : subscriptionMap.values()) {
            SubscriptionNode node = nodeMap.get(generator.getAdapterName());
            if (node == null) {
                throw new IllegalStateException("Invalid Map Generation Missed generating Node");
            }

            String parentAdapterName = generator.getParentAdapterName();
            if (parentAdapterName == null) {
                roots.add(node);
            } else {
                nodeMap.get(parentAdapterName).addChild(node);
            }
        }
        return roots;
    }

    private String getPackageName(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }
}
