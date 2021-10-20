package io.micronaut.inject.beans

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue

import jakarta.inject.Named
import jakarta.inject.Qualifier

class BeanDefinitionSpec extends AbstractTypeElementSpec {

    void 'test dynamic instantiate with constructor'() {
        given:
        def definition = buildBeanDefinition('genctor.Test', '''
package genctor;

import jakarta.inject.*;

@Singleton
class Test {
    Test(Runnable foo) {}
}

''')
        when:
        def instance = definition.constructor.instantiate({} as Runnable)

        then:
        instance != null
    }

    void "test limit the exposed bean types"() {
        given:
        def definition = buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test implements Runnable {
    public void run() {}
}

''')
        expect:
        definition.exposedTypes == [Runnable] as Set
    }

    void "test limit the exposed bean types - reference"() {
        given:
        def reference = buildBeanDefinitionReference('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test implements Runnable {
    public void run() {}
}

''')
        expect:
        reference.exposedTypes == [Runnable] as Set
    }

    void "test fail compilation on invalid exposed bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [java.lang.Runnable] that is not implemented by the bean type")
    }

    void "test exposed types on factory with AOP"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.aop.Logged;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {X.class})
    @Logged
    Y method() {
        return new Y();
    }
}

interface X {
    
}
class Y implements X {
    
}

''')

        then:
        noExceptionThrown()
    }

    void "test fail compilation on exposed subclass of bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = X.class)
class Test {

}

class X extends Test {}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.X] that is not implemented by the bean type")
    }

    void "test fail compilation on exposed subclass of bean type with factory"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {X.class, Y.class})
    X method() {
        return new Y();
    }
}

interface X {
    
}
class Y implements X {
    
}

''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Y] that is not implemented by the bean type")
    }

    void "test exposed bean types with factory invalid type"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {Z.class})
    X method() {
        return new Y();
    }
}

interface Z { }
interface X { }
class Y implements X { }
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Z] that is not implemented by the bean type")
    }

    void 'test order annotation'() {
        given:
        def definition = buildBeanDefinition('test.TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
@Singleton
@Order(value = 10)
class TestOrder {

}
''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void 'test qualifier for named only'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Named("foo")
class Test {

}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
    }

    void 'test no qualifier / only scope'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test {

}
''')
        expect:
        definition.getDeclaredQualifier() == null
    }

    void 'test named via alias'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MockBean(named="foo")
class Test {

}

@Bean
@interface MockBean {

    @AliasFor(annotation = Replaces.class, member = "named")
    @AliasFor(annotation = jakarta.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
    }

    void 'test named via alias and applyDefault'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MockBean
class Test {

}

@Bean
@interface MockBean {

    @AliasFor(annotation = Replaces.class, member = "named", applyDefault = true)
    @AliasFor(annotation = jakarta.inject.Named.class, member = "value", applyDefault = true)
    String named() default "foo";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
        definition.annotationMetadata.stringValue(Replaces, "named").get() == "foo"
    }

    void 'test qualifier annotation'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MyQualifier
class Test {

}

@jakarta.inject.Qualifier
@interface MyQualifier {

    @AliasFor(annotation = Replaces.class, member = "named")
    @AliasFor(annotation = jakarta.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byAnnotation(definition.getAnnotationMetadata(), "test.MyQualifier")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == "test.MyQualifier"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5001")
    void "test building a bean with generics that dont have a type"() {
        when:
        def definition = buildBeanDefinition('test.NumberThingManager', '''
package test;

import jakarta.inject.Singleton;

interface Thing<T> {}

interface NumberThing<T extends Number & Comparable<T>> extends Thing<T> {}

class AbstractThingManager<T extends Thing<?>> {}

@Singleton
public class NumberThingManager extends AbstractThingManager<NumberThing<?>> {}
''')

        then:
        noExceptionThrown()
        definition != null
        definition.getTypeArguments("test.AbstractThingManager")[0].getTypeVariables().get("T").getType() == Number.class
    }

    void "test a bean definition in a package with uppercase letters"() {
        when:
        def definition = buildBeanDefinition('test.A', 'TestBean', '''
package test.A;

@jakarta.inject.Singleton
class TestBean {

}
''')
        then:
        noExceptionThrown()
        definition != null
    }
}
