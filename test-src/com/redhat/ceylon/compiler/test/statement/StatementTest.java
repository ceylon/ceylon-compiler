package com.redhat.ceylon.compiler.test.statement;

import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.compiler.test.CompilerTest;

public class StatementTest extends CompilerTest {
	
	//
	// Method attributes and variables
	
	@Test
	public void testMethodAttribute(){
		compareWithJavaSource("attribute/MethodAttribute");
	}
	
	@Test
	public void testMethodAttributeWithInitializer(){
		compareWithJavaSource("attribute/MethodAttributeWithInitializer");
	}

	@Test
	public void testMethodAttributeWithLateInitializer(){
	    compareWithJavaSource("attribute/MethodAttributeWithLateInitializer");
	}

	@Test
	public void testMethodVariable(){
		compareWithJavaSource("attribute/MethodVariable");
	}

	@Test
	public void testMethodVariableWithInitializer(){
		compareWithJavaSource("attribute/MethodVariableWithInitializer");
	}

    @Test
    public void testMethodVariableWithLateInitializer(){
        compareWithJavaSource("attribute/MethodVariableWithLateInitializer");
    }

    //
	// if/else

	@Test
	public void testInitializerIf(){
		compareWithJavaSource("conditional/InitializerIf");
	}

	@Test
	public void testInitializerIfElse(){
		compareWithJavaSource("conditional/InitializerIfElse");
	}

	@Test
	public void testInitializerIfElseIf(){
		compareWithJavaSource("conditional/InitializerIfElseIf");
	}

	@Test
	public void testMethodIf(){
		compareWithJavaSource("conditional/MethodIf");
	}

	@Test
	public void testMethodIfElse(){
		compareWithJavaSource("conditional/MethodIfElse");
	}

	@Test
	public void testMethodIfElseIf(){
	    compareWithJavaSource("conditional/MethodIfElseIf");
	}

	@Test
	@Ignore
	public void testMethodIfExists(){
		compareWithJavaSource("conditional/MethodIfExists");
	}

	@Test
	@Ignore
	public void testMethodIfIs(){
		compareWithJavaSource("conditional/MethodIfIs");
	}

	@Test
	@Ignore
	public void testMethodIfSatisfies(){
		compareWithJavaSource("conditional/MethodIfSatisfies");
	}

	@Test
	@Ignore
	public void testMethodIfSatisfiesMultiple(){
		compareWithJavaSource("conditional/MethodIfSatisfiesMultiple");
	}

	@Test
	@Ignore
	public void testMethodIfNonEmpty(){
		compareWithJavaSource("conditional/MethodIfNonEmpty");
	}

	//
	// switch / case
	
	@Test
	@Ignore
	public void testMethodSwitch(){
		compareWithJavaSource("conditional/MethodSwitch");
	}

	@Test
	@Ignore
	public void testMethodSwitchNB(){
		compareWithJavaSource("conditional/MethodSwitchNB");
	}

	@Test
	@Ignore
	public void testMethodSwitchElse(){
		compareWithJavaSource("conditional/MethodSwitchElse");
	}

	@Test
	@Ignore
	public void testMethodSwitchElseNB(){
		compareWithJavaSource("conditional/MethodSwitchElseNB");
	}

	//
	// for

	@Test
	@Ignore
	public void testMethodForRange(){
		compareWithJavaSource("loop/MethodForRange");
	}
	
	@Test
	public void testMethodForIterator(){
		compareWithJavaSource("loop/MethodForIterator");
	}
	
	@Test
	@Ignore
	public void testMethodForDoubleIterator(){
		compareWithJavaSource("loop/MethodForDoubleIterator");
	}
	
	@Test
	public void testMethodForFail(){
		compareWithJavaSource("loop/MethodForFail");
	}
	
	//
	// [do] while
	
	@Test
	public void testMethodWhile(){
		compareWithJavaSource("loop/MethodWhile");
	}
	
	@Test
	public void testMethodDoWhile(){
		compareWithJavaSource("loop/MethodDoWhile");
	}
}
