package com.redhat.ceylon.compiler.java.test.structure;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTest;

public class ConcreteTests extends CompilerTest {
    
    @Override
    protected String transformDestDir(String name) {
        return name + "-c";
    }
    
    // Tests for concrete members of interfaces
    @Test
    public void testCncConcrete(){
        compareWithJavaSource("concrete/Concrete");
    }
    @Test
    public void testCncConcreteAttribute(){
        compareWithJavaSource("concrete/ConcreteAttribute");
    }

    @Test
    public void testCncDefaultSetter(){
        compareWithJavaSource("concrete/DefaultSetter");
    }
    
    @Test
    public void testCncListImplementor(){
        compareWithJavaSource("concrete/ListImplementor");
    }
    
    @Test
    public void testCncNameCollision(){
        compareWithJavaSource("concrete/NameCollision");
    }

    @Test
    public void testCncUnionTypeArg(){
        compareWithJavaSource("concrete/UnionTypeArg");
    }
    
    @Test
    public void testCncRaw(){
        compareWithJavaSource("concrete/Raw");
    }
    
    @Test
    public void testCncThis(){
        compareWithJavaSource("concrete/This");
    }
    
    @Test
    public void testCncConcreteInterface(){
        compareWithJavaSource("concrete/ConcreteInterface");
    }

    @Test
    public void testCncInterfaceMethodDefaultedParameter(){
        compareWithJavaSource("concrete/InterfaceMethodDefaultedParameter");
    }

    @Test
    public void testCncInterfaceErasure(){
        compareWithJavaSource("concrete/InterfaceErasure");
    }

    @Test
    public void testCncConcreteMethodBySpecification(){
        compareWithJavaSource("concrete/ConcreteMethodBySpecification");
    }
    
    @Test
    public void testCncSatisfaction(){
        compareWithJavaSource("concrete/Satisfaction");
    }
    
    @Test
    public void testCncConcreteGetter(){
        compareWithJavaSource("concrete/ConcreteGetter");
    }
    
    @Test
    public void testCncAbstractSatisfier(){
        compareWithJavaSource("concrete/AbstractSatisfier");
    }
    
    @Test
    public void testCncIntersectionSatisfier(){
        compareWithJavaSource("concrete/IntersectionSatisfier");
    }
    
    @Test
    public void testCncValueRefiningGetterSetter(){
        compile("concrete/ValueRefiningGetterSetter.ceylon");
    }
    
    @Test
    public void testCncMultipleInheritance(){
        compareWithJavaSource("concrete/MultipleInheritance");
    }
    
    @Test
    public void testCncLazySpec(){
        compareWithJavaSource("concrete/LazySpec");
    }

    @Test
    public void testCncCapturedTypeParam(){
        compareWithJavaSource("concrete/CapturedTypeParam");
    }
    
    @Test
    public void testCncInterfaceQualifiedMembers(){
        compareWithJavaSource("concrete/InterfaceQualifiedMembers");
    }
    
    @Test
    public void testCncString(){
        compareWithJavaSource("concrete/ConcreteString");
        run("com.redhat.ceylon.compiler.java.test.structure.concrete.concreteString");
    }
}
