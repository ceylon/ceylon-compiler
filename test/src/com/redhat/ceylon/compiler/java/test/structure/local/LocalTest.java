package com.redhat.ceylon.compiler.java.test.structure.local;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTest;

/**
 * Test local functions, getters, setters etc.
 */
public class LocalTest extends CompilerTest {

    @Test
    public void testFunctionLocalToToplevelFunction() {
        compareWithJavaSource("FunctionLocalToToplevelFunction");
    }
    
    @Test
    public void testFunctionLocalToToplevelValue() {
        compareWithJavaSource("FunctionLocalToToplevelValue");
    }
    
    @Test
    public void testFunctionLocalToToplevelClass() {
        compareWithJavaSource("FunctionLocalToToplevelClass");
    }
    
    @Test
    public void testFunctionLocalToToplevelClassMethod() {
        compareWithJavaSource("FunctionLocalToToplevelClassMethod");
        // TODO also ClassGetterSetter
        // in fact change this test for ...Member
    }
    
    @Test
    public void testFunctionLocalToToplevelInterfaceMethod() {
        compareWithJavaSource("FunctionLocalToToplevelInterfaceMethod");
        // TODO also InterfaceGetterSetter
        // in fact change this test for ...Member
    }
    
    // TODO functionLocalToClassMemberClassInit
    // TODO functionLocalToClassMemberClassMethod
    // TODO functionLocalToClassMemberClassValue
    // TODO functionLocalToInterfaceMemberClassInit
    // TODO functionLocalToInterfaceMemberClassMethod
    // TODO functionLocalToInterfaceMemberClassValue
    // TODO functionLocalToInterfaceMemberInterfaceMethod
    // TODO functionLocalToInterfaceMemberInterfaceValue
    // TODO functionLocalToClassMemberInterfaceMethod
    // TODO functionLocalToClassMemberInterfaceValue
    
    // TODO functionLocalToLocalFunction
    // TODO functionLocalToLocalValue
    
    // Then the same for local getters and setters
    
    // The the same for local classes
    
    // Then the same for local interfaces
    
    
    
}
