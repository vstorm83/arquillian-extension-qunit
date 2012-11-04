package org.jboss.arquillian.qunit.testng;

import java.lang.reflect.Field;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;

public class TestCaseGenerator {

    private static final String METHOD_SOURCE = "public void %s () { handler.call(\"%s\"); };";

    private ClassPool classPool;

    private CtClass ctClass;
    private ClassFile classFile;
    private ConstPool constPool;
    private Class<?> generatedClass;

    private CallbackHandler callbackHandler = new CallbackHandler();

    public TestCaseGenerator(String testCaseName, ClassPool classPool) throws RuntimeException, NotFoundException {
        this.classPool = classPool;

        initialize(testCaseName);
    }

    private void initialize(String testCaseName) throws RuntimeException, NotFoundException {
        this.ctClass = classPool.makeClass(testCaseName, classPool.get(SuperClass.class.getName()));
        this.classFile = ctClass.getClassFile();
        this.constPool = classFile.getConstPool();
    }

    public void addTestMethod(String name, Callback callback) throws CannotCompileException {
        callbackHandler.add(name, callback);

        String source = String.format(METHOD_SOURCE, name, name);

        CtMethod ctMethod = CtNewMethod.make(source, ctClass);
        ctClass.addMethod(ctMethod);

        // add @Test annotation
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation annotation = new Annotation(Test.class.getName(), constPool);
        attr.addAnnotation(annotation);
        ctMethod.getMethodInfo().addAttribute(attr);
    }

    public void addDrone() throws CannotCompileException, NotFoundException {
        CtClass webdriverClass = classPool.get(WebDriver.class.getName());
        CtField f = new CtField(webdriverClass, "browser", ctClass);
        ctClass.addField(f);

        // add @Drone annotation
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation annotation = new Annotation(Drone.class.getName(), constPool);
        attr.addAnnotation(annotation);
        f.getFieldInfo().addAttribute(attr);
    }

    public Class<?> toClass() throws CannotCompileException {
        if (generatedClass == null) {
            generatedClass = ctClass.toClass();
            finalizeClass();
        }
        return generatedClass;
    }

    private void finalizeClass() {
        setupCallbackHandlerToTestClass();
    }

    private void setupCallbackHandlerToTestClass() {
        try {
            Field field = SuperClass.class.getDeclaredField("handler");
            field.set(null, callbackHandler);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
