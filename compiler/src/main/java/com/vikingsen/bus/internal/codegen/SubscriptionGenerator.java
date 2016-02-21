package com.vikingsen.bus.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.vikingsen.bus.Bus;
import com.vikingsen.bus.EventSubscription;
import com.vikingsen.bus.Registrar;
import com.vikingsen.bus.ThreadMode;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

public class SubscriptionGenerator {

    Set<SubscriptionMethod> methods = new LinkedHashSet<>();

    private final String classPackage;
    private final String className;
    private final TypeMirror targetType;
    private int idx = 1;

    public SubscriptionGenerator(String classPackage, String className, TypeMirror targetType) {
        this.classPackage = classPackage;
        this.className = className;
        this.targetType = targetType;
    }

    public boolean addMethod(SubscriptionMethod method) {
        method.setIndex(idx++);
        return this.methods.add(method);
    }

    public JavaFile generate() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addSuperinterface(ClassName.get(Registrar.class))
                .addField(TypeName.get(targetType), GeneratorConst.VAR_TARGET, Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.get(targetType), GeneratorConst.VAR_TARGET)
                        .addStatement("this.$N = $N", GeneratorConst.VAR_TARGET, GeneratorConst.VAR_TARGET)
                        .build());

        generateMethods(classBuilder);
        generateSubscriptions(classBuilder);

        return JavaFile.builder(classPackage, classBuilder.build()).build();
    }

    private void generateMethods(TypeSpec.Builder classBuilder) {
        generateRegisterMethod(classBuilder);
        generateUnregisterMethod(classBuilder);
//
//        for (SubscriptionMethod subscriptionMethod : methods) {
//            generateSubscriptionMethod(classBuilder, subscriptionMethod);
//        }
//
//        generateEqualsMethod(classBuilder);
    }

    private void generateRegisterMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_REGISTER)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(Bus.class), GeneratorConst.VAR_BUS);

        for (SubscriptionMethod subscription : methods) {
            methodBuilder.addStatement("$N.$L($T.class, $N, $T.$L)", GeneratorConst.VAR_BUS, GeneratorConst.METHOD_REGISTER,
                    TypeName.get(subscription.getEventType()), GeneratorConst.VAR_SUBSCRIPTION + subscription.getIndex(), ClassName.get(ThreadMode.class),
                    subscription.getThreadMode());
        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateUnregisterMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_UNREGISTER)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(Bus.class), GeneratorConst.VAR_BUS);

        for (SubscriptionMethod subscription : methods) {
            methodBuilder.addStatement("$N.$L($T.class, $N)", GeneratorConst.VAR_BUS, GeneratorConst.METHOD_UNREGISTER,
                    TypeName.get(subscription.getEventType()), GeneratorConst.VAR_SUBSCRIPTION + subscription.getIndex());
        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateSubscriptions(TypeSpec.Builder classBuilder) {
        for (SubscriptionMethod subscription : methods) {
            ParameterizedTypeName subscriptionType = ParameterizedTypeName.get(ClassName.get(EventSubscription.class),
                    TypeName.get(subscription.getEventType()));
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(subscriptionType, GeneratorConst.VAR_SUBSCRIPTION + subscription.getIndex(),
                    Modifier.PRIVATE);
            fieldBuilder.initializer("$L", generateAnonymouseSubscription(subscriptionType, subscription));
            classBuilder.addField(fieldBuilder.build());
        }
    }

    private TypeSpec generateAnonymouseSubscription(ParameterizedTypeName subscriptionType, SubscriptionMethod subscription) {
        TypeSpec.Builder builder = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(subscriptionType);

        generateSubscriptionMethod(builder, subscription);
//        generateEqualsMethod(builder);

        return builder.build();
    }

//    private void generateEqualsMethod(TypeSpec.Builder classBuilder) {
//        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_EQUALS)
//                .addModifiers(Modifier.PUBLIC)
//                .addParameter(ClassName.get(Object.class), GeneratorConst.VAR_O)
//                .returns(TypeName.BOOLEAN)
//                .beginControlFlow("if ($N instanceof $N)", GeneratorConst.VAR_O, className)
//                .addStatement("return $N == (($N)$N).$N", GeneratorConst.VAR_TARGET, className, GeneratorConst.VAR_O, GeneratorConst
//                        .VAR_TARGET)
//                .endControlFlow()
//                .addStatement("return false");
//
//        classBuilder.addMethod(methodBuilder.build());
//    }

    private void generateSubscriptionMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscriptionMethod) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_HANDLE)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(subscriptionMethod.getEventType()), GeneratorConst.VAR_EVENT)
                .addStatement("$N.$L($N)", GeneratorConst.VAR_TARGET, subscriptionMethod.getName(), GeneratorConst.VAR_EVENT);

        classBuilder.addMethod(methodBuilder.build());
    }
}
