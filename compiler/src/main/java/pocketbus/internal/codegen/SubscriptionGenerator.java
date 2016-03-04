package pocketbus.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import pocketbus.Registrar;
import pocketbus.Subscription;
import pocketbus.ThreadMode;

public class SubscriptionGenerator {

    private static final ParameterizedTypeName LIST_TYPE = ParameterizedTypeName.get(ClassName.get(List.class),
            ParameterizedTypeName.get(ClassName.get(Subscription.class), WildcardTypeName.subtypeOf(TypeName.OBJECT)));

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
                .addField(LIST_TYPE, GeneratorConst.VAR_SUBSCRIPTIONS, Modifier.PRIVATE, Modifier.FINAL);

        generateConstructor(classBuilder);
        generateSubscriptions(classBuilder);
        generateMethod(classBuilder);

        return JavaFile.builder(classPackage, classBuilder.build()).build();
    }

    private void generateConstructor(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(targetType), GeneratorConst.VAR_TARGET)
                .addStatement("this.$N = $N", GeneratorConst.VAR_TARGET, GeneratorConst.VAR_TARGET)
                .addStatement("$T $N = new $T()", LIST_TYPE, GeneratorConst.VAR_SUBSCRIPTIONS, ParameterizedTypeName.get(ClassName.get(ArrayList.class),
                        ParameterizedTypeName.get(ClassName.get(Subscription.class), WildcardTypeName.subtypeOf(TypeName.OBJECT))));

        for (SubscriptionMethod method : methods) {
            builder.addStatement("$N.add($N)", GeneratorConst.VAR_SUBSCRIPTIONS, GeneratorConst.VAR_SUBSCRIPTION + method.getIndex());
        }

        builder.addStatement("this.$N = $T.unmodifiableList($N)", GeneratorConst.VAR_SUBSCRIPTIONS, ClassName.get(Collections.class),
                GeneratorConst.VAR_SUBSCRIPTIONS);

        classBuilder.addMethod(builder.build());
    }

    private void generateMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(GeneratorConst.METHOD_GET_SUBSCRIPTIONS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(LIST_TYPE)
                .addStatement("return $N", GeneratorConst.VAR_SUBSCRIPTIONS);

        classBuilder.addMethod(builder.build());
    }

    private void generateSubscriptions(TypeSpec.Builder classBuilder) {
//        StringBuilder stringBuilder = new StringBuilder("$T.unmodifiableList($T.asList(");
//        Object[] subscriptionsArgs = new Object[methods.size() + 2];
//        subscriptionsArgs[0] = ClassName.get(Collections.class);
//        subscriptionsArgs[1] = ClassName.get(Arrays.class);

//        int i = 2;
        for (SubscriptionMethod subscription : methods) {
            String name = GeneratorConst.VAR_SUBSCRIPTION + subscription.getIndex();

            ParameterizedTypeName subscriptionType = ParameterizedTypeName.get(ClassName.get(Subscription.class),
                    TypeName.get(subscription.getEventType()));

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(subscriptionType, name, Modifier.PRIVATE);
            fieldBuilder.initializer("$L", generateAnonymousSubscription(subscriptionType, subscription));
            classBuilder.addField(fieldBuilder.build());
//            subscriptionsArgs[i] = name;
//            if (i++ > 2) {
//                stringBuilder.append(",");
//            }
//            stringBuilder.append("$N");
        }

//        stringBuilder.append("))");

//        FieldSpec.Builder subscriptionsBuilder = FieldSpec.builder(LIST_TYPE, GeneratorConst.VAR_SUBSCRIPTIONS, Modifier.PRIVATE, Modifier.FINAL);
//        subscriptionsBuilder.initializer(stringBuilder.toString(), subscriptionsArgs);
//        classBuilder.addField(subscriptionsBuilder.build());
    }

    private TypeSpec generateAnonymousSubscription(ParameterizedTypeName subscriptionType, SubscriptionMethod subscription) {
        TypeSpec.Builder builder = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(subscriptionType);

        generateHandleMethod(builder, subscription);
        generateClassMethod(builder, subscription);
        generateThreadMethod(builder, subscription);

        return builder.build();
    }

    private void generateHandleMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscriptionMethod) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_HANDLE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.get(subscriptionMethod.getEventType()), GeneratorConst.VAR_EVENT)
                .addStatement("$N.$L($N)", GeneratorConst.VAR_TARGET, subscriptionMethod.getName(), GeneratorConst.VAR_EVENT);

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateClassMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscription) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_GET_EVENT_CLASS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.get(subscription.getEventType())))
                .addStatement("return $T.class", TypeName.get(subscription.getEventType()));

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateThreadMethod(TypeSpec.Builder classBuilder, SubscriptionMethod subscription) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(GeneratorConst.METHOD_GET_THREAD_MODE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.get(ThreadMode.class))
                .addStatement("return $T.$L", TypeName.get(ThreadMode.class), subscription.getThreadMode());

        classBuilder.addMethod(methodBuilder.build());
    }
}
