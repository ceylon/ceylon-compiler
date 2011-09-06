package com.redhat.ceylon.compiler.test.statement;

import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.compiler.test.CompilerTest;

public class StatementTest extends CompilerTest {
    
    //
    // Method attributes and variables
    
    @Test
    public void testAtrMethodAttribute(){
        compareWithJavaSource("attribute/MethodAttribute");
    }
    
    @Test
    public void testAtrMethodAttributeWithInitializer(){
        compareWithJavaSource("attribute/MethodAttributeWithInitializer");
    }

    @Test
    public void testAtrMethodAttributeWithLateInitializer(){
        compareWithJavaSource("attribute/MethodAttributeWithLateInitializer");
    }

    @Test
    public void testAtrMethodVariable(){
        compareWithJavaSource("attribute/MethodVariable");
    }

    @Test
    public void testAtrMethodVariableWithInitializer(){
        compareWithJavaSource("attribute/MethodVariableWithInitializer");
    }

    @Test
    public void testAtrMethodVariableWithLateInitializer(){
        compareWithJavaSource("attribute/MethodVariableWithLateInitializer");
    }

    //
    // if/else

    @Test
    public void testConInitializerIf(){
        compareWithJavaSource("conditional/InitializerIf");
    }

    @Test
    public void testConInitializerIfElse(){
        compareWithJavaSource("conditional/InitializerIfElse");
    }

    @Test
    public void testConInitializerIfElseIf(){
        compareWithJavaSource("conditional/InitializerIfElseIf");
    }

    @Test
    public void testConMethodIf(){
        compareWithJavaSource("conditional/MethodIf");
    }

    @Test
    public void testConMethodIfElse(){
        compareWithJavaSource("conditional/MethodIfElse");
    }

    @Test
    public void testConMethodIfElseIf(){
        compareWithJavaSource("conditional/MethodIfElseIf");
    }

    @Test
    public void testConMethodIfExists(){
        compareWithJavaSource("conditional/MethodIfExists");
    }

    @Test
    public void testConMethodIfIs(){
        compareWithJavaSource("conditional/MethodIfIs");
    }

    @Test @Ignore
    public void testConMethodIfIsGeneric(){
        compareWithJavaSource("conditional/MethodIfIsGeneric");
    }

    @Test
    @Ignore //M3
    public void testConMethodIfSatisfies(){
        compareWithJavaSource("conditional/MethodIfSatisfies");
    }

    @Test
    @Ignore //M3
    public void testConMethodIfSatisfiesMultiple(){
        compareWithJavaSource("conditional/MethodIfSatisfiesMultiple");
    }

    @Test
    public void testConMethodIfNonEmpty(){
        compareWithJavaSource("conditional/MethodIfNonEmpty");
    }

    //
    // switch / case
    
    @Test
    @Ignore //M2
    public void testConMethodSwitch(){
        compareWithJavaSource("conditional/MethodSwitch");
    }

    @Test
    @Ignore //M2
    public void testConMethodSwitchNB(){
        compareWithJavaSource("conditional/MethodSwitchNB");
    }

    @Test
    @Ignore //M2
    public void testConMethodSwitchElse(){
        compareWithJavaSource("conditional/MethodSwitchElse");
    }

    @Test
    @Ignore //M2
    public void testConMethodSwitchElseNB(){
        compareWithJavaSource("conditional/MethodSwitchElseNB");
    }

    //
    // for

    @Test
    public void testLopMethodForRange(){
        compareWithJavaSource("loop/MethodForRange");
    }
    
    @Test
    public void testLopMethodForIterator(){
        compareWithJavaSource("loop/MethodForIterator");
    }
    
    @Test
    public void testLopMethodForDoubleIterator(){
        compareWithJavaSource("loop/MethodForDoubleIterator");
    }
    
    @Test
    public void testLopMethodForElse(){
        compareWithJavaSource("loop/MethodForElse");
    }
    
    //
    // [do] while
    
    @Test
    public void testLopMethodWhile(){
        compareWithJavaSource("loop/MethodWhile");
    }
    
    @Test
    @Ignore //Might be removed
    public void testLopMethodDoWhile(){
        compareWithJavaSource("loop/MethodDoWhile");
    }
    
    //
    // Locals (value / function)
    
    @Test
    public void testLocValueKeyword(){
        compareWithJavaSource("local/ValueKeyword");
    }
    
    @Test
    public void testLocFunctionKeyword(){
        compareWithJavaSource("local/FunctionKeyword");
    }
    
    @Test
    public void testLocFunctionAndValueKeyword(){
        compareWithJavaSource("local/FunctionAndValueKeyword");
    }
    
    @Test
    public void testTryExceptionAttr(){
        compareWithJavaSource("trycatch/ExceptionAttr");
    }
    
    @Test
    public void testTryBareThrow(){
        compareWithJavaSource("trycatch/Throw");
    }
    
    @Test
    public void testTryThrowException(){
        compareWithJavaSource("trycatch/ThrowException");
    }
    
    @Test
    public void testTryThrowExceptionSubclass(){
        compareWithJavaSource("trycatch/ThrowExceptionSubclass");
    }
    
    @Test
    public void testTryThrowMethodResult(){
        compareWithJavaSource("trycatch/ThrowMethodResult");
    }
    
    @Test
    public void testTryThrowThrowable(){
        compareWithJavaSource("trycatch/ThrowThrowable");
    }
    
    @Test
    public void testTryTryFinally(){
        compareWithJavaSource("trycatch/TryFinally");
    }
    
    @Test
    public void testTryTryCatch(){
        compareWithJavaSource("trycatch/TryCatch");
    }
    
    @Test
    public void testTryTryCatchFinally(){
        compareWithJavaSource("trycatch/TryCatchFinally");
    }
    
    @Test
    public void testTryTryCatchSubclass(){
        compareWithJavaSource("trycatch/TryCatchSubclass");
    }
    
    @Test
    public void testTryTryCatchUnion(){
        compareWithJavaSource("trycatch/TryCatchUnion");
    }
    
    @Test
    public void testTryReplaceExceptionAtJavaCallSite(){
        compareWithJavaSource("trycatch/WrapExceptionAtJavaCallSite");
    }
}
