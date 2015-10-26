/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.test.metamodel;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerError;
import com.redhat.ceylon.compiler.java.test.CompilerTests;

public class MetamodelTests extends CompilerTests {

    @Override
    protected ModuleWithArtifact getDestModuleWithArtifact(String main){
        return new ModuleWithArtifact("com.redhat.ceylon.compiler.java.test.metamodel", "123");
    }

    @Test
    public void testInteropRuntime() {
        compileAndRun("com.redhat.ceylon.compiler.java.test.metamodel.interopRuntime", "interopRuntime.ceylon", "JavaType.java");
    }

    @Test
    public void testTypeLiterals() {
        compareWithJavaSource("Literals");
    }

    // FIXME: I guess this one should also move to the language module
    @Test
    public void testTypeLiteralRuntime() {
        compileAndRun("com.redhat.ceylon.compiler.java.test.metamodel.literalsRuntime", "Literals.ceylon", "literalsRuntime.ceylon");
    }

    @Test
    public void testBug1793() {
        compileAndRun("com.redhat.ceylon.compiler.java.test.metamodel.bug1793", "bug1793.ceylon", "JavaType.java");
    }

    @Test
    public void testBug1209() {
        assertErrors("bug1209",
                new CompilerError(3, "metamodel reference to local declaration")
        );
    }

    @Test
    public void testBug1205() {
        compareWithJavaSource("bug1205");
    }

    @Test
    public void testBugCL426() {
        // this sucks, but is the only way to remove these from the Eclipse classpath that the 
        // run() ClassLoader delegates to. If we don't they will be loaded from a different classloader
        // than the Ceylon files and since C1 is package-private we get a runtime access exception.
        // If we make the run() ClassLoader have no parent ClassLoader and load everything itself
        // then we can't initialise the Metamodel. This is quicker.
        new File("build/classes/com/redhat/ceylon/compiler/java/test/metamodel/C0.class").delete();
        new File("build/classes/com/redhat/ceylon/compiler/java/test/metamodel/C1.class").delete();
        compile("JavaType.java");
        compileAndRun("com.redhat.ceylon.compiler.java.test.metamodel.bugCL426", "bugCL426.ceylon");
    }

    @Test
    public void testBug1926() {
        compareWithJavaSource("bug1926");
    }
    
    @Test
    public void testBugCL629() {
        compareWithJavaSource("BugCL629");
        run("com.redhat.ceylon.compiler.java.test.metamodel.run629");
    }
    
    @Test
    public void testBugCL634() {
        compareWithJavaSource("bugCL634");
        run("com.redhat.ceylon.compiler.java.test.metamodel.bugCL634");
    }
    
    @Test
    public void testBugCL566() {
        compareWithJavaSource("BugCL566");
        run("com.redhat.ceylon.compiler.java.test.metamodel.bugCl566");
    }

    @Test
    public void testBugCL645() throws Throwable {
        compile("bugCL645.ceylon");
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.metamodel/123", 
                Arrays.asList("--run", "com.redhat.ceylon.compiler.java.test.metamodel::bugCL645"));
    }
    
    @Test
    public void testConstructorLiterals() {
        //compareWithJavaSource("ConstructorLiterals");
        compile("ConstructorLiterals.ceylon");
        run("com.redhat.ceylon.compiler.java.test.metamodel.constructorLiterals");
    }
    
    @Test
    public void testInteropAnnotations() throws Throwable {
        compile("annotations/Type.java", 
                "annotations/Constructor.java",
                "annotations/TypeConstructor.java",
                "interopAnnotations.ceylon");
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.metamodel/123", 
                Arrays.asList("--run", "com.redhat.ceylon.compiler.java.test.metamodel::interopAnnotations"));
        
    }
}

