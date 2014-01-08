package com.redhat.ceylon.compiler.java.test.structure.local;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTest;

/**
 * Test local functions, getters, setters etc.
 */
public class LocalTest extends CompilerTest {

    @Test
    public void testLocalFunctionReference(){
        compareWithJavaSource("LocalFunctionReference");
    }
    
    @Test
    public void testLocalFunctionInvocation(){
        compareWithJavaSource("LocalFunctionInvocation");
    }
    
    @Test
    public void testLocalFunctionSuperCapture(){
        compareWithJavaSource("LocalFunctionSuperCapture");
    }
    
    @Test
    public void testLocalFunctionMultipleTransitiveCapture(){
        compareWithJavaSource("LocalFunctionMultipleTransitiveCapture");
    }
    
    @Test
    public void testLocalFunctionTransitiveCaptureNaming(){
        //compareWithJavaSource("LocalFunctionTransitiveCaptureNaming");
        compile("LocalFunctionTransitiveCaptureNaming.ceylon");
        run("com.redhat.ceylon.compiler.java.test.structure.local.localFunctionTransitiveCaptureNaming");
    }
    
    // Local Functions
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
    
    // Local Values
    @Test
    public void testValueLocalToToplevelFunction() {
        compareWithJavaSource("ValueLocalToToplevelFunction");
    }
    
    @Test
    public void testValueLocalToToplevelValue() {
        compareWithJavaSource("ValueLocalToToplevelValue");
    }
    
    @Test
    public void testValueLocalToToplevelClass() {
        compareWithJavaSource("ValueLocalToToplevelClass");
    }
    
    @Test
    public void testValueLocalToToplevelClassMethod() {
        compareWithJavaSource("ValueLocalToToplevelClassMethod");
        // TODO also ClassGetterSetter
        // in fact change this test for ...Member
    }
    
    @Test
    public void testValueLocalToToplevelInterfaceMethod() {
        compareWithJavaSource("ValueLocalToToplevelInterfaceMethod");
        // TODO also InterfaceGetterSetter
        // in fact change this test for ...Member
    }
    
    
    // TODO valueLocalToClassMemberClassInit
    // TODO valueLocalToClassMemberClassMethod
    // TODO valueLocalToClassMemberClassValue
    // TODO valueLocalToInterfaceMemberClassInit
    // TODO valueLocalToInterfaceMemberClassMethod
    // TODO valueLocalToInterfaceMemberClassValue
    // TODO valueLocalToInterfaceMemberInterfaceMethod
    // TODO valueLocalToInterfaceMemberInterfaceValue
    // TODO valueLocalToClassMemberInterfaceMethod
    // TODO valueLocalToClassMemberInterfaceValue
    
    // TODO valueLocalToLocalFunction
    // TODO valueLocalToLocalValue
    
    /*
    // Local classes
    @Test
    public void testClassLocalToToplevelFunction() {
        compareWithJavaSource("ClassLocalToToplevelFunction");
    }
    
    @Test
    public void testClassLocalToToplevelValue() {
        compareWithJavaSource("ClassLocalToToplevelValue");
    }
    
    @Test
    public void testClassLocalToToplevelClass() {
        compareWithJavaSource("ClassLocalToToplevelClass");
    }
    
    @Test
    public void testClassLocalToToplevelClassMethod() {
        compareWithJavaSource("ClassLocalToToplevelClassMethod");
        // TODO also ClassGetterSetter
        // in fact change this test for ...Member
    }
    
    @Test
    public void testClassLocalToToplevelInterfaceMethod() {
        compareWithJavaSource("ClassLocalToToplevelInterfaceMethod");
        // TODO also InterfaceGetterSetter
        // in fact change this test for ...Member
    }
    // Then the same for local interfaces
    
    
    */
}
