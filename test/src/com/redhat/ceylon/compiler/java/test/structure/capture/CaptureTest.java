package com.redhat.ceylon.compiler.java.test.structure.capture;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.redhat.ceylon.compiler.java.codegen.LocalCaptureVisitor;
import com.redhat.ceylon.compiler.java.loader.MethodOrValueReferenceVisitor;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.TypeCheckerBuilder;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.util.AssertionVisitor;

public class CaptureTest {

    private void assertStuff(String ceylonFile) {
        TypeChecker typeChecker = new TypeCheckerBuilder()
            .verbose(true)
            .addSrcDirectory( new File("test/src/com/redhat/ceylon/compiler/java/test/structure/capture/" + ceylonFile) )
            .getTypeChecker();
        typeChecker.process();
        PhasedUnits phasedUnits = typeChecker.getPhasedUnits();
        AssertionVisitor av = new AssertionVisitor();
        for (PhasedUnit pu : phasedUnits.getPhasedUnits()) {
            Tree.CompilationUnit compilationUnit = pu.getCompilationUnit();
            if ( compilationUnit == null ) {
                throw new RuntimeException("No CompilationUnit found for " + ceylonFile);
            }
            // Run the normal capture visitor, which sets the captured flag on decls
            Unit unit = pu.getUnit();
            for (Declaration d: unit.getDeclarations()) {
                if (d instanceof TypedDeclaration && !(d instanceof Setter)) {
                    compilationUnit.visit(new MethodOrValueReferenceVisitor((TypedDeclaration) d));
                }
            }
            // Assert the unit isn't broken 
            av.visit(compilationUnit);
            if (av.getErrors() > 0 || av.getWarnings() > 0) {
                Assert.fail("Typechecker errors");
            }
            final int[] numCaptures = {0};
            new Visitor() {
                public void visit(Tree.CompilationUnit that) {
                    LocalCaptureVisitor lcv = new LocalCaptureVisitor();
                    lcv.visit(that);
                    super.visit(that);
                }
                public void visit(Tree.Declaration that) {
                    for (Tree.CompilerAnnotation ca : that.getCompilerAnnotations()) {
                        Declaration declaration = that.getDeclarationModel();
                        LinkedHashSet<String> expectedCaptures = null;
                        if ("captures".equals(ca.getIdentifier().getText())) {
                            if (ca.getStringLiteral() != null && !ca.getStringLiteral().getText().isEmpty()) {
                                expectedCaptures = new LinkedHashSet<String>(Arrays.asList(ca.getStringLiteral().getText().split("[\\s,?]+")));
                            } else {
                                expectedCaptures = new LinkedHashSet<String>();
                            }
                        }
                        if (expectedCaptures != null) {
                            // Compare as strings for easy diffs
                            System.out.println(that.getDeclarationModel() + " captures " + declaration.getDirectlyCaptured());
                            Assert.assertEquals("Wrong capture on line " + that.getToken().getLine(), 
                                expectedCaptures.toString(), 
                                setOfCaptures(declaration.getDirectlyCaptured()).toString());
                            numCaptures[0]++;
                        }
                    }
                    super.visit(that);
                }
                private LinkedHashSet<String> setOfCaptures(List<Declaration> capturedLocals) {
                    LinkedHashSet<String> result = new LinkedHashSet<String>();
                    if (capturedLocals != null) {
                        for (Declaration d : capturedLocals) {
                            result.add(d.getQualifiedNameString());
                        }
                    }
                    return result;
                }
                public void handleException(Exception e, Node that) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException)e;
                    }
                    throw new RuntimeException(e);
                }
            }.visit(compilationUnit);
            
            Assert.assertTrue("Test made no assertions about capture", numCaptures[0] > 0);
        }
    }
    
    @Test
    public void localValueCapByLocalFunction() {
        assertStuff("localValueCapByLocalFunction.ceylon");
    }
    
    @Test
    public void localFunctionCapByLocalFunction() {
        assertStuff("localFunctionCapByLocalFunction.ceylon");
    }
    
    @Test
    public void MethodCapBySiblingMethod() {
        assertStuff("MethodCapBySiblingMethod.ceylon");
    }
    
    @Test
    public void MethodCapByLocalFunction() {
        assertStuff("MethodCapByLocalFunction.ceylon");
    }
    
    @Test
    public void AttributeCapByLocalFunction() {
        assertStuff("AttributeCapByLocalFunction.ceylon");
    }
    
    @Test
    public void ValueCapByInitFunction() {
        assertStuff("ValueCapByInitFunction.ceylon");
    }
    
    @Test
    public void LambdaParameterNotCaptured() {
        assertStuff("LambdaParameterNotCaptured.ceylon");
    }
    
    @Test
    public void localFunctionTransitiveCaptureNaming() {
        assertStuff("localFunctionTransitiveCaptureNaming.ceylon");
    }

}
