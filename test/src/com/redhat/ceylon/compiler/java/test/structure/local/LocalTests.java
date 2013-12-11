package com.redhat.ceylon.compiler.java.test.structure.local;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTest;

public class LocalTests extends CompilerTest {

    @Test
    public void testFunctionLocalToToplevelClass() {
        compareWithJavaSource("FunctionLocalToToplevelClass");
    }
    
    @Test
    public void testFunctionLocalToToplevelClassMethod() {
        compareWithJavaSource("FunctionLocalToToplevelClassMethod");
        // also ClassGetterSetter
    }
    
    @Test
    public void testFunctionLocalToToplevelInterfaceMethod() {
        compareWithJavaSource("FunctionLocalToToplevelInterfaceMethod");
     // also ClassGetterSetter
    }
    
    @Test
    public void testFunctionLocalToToplevelMethod() {
        compareWithJavaSource("FunctionLocalToToplevelMethod");
    }
    
    @Test
    public void testFunctionLocalToToplevelValue() {
        compareWithJavaSource("FunctionLocalToToplevelValue");
    }
    
    // functionLocalToClassMemberClassInit
    // functionLocalToClassMemberClassMethod
    // functionLocalToClassMemberClassValue
    // functionLocalToInterfaceMemberClassInit
    // functionLocalToInterfaceMemberClassMethod
    // functionLocalToInterfaceMemberClassValue
    // functionLocalToInterfaceMemberInterfaceMethod
    // functionLocalToInterfaceMemberInterfaceValue
    // functionLocalToClassMemberInterfaceMethod
    // functionLocalToClassMemberInterfaceValue
    
    // functionLocalToLocalFunction
    // functionLocalToLocalValue
    
    // Then the same for local getters and setters
    
    // The the same for local classes
    
    // Then the same for local interfaces
    
    
    
}
