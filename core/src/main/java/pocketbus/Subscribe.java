package pocketbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Subscribe {
    /**
     * ThreadMode the subscription is run on
     */
    ThreadMode value() default ThreadMode.CURRENT;
}
