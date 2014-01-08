package com.redhat.ceylon.compiler.java.codegen;

import java.util.ArrayList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Figures out the local values, and containing instances captured by a 
 * local declaration
 */
public class LocalCaptureVisitor extends Visitor {
    
    public LocalCaptureVisitor() {
    }
    
    public static boolean isStaticDeclaration(Scope d) {
        return d instanceof MethodOrValue;
    }
    
    static void addCapture(Declaration capturer, Declaration captured) {
        List<Declaration> c = capturer.getDirectlyCaptured();
        if (c == null) {
            c = new ArrayList<Declaration>(3);
            capturer.setDirectlyCaptured(c);
        }
        if (!c.contains(captured)) {
            c.add(captured);
            if (captured.getDirectlyCaptured() != null) {
                for (Declaration transitive : captured.getDirectlyCaptured()) {
                    if (!c.contains(transitive)) {
                        c.add(transitive);
                    }
                }
            }
        }
        if (captured instanceof Value
                && ((Value)captured).isTransient()
                && ((Value)captured).getSetter() != null) {
            // TODO Only say the setter is captured if it's actually used
            addCapture(capturer, ((Value)captured).getSetter());
        }
    }
    
    public void visit(Tree.BaseMemberExpression that) {
        if (noErrors(that)
                && !that.getDeclaration().isToplevel()) {
            Scope useScope = that.getScope();
            Scope scope = useScope;
            Scope declScope = that.getDeclaration().getScope();
            while (declScope != null 
                    && !(declScope instanceof Declaration)) {
                declScope = declScope.getContainer();
            }
            while (true) {
                if (scope == null) {
                    break;
                }
                if (scope == declScope) {
                    break;
                }
                if (isStaticDeclaration(scope) && !(scope.getContainer() instanceof Class)) {
                    addCapture((Declaration)scope, that.getDeclaration());
                }
                scope = scope.getContainer();
            }
        }
        
        super.visit(that);
    }

    private boolean noErrors(Node that) {
        return that.getErrors() == null || that.getErrors().isEmpty();
    }
    
    public void visit(Tree.This that) {
        if (noErrors(that)) {
            TypeDeclaration typeDecl = that.getTypeModel().getDeclaration();
            Scope useScope = that.getScope();
            Scope scope = useScope;
            while (true) {
                if (scope == null) {
                    break;
                }
                if (scope == typeDecl) {
                    break;
                }
                if (isStaticDeclaration(scope)) {
                    addCapture((Declaration)scope, typeDecl);
                }
                scope = scope.getContainer();
            }
        }
        super.visit(that);
    }
    
    public void visit(Tree.Outer that) {
        if (noErrors(that)) {
            TypeDeclaration typeDecl = that.getTypeModel().getDeclaration();
            Scope useScope = that.getScope();
            Scope scope = useScope;
            while (true) {
                if (scope == null) {
                    break;
                }
                if (scope == typeDecl) {
                    break;
                }
                if (isStaticDeclaration(scope)) {
                    addCapture((Declaration)scope, typeDecl);
                }
                scope = scope.getContainer();
            }
        }
        super.visit(that);
    }
    
    public void visit(Tree.Super that) {
        if (noErrors(that)) {
            // First find the containing type
            TypeDeclaration typeDecl = null;
            Scope useScope = that.getScope();
            Scope scope = useScope;
            while (true) {
                if (scope == null) {
                    break;
                }
                if (scope instanceof TypeDeclaration) {
                    typeDecl = (TypeDeclaration)scope;
                    break;
                }
                scope = scope.getContainer();
            }
            // the below bit it the same as for this
            scope = useScope;
            while (true) {
                if (scope == null) {
                    break;
                }
                if (scope == typeDecl) {
                    break;
                }
                if (isStaticDeclaration(scope)) {
                    addCapture((Declaration)scope, typeDecl);
                }
                scope = scope.getContainer();
            }
        }
        super.visit(that);
    }
}
