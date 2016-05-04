package pocketbus.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import pocketbus.SubscriptionRegistration;
import pocketbus.Subscription;
import pocketbus.ThreadMode;
import pocketbus.internal.PocketBusConst;

public class SubscriptionGenerator {

    private static final ParameterizedTypeName SUBSCRIPTION_TYPE = ParameterizedTypeName.get(ClassName.get(Subscription.class),
            WildcardTypeName.subtypeOf(TypeName.OBJECT));
    private static final ParameterizedTypeName LIST_TYPE = ParameterizedTypeName.get(ClassName.get(List.class), SUBSCRIPTION_TYPE);

    Set<SubscriptionMethod> methods = new LinkedHashSet<>();

    private final String classPackage;
    private final String className;
    private final TypeMirror targetType;
    private ClassName parentAdapter;
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
                .addModifiers(Modifier.PUBLIC)
                .addField(getWeakReferenceType(), PocketBusConst.VAR_TARGET_REF, Modifier.PRIVATE, Modifier.FINAL)
                .addField(LIST_TYPE, PocketBusConst.VAR_SUBSCRIPTIONS, Modifier.PRIVATE, Modifier.FINAL);

        if (parentAdapter != null) {
            classBuilder.superclass(parentAdapter);
        } else {
            classBuilder.addSuperinterface(ClassName.get(SubscriptionRegistration.class));
        }

        generateConstructor(classBuilder);
        generateSubscriptions(classBuilder);
        generateMethod(classBuilder);

        return JavaFile.builder(classPackage, classBuilder.build()).build();
    }

    public void setParentAdapter(String packageName, String className) {
        this.parentAdapter = ClassName.get(packageName, className);
    }

    public String getParentAdapterName() {
        return parentAdapter != null ? parentAdapter.toString() : null;
    }

    public String getAdapterName() {
        return ClassName.get(classPackage, className).toString();
    }

    private void generateConstructor(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(targetType), PocketBusConst.VAR_TARGET);

        if (parentAdapter != null) {
            builder.addStatement("super($N)", PocketBusConst.VAR_TARGET);
        }

        builder.addStatement("this.$N = new $T($N)", PocketBusConst.VAR_TARGET_REF, getWeakReferenceType(), PocketBusConst.VAR_TARGET)
                .addStatement("$T $N = new $T()", LIST_TYPE, PocketBusConst.VAR_SUBSCRIPTIONS, ParameterizedTypeName.get(ClassName.get(ArrayList.class),
                        ParameterizedTypeName.get(ClassName.get(Subscription.class), WildcardTypeName.subtypeOf(TypeName.OBJECT))));

        if (parentAdapter != null) {
            builder.addStatement("$N.addAll(super.$L())", PocketBusConst.VAR_SUBSCRIPTIONS, PocketBusConst.METHOD_GET_SUBSCRIPTIONS);
        }

        for (SubscriptionMethod method : methods) {
            builder.addStatement("$N.add($N)", PocketBusConst.VAR_SUBSCRIPTIONS, PocketBusConst.VAR_SUBSCRIPTION + method.getIndex());
        }

        builder.addStatement("this.$N = $T.unmodifiableList($N)", PocketBusConst.VAR_SUBSCRIPTIONS, ClassName.get(Collections.class),
                PocketBusConst.VAR_SUBSCRIPTIONS);

        classBuilder.addMethod(builder.build());
    }

    private void generateMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(PocketBusConst.METHOD_GET_SUBSCRIPTIONS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(LIST_TYPE)
                .addStatement("return $N", PocketBusConst.VAR_SUBSCRIPTIONS);

        classBuilder.addMethod(builder.build());
    }

    private void generateSubscriptions(TypeSpec.Builder classBuilder) {
        for (SubscriptionMethod subscription : methods) {
            String name = PocketBusConst.VAR_SUBSCRIPTION + subscription.getIndex();

            ParameterizedTypeName subscriptionType = ParameterizedTypeName.get(ClassName.get(Subscription.class),
                    TypeName.get(subscription.getEventType()));

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(subscriptionType, name, Modifier.PRIVATE);
            fieldBuilder.initializer("$L", generateAnonymousSubscription(subscriptionType, subscription));
            classBuilder.addField(fieldBuilder.build());
        }
    }

    private TypeSpec generateAnonymousSubscription(ParameterizedTypeName subscriptionType, SubscriptionMethod subscription) {
        TypeSpec.Builder builder = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(subscriptionType);

        generateHandleMethod(builder, subscription);
        generateClassMethod(builder, subscription);
        generateThreadMethod(builder, subscription);
        generateGetTargetMethod(builder);
        generateEqualsMethod(builder);

        return builder.build();
    }

    private void generateHandleMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscriptionMethod) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_HANDLE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.get(subscriptionMethod.getEventType()), PocketBusConst.VAR_EVENT)
                .returns(TypeName.BOOLEAN)
                .addStatement("$T $N = $N.get()", TypeName.get(targetType), PocketBusConst.VAR_TARGET, PocketBusConst.VAR_TARGET_REF)
                .beginControlFlow("if ($N != null)", PocketBusConst.VAR_TARGET)
                .addStatement("$N.$L($N)", PocketBusConst.VAR_TARGET, subscriptionMethod.getName(), PocketBusConst.VAR_EVENT)
                .endControlFlow()
                .addStatement("return $N != null", PocketBusConst.VAR_TARGET)
                ;

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateClassMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscription) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_GET_EVENT_CLASS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.get(subscription.getEventType())))
                .addStatement("return $T.class", TypeName.get(subscription.getEventType()));

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateThreadMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscription) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_GET_THREAD_MODE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.get(ThreadMode.class))
                .addStatement("return $T.$L", TypeName.get(ThreadMode.class), subscription.getThreadMode());

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateGetTargetMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_GET_TARGET)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.get(targetType))
                .addStatement("return $N.get()", PocketBusConst.VAR_TARGET_REF);

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateEqualsMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(PocketBusConst.METHOD_EQUALS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.OBJECT, PocketBusConst.VAR_O)
                .returns(TypeName.BOOLEAN)
                .beginControlFlow("if ($N instanceof $T)", PocketBusConst.VAR_O, SUBSCRIPTION_TYPE)
                .addStatement("return this.getTarget() != null && this.getTarget().equals((($T)$N).getTarget())", SUBSCRIPTION_TYPE, PocketBusConst.VAR_O)
                .endControlFlow()
                .addStatement("return false");

        classBuilder.addMethod(methodBuilder.build());
    }

    private TypeName getWeakReferenceType() {
        return ParameterizedTypeName.get(ClassName.get(WeakReference.class), TypeName.get(targetType));
    }
}
