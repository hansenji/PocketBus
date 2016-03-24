package pocketbus.internal.codegen;

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import pocketbus.Registry;

public class RegistryProcessor {
    private final Messager messager;
    private final Elements elements;

    public RegistryProcessor(Messager messager, Elements elements) {
        this.messager = messager;
        this.elements = elements;
    }

    public RegistryGenerator findAndParseTarget(RoundEnvironment roundEnv, Map<TypeElement, SubscriptionGenerator> subscriptionMap) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Registry.class);
        if (elements.isEmpty()) {
            if (!subscriptionMap.isEmpty()) {
                throw new IllegalStateException("Missing @Registry");
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
            for (TypeElement targetElement : subscriptionMap.keySet()) {
                generator.addTargetType(targetElement.asType());
            }
            return generator;
        } else {
            throw new IllegalStateException("Element is invalid: " + element);
        }
    }

    private String getPackageName(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }
}
