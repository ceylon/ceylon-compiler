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
package com.redhat.ceylon.compiler.java.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.BoxingStrategy;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Functional;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.Value;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.util.ListBuffer;

class AnnotationInvocationVisitor extends Visitor {

    public static Class annoClass(Tree.InvocationExpression invocation) {
        Declaration declaration = ((Tree.BaseMemberOrTypeExpression)invocation.getPrimary()).getDeclaration();
        Set<Declaration> ctors = new HashSet<Declaration>();
        while (declaration instanceof Function) {
            if (!ctors.add(declaration)) {
                throw new BugException(invocation, "recursive annotation constructor");
            }
            declaration = ((AnnotationInvocation)((Function)declaration).getAnnotationConstructor()).getPrimary();
        } 
        
        if (declaration instanceof Class) {
            return (Class)declaration;
        } else {
            throw new BugException(invocation, "invocation primary has unexpected declaration: " + declaration);
        }
    }
    
    public static Function annoCtor(Tree.InvocationExpression invocation) {
        Declaration declaration = ((Tree.BaseMemberOrTypeExpression)invocation.getPrimary()).getDeclaration();
        if (declaration instanceof Function) {
            return (Function)declaration;
        } else if (declaration instanceof Class) {
            return null;
        } else {
            throw new BugException(invocation, "invocation primary has unexpected declaration: " + declaration);
        }
    }
    
    public static AnnotationInvocation annoCtorModel(Tree.InvocationExpression invocation) {
        Declaration declaration = ((Tree.BaseMemberOrTypeExpression)invocation.getPrimary()).getDeclaration();
        if (declaration instanceof Function) {
            return (AnnotationInvocation)((Function)declaration).getAnnotationConstructor();
        } else if (declaration instanceof Class) {
            // TODO Why doesn't the AnnotationModelVisitor do this? I guess because
            // an annotation Class's doesn't have a body, so there's no need for Visitor
            // But it could have defaulted arguments
            AnnotationInvocation in = new AnnotationInvocation();
            in.setPrimary((Class)declaration);
            java.util.List<AnnotationArgument> args = new ArrayList<>();
            for (Parameter p : ((Class)declaration).getParameterList().getParameters()) {
                AnnotationArgument arg = new AnnotationArgument();
                arg.setParameter(p);
                ParameterAnnotationTerm term = new ParameterAnnotationTerm();
                term.setSourceParameter(p);
                term.setSpread(false);
                arg.setTerm(term);
                args.add(arg);
            }
            in.getAnnotationArguments().addAll(args);
            in.setInterop(false);
            in.setConstructorDeclaration(null);
            return in;
        } else {
            throw new BugException(invocation, "invocation primary has unexpected declaration: " + declaration);
        }
    }
    
    private final Node errorNode;
    private final ExpressionTransformer exprGen;
    private final AnnotationInvocation anno;
    
    
    private Parameter parameter;
    private Type expectedType;
    private ListBuffer<JCExpression> arrayExprs = null;
    private JCExpression argumentExpr;

    private AnnotationInvocationVisitor(
            ExpressionTransformer expressionTransformer, Node errorNode, AnnotationInvocation anno) {
        this.exprGen = expressionTransformer;
        this.errorNode = errorNode;
        this.anno = anno;
    }
    
    private JCExpression getExpression() {
        if (argumentExpr != null) {
            return argumentExpr;
        } else if (arrayExprs != null) {
            return exprGen.make().NewArray(null, null, arrayExprs.toList());
        } else if (anno.isInterop()) {
            // This can happen if we're invoking an interop constructor
            // and defaulting an argument
            return null;
        } else {
            return exprGen.makeErroneous(errorNode, "compiler bug: no result when transforming annotation");
        }
        
    }
    
    public static JCAnnotation transform(ExpressionTransformer expressionTransformer, Tree.InvocationExpression invocation) {
        AnnotationInvocationVisitor visitor = new AnnotationInvocationVisitor(expressionTransformer, invocation, annoCtorModel(invocation));
        visitor.visit(invocation);
        return (JCAnnotation) visitor.getExpression();
    }
    
    public void visit(Tree.InvocationExpression invocation) {
        // Is it a class instantiation or a constructor invocation?
        Tree.Primary primary = invocation.getPrimary();
        try {
            if (primary instanceof Tree.BaseMemberExpression) {
                Tree.BaseMemberExpression ctor = (Tree.BaseMemberExpression)primary;
                if (!Decl.isAnnotationConstructor(ctor.getDeclaration())) {
                    append(exprGen.makeErroneous(primary, "compiler bug: " + ctor.getDeclaration().getName() + " is not an annotation constructor"));
                }
                append(transformConstructor(exprGen, invocation));
            } else if (primary instanceof Tree.BaseTypeExpression) {
                Tree.BaseTypeExpression bte = (Tree.BaseTypeExpression)primary;
                if (((TypeDeclaration) bte.getDeclaration()).isByte()) {
                    // Special case for "Byte(x)" where we make use of the fact that it looks
                    // like an annotation class instantiation but is in fact a byte literal
                    PositionalArgument arg = invocation.getPositionalArgumentList().getPositionalArguments().get(0);
                    arg.visit(this);
                } else {
                    if (!Decl.isAnnotationClass(bte.getDeclaration())) {
                        append(exprGen.makeErroneous(primary, "compiler bug: " + bte.getDeclaration().getName() + " is not an annotation class"));
                    }
                    append(transformInstantiation(exprGen, invocation));
                }
            } else {
                append(exprGen.makeErroneous(primary, "compiler bug: primary is not an annotation constructor or annotation class"));
            }
        } catch (BugException e) {
            e.addError(invocation);
        }
    }
    
    private static JCAnnotation transformInstantiation(ExpressionTransformer exprGen, Tree.InvocationExpression invocation) {
        AnnotationInvocation ai = annoCtorModel(invocation);
        AnnotationInvocationVisitor visitor = new AnnotationInvocationVisitor(exprGen, invocation, annoCtorModel(invocation));
        ListBuffer<JCExpression> annotationArguments = ListBuffer.<JCExpression>lb();
        if (invocation.getPositionalArgumentList() != null) {
            for (Tree.PositionalArgument arg : invocation.getPositionalArgumentList().getPositionalArguments()) {
                visitor.parameter = arg.getParameter();
                arg.visit(visitor);
                annotationArguments.append(makeArgument(exprGen, invocation, visitor.parameter, visitor.getExpression()));
            }
        } 
        if (invocation.getNamedArgumentList() != null) {
            for (Tree.NamedArgument arg : invocation.getNamedArgumentList().getNamedArguments()) {
                visitor.parameter = arg.getParameter();
                arg.visit(visitor);
                annotationArguments.append(makeArgument(exprGen, invocation, visitor.parameter, visitor.getExpression()));
            }
        }
        return exprGen.at(invocation).Annotation(
                ai.makeAnnotationType(exprGen),
                annotationArguments.toList());
    }
    
    public static JCAnnotation transformConstructor(ExpressionTransformer exprGen, Tree.InvocationExpression invocation) {
        AnnotationInvocation ai = annoCtorModel(invocation);
        return transformConstructor(exprGen, invocation, ai, com.sun.tools.javac.util.List.<AnnotationFieldName>nil());
    }

    static String checkForBannedJavaAnnotation(Tree.InvocationExpression invocation) {
        if (invocation.getPrimary() instanceof Tree.BaseMemberExpression
                && ((Tree.BaseMemberExpression)invocation.getPrimary()).getDeclaration() != null) {
            String name = ((Tree.BaseMemberExpression)invocation.getPrimary()).getDeclaration().getQualifiedNameString();
            if ("java.lang::deprecated".equals(name)) {
                return "inappropiate java annotation: interoperation with @Deprecated is not supported: use deprecated";
            } else if ("java.lang::override".equals(name)) {
                return "inappropiate java annotation: interoperation with @Override is not supported: use actual";
            } else if ("java.lang.annotation::target".equals(name)) {
                return "inappropiate java annotation: interoperation with @Target is not supported";
            } else if ("java.lang.annotation::retention".equals(name)) {
                return "inappropiate java annotation: interoperation with @Retention is not supported";
            }
        }
        return null;
    }
    
    private static JCAnnotation transformConstructor(
            ExpressionTransformer exprGen,
            Tree.InvocationExpression invocation, AnnotationInvocation ai, 
            com.sun.tools.javac.util.List<AnnotationFieldName> fieldPath) {
        Map<Parameter, ListBuffer<JCExpression>> args = new LinkedHashMap<Parameter, ListBuffer<JCExpression>>();
        
        List<Parameter> classParameters = ai.getClassParameters();
        // The class parameter's we've not yet figured out the value for
        ArrayList<Parameter> unbound = new ArrayList<Parameter>(classParameters);
        for (Parameter classParameter : classParameters) {
            for (AnnotationArgument argument : ai.findAnnotationArgumentForClassParameter(classParameter)) {
                JCExpression expr = transformConstructorArgument(exprGen, invocation, classParameter, argument, fieldPath);
                appendArgument(args, classParameter, expr);
                unbound.remove(classParameter);
            }
        }
        outer: for (Parameter classParameter : ((ArrayList<Parameter>)unbound.clone())) {
            // Defaulted argument
            if (ai.isInstantiation()) {
                if (classParameter.isDefaulted()) {
                    // That's OK, we'll pick up the default argument from 
                    // the Java Annotation type
                    unbound.remove(classParameter);
                    continue outer;
                }
            } else {
                Function ac2 = (Function)ai.getPrimary();
                AnnotationInvocation i = (AnnotationInvocation)ac2.getAnnotationConstructor();
                for (AnnotationArgument aa : i.getAnnotationArguments()) {
                    if (aa.getParameter().equals(classParameter)) {
                        appendArgument(args, classParameter, 
                                aa.getTerm().makeAnnotationArgumentValue(exprGen, i,com.sun.tools.javac.util.List.<AnnotationFieldName>of(aa)));
                        unbound.remove(classParameter);
                        continue outer;
                    }
                }
            }
            
            if (Strategy.hasEmptyDefaultArgument(classParameter)) {
                appendArgument(args, classParameter, 
                        exprGen.make().NewArray(null,  null, com.sun.tools.javac.util.List.<JCExpression>nil()));
                unbound.remove(classParameter);
                continue outer;
            }
            
        }
        
        for (Parameter classParameter : unbound) {
            appendArgument(args, classParameter, 
                exprGen.makeErroneous(invocation, "compiler bug: unbound annotation class parameter " + classParameter.getName()));
        }
        ListBuffer<JCExpression> assignments = ListBuffer.<JCExpression>lb();
        for (Map.Entry<Parameter, ListBuffer<JCExpression>> entry : args.entrySet()) {
            ListBuffer<JCExpression> exprs = entry.getValue();
            if (exprs.size() == 1) {
                assignments.append(makeArgument(exprGen, invocation, entry.getKey(), exprs.first()));
            } else {
                assignments.append(makeArgument(exprGen, invocation, entry.getKey(), 
                        exprGen.make().NewArray(null, null, exprs.toList())));
            }
            
        }
        
        JCAnnotation annotation = exprGen.at(invocation).Annotation(
                ai.makeAnnotationType(exprGen),
                assignments.toList());
        return annotation;
    }
    
    private static void appendArgument(
            Map<Parameter, ListBuffer<JCExpression>> args,
            Parameter classParameter, JCExpression expr) {
        if (expr != null) {
            ListBuffer<JCExpression> exprList = args.get(classParameter);
            if (exprList == null) {
                exprList = ListBuffer.<JCExpression>lb();
                args.put(classParameter, exprList);
            }
            exprList.append(expr);
        }
    }

    public static JCExpression transformConstructorArgument(
            ExpressionTransformer exprGen,
            Tree.InvocationExpression invocation,
            Parameter classParameter, AnnotationArgument argument, 
            com.sun.tools.javac.util.List<AnnotationFieldName> fieldPath) {
        AnnotationInvocation anno = annoCtorModel(invocation);
        AnnotationInvocationVisitor visitor = new AnnotationInvocationVisitor(exprGen, invocation, anno);
        visitor.parameter = classParameter;
        AnnotationTerm term = argument.getTerm();
        if (term instanceof ParameterAnnotationTerm) {
            ParameterAnnotationTerm parameterArgument = (ParameterAnnotationTerm)term;
            Parameter sp = parameterArgument.getSourceParameter();
            int argumentIndex = ((Functional)sp.getDeclaration())
                    .getFirstParameterList().getParameters()
                    .indexOf(sp);
            if (invocation.getPositionalArgumentList() != null) {
                java.util.List<Tree.PositionalArgument> positionalArguments = invocation.getPositionalArgumentList().getPositionalArguments();
                
                if (parameterArgument.isSpread()) {
                    visitor.transformSpreadArgument(positionalArguments.subList(argumentIndex, positionalArguments.size()), classParameter);
                } else {
                    if (0 <= argumentIndex && argumentIndex < positionalArguments.size()) {
                        Tree.PositionalArgument pargument = positionalArguments.get(argumentIndex);
                        if (pargument.getParameter().isSequenced()) {
                            visitor.transformVarargs(argumentIndex, positionalArguments);
                        } else {
                            visitor.transformArgument(pargument);
                        }
                    } else if (sp.isDefaulted()) {
                        visitor.makeDefaultExpr(invocation, parameterArgument, sp);
                    } else if (sp.isSequenced()) {
                        visitor.appendBuiltArray(visitor.startArray());
                    }
                }
            } else if (invocation.getNamedArgumentList() != null) {
                boolean found = false;
                for (Tree.NamedArgument na : invocation.getNamedArgumentList().getNamedArguments()) {
                    Parameter parameter = na.getParameter();
                    int parameterIndex = anno.indexOfConstructorParameter(parameter);
                    if (parameterIndex == argumentIndex) {
                        visitor.transformArgument(na);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (sp.isDefaulted()) {
                        visitor.makeDefaultExpr(invocation, parameterArgument, sp);
                    } else if (sp.isSequenced()) {
                        visitor.appendBuiltArray(visitor.startArray());
                    }else {
                        visitor.append(exprGen.makeErroneous(invocation, "Unable to find argument"));
                    }
                }
            }
        } else if (term instanceof LiteralAnnotationTerm) {
            visitor.append(term.makeAnnotationArgumentValue(visitor.exprGen, visitor.anno, fieldPath.append(argument)));
        } else if (term instanceof InvocationAnnotationTerm) {
            AnnotationInvocation instantiation = ((InvocationAnnotationTerm)term).getInstantiation();
            visitor.append(transformConstructor(visitor.exprGen, invocation, instantiation, fieldPath.append(argument)));
        } else {
            visitor.append(visitor.exprGen.makeErroneous(invocation, "Unable to find argument"));
        }
        return visitor.getExpression();
    }
    
    
    private void makeDefaultExpr(Tree.InvocationExpression invocation,
            ParameterAnnotationTerm parameterArgument, Parameter sp) {
        AnnotationConstructorParameter defaultedCtorParam = null;
        for (AnnotationConstructorParameter ctorParam : anno.getConstructorParameters()) {
            if (ctorParam.getParameter().equals(parameterArgument.getSourceParameter())) {
                defaultedCtorParam = ctorParam;
                break;
            }
        }
        if (defaultedCtorParam == null) {
            append(exprGen.makeErroneous(invocation, "compiler bug: defaulted parameter " + anno.getConstructorDeclaration().getName() + " could not be found"));
            return;
        }
        
        // Use the default parameter from the constructor
        if (defaultedCtorParam.getDefaultArgument() instanceof LiteralAnnotationTerm) {
            JCExpression expr = ((LiteralAnnotationTerm)defaultedCtorParam.getDefaultArgument()).makeLiteral(exprGen);
            append(expr);
        } else if (Decl.isAnnotationClass(sp.getType().getDeclaration())) {
                InvocationAnnotationTerm defaultedInvocation = (InvocationAnnotationTerm)defaultedCtorParam.getDefaultArgument();
                append(transformConstructor(exprGen, invocation, defaultedInvocation.getInstantiation(), 
                        com.sun.tools.javac.util.List.<AnnotationFieldName>of(defaultedCtorParam)));
        }
    }
    
    private void transformVarargs(int argumentIndex,
            java.util.List<Tree.PositionalArgument> pa) {
        ListBuffer<JCExpression> prevCollect = startArray();
        try {
            for (int jj = argumentIndex; jj < pa.size(); jj++) {
                transformArgument(pa.get(jj));
            }
        } finally {
            appendBuiltArray(prevCollect);
        }
    }

    private static JCAssign makeArgument(ExpressionTransformer exprGen, Node errorNode, 
            Parameter parameter, JCExpression expr) {
        JCExpression memberName;
        if (parameter != null) {
            memberName = exprGen.naming.makeUnquotedIdent(
            Naming.selector(parameter.getModel(), Naming.NA_ANNOTATION_MEMBER));
        } else {
            memberName = exprGen.makeErroneous(errorNode, "compiler bug: null parameter in makeArgument");
        }
        return exprGen.make().Assign(memberName, expr);
    }
    
    /**
     * If we're currently constructing an array then or append the given expression
     * Otherwise make an annotation argument for given expression and 
     * append it to the annotation arguments,
     */
    private void append(JCExpression expr) {
        if (arrayExprs != null) {
            arrayExprs.append(expr);
        } else {
            if (this.argumentExpr != null) {
                throw new BugException(errorNode, "assertion failed");
            }
            this.argumentExpr = expr;
        }
    }

    private void transformSpreadArgument(java.util.List<Tree.PositionalArgument> arguments, Parameter classParameter) {
        boolean varargs = classParameter.isSequenced()
                && arguments.size() > 1 || arguments.size() == 0;
        ListBuffer<JCExpression> prevCollect = varargs ? startArray() : null;
        try {
            for (Tree.PositionalArgument arg : arguments) {
                exprGen.at(arg); 
                arg.visit(this);
            }
        } finally {
            if (varargs) {
                appendBuiltArray(prevCollect);
            }
        }
    }
    
    private void transformArgument(Tree.NamedArgument node) {
        exprGen.at(node);
        node.visit(this);
    }
    
    private void transformArgument(Tree.PositionalArgument node) {
        exprGen.at(node);
        node.visit(this);
    }
    
    @Override
    public void handleException(Exception e, Node node) {
        if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
        } else {
            throw new RuntimeException(e);
        }
    }
    
    public void visit(Tree.Expression term) {
        term.visitChildren(this);
    }
    
    public void visit(Tree.Term term) {
        append(exprGen.makeErroneous(term, "compiler bug: " + term.getNodeType() + " is an unsupported term in an annotation invocation"));
    }
    
    public void visit(Tree.NegativeOp term) {
        if (term.getTerm() instanceof Tree.NaturalLiteral
                || term.getTerm() instanceof Tree.FloatLiteral) {
            append(exprGen.transformExpression(term, BoxingStrategy.UNBOXED, expectedType(), 
                    ExpressionTransformer.EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK));
        } else {
            append(exprGen.makeErroneous(term, "compiler bug: " + term.getNodeType() + " is an unsupported term in an annotation invocation"));
        }
    }
    
    public void visit(Tree.Literal term) {
        append(exprGen.transformExpression(term, BoxingStrategy.UNBOXED, expectedType(), 
                ExpressionTransformer.EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK));
    }

    private Type expectedType() {
        return this.expectedType != null ?  this.expectedType : this.parameter.getType();
    }
    
    public void visit(Tree.BaseMemberExpression term) {
        if (exprGen.isBooleanTrue(term.getDeclaration())
                || exprGen.isBooleanFalse(term.getDeclaration())) {
            append(exprGen.transformExpression(term, BoxingStrategy.UNBOXED, term.getTypeModel()));
        } else if (Decl.isAnonCaseOfEnumeratedType(term)
                && !exprGen.isJavaEnumType(term.getTypeModel())) {
            append(exprGen.makeClassLiteral(term.getTypeModel()));
        } else if (anno.isInterop()) {
            if (exprGen.isJavaEnumType(term.getTypeModel())) {
                // A Java enum
                append(exprGen.transformExpression(term, BoxingStrategy.UNBOXED, null));
            }
        } else {
            super.visit(term);
        }
    }
    
    public void visit(Tree.MemberOrTypeExpression term) {
        // Metamodel reference
        if (anno.isInterop()) {
            Declaration decl = term.getDeclaration();
            if (decl instanceof ClassOrInterface) {
                append(exprGen.naming.makeQualIdent(
                        exprGen.makeJavaType(((ClassOrInterface)decl).getType()),
                        "class"));
            } else if (decl instanceof Value) {
                append(exprGen.transformExpression(term, BoxingStrategy.UNBOXED, term.getTypeModel(),
                        // target doesn't actually accept null, but we can't put a checkNull() call in there.
                        exprGen.EXPR_TARGET_ACCEPTS_NULL));
            }
        } else {
            append(exprGen.make().Literal(term.getDeclaration().getQualifiedNameString()));
        }
    }
    
    @Override
    public void visit(Tree.TypeLiteral tl){
        if((tl.getType() != null && tl.getType().getTypeModel() != null)
                || (tl.getDeclaration() != null
                    && tl.getDeclaration().isAnonymous())){
            if (anno.isInterop()) {
                append(exprGen.naming.makeQualIdent(
                        exprGen.makeJavaType(tl.getType().getTypeModel(), AbstractTransformer.JT_NO_PRIMITIVES | AbstractTransformer.JT_RAW),
                        "class"));
            } else {
                append(exprGen.makeMetaLiteralStringLiteralForAnnotation(tl));
            }
        }
    }
    
    @Override
    public void visit(Tree.MetaLiteral tl){
        append(exprGen.makeMetaLiteralStringLiteralForAnnotation(tl));
    }
    
    private ListBuffer<JCExpression> startArray() {
        ListBuffer<JCExpression> prevArray = arrayExprs;
        arrayExprs = ListBuffer.<JCExpression>lb();
        expectedType = exprGen.typeFact().getIteratedType(parameter.getType());
        return prevArray;
    }
    
    private JCNewArray endArray(ListBuffer<JCExpression> prevArray) {
        ListBuffer<JCExpression> collected = arrayExprs;
        arrayExprs = prevArray;
        expectedType = null;
        return exprGen.make().NewArray(null,  null, collected.toList());
    }
    
    private void appendBuiltArray(ListBuffer<JCExpression> prevArray) {
        append(endArray(prevArray));
    }

    public void visit(Tree.SequenceEnumeration term) {
        ListBuffer<JCExpression> prevCollect = startArray();
        try {
            term.visitChildren(this);
        } finally {
            appendBuiltArray(prevCollect);
        }
    }
    
    public void visit(Tree.Tuple term) {
        ListBuffer<JCExpression> prevCollect = startArray();
        try {
            term.visitChildren(this);
        } finally {
            appendBuiltArray(prevCollect);
        }
    }
    
    public void visit(Tree.PositionalArgument arg) {
        append(exprGen.makeErroneous(arg, "compiler bug: " + arg.getNodeType() + " is an unsupported positional argument in an annotation invocation"));
    }
    
    public void visit(Tree.ListedArgument arg) {
        arg.visitChildren(this);
    }
    
    public void visit(Tree.NamedArgument arg) {
        append(exprGen.makeErroneous(arg, "compiler bug: " + arg.getNodeType() + " is an unsupported named argument in an annotation invocation"));
    }
    
    public void visit(Tree.SpecifiedArgument arg) {
        arg.visitChildren(this);
    }
    
}