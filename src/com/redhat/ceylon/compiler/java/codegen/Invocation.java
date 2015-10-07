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

import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_COMPANION;
import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_NO_PRIMITIVES;
import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_RAW;
import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_TYPE_ARGUMENT;
import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.BoxingStrategy;
import com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Comprehension;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.FunctionArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgumentList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.QualifiedTypeExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SequencedArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.TreeUtil;
import com.redhat.ceylon.model.loader.JvmBackendUtil;
import com.redhat.ceylon.model.loader.NamingBase.Suffix;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassAlias;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.Functional;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ParameterList;
import com.redhat.ceylon.model.typechecker.model.Reference;
import com.redhat.ceylon.model.typechecker.model.Scope;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedReference;
import com.redhat.ceylon.model.typechecker.model.Value;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

abstract class Invocation {

    static boolean onValueType(AbstractTransformer gen, Tree.Term primary, Declaration primaryDeclaration) {
        // don't use the value type mechanism for optimised Java arrays get/set invocations
        if (primary instanceof Tree.QualifiedMemberOrTypeExpression){
            Tree.Primary qmePrimary = ((Tree.QualifiedMemberOrTypeExpression) primary).getPrimary();
            if(qmePrimary != null 
                    && gen.isJavaArray(qmePrimary.getTypeModel())
                    && (primaryDeclaration.getName().equals("get")
                        || primaryDeclaration.getName().equals("set"))) {
                return false;
            } else {
                return Decl.isValueTypeDecl(qmePrimary)
                        && (CodegenUtil.isUnBoxed(qmePrimary) || gen.isJavaArray(qmePrimary.getTypeModel()));
            }
        } else {
            return false;
        }
    }
    
    protected final AbstractTransformer gen;
    private final Node node;
    private final Tree.Term primary;
    private final Declaration primaryDeclaration;
    private final Type returnType;
    protected boolean handleBoxing;
    protected boolean unboxed;
    protected boolean erased;
    protected BoxingStrategy boxingStrategy;
    private final Tree.Primary qmePrimary;
    private final boolean onValueType;
    private boolean callable;
    
    public boolean isCallable() {
        return callable;
    }
    
    protected Invocation(AbstractTransformer gen, 
            Tree.Term primary, Declaration primaryDeclaration,
            Type returnType, Node node) {
        this.gen = gen;
        this.primary = primary;
        this.primaryDeclaration = primaryDeclaration;
        this.returnType = returnType;
        this.node = node;
        
        if (primary instanceof Tree.QualifiedMemberOrTypeExpression){
            this.qmePrimary = ((Tree.QualifiedMemberOrTypeExpression) primary).getPrimary();
        } else {
            this.qmePrimary = null;
        }
        this.onValueType = onValueType(gen, primary, primaryDeclaration);
    }
    
    public String toString() {
        return getClass().getName() + " of " + node;
    }
    
    Node getNode() {
        return node;
    }

    Tree.Term getPrimary() {
        return primary;
    }

    Declaration getPrimaryDeclaration() {
        return primaryDeclaration;
    }

    Type getReturnType() {
        return returnType;
    }

    Tree.Primary getQmePrimary() {
        return qmePrimary;
    }

    boolean isOnValueType() {
        return onValueType;
    }
    
    protected Type getParameterTypeForValueType(Reference producedReference, Parameter param) {
        // we need to find the interface for this method
        Type paramType = param.getModel().getReference().getFullType().getType();
        Scope paramContainer = param.getModel().getContainer();
        if(paramContainer instanceof TypedDeclaration){
            TypedDeclaration method = (TypedDeclaration) paramContainer;
            if(method.getContainer() instanceof TypeDeclaration
                    && !(method.getContainer() instanceof Constructor)){
                TypeDeclaration container = (TypeDeclaration) method.getContainer();
                Type qualifyingType = producedReference.getQualifyingType();
                Type supertype = qualifyingType.getSupertype(container);
                return paramType.substitute(supertype);
            }
        }
        return paramType;
    }
    

    protected boolean isParameterRaw(Parameter param){
        Type type = param.getType();
        return type == null ? false : type.isRaw();
    }
    
    protected boolean isParameterWithConstrainedTypeParameters(Parameter param) {
        return gen.hasConstrainedTypeParameters(param);
    }

    protected boolean isParameterWithDependentCovariantTypeParameters(Parameter param) {
        Type type = param.getType();
        return gen.hasDependentCovariantTypeParameters(type);
    }

    protected abstract void addReifiedArguments(ListBuffer<ExpressionAndType> result);
    
    public final void setUnboxed(boolean unboxed) {
        this.unboxed = unboxed;
    }

    public final void handleBoxing(boolean b) {
        handleBoxing = b;
    }
    
    public final void setErased(boolean erased) {
        this.erased = erased;
    }

    public final void setBoxingStrategy(BoxingStrategy boxingStrategy) {
        this.boxingStrategy = boxingStrategy;
    }
    
    class TransformedInvocationPrimary {
        final JCExpression expr;
        final String selector;
        TransformedInvocationPrimary(JCExpression expr, String selector) {
            this.expr = expr;
            this.selector = selector;
        }
    }
    
    protected TransformedInvocationPrimary transformPrimary(JCExpression primaryExpr,
            String selector) {
            
        if (Decl.isJavaStaticOrInterfacePrimary(getPrimary())) {
            Declaration methodOrClass = ((Tree.QualifiedMemberOrTypeExpression)getPrimary()).getDeclaration();
            if (methodOrClass instanceof Function) {
                return new TransformedInvocationPrimary(gen.naming.makeName(
                        (Function)methodOrClass, Naming.NA_FQ | Naming.NA_WRAPPER_UNQUOTED), 
                        null);
            } else if (methodOrClass instanceof Class) {
                return new TransformedInvocationPrimary(
                        gen.makeJavaType(((Class)methodOrClass).getType(), JT_RAW | JT_NO_PRIMITIVES),
                        null);
            }
        }
        if (isMemberRefInvocation()) {
            JCExpression callable = gen.expressionGen().transformMemberReference((Tree.QualifiedMemberOrTypeExpression)getPrimary(), (Tree.MemberOrTypeExpression)getQmePrimary());
            // The callable is a Callable we generate ourselves, it can never be erased to Object so there's no need to unerase
            selector = Naming.getCallableMethodName();
            return new TransformedInvocationPrimary(callable, selector);
        }
            
        JCExpression actualPrimExpr;
        if (getPrimary() instanceof Tree.QualifiedTypeExpression
                && ((Tree.QualifiedTypeExpression)getPrimary()).getPrimary() instanceof Tree.BaseTypeExpression
                && !Decl.isConstructor(getPrimaryDeclaration())) {
            actualPrimExpr = gen.naming.makeQualifiedThis(primaryExpr);
        } else {
            actualPrimExpr = primaryExpr;
        }
         
        if (getPrimary() instanceof Tree.BaseTypeExpression) {
            Tree.BaseTypeExpression type = (Tree.BaseTypeExpression)getPrimary();
            Declaration declaration = type.getDeclaration();
            if (Strategy.generateInstantiator(declaration)) {
                if (Decl.withinInterface(declaration)) {
                    if (primaryExpr != null) {
                        // if we have some other primary then respect that
                        actualPrimExpr = primaryExpr;
                    } else {
                        // if the class being instantiated is 
                        // within a class we expect the instantiation to be
                        // accessible from `this`, so use null
                        // otherwise we must be in an companion class, so we 
                        // need to qualify the instantiator invocation with $this
                        actualPrimExpr =  type.getScope().getInheritingDeclaration(declaration) instanceof Class ? null : gen.naming.makeQuotedThis();
                    }
                } else if (declaration.isToplevel()) {
                    actualPrimExpr = null;
                }
                // if the decl is not toplevel (but the primary is a base type
                // we must be invoking a member imported from an object
                // in which case the qualifer is needed.
            }
            if (Decl.isConstructor(declaration)) {
                selector = null;
            }
        } else {
            
            if (getPrimary() instanceof Tree.QualifiedMemberOrTypeExpression) {
                Tree.QualifiedMemberOrTypeExpression type = (Tree.QualifiedMemberOrTypeExpression)getPrimary();
                Declaration declaration = type.getDeclaration();
                if (Decl.isConstructor(declaration)) {
                    if (Decl.withinInterface(Decl.getConstructedClass(declaration))) {
                        if (Strategy.generateInstantiator(declaration)) {
                            actualPrimExpr = primaryExpr != null ? primaryExpr : gen.naming.makeQuotedThis();
                        } else {
                            actualPrimExpr = null;
                        }
                    }
                }
            } else if (getPrimary() instanceof Tree.BaseMemberOrTypeExpression) {
                Tree.BaseMemberOrTypeExpression type = (Tree.BaseMemberOrTypeExpression)getPrimary();
                Declaration declaration = type.getDeclaration();
                if (Decl.isConstructor(declaration)) {
                    selector = null;
                }
            }
            if (isIndirect()) {
                if (getPrimaryDeclaration() != null
                        && (Decl.isGetter(getPrimaryDeclaration())
                                || Decl.isToplevel(getPrimaryDeclaration())
                                || (Decl.isValueOrSharedOrCapturedParam(getPrimaryDeclaration()) 
                                        && Decl.isCaptured(getPrimaryDeclaration()) 
                                        && !Decl.isLocalNotInitializer(getPrimaryDeclaration())))) {
                    // We need to invoke the getter to obtain the Callable
                    actualPrimExpr = gen.make().Apply(null, 
                            gen.naming.makeQualIdent(primaryExpr, selector), 
                            List.<JCExpression>nil());
                } else if (selector != null) {
                    actualPrimExpr = gen.naming.makeQualIdent(primaryExpr, selector);
                } else if (getPrimaryDeclaration() == null || !((TypedDeclaration)getPrimaryDeclaration()).getType().isTypeConstructor()) {
                    actualPrimExpr = gen.naming.makeQualifiedName(primaryExpr, (TypedDeclaration)getPrimaryDeclaration(), Naming.NA_MEMBER);
                }
                actualPrimExpr = unboxCallableIfNecessary(actualPrimExpr, getPrimary());
                if (gen.isVariadicCallable(getPrimary().getTypeModel())) {
                    selector = Naming.getCallableVariadicMethodName();
                    this.callable = true;
                } else {
                    selector = Naming.getCallableMethodName();
                    this.callable = true;
                }
                // If it's indirect the primary might be erased
                actualPrimExpr = gen.expressionGen().applyErasureAndBoxing(actualPrimExpr, 
                        getPrimary().getTypeModel(),
                        getPrimary().getTypeErased(),
                        true, // boxed
                        BoxingStrategy.BOXED, 
                        getPrimary().getTypeModel(), 
                        0);
            } else if ((getPrimaryDeclaration() instanceof Function
                            && ((Function)getPrimaryDeclaration()).isParameter()// i.e. functional parameter
                            && (!JvmBackendUtil.createMethod((Function)getPrimaryDeclaration())) // not class member, or not shared/captured
                            // we may create a method, but if we're accessing it from a default parameter expression
                            // we need to access the Callable parameter, no the member method
                        || gen.expressionGen().isWithinDefaultParameterExpression(getPrimaryDeclaration().getContainer()))) {
                if (selector != null) {
                    actualPrimExpr = gen.naming.makeQualIdent(primaryExpr, selector);
                } else {
                    actualPrimExpr = gen.naming.makeQualifiedName(primaryExpr, (TypedDeclaration)getPrimaryDeclaration(), Naming.NA_MEMBER);
                }
                actualPrimExpr = unboxCallableIfNecessary(actualPrimExpr, getPrimary());
                if (gen.isVariadicCallable(getPrimary().getTypeModel())) {
                    selector = Naming.getCallableVariadicMethodName();
                    this.callable = true;
                } else {
                    selector = Naming.getCallableMethodName();
                    this.callable = true;
                }
            }
        }
        
        return new TransformedInvocationPrimary(actualPrimExpr, selector);
    }

    protected JCExpression unboxCallableIfNecessary(JCExpression actualPrimExpr, Tree.Term primary) {
        Type primaryModel = primary.getTypeModel();
        if(!gen.isCeylonCallable(primaryModel)){
            // if it's not exactly a Callable we may have to unerase it to one
            Type expectedType;
            if (gen.typeFact().getNothingType().isExactly(primaryModel)) {
                expectedType = gen.typeFact().getCallableType(gen.typeFact().getNothingType());
            } else {
                expectedType = primaryModel.getSupertype(gen.typeFact().getCallableDeclaration());
            }
            return gen.expressionGen().applyErasureAndBoxing(actualPrimExpr, primaryModel, 
                                                             primary.getTypeErased(), !primary.getUnboxed(), BoxingStrategy.BOXED, 
                                                             expectedType, 0);
        }
        return actualPrimExpr;
    }

    boolean isMemberRefInvocation() {
        return false;
    }
    
    public boolean isIndirect() {
        return false;
    }

    public void location(CallBuilder callBuilder) {
        callBuilder.location(getNode());
    }

    public boolean isUnknownArguments() {
        return false;
    }

    public Constructor getConstructor() {
        Declaration primaryDeclaration = getPrimaryDeclaration();
        return getConstructorFromPrimary(primaryDeclaration);
    }

    protected Constructor getConstructorFromPrimary(
            Declaration primaryDeclaration) {
        if (Decl.isConstructor(primaryDeclaration)) {
            primaryDeclaration = Decl.getConstructor(primaryDeclaration);
        }
        if (primaryDeclaration instanceof Constructor) {
            return (Constructor)primaryDeclaration;
        } else if (primaryDeclaration instanceof ClassAlias) {
            TypeDeclaration aliasCtor = ((ClassAlias) primaryDeclaration).getConstructor();
            while (aliasCtor instanceof ClassAlias) {
                aliasCtor = ((ClassAlias) aliasCtor).getConstructor();
            }
            if (aliasCtor instanceof Constructor) {
                return (Constructor)aliasCtor;
            } else {
                return null;
            }
        } else if (primaryDeclaration instanceof Class
                && Decl.getDefaultConstructor((Class)primaryDeclaration) != null) {
            return Decl.getDefaultConstructor((Class)primaryDeclaration);
        } else {
            return null;
        }
    }
    

    protected boolean erasedArgument(Tree.Term expr) {
        // technically expr.getTypeErased() is all we need
        // but it usually results in unnecessary casting of null
        // the exception to that is if and switch expressions
        // with all branches being null.
        return expr.getTypeErased()
                && gen.isNullValue(expr.getTypeModel())
                && (expr instanceof Tree.SwitchExpression
                        ||expr instanceof Tree.IfExpression);
    }
}

abstract class SimpleInvocation extends Invocation {

    public SimpleInvocation(AbstractTransformer gen, Tree.Term primary,
            Declaration primaryDeclaration, Type returnType, Node node) {
        super(gen, primary, primaryDeclaration, returnType, node);
    }

    protected abstract boolean isParameterVariadicStar(int argIndex);
    protected abstract boolean isParameterVariadicPlus(int argIndex);
    
    protected final boolean isParameterSequenced(int argIndex) {
        return isParameterVariadicStar(argIndex) || isParameterVariadicPlus(argIndex);
    }

    protected abstract Type getParameterType(int argIndex);

    //protected abstract String getParameterName(int argIndex);
    protected abstract JCExpression getParameterExpression(int argIndex);

    protected abstract boolean isArgumentComprehension(int argIndex);

    protected abstract boolean getParameterUnboxed(int argIndex);

    protected abstract BoxingStrategy getParameterBoxingStrategy(int argIndex);

    protected abstract boolean hasParameter(int argIndex);

    // to be overridden
    protected boolean isParameterRaw(int argIndex) {
        return false;
    }

    // to be overridden
    protected boolean isParameterWithConstrainedTypeParameters(int argIndex) {
        return false;
    }

    // to be overridden
    protected boolean isParameterWithDependentCovariantTypeParameters(int argIndex) {
        return false;
    }

    /** Gets the number of arguments actually being supplied */
    protected abstract int getNumArguments();

    /** Gets the number of parameters that are available */
    protected abstract int getNumParameters();

    /**
     * Gets the transformed expression supplying the argument value for the 
     * given argument index
     */
    //protected abstract JCExpression getTransformedArgumentExpression(int argIndex);

    protected abstract boolean isSpread();
    protected abstract boolean isArgumentSpread(int argIndex);

    /**
     * For subclasses if the target method doesn't support default values for variadic
     * using overloading.
     */
    protected boolean requiresEmptyForVariadic() {
        return false;
    }

    protected boolean isJavaMethod() {
        if(getPrimaryDeclaration() instanceof Function) {
            return gen.isJavaMethod((Function) getPrimaryDeclaration());
        } else if (getPrimaryDeclaration() instanceof Class) {
            return gen.isJavaCtor((Class) getPrimaryDeclaration());
        }
        return false;
    }

    protected abstract Tree.Expression getArgumentExpression(int argIndex);
    protected Type getArgumentType(int argIndex) {
        return getArgumentExpression(argIndex).getTypeModel();
    }
    
    protected abstract JCExpression getTransformedArgumentExpression(int argIndex);
    
}

/**
 * Generates calls to Callable methods. This is for regular {@code Callable<T>} objects and not
 * functional parameters, which have more info like parameter names and default values.
 */
class IndirectInvocation extends SimpleInvocation {

    private final java.util.List<Type> parameterTypes;
    private final java.util.List<Tree.Expression> argumentExpressions;
    private final Comprehension comprehension;
    private final boolean variadic;
    private final boolean spread;
    private final boolean unknownArguments;

    public IndirectInvocation(
            AbstractTransformer gen, 
            Tree.Term primary,
            Declaration primaryDeclaration,
            Tree.InvocationExpression invocation) {
        super(gen, primary, primaryDeclaration, invocation.getTypeModel(), invocation);

        Type callableType = primary.getTypeModel();

        this.unknownArguments = gen.isUnknownArgumentsCallable(callableType);
        
        final java.util.List<Type> parameterTypes;
        // if we have an unknown parameter list, like Callble<Ret,Args>, we can't look at parameter types
        // note that ATM the typechecker only allows a single argument to be passed in spread form in this
        // case so we don't need to look at parameter types
        if(!this.unknownArguments){
            // find the parameter types
            final java.util.List<Type> tas = new ArrayList<>();
            tas.add(gen.getReturnTypeOfCallable(callableType));
            for (int ii = 0, l = gen.getNumParametersOfCallable(callableType); ii < l; ii++) {
                tas.add(gen.getParameterTypeOfCallable(callableType, ii));
            }
            this.variadic = gen.isVariadicCallable(callableType);
            //final java.util.List<Type> tas = primary.getTypeModel().getTypeArgumentList();
            parameterTypes = tas.subList(1, tas.size());
        }else{
            this.variadic = false; // we don't know
            parameterTypes = Collections.emptyList();
        }
        
        PositionalArgumentList positionalArgumentList = invocation.getPositionalArgumentList();
        final java.util.List<Tree.Expression> argumentExpressions = new ArrayList<Tree.Expression>(positionalArgumentList.getPositionalArguments().size());
        boolean spread = false;
        Comprehension comprehension = null;
        for (Tree.PositionalArgument argument : positionalArgumentList.getPositionalArguments()) {
            if(argument instanceof Tree.ListedArgument)
                argumentExpressions.add(((Tree.ListedArgument)argument).getExpression());
            else if(argument instanceof Tree.SpreadArgument){
                argumentExpressions.add(((Tree.SpreadArgument)argument).getExpression());
                spread = true;
            }else{
                comprehension = (Comprehension) argument;
            }

        }
        this.spread = spread;
        this.comprehension = comprehension;
        this.argumentExpressions = argumentExpressions;
        this.parameterTypes = parameterTypes;
    }
    
    @Override
    public boolean isIndirect() {
        return true;
    }
    
    @Override
    boolean isMemberRefInvocation() {
        return CodegenUtil.isMemberReferenceInvocation((Tree.InvocationExpression)getNode());
    }
    
    @Override
    public int getNumParameters() {
        return parameterTypes.size();
    }
    
    @Override
    protected void addReifiedArguments(ListBuffer<ExpressionAndType> result) {
        // can never be parameterised
    }

    @Override
    protected boolean isParameterVariadicStar(int argIndex) {
        return variadic && argIndex >= parameterTypes.size() - 1;
    }
    
    @Override
    protected boolean isParameterVariadicPlus(int argIndex) {
        return variadic && argIndex >= parameterTypes.size() - 1;
    }

    @Override
    protected Type getParameterType(int argIndex) {
        // in the Java code, all Callable.call() params are of type Object so let's not
        // pretend they are typed, this saves a lot of casting.
        // except for sequenced parameters where we do care about the iterated type
        if(isParameterSequenced(argIndex)){
            if (isArgumentSpread(argIndex) 
                    && isParameterVariadicPlus(argIndex)) {
                // We might end up calling Util.sequentialInstance to handle 
                // the spread and if the spread argument is empty, we need to 
                // transform it to something of sequential type so we can 
                // call sequentialInstance(). 
                // No worries if we don't end up calling sequentialInstance(), 
                // because the call it indirect, so not type-safe at the Java level anyway
                return gen.typeFact().getSequentialType(gen.typeFact().getIteratedType(parameterTypes.get(parameterTypes.size()-1)));
            } else {
                return parameterTypes.get(parameterTypes.size()-1);
            }
        }
        return gen.typeFact().getObjectType();
    }

    @Override
    protected JCExpression getParameterExpression(int argIndex) {
        return gen.naming.makeQuotedIdent("arg" + argIndex);
    }

    @Override
    protected boolean getParameterUnboxed(int argIndex) {
        return false;
    }

    @Override
    protected BoxingStrategy getParameterBoxingStrategy(int argIndex) {
        return BoxingStrategy.BOXED;
    }

    @Override
    protected boolean hasParameter(int argIndex) {
        return true;
    }

    @Override
    protected int getNumArguments() {
        return argumentExpressions.size() + (comprehension != null ? 1 : 0);
    }

    @Override
    protected boolean isSpread() {
        return comprehension != null || spread;
    }

    @Override
    public boolean isUnknownArguments(){
        return unknownArguments;
    }
    
    @Override
    protected boolean isArgumentSpread(int argIndex) {
        if(spread) // spread args must be last argument
            return argIndex == argumentExpressions.size() - 1;
        if(comprehension != null) // comprehension must be last
            return argIndex == argumentExpressions.size();
        return false;
    }
    
    @Override
    protected boolean isArgumentComprehension(int argIndex){
        // comprehensions are listed as last argument after all argumentExpressions
        return comprehension != null && argIndex == argumentExpressions.size();
    }
    
    @Override
    protected Tree.Expression getArgumentExpression(int argIndex) {
        return argumentExpressions.get(argIndex);
    }
    
    @Override
    protected Type getArgumentType(int argIndex) {
        if (argIndex == argumentExpressions.size() && comprehension != null) {   
            return gen.typeFact().getSequentialType(comprehension.getTypeModel());
        }
        return super.getArgumentType(argIndex);
    }
    
    @Override
    protected JCExpression getTransformedArgumentExpression(int argIndex) {
        if (argIndex == argumentExpressions.size() && comprehension != null) {
            Type type = getParameterType(argIndex);
            return gen.expressionGen().comprehensionAsSequential(comprehension, type); 
        }
        Tree.Expression expr = getArgumentExpression(argIndex);
        if (expr.getTerm() instanceof FunctionArgument) {
            FunctionArgument farg = (FunctionArgument)expr.getTerm();
            return gen.expressionGen().transform(farg, getParameterType(argIndex));
        }
        return gen.expressionGen().transformArg(this, argIndex);
    }
    
    @Override
    public void location(CallBuilder callBuilder) {
        callBuilder.location(((Tree.InvocationExpression)getNode()).getPositionalArgumentList());
    }

}

/**
 * An abstract implementation of InvocationBuilder support invocation 
 * via positional arguments. Supports with sequenced arguments but not 
 * defaulted arguments.
 */
abstract class DirectInvocation extends SimpleInvocation {

    private final Reference producedReference;

    protected DirectInvocation(
            AbstractTransformer gen,
            Tree.Term primary,
            Declaration primaryDeclaration,
            Reference producedReference, Type returnType, 
            Node node) {
        super(gen, primary, primaryDeclaration, returnType, node);
        this.producedReference = producedReference;
    }

    protected Reference appliedReference() {
        return producedReference;
    }

    /**
     * Gets the Parameter corresponding to the given argument
     * @param argIndex
     * @return
     */
    protected abstract Parameter getParameter(int argIndex);
    
    @Override
    protected boolean isParameterVariadicStar(int argIndex) {
        return getParameter(argIndex).isSequenced() && !getParameter(argIndex).isAtLeastOne();
    }
    
    @Override
    protected boolean isParameterVariadicPlus(int argIndex) {
        return getParameter(argIndex).isSequenced() && getParameter(argIndex).isAtLeastOne();
    }
    
    @Override
    protected Type getParameterType(int argIndex) {
        int flags = AbstractTransformer.TP_TO_BOUND;
        if(isParameterSequenced(argIndex)
                && isJavaMethod()
                && isSpread())
            flags |= AbstractTransformer.TP_SEQUENCED_TYPE;
        return gen.expressionGen().getTypeForParameter(getParameter(argIndex), appliedReference(), flags);
    }
    
    @Override
    protected JCExpression getParameterExpression(int argIndex) {
        return gen.naming.makeName(getParameter(argIndex).getModel(), Naming.NA_MEMBER);
    }
    
    @Override
    protected boolean getParameterUnboxed(int argIndex) {
        return getParameter(argIndex).getModel().getUnboxed();
    }
    
    @Override
    protected BoxingStrategy getParameterBoxingStrategy(int argIndex) {
        Parameter param = getParameter(argIndex);
        if (isOnValueType() && Decl.isValueTypeDecl(getParameterTypeForValueType(producedReference, param))) {
            return BoxingStrategy.UNBOXED;
        }
        return CodegenUtil.getBoxingStrategy(param.getModel());
    }
    
    @Override
    protected boolean hasParameter(int argIndex) {
        return getParameter(argIndex) != null;
    }
    
    @Override
    protected void addReifiedArguments(ListBuffer<ExpressionAndType> result) {
        if (getPrimary().getTypeModel().isTypeConstructor()) {
            addTypeConstructorArguments(getPrimary().getTypeModel().getTypeArgumentList(), result);
        } else {
            addReifiedArguments(gen, producedReference, result);
        }
    }
    
    private void addTypeConstructorArguments(
            java.util.List<Type> typeArgumentList,
            ListBuffer<ExpressionAndType> result) {
        int ii = 0;
        for(Type reifiedTypeArg : typeArgumentList)
            result.append(new ExpressionAndType(
                    gen.make().Indexed(
                            gen.makeUnquotedIdent("applied"),
                            gen.make().Literal(ii++)), 
                    gen.makeTypeDescriptorType()));
    }

    static void addReifiedArguments(AbstractTransformer gen, Reference producedReference, ListBuffer<ExpressionAndType> result) {
        java.util.List<JCExpression> reifiedTypeArgs = gen.makeReifiedTypeArguments(producedReference);
        for(JCExpression reifiedTypeArg : reifiedTypeArgs)
            result.append(new ExpressionAndType(reifiedTypeArg, gen.makeTypeDescriptorType()));
    }
}

/**
 * InvocationBuilder used for 'normal' method and initializer invocations via 
 * positional arguments. Supports sequenced and defaulted arguments.
 */
class PositionalInvocation extends DirectInvocation {

    private final Tree.PositionalArgumentList positional;
    private final java.util.List<Parameter> parameters;

    public PositionalInvocation(
            AbstractTransformer gen, 
            Tree.Term primary,
            Declaration primaryDeclaration,
            Reference producedReference, Tree.InvocationExpression invocation,
            java.util.List<Parameter> parameters) {
        super(gen, primary, primaryDeclaration, producedReference, invocation.getTypeModel(), invocation);
        positional = invocation.getPositionalArgumentList();
        this.parameters = parameters;
    }
    java.util.List<Parameter> getParameters() {
        return parameters;
    }
    Tree.PositionalArgumentList getPositional() {
        return positional;
    }
    @Override
    protected Tree.Expression getArgumentExpression(int argIndex) {
        PositionalArgument arg = getPositional().getPositionalArguments().get(argIndex);
        if(arg instanceof Tree.ListedArgument)
            return ((Tree.ListedArgument) arg).getExpression();
        if(arg instanceof Tree.SpreadArgument)
            return ((Tree.SpreadArgument) arg).getExpression();
        throw new BugException("argument expression is " + arg.getNodeType());
    }
    
    @Override
    protected boolean isArgumentComprehension(int argIndex){
        PositionalArgument arg = getPositional().getPositionalArguments().get(argIndex);
        return arg instanceof Tree.Comprehension;
    }
    
    @Override
    protected Type getArgumentType(int argIndex) {
        PositionalArgument arg = getPositional().getPositionalArguments().get(argIndex);
        if (arg instanceof Tree.Comprehension) {
            return gen.typeFact().getSequentialType(arg.getTypeModel());
        }
        return arg.getTypeModel();
    }
    @Override
    protected JCExpression getTransformedArgumentExpression(int argIndex) {
        PositionalArgument arg = getPositional().getPositionalArguments().get(argIndex);
        // FIXME: I don't like much this weird special case here
        if(arg instanceof Tree.ListedArgument){
            Tree.Expression expr = ((Tree.ListedArgument) arg).getExpression();
            if (expr.getTerm() instanceof FunctionArgument) {
                FunctionArgument farg = (FunctionArgument)expr.getTerm();
                return gen.expressionGen().transform(farg, getParameterType(argIndex));
            }
        }
        // special case for comprehensions which are not expressions
        if(arg instanceof Tree.Comprehension){
            Type type = getParameterType(argIndex);
            return gen.expressionGen().comprehensionAsSequential((Comprehension) arg, type); 
        }
        return gen.expressionGen().transformArg(this, argIndex);
    }
    @Override
    protected Parameter getParameter(int argIndex) {
        return parameters.get(argIndex >= parameters.size() ? parameters.size()-1 : argIndex);
    }
    @Override
    protected int getNumArguments() {
        return getPositional().getPositionalArguments().size();
    }
    @Override
    protected int getNumParameters() {
        return parameters.size();
    }
    @Override
    protected boolean isSpread() {
        java.util.List<PositionalArgument> args = getPositional().getPositionalArguments();
        if(args.isEmpty())
            return false;
        PositionalArgument last = args.get(args.size()-1);
        return last instanceof Tree.SpreadArgument || last instanceof Tree.Comprehension;
    }
    
    @Override
    protected boolean isArgumentSpread(int argIndex) {
        PositionalArgument arg = getPositional().getPositionalArguments().get(argIndex);
        return arg instanceof Tree.SpreadArgument || arg instanceof Tree.Comprehension;
    }
    
    @Override
    protected boolean isParameterRaw(int argIndex){
        return isParameterRaw(getParameter(argIndex));
    }
    
    @Override
    protected boolean isParameterWithConstrainedTypeParameters(int argIndex) {
        return isParameterWithConstrainedTypeParameters(getParameter(argIndex));
    }

    @Override
    protected boolean isParameterWithDependentCovariantTypeParameters(int argIndex) {
        return isParameterWithDependentCovariantTypeParameters(getParameter(argIndex));
    }

    protected boolean hasDefaultArgument(int ii) {
        return getParameters().get(ii).isDefaulted();
    }
    
    @Override
    public void location(CallBuilder callBuilder) {
        callBuilder.location(positional);
    }
}

/**
 * InvocationBuilder used for constructing invocations of {@code super()}
 * when creating constructors.
 */
class SuperInvocation extends PositionalInvocation {
    
    static Declaration unaliasedPrimaryDeclaration(Tree.InvocationExpression invocation) {
        Declaration declaration = ((Tree.MemberOrTypeExpression)invocation.getPrimary()).getDeclaration();
        if (declaration instanceof ClassAlias) {
            Type et = ((ClassAlias) declaration).getExtendedType();
            if (et!=null) {
                declaration = et.getDeclaration();
            }
        }
        return declaration;
    }
    
    private final ClassOrInterface sub;
    private CtorDelegation delegation;
    private boolean delegationDelegation;
    
    SuperInvocation(AbstractTransformer gen,
            ClassOrInterface sub,
            CtorDelegation delegation,
            Tree.InvocationExpression invocation,
            ParameterList parameterList, 
            boolean delegationDelegation) {
        super(gen, 
                invocation.getPrimary(), 
                unaliasedPrimaryDeclaration(invocation),
                ((Tree.MemberOrTypeExpression)invocation.getPrimary()).getTarget(),
                invocation,
                parameterList.getParameters());
        this.sub = sub;
        this.delegation = delegation;
        this.delegationDelegation = delegationDelegation;
    }
    
    CtorDelegation getDelegation() {
        return delegation;
    }
    
    ClassOrInterface getSub() {
        return sub;
    }

    @Override
    public Constructor getConstructor() {
        // For the constructor we need the possibly-aliased primary declaration
        Declaration primaryDeclaration = ((Tree.MemberOrTypeExpression)getPrimary()).getDeclaration();
        return getConstructorFromPrimary(primaryDeclaration);
    }

    public boolean isDelegationDelegation() {
        return delegationDelegation;
    }

    @Override
    protected void addReifiedArguments(ListBuffer<ExpressionAndType> result) {
        if (!isDelegationDelegation()) {
            super.addReifiedArguments(result);
        } else {
            addReifiedArguments(gen, sub.getReference(), result);
        }
    }
}


/**
 * InvocationBuilder for constructing the invocation of a method reference 
 * used when implementing {@code Callable.call()}.
 * 
 * This will be used when you do:
 * <p>
 * <code>
 * void f(){
 *   value callable = f;
 * }
 * </code>
 * </p>
 * And will generate the code required to put inside the Callable's {@code $call} method to
 * invoke {@code f}: {@code f();}. The generation of the Callable or its methods is not done here.
 */
class CallableInvocation extends DirectInvocation {
    
    private final java.util.List<Parameter> callableParameters;
    
    private final java.util.List<Parameter> functionalParameters;

    private final int parameterCount;
    
    private boolean tempVars;

    private Naming.SyntheticName instanceFieldName;
    private boolean instanceFieldIsBoxed;

    public CallableInvocation(
            AbstractTransformer gen, Naming.SyntheticName primaryName, boolean primaryIsBoxed, Tree.Term primary,
            Declaration primaryDeclaration, Reference producedReference, Type returnType,
            Tree.Term expr, ParameterList parameterList, int parameterCount, boolean tempVars) {
        super(gen, primary, primaryDeclaration, producedReference, returnType, expr);
        this.instanceFieldName = primaryName;
        this.instanceFieldIsBoxed = primaryIsBoxed;
        Functional functional = null;
        if(primary instanceof Tree.MemberOrTypeExpression)
            functional = (Functional) ((Tree.MemberOrTypeExpression) primary).getDeclaration();
        else if(primary instanceof Tree.FunctionArgument)
            functional = ((Tree.FunctionArgument) primary).getDeclarationModel();
        if(functional != null)
            callableParameters = functional.getFirstParameterList().getParameters();
        else
            callableParameters = Collections.emptyList();
        functionalParameters = parameterList.getParameters();
        this.parameterCount = parameterCount;
        setUnboxed(expr.getUnboxed());
        setBoxingStrategy(BoxingStrategy.BOXED);// Must be boxed because non-primitive return type
        handleBoxing(true);
        if (producedReference.getDeclaration() instanceof TypedDeclaration) {
            TypedDeclaration tdecl = (TypedDeclaration) producedReference.getDeclaration();
            setErased(CodegenUtil.hasTypeErased(tdecl)|| CodegenUtil.hasTypeErased(primary));
        }
        this.tempVars = tempVars;
    }

    @Override
    boolean isOnValueType() {
        return super.isOnValueType() && !instanceFieldIsBoxed;
    }

    @Override
    protected int getNumArguments() {
        return parameterCount;
    }
    @Override
    protected int getNumParameters() {
        return functionalParameters.size();
    }
    @Override
    protected boolean isSpread() {
        return isParameterSequenced(getNumArguments() - 1);
    }
    @Override
    protected boolean isArgumentSpread(int argIndex) {
        return isSpread() && argIndex == getNumArguments() - 1;
    }
    @Override
    protected boolean isArgumentComprehension(int argIndex){
        throw new BugException("I override getTransformedArgumentExpression(), so should never be called");
    }
    @Override
    protected JCExpression getTransformedArgumentExpression(int argIndex) {
        Parameter param = callableParameters.get(argIndex);

        // note: we don't deal with unboxing here, as that is taken care of already by CallableBuilder by unboxing the
        // Callable arguments into unboxed local vars if required and if it's a value type
        String paramName;
        if (tempVars) {
            paramName = Naming.getCallableTempVarName(param);
        } else if (getPrimaryDeclaration() instanceof Class &&
            ((Class)getPrimaryDeclaration()).hasConstructors()) {
            paramName = Naming.getAliasedParameterName(param);
        } else {
            paramName = param.getName();
        }
        return gen.makeUnquotedIdent(paramName);
    }
    @Override
    protected Parameter getParameter(int index) {
        return functionalParameters.get(index);
    }
    @Override
    protected Expression getArgumentExpression(int argIndex) {
        throw new BugException("I override getTransformedArgumentExpression(), so should never be called");
    }
    @Override
    protected Type getArgumentType(int argIndex) {
        Parameter param = callableParameters.get(argIndex);
        return getParameterTypeForValueType(appliedReference(), param);
    }
    
    @Override
    public void location(CallBuilder callBuilder) {
        callBuilder.location(null);
    }
    
    protected TransformedInvocationPrimary transformPrimary(JCExpression primaryExpr,
            String selector) {
        return new TransformedInvocationPrimary(instanceFieldName != null ? instanceFieldName.makeIdent() : primaryExpr, selector);
    }
    
    public Constructor getConstructor() {
        return getConstructorFromPrimary(appliedReference().getDeclaration());
    }
}

/**
 * InvocationBuilder for methods specified with a method reference. This builds the specifier invocation
 * within the body of the specified method.
 * 
 * For example for {@code void foo(); foo = f;} we generate: {@code f()} that you would then place into
 * the generated method for {@code foo}.
 */
class MethodReferenceSpecifierInvocation extends DirectInvocation {
    
    private final Function method;

    public MethodReferenceSpecifierInvocation(
            AbstractTransformer gen, Tree.Primary primary,
            Declaration primaryDeclaration,
            Reference producedReference, Function method, Tree.SpecifierExpression node) {
        super(gen, primary, primaryDeclaration, producedReference, method.getType(), node);
        this.method = method;
        setUnboxed(primary.getUnboxed());
        setBoxingStrategy(CodegenUtil.getBoxingStrategy(method));
    }

    @Override
    protected int getNumArguments() {
        return method.getFirstParameterList().getParameters().size();
    }
    
    @Override
    protected int getNumParameters() {
        return method.getFirstParameterList().getParameters().size();
    }
    
    @Override
    protected JCExpression getTransformedArgumentExpression(int argIndex) {
        Type exprType = getParameterType(argIndex);
        Parameter declaredParameter = ((Functional)getPrimaryDeclaration()).getFirstParameterList().getParameters().get(argIndex);
        JCExpression result = getParameterExpression(argIndex);
        result = gen.expressionGen().applyErasureAndBoxing(
                result, 
                exprType, 
                !getParameterUnboxed(argIndex), 
                CodegenUtil.getBoxingStrategy(declaredParameter.getModel()), 
                declaredParameter.getType());
        return result;
    }
    @Override
    protected Parameter getParameter(int argIndex) {
        return method.getFirstParameterList().getParameters().get(argIndex);
    }
    @Override
    protected boolean isSpread() {
        return method.getFirstParameterList().getParameters().get(getNumArguments() - 1).isSequenced();
    }
    @Override
    protected boolean isArgumentSpread(int argIndex) {
        return isSpread() && argIndex == getNumArguments() - 1;
    }
    @Override
    protected Expression getArgumentExpression(int argIndex) {
        throw new BugException("I override getTransformedArgumentExpression(), so should never be called");
    }
    @Override
    protected boolean isArgumentComprehension(int argIndex){
        throw new BugException("I override getTransformedArgumentExpression(), so should never be called");
    }
    @Override
    public void location(CallBuilder callBuilder) {
        callBuilder.location(null);
    }
}

/**
 * InvocationBuilder for methods specified eagerly with a Callable. This builds the Callable invocation
 * within the body of the specified method.
 * 
 * For example for {@code void foo(); foo = f;} we generate: {@code f.$call()} that you would then place into
 * the generated method for {@code foo}.
 */
class CallableSpecifierInvocation extends Invocation {
    
    private final Function method;
    private final JCExpression callable;
    private final Term callableTerm;
    public CallableSpecifierInvocation(
            AbstractTransformer gen, 
            Function method,
            JCExpression callableExpr,
            Tree.Term callableTerm,
            Node node) {
        super(gen, null, null, method.getType(), node);
        this.callable = callableExpr;
        this.callableTerm = callableTerm;
        this.method = method;
        // Because we're calling a callable, and they always return a 
        // boxed result
        setUnboxed(false);
        setBoxingStrategy(method.getUnboxed() ? BoxingStrategy.UNBOXED : BoxingStrategy.BOXED);
    }

    @Override
    protected void addReifiedArguments(ListBuffer<ExpressionAndType> result) {
        // nothing required here
    }
    
    JCExpression getCallable() {
        if(callableTerm != null)
            return unboxCallableIfNecessary(callable, callableTerm);
        return callable;
    }

    Function getMethod() {
        return method;
    }


}

/**
 * InvocationBuilder for 'normal' method and initializer invocations
 * using named arguments.
 */
class NamedArgumentInvocation extends Invocation {
    
    private final Tree.NamedArgumentList namedArgumentList;
    private final ListBuffer<JCStatement> vars = ListBuffer.lb();
    private final Naming.SyntheticName callVarName;
    private final Naming.SyntheticName varBaseName;
    private final Set<String> argNames = new HashSet<String>();
    private final TreeMap<Integer, Naming.SyntheticName> argsNamesByIndex = new TreeMap<Integer, Naming.SyntheticName>();
    private final TreeMap<Integer, ExpressionAndType> argsAndTypes = new TreeMap<Integer, ExpressionAndType>();
    private final Set<Parameter> bound = new HashSet<Parameter>();
    private Reference producedReference;
    
    public NamedArgumentInvocation(
            AbstractTransformer gen, Tree.Term primary,
            Declaration primaryDeclaration,
            Reference producedReference,
            Tree.InvocationExpression invocation) {
        super(gen, primary, primaryDeclaration, invocation.getTypeModel(), invocation);
        this.producedReference = producedReference;
        namedArgumentList = invocation.getNamedArgumentList();
        varBaseName = gen.naming.alias("arg");
        callVarName = varBaseName.suffixedBy(Suffix.$callable$);
    }
    
    @Override
    protected void addReifiedArguments(ListBuffer<ExpressionAndType> result) {
        Reference ref = gen.resolveAliasesForReifiedTypeArguments(producedReference);
        if(!gen.supportsReified(ref.getDeclaration()))
            return;
        int tpCount = gen.getTypeParameters(ref).size();
        for(int tpIndex = 0;tpIndex<tpCount;tpIndex++){
            result.append(new ExpressionAndType(reifiedTypeArgName(tpIndex).makeIdent(), gen.makeTypeDescriptorType()));
        }
    }
    
    ListBuffer<JCStatement> getVars() {
        return vars;
    }
    
    Iterable<Naming.SyntheticName> getArgsNamesByIndex() {
        return argsNamesByIndex.values();
    }
    
    Iterable<ExpressionAndType> getArgumentsAndTypes() {
        return argsAndTypes.values();
    }

    /**
     * Constructs the vars used in the Let expression
     */
    private void buildVars() {
        if (getPrimaryDeclaration() == null) {
            return;
        }
        boolean prev = gen.expressionGen().withinInvocation(false);
        java.util.List<Tree.NamedArgument> namedArguments = namedArgumentList.getNamedArguments();
        SequencedArgument sequencedArgument = namedArgumentList.getSequencedArgument();
        java.util.List<ParameterList> paramLists = ((Functional)getPrimaryDeclaration()).getParameterLists();
        java.util.List<Parameter> declaredParams = paramLists.get(0).getParameters();
        appendVarsForNamedArguments(namedArguments, declaredParams);
        appendVarsForReifiedTypeArguments();
        if(sequencedArgument != null)
            appendVarsForSequencedArguments(sequencedArgument, declaredParams);
        boolean hasDefaulted = appendVarsForDefaulted(declaredParams);
        
        if (hasDefaulted 
                && !Strategy.defaultParameterMethodStatic(getPrimaryDeclaration())
                && !Strategy.defaultParameterMethodOnOuter(getPrimaryDeclaration())) {
            vars.prepend(makeThis());
        }
        gen.expressionGen().withinInvocation(prev);
    }
    
    private void appendVarsForReifiedTypeArguments() {
        java.util.List<JCExpression> reifiedTypeArgs = gen.makeReifiedTypeArguments(producedReference);
        int index = 0;
        for(JCExpression reifiedTypeArg : reifiedTypeArgs){
            Naming.SyntheticName argName = reifiedTypeArgName(index);
            JCVariableDecl varDecl = gen.makeVar(argName, gen.makeTypeDescriptorType(), reifiedTypeArg);
            this.vars.append(varDecl);
            index++;
        }
    }
    
    private void appendVarsForSequencedArguments(Tree.SequencedArgument sequencedArgument, java.util.List<Parameter> declaredParams) {
        // FIXME: this is suspisciously similar to AbstractTransformer.makeIterable(java.util.List<Tree.PositionalArgument> list, Type seqElemType)
        // and possibly needs to be merged
        Parameter parameter = sequencedArgument.getParameter();
        Type parameterType = parameterType(parameter, parameter.getType(), gen.TP_TO_BOUND);
        // find out the individual type, we use the argument type for the value, and the param type for the temp variable
        Type tupleType = AnalyzerUtil.getTupleType(sequencedArgument.getPositionalArguments(), gen.typeFact(), false);
        Type argumentsType = tupleType.getSupertype(gen.typeFact().getIterableDeclaration());
        Type iteratedType = gen.typeFact().getIteratedType(argumentsType);
        Type absentType = gen.typeFact().getIteratedAbsentType(argumentsType);
        // we can't just generate types like Foo<?> if the target type param is not raw because the bounds will
        // not match, so we go raw, we also ignore primitives naturally
        int flags = JT_RAW | JT_NO_PRIMITIVES;
        JCTree.JCExpression sequenceValue = gen.makeLazyIterable(sequencedArgument, iteratedType, absentType, flags);
        JCTree.JCExpression sequenceType = gen.makeJavaType(parameterType, flags);
        
        Naming.SyntheticName argName = argName(parameter);

        JCTree.JCVariableDecl varDecl = gen.makeVar(argName, sequenceType, sequenceValue);
        gen.at(getPrimary());
        bind(parameter, argName, gen.makeJavaType(parameterType, flags), List.<JCTree.JCStatement>of(varDecl));
    }

    private JCExpression makeDefaultedArgumentMethodCall(Parameter param) {
        JCExpression thisExpr = null;
        switch (Strategy.defaultParameterMethodOwner(param.getModel())) {
        case SELF:
        case STATIC:
            break;
        case OUTER:
            if(getQmePrimary() != null && !Decl.isConstructor(getPrimaryDeclaration()))
                thisExpr = callVarName.makeIdent();
            break;
        case OUTER_COMPANION:
            thisExpr = callVarName.makeIdent();
            break;
        case INIT_COMPANION:
            thisExpr = varBaseName.suffixedBy(Suffix.$argthis$).makeIdent();
            if (isOnValueType()) {
                thisExpr = gen.boxType(thisExpr, getQmePrimary().getTypeModel());
            }
            break;
        }
        JCExpression defaultValueMethodName = gen.naming.makeDefaultedParamMethod(thisExpr, param);
        JCExpression argExpr = gen.at(getNode()).Apply(null, 
                defaultValueMethodName, 
                makeVarRefArgumentList(param));
        return argExpr;
    }
    
    // Make a list of ($arg0, $arg1, ... , $argN)
    // or ($arg$this$, $arg0, $arg1, ... , $argN)
    private List<JCExpression> makeVarRefArgumentList(Parameter param) {
        ListBuffer<JCExpression> names = ListBuffer.<JCExpression> lb();
        if (!Strategy.defaultParameterMethodStatic(getPrimaryDeclaration())
                && Strategy.defaultParameterMethodTakesThis(param.getModel())) {
            names.append(varBaseName.suffixedBy(Suffix.$argthis$).makeIdent());
        }
        // put all the required reified type args too
        Reference ref = gen.resolveAliasesForReifiedTypeArguments(producedReference);
        int tpCount = gen.getTypeParameters(ref).size();
        for(int tpIndex = 0;tpIndex<tpCount;tpIndex++){
            names.append(reifiedTypeArgName(tpIndex).makeIdent());
        }
        final int parameterIndex = parameterIndex(param);
        for (int ii = 0; ii < parameterIndex; ii++) {
            names.append(this.argsNamesByIndex.get(ii).makeIdent());
        }
        return names.toList();
    }
    
    /** Generates the argument name; namedArg may be null if no  
     * argument was given explicitly */
    private Naming.SyntheticName argName(Parameter param) {
        final int paramIndex = parameterIndex(param);
        //if (this.argNames.isEmpty()) {
            //this.argNames.addAll(Collections.<String>nCopies(parameterList(param).size(), null));
        //}
        final Naming.SyntheticName argName = varBaseName.suffixedBy(paramIndex);
        if (this.argsNamesByIndex.containsValue(argName)) {
            throw new BugException();
        }
        //if (!this.argNames.add(argName)) {
        //    throw new BugException();
        //}
        return argName;
    }

    /** Generates the argument name; namedArg may be null if no  
     * argument was given explicitly */
    private Naming.SyntheticName reifiedTypeArgName(int index) {
        return varBaseName.suffixedBy(Suffix.$reified$, index);
    }

    private java.util.List<Parameter> parameterList(Parameter param) {
        Functional functional = (Functional)param.getDeclaration();
        return functional.getFirstParameterList().getParameters();
    }
    
    private int parameterIndex(Parameter param) {
        return parameterList(param).indexOf(param);
    }

    private Type parameterType(Parameter declaredParam, Type pt, int flags) {
        if(declaredParam == null)
            return pt;
        return gen.getTypeForParameter(declaredParam, producedReference, flags);
    }
    
    private void appendVarsForNamedArguments(
            java.util.List<Tree.NamedArgument> namedArguments,
            java.util.List<Parameter> declaredParams) {
        // Assign vars for each named argument given
        for (Tree.NamedArgument namedArg : namedArguments) {
            gen.at(namedArg);
            Parameter declaredParam = namedArg.getParameter();
            Naming.SyntheticName argName = argName(declaredParam);
            if (namedArg instanceof Tree.SpecifiedArgument) {
                bindSpecifiedArgument((Tree.SpecifiedArgument)namedArg, declaredParam, argName);
            } else if (namedArg instanceof Tree.MethodArgument) {
                bindMethodArgument((Tree.MethodArgument)namedArg, declaredParam, argName);
            } else if (namedArg instanceof Tree.ObjectArgument) {
                bindObjectArgument((Tree.ObjectArgument)namedArg, declaredParam, argName);
            } else if (namedArg instanceof Tree.AttributeArgument) {
                bindAttributeArgument((Tree.AttributeArgument)namedArg, declaredParam, argName);
            } else {
                throw BugException.unhandledNodeCase(namedArg);
            }
            
        }
    }

    private void bindSpecifiedArgument(Tree.SpecifiedArgument specifiedArg,
            Parameter declaredParam, Naming.SyntheticName argName) {
        ListBuffer<JCStatement> statements;
        Tree.Expression expr = specifiedArg.getSpecifierExpression().getExpression();
        Type type = parameterType(declaredParam, expr.getTypeModel(), gen.TP_TO_BOUND);
        final BoxingStrategy boxType = getNamedParameterBoxingStrategy(declaredParam);
        int jtFlags = 0;
        int exprFlags = 0;
        if(boxType == BoxingStrategy.BOXED)
            jtFlags |= JT_TYPE_ARGUMENT;
        
        if(!isParameterRaw(declaredParam)) {
            exprFlags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_NOT_RAW;
        }
        if(isParameterWithConstrainedTypeParameters(declaredParam)) {
            exprFlags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_HAS_CONSTRAINED_TYPE_PARAMETERS;
            // we can't just generate types like Foo<?> if the target type param is not raw because the bounds will
            // not match, so we go raw
            jtFlags |= JT_RAW;
        }
        if(isParameterWithDependentCovariantTypeParameters(declaredParam)) {
            exprFlags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_HAS_DEPENDENT_COVARIANT_TYPE_PARAMETERS;
        }
        if (erasedArgument(TreeUtil.unwrapExpressionUntilTerm(expr))) {
            exprFlags |= ExpressionTransformer.EXPR_DOWN_CAST;
        }
        JCExpression typeExpr = gen.makeJavaType(type, jtFlags);
        JCExpression argExpr = gen.expressionGen().transformExpression(expr, boxType, type, exprFlags);
        JCVariableDecl varDecl = gen.makeVar(argName, typeExpr, argExpr);
        statements = ListBuffer.<JCStatement>of(varDecl);
        bind(declaredParam, argName, gen.makeJavaType(type, jtFlags), statements.toList());
    }

    private void bindMethodArgument(Tree.MethodArgument methodArg,
            Parameter declaredParam, Naming.SyntheticName argName) {
        ListBuffer<JCStatement> statements;
        Function model = methodArg.getDeclarationModel();
        List<JCStatement> body;
        boolean prevNoExpressionlessReturn = gen.statementGen().noExpressionlessReturn;
        boolean prevSyntheticClassBody = gen.expressionGen().withinSyntheticClassBody(Decl.isMpl(model) || gen.expressionGen().isWithinSyntheticClassBody()); 
        try {
            gen.statementGen().noExpressionlessReturn = gen.isAnything(model.getType());
            if (methodArg.getBlock() != null) {
                body = gen.statementGen().transformBlock(methodArg.getBlock());
                if (!methodArg.getBlock().getDefinitelyReturns()) {
                    if (gen.isAnything(model.getType())) {
                        body = body.append(gen.make().Return(gen.makeNull()));
                    } else {
                        body = body.append(gen.make().Return(gen.makeErroneous(methodArg.getBlock(), "compiler bug: non-void method does not definitely return")));
                    }
                }
            } else {
                Expression expr = methodArg.getSpecifierExpression().getExpression();
                BoxingStrategy boxing = CodegenUtil.getBoxingStrategy(model);
                Type type = model.getType();
                JCExpression transExpr = gen.expressionGen().transformExpression(expr, boxing, type);
                JCReturn returnStat = gen.make().Return(transExpr);
                body = List.<JCStatement>of(returnStat);
            }
        } finally {
            gen.expressionGen().withinSyntheticClassBody(prevSyntheticClassBody);
            gen.statementGen().noExpressionlessReturn = prevNoExpressionlessReturn;
        }
        
        Type callableType = model.appliedReference(null, Collections.<Type>emptyList()).getFullType();
        CallableBuilder callableBuilder = CallableBuilder.methodArgument(gen.gen(),
                methodArg,
                model,
                callableType, 
                Collections.singletonList(methodArg.getParameterLists().get(0)),
                gen.classGen().transformMplBody(methodArg.getParameterLists(), model, body));
        JCExpression callable = callableBuilder.build();
        JCExpression typeExpr = gen.makeJavaType(callableType, JT_RAW);
        JCVariableDecl varDecl = gen.makeVar(argName, typeExpr, callable);
        
        statements = ListBuffer.<JCStatement>of(varDecl);
        bind(declaredParam, argName, gen.makeJavaType(callableType), statements.toList());
    }

    private void bindObjectArgument(Tree.ObjectArgument objectArg,
            Parameter declaredParam, Naming.SyntheticName argName) {
        ListBuffer<JCStatement> statements;
        List<JCTree> object = gen.classGen().transformObjectArgument(objectArg);
        // No need to worry about boxing (it cannot be a boxed type) 
        JCVariableDecl varDecl = gen.makeLocalIdentityInstance(argName.getName(), Naming.quoteClassName(objectArg.getIdentifier().getText()), false);
        statements = toStmts(objectArg, object).append(varDecl);
        bind(declaredParam, argName, gen.makeJavaType(objectArg.getType().getTypeModel()), statements.toList());
    }

    private void bindAttributeArgument(Tree.AttributeArgument attrArg,
            Parameter declaredParam, Naming.SyntheticName argName) {
        ListBuffer<JCStatement> statements;
        final Value model = attrArg.getDeclarationModel();
        final String name = model.getName();
        String className = Naming.getAttrClassName(model, 0);
        final List<JCTree> attrClass = gen.gen().transformAttribute(model, name, className, null, attrArg.getBlock(), attrArg.getSpecifierExpression(), null, null);
        TypedReference typedRef = gen.getTypedReference(model);
        TypedReference nonWideningTypedRef = gen.nonWideningTypeDecl(typedRef);
        Type nonWideningType = gen.nonWideningType(typedRef, nonWideningTypedRef);
        Type type = parameterType(declaredParam, model.getType(), 0);
        final BoxingStrategy boxType = getNamedParameterBoxingStrategy(declaredParam);
        JCExpression initValue = gen.make().Apply(null, 
                gen.makeSelect(gen.makeUnquotedIdent(className), Naming.getGetterName(model)),
                List.<JCExpression>nil());
        initValue = gen.expressionGen().applyErasureAndBoxing(
                initValue, 
                nonWideningType, 
                !CodegenUtil.isUnBoxed(nonWideningTypedRef.getDeclaration()),
                boxType,
                type);
        JCTree.JCVariableDecl var = gen.make().VarDef(
                gen.make().Modifiers(FINAL, List.<JCAnnotation>nil()), 
                argName.asName(), 
                gen.makeJavaType(type, boxType==BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0), 
                initValue);
        statements = toStmts(attrArg, attrClass).append(var);
        bind(declaredParam, argName, gen.makeJavaType(type, boxType==BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0),
                statements.toList());
    }
    
    private void bind(Parameter param, Naming.SyntheticName argName, JCExpression argType, List<JCStatement> statements) {
        this.vars.appendList(statements);
        this.argsAndTypes.put(parameterIndex(param), new ExpressionAndType(argName.makeIdent(), argType));
        this.argsNamesByIndex.put(parameterIndex(param), argName);
        this.bound.add(param);
    }
    
    private ListBuffer<JCStatement> toStmts(Tree.NamedArgument namedArg, final List<JCTree> listOfStatements) {
        final ListBuffer<JCStatement> result = ListBuffer.<JCStatement>lb();
        for (JCTree tree : listOfStatements) {
            if (tree instanceof JCStatement) {
                result.append((JCStatement)tree);
            } else {
                result.append(gen.make().Exec(gen.makeErroneous(namedArg, "compiler bug: attempt to put a non-statement in a let")));
            }
        }
        return result;
    }
    
    private final void appendDefaulted(Parameter param, JCExpression argExpr) {
        // we can't just generate types like Foo<?> if the target type param is not raw because the bounds will
        // not match, so we go raw
        int flags = JT_RAW;
        if (getNamedParameterBoxingStrategy(param) == BoxingStrategy.BOXED) {
            flags |= JT_TYPE_ARGUMENT;
        }
        Type type = gen.getTypeForParameter(param, producedReference, gen.TP_TO_BOUND);
        Naming.SyntheticName argName = argName(param);
        JCExpression typeExpr = gen.makeJavaType(type, flags);
        JCVariableDecl varDecl = gen.makeVar(argName, typeExpr, argExpr);
        bind(param, argName, gen.makeJavaType(type, flags), List.<JCStatement>of(varDecl));
    }
    
    private BoxingStrategy getNamedParameterBoxingStrategy(Parameter param) {
        if (param != null) {
            if (isOnValueType() && Decl.isValueTypeDecl(getParameterTypeForValueType(producedReference, param))) {
                return BoxingStrategy.UNBOXED;
            }
            return CodegenUtil.getBoxingStrategy(param.getModel());
        } else {
            return BoxingStrategy.UNBOXED;
        }
    }
    
    private boolean appendVarsForDefaulted(java.util.List<Parameter> declaredParams) {
        boolean hasDefaulted = false;
        if (!Decl.isOverloaded(getPrimaryDeclaration())) {
            // append any arguments for defaulted parameters
            for (Parameter param : declaredParams) {
                if (bound.contains(param)) {
                    continue;
                }
                final JCExpression argExpr;
                if (Strategy.hasDefaultParameterValueMethod(param)) {
                    // special handling for "element" optional param of java array constructors
                    if(getPrimaryDeclaration() instanceof Class
                            && gen.isJavaArray(((Class)getPrimaryDeclaration()).getType())){
                        // default values are hard-coded to Java default values, and are actually ignored
                        continue;
                    }else if(getQmePrimary() != null 
                             && gen.isJavaArray(getQmePrimary().getTypeModel())){
                        // we support array methods with optional parameters
                        if(getPrimaryDeclaration() instanceof Function
                                && getPrimaryDeclaration().getName().equals("copyTo")){
                            if(param.getName().equals("sourcePosition")
                                    || param.getName().equals("destinationPosition")){
                                argExpr = gen.makeInteger(0);
                                hasDefaulted |= true;
                            }else if(param.getName().equals("length")){
                                argExpr = gen.makeSelect(varBaseName.suffixedBy(Suffix.$argthis$).makeIdent(), "length");
                                hasDefaulted |= true;
                            }else{
                                argExpr = gen.makeErroneous(this.getNode(), "compiler bug: argument to copyTo method of java array type not supported: "+param.getName());
                            }
                        }else{
                            argExpr = gen.makeErroneous(this.getNode(), "compiler bug: virtual method of java array type not supported: "+getPrimaryDeclaration());
                        }
                    }else{
                        argExpr = makeDefaultedArgumentMethodCall(param);
                        hasDefaulted |= true;
                    }
                } else if (Strategy.hasEmptyDefaultArgument(param)) {
                    argExpr = gen.makeEmptyAsSequential(true);
                } else if(gen.typeFact().isIterableType(param.getType())){
                    // must be an iterable we need to fill with empty
                    // FIXME: deal with this erasure bug later
                    argExpr = gen.make().TypeCast(gen.makeJavaType(gen.typeFact().getIterableDeclaration().getType(), AbstractTransformer.JT_RAW), gen.makeEmpty());
                } else {
                    argExpr = gen.makeErroneous(this.getNode(), "compiler bug: missing argument, and parameter is not defaulted");
                }
                appendDefaulted(param, argExpr);
            }
        }
        return hasDefaulted;
    }
    
    private final JCVariableDecl makeThis() {
        // first append $this
        JCExpression defaultedParameterInstance;
        // TODO Fix how we figure out the thisType, because it's doesn't 
        // handle type parameters correctly
        // we used to use thisType = gen.getThisType(getPrimaryDeclaration());
        final JCExpression thisType;
        Reference target = ((Tree.MemberOrTypeExpression)getPrimary()).getTarget();
        if (getPrimary() instanceof Tree.BaseMemberExpression
                && !gen.expressionGen().isWithinSyntheticClassBody()) {
            if (Decl.withinClassOrInterface(getPrimaryDeclaration())) {
                // a member method
                thisType = gen.makeJavaType(target.getQualifyingType(), JT_NO_PRIMITIVES);
                defaultedParameterInstance = gen.naming.makeThis();
            } else {
                // a local or toplevel function
                thisType = gen.naming.makeName((TypedDeclaration)getPrimaryDeclaration(), Naming.NA_WRAPPER);
                defaultedParameterInstance = gen.naming.makeName((TypedDeclaration)getPrimaryDeclaration(), Naming.NA_MEMBER);
            }
        } else if (getPrimary() instanceof Tree.BaseTypeExpression
                || getPrimary() instanceof Tree.QualifiedTypeExpression) {
            TypeDeclaration declaration = (TypeDeclaration)((Tree.MemberOrTypeExpression) getPrimary()).getDeclaration();
            thisType = gen.makeJavaType(declaration.getType(), JT_COMPANION);
            defaultedParameterInstance = gen.make().NewClass(
                    null, 
                    null,
                    gen.makeJavaType(declaration.getType(), JT_COMPANION), 
                    List.<JCExpression>nil(), null);
        } else {
            if (isOnValueType()) {
                thisType = gen.makeJavaType(target.getQualifyingType());
            } else {
                thisType = gen.makeJavaType(target.getQualifyingType(), JT_NO_PRIMITIVES);
            }
            defaultedParameterInstance = callVarName.makeIdent();
        }
        JCVariableDecl thisDecl = gen.makeVar(varBaseName.suffixedBy(Suffix.$argthis$), 
                thisType, 
                defaultedParameterInstance);
        return thisDecl;
    }


    
    @Override
    protected TransformedInvocationPrimary transformPrimary(JCExpression primaryExpr,
            String selector) {
        // We need to build the vars before transforming the primary, because the primary is just a var
        buildVars();
        JCExpression result;
        TransformedInvocationPrimary actualPrimExpr = super.transformPrimary(primaryExpr, selector);
        result = actualPrimExpr.expr;
        if (vars != null 
                && !vars.isEmpty() 
                && primaryExpr != null
                && selector != null) {
            // Prepare the first argument holding the primary for the call
            Type type = ((Tree.MemberOrTypeExpression)getPrimary()).getTarget().getQualifyingType();
            JCExpression varType;
            if (isOnValueType()) {
                varType = gen.makeJavaType(getQmePrimary().getTypeModel());
            } else {
                int jtFlags = JT_NO_PRIMITIVES;
                if (getPrimary() instanceof QualifiedTypeExpression
                        && !getPrimaryDeclaration().isShared()
                        && type.getDeclaration() instanceof Interface) {
                    jtFlags |= JT_COMPANION;
                } else if (
                        getPrimary() instanceof Tree.StaticMemberOrTypeExpression
                        && Decl.isPrivateAccessRequiringCompanion((Tree.StaticMemberOrTypeExpression)getPrimary())) {
                    jtFlags |= JT_COMPANION;
                }
                varType = gen.makeJavaType(type, jtFlags);
            }
            vars.prepend(gen.makeVar(callVarName, varType, result));
            result = callVarName.makeIdent();
        }
        final Constructor ctor = getConstructor();
        if (ctor != null && !Decl.isDefaultConstructor(ctor)) {
            argsAndTypes.put(-1, 
                    new ExpressionAndType(gen.naming.makeNamedConstructorName(ctor, false),
                            gen.naming.makeNamedConstructorType(ctor, false)));
        }
        return new TransformedInvocationPrimary(result, actualPrimExpr.selector);
    }
    
    @Override
    public void location(CallBuilder callBuilder) {
        callBuilder.location(namedArgumentList);
    }
}