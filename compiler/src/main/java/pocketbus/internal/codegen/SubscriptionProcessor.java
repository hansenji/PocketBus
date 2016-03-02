package pocketbus.internal.codegen;

import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import pocketbus.Subscribe;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

public class SubscriptionProcessor {

    private final Messager messager;
    private final Elements elements;

    public SubscriptionProcessor(Messager messager, Elements elements) {
        this.messager = messager;
        this.elements = elements;
    }

    public Map<TypeElement, SubscriptionGenerator> findAndParseTargets(RoundEnvironment roundEnv) {
        LinkedHashMap<TypeElement, SubscriptionGenerator> targetMap = new LinkedHashMap<>();
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Subscribe.class);
        for (Element element : elements) {
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

            Subscribe subscribeAnnotation = element.getAnnotation(Subscribe.class);
            if (subscribeAnnotation == null) {
                messager.printMessage(WARNING, String.format("@%s annotation not found on %s", Subscribe.class.getSimpleName(), element), element);
                continue;
            }

            if (!(element instanceof ExecutableElement) || element.getKind() != ElementKind.METHOD) {
                error(String.format("@%s annotation must be on a method (%s.%s)", Subscribe.class.getSimpleName(), enclosingElement.getQualifiedName(),
                        element.getSimpleName()), element);
            }

            ExecutableElement executableElement = (ExecutableElement) element;

            validateMethod(executableElement);
            validateBindingPackage(element);
            validateVisibility(element);

            SubscriptionGenerator generator = getOrCreateTargetClass(targetMap, enclosingElement);
            SubscriptionMethod method = new SubscriptionMethod(executableElement, subscribeAnnotation.value());
            if (!generator.addMethod(method)) {
                error(String.format("@%s method cannot have multiple subscriptions for type %s on ThreadMode.%s (%s.%s)",
                        Subscribe.class.getSimpleName(), method.getEventType(), method.getThreadMode(), enclosingElement.getQualifiedName(),
                        element.getSimpleName()), element);
            }
        }
        return targetMap;
    }

    private void validateVisibility(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        TypeElement enclosingElement = (TypeElement)element.getEnclosingElement();
        if (!Collections.disjoint(modifiers, ImmutableSet.of(Modifier.STATIC, Modifier.PRIVATE, Modifier.PROTECTED))) {
            throw new IllegalStateException(String.format("@%s method must not be static, private, nor protected (%s.%s)",
                    Subscribe.class.getSimpleName(), enclosingElement.getQualifiedName(), element.getSimpleName()));
        }

        if (enclosingElement.getModifiers().contains(Modifier.PRIVATE)) {
            throw new IllegalStateException(String.format("@%s method must not be contained in a private class (%s.%s)",
                    Subscribe.class.getSimpleName(), enclosingElement.getQualifiedName(), element.getSimpleName()));
        }
    }

    private void validateBindingPackage(Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith(pocketbus.internal.codegen.GeneratorConst.ANDROID_PREFIX)) {
            error(String.format("@%s-annotated interface incorrectly in Android framework package. (%s.%s)", Subscribe.class.getSimpleName(), qualifiedName,
                    element.getSimpleName()), element);
        }
        if (qualifiedName.startsWith(pocketbus.internal.codegen.GeneratorConst.JAVA_PREFIX)) {
            error(String.format("@%s-annotated interface incorrectly in Java framework package. (%s.%s)", Subscribe.class.getSimpleName(), qualifiedName,
                    element.getSimpleName()), element);
        }
    }

    private void validateMethod(ExecutableElement element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        if (element.getParameters().size() != 1) {
            error(String.format("@%s method shalt have only ONE parameter no more no less (%s.%s)", Subscribe.class.getSimpleName(),
                    enclosingElement.getQualifiedName(), element.getSimpleName()), element);
        }

        if (element.getReturnType().getKind() != TypeKind.VOID) {
            error(String.format("@%s method must return void (%s.%s)", Subscribe.class.getSimpleName(), enclosingElement.getQualifiedName(),
                    element.getSimpleName()), element);
        }

        if (!element.getThrownTypes().isEmpty()) {
            error(String.format("@%s method cannot declare thrown Exceptions (%s.%s)", Subscribe.class.getSimpleName(), enclosingElement.getQualifiedName(),
                    element.getSimpleName()), element);
        }

        if (element.getParameters().get(0).asType().getKind().isPrimitive()) {
            error(String.format("@%s method cannot have a primitive as a parameter (%s.%s)", Subscribe.class.getSimpleName(),
                    enclosingElement.getQualifiedName(), element.getSimpleName()), element);
        }
    }

    private SubscriptionGenerator getOrCreateTargetClass(LinkedHashMap<TypeElement, SubscriptionGenerator> targetMap, TypeElement element) {
        SubscriptionGenerator generator = targetMap.get(element);
        if (generator == null) {
            TypeMirror targetType = element.asType();
            String classPackage = getPackageName(element);
            String className = getClassName(element, classPackage) + pocketbus.internal.codegen.GeneratorConst.REGISTRAR_SUFFIX;

            generator = new SubscriptionGenerator(classPackage, className, targetType);
            targetMap.put(element, generator);
        }
        return generator;
    }

    private String getPackageName(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }

    private String getClassName(TypeElement typeElement, String packageName) {
        int packageLen = packageName.length() + 1;
        return typeElement.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }

    private void error(String message, Element element) {
        messager.printMessage(ERROR, message, element);
    }
}
