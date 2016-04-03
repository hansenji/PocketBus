package pocketbus.internal.codegen;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.JavaFile;

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import pocketbus.Subscribe;

import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
public final class BusProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;
    private SubscriptionProcessor subscriptionProcessor;
    private RegistryProcessor registryProcessor;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        elements = processingEnv.getElementUtils();
        types = processingEnv.getTypeUtils();
        // Create processors
        subscriptionProcessor = new SubscriptionProcessor(messager, elements);
        registryProcessor = new RegistryProcessor(elements);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(Subscribe.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(Diagnostic.Kind.NOTE, "*****Bus Processor*****");
        Map<TypeElement, SubscriptionGenerator> subscriptionMap = subscriptionProcessor.findAndParseTargets(roundEnv);
        for (Map.Entry<TypeElement, SubscriptionGenerator> entry : subscriptionMap.entrySet()) {
            TypeElement typeElement = entry.getKey();
            SubscriptionGenerator generator = entry.getValue();
            try {
                JavaFile javaFile = generator.generate();
                javaFile.writeTo(filer);
            } catch (Exception e) {
                error(typeElement, "Unable to generate subscriptions for %s: %s", typeElement, e.getMessage());
            }
        }
        // Get registry
        RegistryGenerator registryGenerator = registryProcessor.findAndParseTarget(roundEnv, subscriptionMap);
        if (registryGenerator != null) {
            try {
                JavaFile javaFile = registryGenerator.generate();
                javaFile.writeTo(filer);
            } catch (Exception e) {
                error(registryGenerator.getTypeElement(), "Unable to generate registry %s", e.getMessage());
            }
        }

        return false;
    }

    private void error(TypeElement typeElement, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        messager.printMessage(ERROR, message, typeElement);
    }
}
