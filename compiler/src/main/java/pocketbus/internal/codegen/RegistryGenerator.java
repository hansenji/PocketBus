package pocketbus.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import pocketbus.Registrar;
import pocketbus.internal.PocketBusConst;
import pocketbus.internal.Registry;

public class RegistryGenerator {
    private final Set<TypeMirror> targetTypes = new LinkedHashSet<>();

    private final String packageName;
    private final TypeElement typeElement;

    public RegistryGenerator(TypeElement typeElement, String packageName) {
        this.typeElement = typeElement;
        this.packageName = packageName;
    }

    public boolean addTargetType(TypeMirror targetType) {
        return this.targetTypes.add(targetType);
    }

    public JavaFile generate() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(PocketBusConst.REGISTRY_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(Registry.class));

        generateMethod(classBuilder);

        return JavaFile.builder(packageName, classBuilder.build()).build();
    }

    private void generateMethod(TypeSpec.Builder classBuilder) {
        TypeVariableName t = TypeVariableName.get("T");
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_GET_REGISTRAR)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addTypeVariable(t)
                .addParameter(t, PocketBusConst.VAR_TARGET)
                .returns(Registrar.class);

        boolean first = true;
        for (TypeMirror targetType : targetTypes) {
            if (first) {
                first = false;
                methodBuilder.beginControlFlow("if ($N instanceof $T)", PocketBusConst.VAR_TARGET, targetType);
            } else {
                methodBuilder.nextControlFlow("else if ($N instanceof $T)", PocketBusConst.VAR_TARGET, targetType);
            }
            ClassName registrarClass = ClassName.bestGuess(targetType + PocketBusConst.REGISTRAR_SUFFIX);
            methodBuilder.addStatement("return new $T(($T)$N)", registrarClass, targetType, PocketBusConst.VAR_TARGET);
        }
        if (!first) {
            methodBuilder.endControlFlow();
        }
        methodBuilder.addStatement("return null");

        classBuilder.addMethod(methodBuilder.build());
    }

    public TypeElement getTypeElement() {
        return typeElement;
    }
}
