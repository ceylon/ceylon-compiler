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

import static com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil.isIndirectInvocation;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.eliminateParensAndWidening;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.hasUncheckedNulls;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.unwrapExpressionUntilTerm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.compiler.java.codegen.Invocation.TransformedInvocationPrimary;
import com.redhat.ceylon.compiler.java.codegen.Naming.DeclNameFlag;
import com.redhat.ceylon.compiler.java.codegen.Naming.Substitution;
import com.redhat.ceylon.compiler.java.codegen.Naming.SyntheticName;
import com.redhat.ceylon.compiler.java.codegen.Operators.AssignmentOperatorTranslation;
import com.redhat.ceylon.compiler.java.codegen.Operators.OperatorTranslation;
import com.redhat.ceylon.compiler.java.codegen.Operators.OptimisationStrategy;
import com.redhat.ceylon.compiler.java.codegen.StatementTransformer.Cond;
import com.redhat.ceylon.compiler.java.codegen.StatementTransformer.CondList;
import com.redhat.ceylon.compiler.java.codegen.StatementTransformer.VarDefBuilder;
import com.redhat.ceylon.compiler.java.codegen.StatementTransformer.VarTrans;
import com.redhat.ceylon.compiler.java.codegen.recovery.HasErrorException;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LetExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.QualifiedMemberOrTypeExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.model.loader.JvmBackendUtil;
import com.redhat.ceylon.model.loader.NamingBase.Prefix;
import com.redhat.ceylon.model.loader.NamingBase.Suffix;
import com.redhat.ceylon.model.loader.model.FieldValue;
import com.redhat.ceylon.model.loader.model.OutputElement;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.Functional;
import com.redhat.ceylon.model.typechecker.model.Generic;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.ModelUtil;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ParameterList;
import com.redhat.ceylon.model.typechecker.model.Reference;
import com.redhat.ceylon.model.typechecker.model.Referenceable;
import com.redhat.ceylon.model.typechecker.model.Scope;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeAlias;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypeParameter;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedReference;
import com.redhat.ceylon.model.typechecker.model.Value;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

/**
 * This transformer deals with expressions only
 */
public class ExpressionTransformer extends AbstractTransformer {

    // flags for transformExpression
    /** 
     * This implies inclusion of the JT_SATISFIES flags when 
     * constructing the type for a variance typecast.
     */
    public static final int EXPR_FOR_COMPANION = 1;
    /** 
     * The expected type has type parameters 
     * (so an extra typecast to the raw type will be required)
     */
    public static final int EXPR_EXPECTED_TYPE_NOT_RAW = 1 << 1;
    /** 
     * The expected type has type parameters with {@code satisfies} 
     * constraints (which may be erased, and thus a type cast may be required 
     * irrespective of the presence of type arguments)
     */
    public static final int EXPR_EXPECTED_TYPE_HAS_CONSTRAINED_TYPE_PARAMETERS = 1 << 2;
    /** 
     * Seems to be used when the expected and expression 
     * types have no supertype in common.
     */
    public static final int EXPR_DOWN_CAST = 1 << 3;
    /** 
     * Use this when the expression being passed contains code to check for nulls coming from Java
     */
    public static final int EXPR_HAS_NULL_CHECK_FENCE = 1 << 4;
    /** 
     * This implies inclusion of the JT_COMPANION flags when 
     * constructing the type casts.
     */
    public static final int EXPR_WANTS_COMPANION = 1 << 5;

    /** 
     * The expected type has type parameters with {@code satisfies} 
     * constraints which have a covariant type parameter that is used by other
     * type parameter bounds, so we will always generate types where it is fixed rather
     * than using wildcards and in some cases we need to cast to raw
     */
    public static final int EXPR_EXPECTED_TYPE_HAS_DEPENDENT_COVARIANT_TYPE_PARAMETERS = 1 << 6;

    
    /**
     * Usually if a {@code long} to {@code int}, {@code short} or {@code byte}
     * conversion is required we use a Util invocation so that a runtime check 
     * is performed. 
     * 
     * In some circumstances (e.g. annotations) we need to use a 
     * real typecast.
     */
    public static final int EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK = 1 << 7;

    /** 
     * Use this when the expression is to be passed to a Java field/setter/parameter which does not require null-safety
     */
    public static final int EXPR_TARGET_ACCEPTS_NULL = 1 << 8;
    
    public static final int EXPR_WIDEN_PRIM = 1 << 9;

    static{
        // only there to make sure this class is initialised before the enums defined in it, otherwise we
        // get an initialisation error
        Operators.init();
    }
    
    private boolean inStatement = false;
    private boolean withinInvocation = false;
    private boolean withinSyntheticClassBody = false;
    /** The transformation for spread method references involves nested invocations of 
     * {@link #transformSpreadOperator(com.redhat.ceylon.compiler.typechecker.tree.Tree.QualifiedMemberExpression, TermTransformer)}
     * and what we generate depends on the invocation. This field will be odd on 
     * the "outer" invocation (which generates the outer part of the tree) 
     * and even on the "inner" invocation (which generates the inner part of the tree)
     */
    private Tree.QualifiedMemberExpression spreading = null;
    private Naming.SyntheticName memberPrimary = null;
    private ClassOrInterface withinSuperInvocation = null;
    private ClassOrInterface withinDefaultParameterExpression = null;
    
    public static ExpressionTransformer getInstance(Context context) {
        ExpressionTransformer trans = context.get(ExpressionTransformer.class);
        if (trans == null) {
            trans = new ExpressionTransformer(context);
            context.put(ExpressionTransformer.class, trans);
        }
        return trans;
    }

	private ExpressionTransformer(Context context) {
        super(context);
    }

    // Statement expressions
    
    public JCStatement transform(Tree.ExpressionStatement tree) {
        // ExpressionStatements do not return any value, therefore we don't care about the type of the expressions.
        inStatement = true;
        JCStatement result;
        HasErrorException error = errors().getFirstExpressionErrorAndMarkBrokenness(tree.getExpression());
        if (error != null) {
            result = this.makeThrowUnresolvedCompilationError(error);
        } else {
            result = at(tree).Exec(transformExpression(tree.getExpression(), BoxingStrategy.INDIFFERENT, null));
        }
        inStatement = false;
        return result;
    }
    
    public JCStatement transform(Tree.SpecifierStatement op) {
        // SpecifierStatement do not return any value, therefore we don't care about the type of the expressions.
        inStatement = true;
        JCStatement  result;
        HasErrorException error = errors().getFirstExpressionErrorAndMarkBrokenness(op.getBaseMemberExpression());
        if (error != null) {
            result = this.makeThrowUnresolvedCompilationError(error);
        } else if ((error = errors().getFirstExpressionErrorAndMarkBrokenness(op.getSpecifierExpression().getExpression())) != null) {
            result = this.makeThrowUnresolvedCompilationError(error);
        } else {
            result = at(op).Exec(transformAssignment(op, op.getBaseMemberExpression(), op.getSpecifierExpression().getExpression()));
        }
        inStatement = false;
        return result;
    }
    
    public JCExpression transform(Tree.SpecifierOrInitializerExpression expr,
            BoxingStrategy boxing, Type expectedType) {
        return transformExpression(expr.getExpression(), boxing, expectedType);
    }
    
    //
    // Any sort of expression
    
    JCExpression transformExpression(final TypedDeclaration declaration, final Tree.Term expr) {
        // make sure we use the best declaration for boxing and type
        TypedReference typedRef = getTypedReference(declaration);
        TypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
        Type nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
        // If this is a return statement in a MPL method we want to know 
        // the non-widening type of the innermost callable
        if (declaration instanceof Functional
                && Decl.isMpl((Functional)declaration)) {
            for (int i = ((Functional)declaration).getParameterLists().size(); i > 1; i--) {
                nonWideningType = getReturnTypeOfCallable(nonWideningType);
            }
        }
        // respect the refining definition of optionality
        nonWideningType = propagateOptionality(declaration.getType(), nonWideningType);
        BoxingStrategy boxing = CodegenUtil.getBoxingStrategy(nonWideningTypedRef.getDeclaration());
        return transformExpression(expr, boxing, nonWideningType);
    }

    private Type propagateOptionality(Type type, Type nonWideningType) {
        if(!isNull(type)){
            if(isOptional(type)){
                if(!isOptional(nonWideningType)){
                    return typeFact().getOptionalType(nonWideningType);
                }
            }else{
                if(isOptional(nonWideningType)){
                    return typeFact().getDefiniteType(nonWideningType);
                }
            }
        }
        return nonWideningType;
    }
    
    JCExpression transformExpression(final Tree.Term expr) {
        return transformExpression(expr, BoxingStrategy.BOXED, expr.getTypeModel());
    }

    JCExpression transformExpression(final Tree.Term expr, BoxingStrategy boxingStrategy, Type expectedType) {
        return transformExpression(expr, boxingStrategy, expectedType, 0);
    }
    
    JCExpression transformExpression(final Tree.Term expr, BoxingStrategy boxingStrategy, 
            Type expectedType, int flags) {
        if (expr == null) {
            return null;
        }
        
        at(expr);
        if (inStatement && boxingStrategy != BoxingStrategy.INDIFFERENT) {
            // We're not directly inside the ExpressionStatement anymore
            inStatement = false;
        }
        
        // Cope with things like ((expr))
        // FIXME: shouldn't that be in the visitor?
        Tree.Term term = expr;
        while (term instanceof Tree.Expression) {
            term = ((Tree.Expression)term).getTerm();
        }
        
        JCExpression result;
        if(term instanceof Tree.SequenceEnumeration){
            // special case to be able to pass expected type to sequences
            result = transform((Tree.SequenceEnumeration)term, expectedType);
        }else if(term instanceof Tree.DefaultOp){
            // special case to be able to pass expected type to else op
            result = transform((Tree.DefaultOp)term, expectedType);
        }else if(term instanceof Tree.LetExpression){
            // special case to be able to pass expected type to let op
            result = transform((Tree.LetExpression)term, expectedType);
        }else if(term instanceof Tree.IfExpression){
            // special case to be able to pass expected type to if op
            result = transform((Tree.IfExpression)term, expectedType);
        }else if(term instanceof Tree.SwitchExpression){
            // special case to be able to pass expected type to switch op
            result = transform((Tree.SwitchExpression)term, expectedType);
        }else{
            CeylonVisitor v = gen().visitor;
            final ListBuffer<JCTree> prevDefs = v.defs;
            final boolean prevInInitializer = v.inInitializer;
            final ClassDefinitionBuilder prevClassBuilder = v.classBuilder;
            try {
                v.defs = new ListBuffer<JCTree>();
                v.inInitializer = false;
                v.classBuilder = gen().current();
                term.visit(v);
                if (v.hasResult()) {
                    result = v.getSingleResult();
                    if (result == null) {
                        throw new BugException(term, "visitor yielded multiple results");
                    }
                } else {
                    throw new BugException(term, "visitor didn't yield any result");
                }
            } catch (BugException e) {
                result = e.makeErroneous(this, expr);
            } finally {
                v.classBuilder = prevClassBuilder;
                v.inInitializer = prevInInitializer;
                v.defs = prevDefs;
            }
        }

        if ((flags & EXPR_TARGET_ACCEPTS_NULL) == 0
                && expectedType != null 
                && hasUncheckedNulls(expr)
                && expectedType.isSubtypeOf(typeFact().getObjectType())) {
            result = utilInvocation().checkNull(result);
            flags |= EXPR_HAS_NULL_CHECK_FENCE;
        }
        result = applyErasureAndBoxing(result, expr, boxingStrategy, expectedType, flags);

        return result;
    }
    
    JCExpression transform(Tree.FunctionArgument functionArg, Type expectedType) {
        Function model = functionArg.getDeclarationModel();
        List<JCStatement> body;
        boolean prevNoExpressionlessReturn = statementGen().noExpressionlessReturn;
        boolean prevSyntheticClassBody = expressionGen().withinSyntheticClassBody(true);
        try {
            statementGen().noExpressionlessReturn = isAnything(model.getType());
            if (functionArg.getBlock() != null) {
                body = statementGen().transformBlock(functionArg.getBlock());
                if (!functionArg.getBlock().getDefinitelyReturns()) {
                    if (isAnything(model.getType())) {
                        body = body.append(make().Return(makeNull()));
                    } else {
                        body = body.append(make().Return(makeErroneous(functionArg.getBlock(), "compiler bug: non-void method does not definitely return")));
                    }
                }
            } else {
                Tree.Expression expr = functionArg.getExpression();
                JCExpression transExpr = expressionGen().transformExpression(expr);
                JCReturn returnStat = make().Return(transExpr);
                body = List.<JCStatement>of(returnStat);
            }
        } finally {
            expressionGen().withinSyntheticClassBody(prevSyntheticClassBody);
            statementGen().noExpressionlessReturn = prevNoExpressionlessReturn;
        }

        Type callableType = model.getTypedReference().getFullType();//functionArg.getTypeModel();
        CallableBuilder callableBuilder = CallableBuilder.methodArgument(gen(),
                functionArg,
                model,
                callableType, 
                Collections.singletonList(functionArg.getParameterLists().get(0)),
                classGen().transformMplBody(functionArg.getParameterLists(), model, body));
        
        JCExpression result = callableBuilder.build();
        result = applyErasureAndBoxing(result, callableType, true, BoxingStrategy.BOXED, expectedType);
        return result;
    }
    
    //
    // Boxing and erasure of expressions
    
    private JCExpression applyErasureAndBoxing(JCExpression result, Tree.Term expr, BoxingStrategy boxingStrategy, 
            Type expectedType) {
        return applyErasureAndBoxing(result, expr, boxingStrategy, expectedType, 0);
    }
    
    private JCExpression applyErasureAndBoxing(JCExpression result, Tree.Term expr, BoxingStrategy boxingStrategy, 
                Type expectedType, int flags) {
        Type exprType = expr.getTypeModel();
        if ((flags & EXPR_HAS_NULL_CHECK_FENCE) != 0) {
            exprType = getNonNullType(exprType);
        } else if (hasUncheckedNulls(expr) && !isOptional(exprType)) {
            exprType = typeFact().getOptionalType(exprType);
        }
        boolean exprBoxed = !CodegenUtil.isUnBoxed(expr);
        boolean exprErased = CodegenUtil.hasTypeErased(expr);
        boolean exprUntrustedType = CodegenUtil.hasUntrustedType(expr);
        return applyErasureAndBoxing(result, exprType, exprErased, exprBoxed, exprUntrustedType, boxingStrategy, expectedType, flags);
    }
    
    JCExpression applyErasureAndBoxing(JCExpression result, Type exprType,
            boolean exprBoxed,
            BoxingStrategy boxingStrategy, Type expectedType) {
        return applyErasureAndBoxing(result, exprType, false, exprBoxed, boxingStrategy, expectedType, 0);
    }
    
    JCExpression applyErasureAndBoxing(JCExpression result, Type exprType,
            boolean exprErased, boolean exprBoxed, 
            BoxingStrategy boxingStrategy, Type expectedType, 
            int flags) {
        return applyErasureAndBoxing(result, exprType, exprErased, exprBoxed, false, boxingStrategy, expectedType, flags);
    }
    
    JCExpression applyErasureAndBoxing(JCExpression result, Type exprType,
            boolean exprErased, boolean exprBoxed, boolean exprUntrustedType,
            BoxingStrategy boxingStrategy, Type expectedType, 
            int flags) {
        
        if(exprType != null)
            exprType = exprType.resolveAliases();
        if(expectedType != null)
            expectedType = expectedType.resolveAliases();
        boolean canCast = false;

        if (expectedType != null
                // don't add cast to an erased type 
                && !willEraseToObject(expectedType)) {

            // only try to cast boxed types, no point otherwise
            if(exprBoxed){

                boolean expectedTypeIsNotRaw = (flags & EXPR_EXPECTED_TYPE_NOT_RAW) != 0;
                boolean expectedTypeHasConstrainedTypeParameters = (flags & EXPR_EXPECTED_TYPE_HAS_CONSTRAINED_TYPE_PARAMETERS) != 0;
                boolean expectedTypeHasDependentCovariantTypeParameters = (flags & EXPR_EXPECTED_TYPE_HAS_DEPENDENT_COVARIANT_TYPE_PARAMETERS) != 0;
                boolean downCast = (flags & EXPR_DOWN_CAST) != 0;
                int companionFlags = (flags & EXPR_WANTS_COMPANION) != 0 ? AbstractTransformer.JT_COMPANION : 0;

                // special case for returning Null expressions
                if (isNull(exprType)){
                    // don't add cast for null
                    if(!isNullValue(exprType)
                            // include a cast even for null for interop and disambiguating bw overloads and null values
                            // of different types using the "of" operator
                            || downCast){
                        // in some cases we may have an instance of Null, which is of type java.lang.Object, being
                        // returned in a context where we expect a String? (aka ceylon.language.String) so even though
                        // the instance at hand will really be null, we need a up-cast to it
                        JCExpression targetType = makeJavaType(expectedType, AbstractTransformer.JT_RAW | companionFlags);
                        result = make().TypeCast(targetType, result);
                    }
                }else if(exprType.isNothing()){
                    // type param erasure
                    JCExpression targetType = makeJavaType(expectedType, 
                            AbstractTransformer.JT_NO_PRIMITIVES | companionFlags);
                    result = make().TypeCast(targetType, result);
                }else if(// expression was forcibly erased
                         exprErased
                         // expression type cannot be trusted to be true, most probably because we had to satisfy Java type parameter
                         // bounds that are different from what we think the expression type should be
                         || exprUntrustedType
                         // if we have a covariant type parameter which is dependent and whose type arg contains erased type parameters
                         // we need a raw cast because it will be fixed rather than using a wildcard and there's a good chance
                         // we can't use proper subtyping rules to assign to it
                         // see https://github.com/ceylon/ceylon-compiler/issues/1557
                         || expectedTypeHasDependentCovariantTypeParameters
                         // some type parameter somewhere needs a cast
                         || needsCast(exprType, expectedType, expectedTypeIsNotRaw, expectedTypeHasConstrainedTypeParameters, downCast)
                         // if the exprType is raw and the expected type isn't
                         || (exprType.isRaw() && (expectedTypeIsNotRaw || !isTurnedToRaw(expectedType)))){

                    // save this before we simplify it because we lose that flag doing so
                    boolean exprIsRaw = exprType.isRaw();
                    boolean expectedTypeIsRaw = isTurnedToRaw(expectedType) && !expectedTypeIsNotRaw;

                    // We will need a raw cast if either the expected type or the
                    // expression type has type parameters while the other hasn't 
                    // (unless the other type is already raw)
                    if ((!exprIsRaw && hasTypeParameters(expectedType))
                            || (downCast && !expectedTypeIsRaw && hasTypeParameters(exprType))) {
                        Type rawType = hasTypeParameters(expectedType) ? expectedType : exprType;
                        JCExpression rawTypeExpr = makeJavaType(rawType, 
                                AbstractTransformer.JT_TYPE_ARGUMENT | AbstractTransformer.JT_RAW | companionFlags);
                        result = make().TypeCast(rawTypeExpr, result);
                        // expr is now raw
                        exprIsRaw = true;
                        // let's not add another downcast if we got a cast: one is enough
                        downCast = false;
                        // same for forced erasure
                        exprErased = false;
                        exprUntrustedType = false;
                    }

                    // simplify the type
                    // (without the underlying type, because the cast is always to a non-primitive)
                    exprType = simplifyType(expectedType).withoutUnderlyingType();

                    // if the expr is not raw, we need a cast
                    // if the expr is raw:
                    //  don't even try making an actual cast if there are bounded type parameters in play, because going raw is much safer
                    //  also don't try making the cast if the expected type is raw because anything goes
                    boolean needsTypedCast = !exprIsRaw 
                            || (!expectedTypeHasConstrainedTypeParameters
                                    && !expectedTypeHasDependentCovariantTypeParameters
                                    && !expectedTypeIsRaw);
                    if(needsTypedCast
                            // make sure that downcasts get at least one cast
                            || downCast
                            // same for forced erasure
                            || exprUntrustedType){
                        // forced erasure may require a previous cast to Object if we were not able to insert a raw cast
                        // because for instance Sequential<String> cannot be cast forcibly to Empty because Java is so smart
                        // it figures out that there's no intersection between the two types, but we know better
                        if(exprUntrustedType && !exprIsRaw){
                            result = make().TypeCast(syms().objectType, result);
                        }
                        // Do the actual cast
                        JCExpression targetType = makeJavaType(expectedType, 
                                AbstractTransformer.JT_TYPE_ARGUMENT | companionFlags);
                        result = make().TypeCast(targetType, result);
                    }
                }else
                    canCast = true;
            }else
                canCast = true;
        }

        // If expr type if Self<T> and expected type is T we need to cast before any unboxing
        if (exprType.getDeclaration().getSelfType() != null
                && expectedType != null
                && expectedType.isExactly(exprType.getTypeArguments().get(exprType.getDeclaration().getSelfType().getDeclaration()))) {
            result = applySelfTypeCasts(result, exprType, exprBoxed, BoxingStrategy.BOXED, expectedType);
            exprType = expectedType;
        }
        // we must do the boxing after the cast to the proper type
        JCExpression ret = boxUnboxIfNecessary(result, exprBoxed, exprType, boxingStrategy, expectedType);
        
        // very special case for nothing that we need to "unbox" to a primitive type
        if(exprType != null
                && exprType.isNothing()
                && boxingStrategy == BoxingStrategy.UNBOXED){
            // in this case we have to use the expected type
            ret = unboxType(ret, expectedType);
        }
        
        // now check if we need variance casts
        if (canCast) {
            ret = applyVarianceCasts(ret, exprType, exprBoxed, boxingStrategy, expectedType, flags);
        }
        ret = applySelfTypeCasts(ret, exprType, exprBoxed, boxingStrategy, expectedType);
        ret = applyJavaTypeConversions(ret, exprType, expectedType, boxingStrategy, exprBoxed, flags);
        return ret;
    }

    boolean needsCast(Type exprType, Type expectedType, 
                              boolean expectedTypeNotRaw, 
                              boolean expectedTypeHasConstrainedTypeParameters,
                              boolean downCast) {
        // error handling
        if(exprType == null)
            return false;
        // make sure we work on definite types
        exprType = simplifyType(exprType);
        expectedType = simplifyType(expectedType);
        // abort if both types are the same
        if(exprType.isExactly(expectedType)){
            // unless the expected type is parameterised with bounds because in that case we can't
            // really trust the expected type
            if(!expectedTypeHasConstrainedTypeParameters)
                return false;
        }

        // now see about erasure
        boolean eraseExprType = willEraseToObject(exprType);
        boolean eraseExpectedType = willEraseToObject(expectedType);
        
        // if we erase expected type we need no cast
        if(eraseExpectedType){
            // unless the expected type is parameterised with bounds that erasure to Object can't possibly satisfy
            if(!expectedTypeHasConstrainedTypeParameters)
                return false;
        }
        // if we erase the expr type we need a cast
        if(eraseExprType)
            return true;
        
        // find their common type
        Type commonType = exprType.getSupertype(expectedType.getDeclaration());
        
        if(commonType == null || !(commonType.getDeclaration() instanceof ClassOrInterface)){
            // we did not find any common type, but we may be downcasting, in which case we need a cast
            return downCast;
        }
        
        // some times we can lose info due to an erased type parameter somewhere in the inheritance graph
        if(lostTypeParameterInInheritance(exprType, commonType))
            return true;
        
        if(!expectedTypeNotRaw){
            // the truth is that we don't really know if the expected type is raw or not, that flag only gets set
            // if we know for sure that the expected type is NOT raw. if it's false we've no idea but we can check:
            if(isTurnedToRaw(expectedType)){
                return false;
            }
            // if the expected type is exactly the common type, they must have the same erasure
            // note that we don't do that test if we know the expected type is not raw, because
            // the common type could be erased
            if(commonType.isExactly(expectedType))
                return false;
        }
        //special case for Callable because only the first type param exists in Java, the rest is completely suppressed
        boolean isCallable = isCeylonCallable(commonType);
        
        // now see if the type parameters match
        java.util.List<Type> commonTypeArgs = commonType.getTypeArgumentList();
        java.util.List<TypeParameter> commonTps = commonType.getDeclaration().getTypeParameters();
        java.util.List<Type> expectedTypeArgs = expectedType.getTypeArgumentList();
        java.util.List<TypeParameter> expectedTps = expectedType.getDeclaration().getTypeParameters();
        // check that we got them all otherwise we just don't know
        if(commonTypeArgs.size() != expectedTypeArgs.size())
            return false;
        for(int i=0,n=commonTypeArgs.size(); i < n ; i++){
            // apply the same logic to each type param: see if they would require a raw cast
            Type commonTypeArg = commonTypeArgs.get(i);
            Type expectedTypeArg = expectedTypeArgs.get(i);
            
            if (hasDependentTypeParameters(commonTps, commonTps.get(i))
                    || hasDependentTypeParameters(expectedTps, expectedTps.get(i))) {
                // In this case makeJavaType() will have made the Java decl 
                // invariant in this type argument, so we will need a type cast 
                // if the type parameters are not identical:
                if (!simplifyType(commonTypeArg).isExactly(simplifyType(expectedTypeArg))) {
                    return true;
                }
            }
            
            if(needsCast(commonTypeArg, expectedTypeArg, expectedTypeNotRaw, 
                         expectedTypeHasConstrainedTypeParameters, 
                         downCast))
                return true;
            // stop after the first one for Callable
            if(isCallable)
                break;
        }
        return false;
    }

    private boolean lostTypeParameterInInheritance(Type exprType, Type commonType) {
        if(exprType.getDeclaration() instanceof ClassOrInterface == false
                || commonType.getDeclaration() instanceof ClassOrInterface == false)
            return false;
        ClassOrInterface exprDecl = (ClassOrInterface) exprType.getDeclaration();
        ClassOrInterface commonDecl = (ClassOrInterface) commonType.getDeclaration();
        // do not search interfaces if the common declaration is a class, because interfaces cannot be subtypes of a class
        boolean searchInterfaces = commonDecl instanceof Interface;
        return lostTypeParameterInInheritance(exprDecl, commonDecl, searchInterfaces, false);
    }

    private boolean lostTypeParameterInInheritance(ClassOrInterface exprDecl, ClassOrInterface commonDecl, boolean searchInterfaces, boolean lostTypeParameter) {
        // stop if we found the common decl
        if(Decl.equal(exprDecl, commonDecl))
            return lostTypeParameter;
        if(searchInterfaces){
            // find a match in interfaces
            for(Type pt : exprDecl.getSatisfiedTypes()){
                // FIXME: this is very heavy-handed because we consider that once we've lost a type parameter we've lost them all
                // but we could optimise this by checking:
                // 1/ which type parameter we've really lost
                // 2/ if the type parameters we're passing to our super type actually depend in any way from type parameters we've lost
                boolean lostTypeParameter2 = lostTypeParameter || isTurnedToRaw(pt);
                pt = simplifyType(pt);
                // it has to be an interface
                Interface interf = (Interface) pt.getDeclaration();
                if(lostTypeParameterInInheritance(interf, commonDecl, searchInterfaces, lostTypeParameter2))
                    return true;
            }
        }
        // search for super classes
        Type extendedType = exprDecl.getExtendedType();
        if(extendedType != null){
            // FIXME: see above
            boolean lostTypeParameter2 = lostTypeParameter || isTurnedToRaw(extendedType);
            extendedType = simplifyType(extendedType);
            // it has to be a Class
            Class extendedTypeDeclaration = (Class) extendedType.getDeclaration();
            // looks like Object's superclass is Object, so stop right there
            if(extendedTypeDeclaration != typeFact().getObjectDeclaration())
                return lostTypeParameterInInheritance(extendedTypeDeclaration, commonDecl, searchInterfaces, lostTypeParameter2);
        }
        // didn't find it
        return false;
    }

    private boolean hasTypeParameters(Type type) {
        if (!type.getTypeArgumentList().isEmpty()) {
            return true;
        }
        if (type.getCaseTypes() != null) {
            for (Type ct : type.getCaseTypes()) {
                if (hasTypeParameters(ct)) {
                    return true;
                }
            }
        }
        return false;
    }

    private JCExpression applyVarianceCasts(JCExpression result, Type exprType,
            boolean exprBoxed,
            BoxingStrategy boxingStrategy, Type expectedType, int flags) {
        // unboxed types certainly don't need casting for variance
        if(exprBoxed || boxingStrategy == BoxingStrategy.BOXED){
            VarianceCastResult varianceCastResult = getVarianceCastResult(expectedType, exprType);
            if(varianceCastResult != null){
                result = applyVarianceCasts(result, expectedType, varianceCastResult, flags);
            }
        }
        return result;
    }

    private JCExpression applyVarianceCasts(JCExpression result, Type expectedType, VarianceCastResult varianceCastResult,
            int flags) {
        // Types with variance types need a type cast, let's start with a raw cast to get rid
        // of Java's type system constraint (javac doesn't grok multiple implementations of the same
        // interface with different type params, which the JVM allows)
        int forCompanionMask = (flags & EXPR_FOR_COMPANION) != 0 ? JT_SATISFIES : 0;
        int wantsCompanionMask = (flags & EXPR_WANTS_COMPANION) != 0 ? JT_COMPANION : 0;
        JCExpression targetType = makeJavaType(expectedType, AbstractTransformer.JT_RAW | wantsCompanionMask);
        // do not change exprType here since this is just a Java workaround
        result = make().TypeCast(targetType, result);
        // now, because a raw cast is losing a lot of info, can we do better?
        if(varianceCastResult.isBetterCastAvailable()){
            // let's recast that to something finer than a raw cast
            targetType = makeJavaType(varianceCastResult.castType, AbstractTransformer.JT_TYPE_ARGUMENT | wantsCompanionMask | forCompanionMask);
            result = make().TypeCast(targetType, result);
        }
        return result;
    }
    
    private JCExpression applySelfTypeCasts(JCExpression result, Type exprType,
            boolean exprBoxed,
            BoxingStrategy boxingStrategy, Type expectedType) {
        if (expectedType == null) {
            return result;
        }
        final Type selfType = exprType.getDeclaration().getSelfType();
        if (selfType != null) {
            if (selfType.isExactly(exprType) // self-type within its own scope
                    || !exprType.isExactly(expectedType)) {
                final Type castType = findTypeArgument(exprType, selfType.getDeclaration());
                // the fact that the original expr was or not boxed doesn't mean the current result is boxed or not
                // as boxing transformations occur before this method
                boolean resultBoxed = boxingStrategy == BoxingStrategy.BOXED
                        || (boxingStrategy == BoxingStrategy.INDIFFERENT && exprBoxed);
                JCExpression targetType = makeJavaType(castType, resultBoxed ? AbstractTransformer.JT_TYPE_ARGUMENT : 0);
                result = make().TypeCast(targetType, result);
            }
        }
        return result;
    }

    private Type findTypeArgument(Type type, TypeDeclaration declaration) {
        if(type == null)
            return null;
        Type typeArgument = type.getTypeArguments().get(declaration);
        if(typeArgument != null)
            return typeArgument;
        return findTypeArgument(type.getQualifyingType(), declaration);
    }

    private JCExpression applyJavaTypeConversions(JCExpression ret, Type exprType, Type expectedType, 
            BoxingStrategy boxingStrategy, boolean exprBoxed, int flags) {
        if(exprType == null || boxingStrategy != BoxingStrategy.UNBOXED)
            return ret;
        Type definiteExprType = simplifyType(exprType);
        if(definiteExprType == null)
            return ret;
        // ignore the underlying type of the expr type if it was boxed, since we must have unboxed it to
        // something with no underlying type first
        String convertFrom = exprBoxed ? null : definiteExprType.getUnderlyingType();

        Type definiteExpectedType = null;
        String convertTo = null;
        if (expectedType != null) {
            definiteExpectedType = simplifyType(expectedType);
            convertTo = definiteExpectedType.getUnderlyingType();
        }
        // check for identity conversion
        if (convertFrom != null && convertFrom.equals(convertTo)) {
            return ret;
        }
        if (isCeylonByte(definiteExpectedType) && isCeylonInteger(exprType)) {
            if ((flags & EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK) == 0) {
                if(ret instanceof JCTree.JCUnary){
                    JCTree.JCUnary unary = (JCTree.JCUnary)ret;
                    if(unary.getTag() == JCTree.NEG
                            && unary.arg instanceof JCTree.JCLiteral){
                        Object value = ((JCTree.JCLiteral)unary.arg).value;
                        if(value instanceof Integer){
                            int val = (Integer)value;
                            // if it fits let's just leave it
                            if(val >= 0 && val <= -Byte.MIN_VALUE){
                                // in the case of -128 to 127 we don't need to cast to byte by using an int literal, but only for
                                // assignment, not for method calls, so it's simpler to always cast
                                return make().TypeCast(syms().byteType, ret);
                            }
                        }
                    }
                }
            }
            ret = make().TypeCast(syms().byteType, ret);
        } else {
            if (convertTo != null) {
                if(convertTo.equals("short")) {
                    if ((flags & EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK) == 0) {
                        ret = utilInvocation().toShort(ret);
                    } else {
                        ret = make().TypeCast(syms().shortType, ret);
                    }
                } else if(convertTo.equals("int")) {
                    if ((flags & EXPR_UNSAFE_PRIMITIVE_TYPECAST_OK) == 0) {
                        ret = utilInvocation().toInt(ret);
                    } else {
                        ret = make().TypeCast(syms().intType, ret);
                    }
                } else if(convertTo.equals("float")) {
                    ret = make().TypeCast(syms().floatType, ret);
                } else if(convertTo.equals("char")) {
                    ret = make().TypeCast(syms().charType, ret);
                }
            } else if (convertFrom != null
                    && (flags & EXPR_WIDEN_PRIM) != 0) {
                if (isCeylonInteger(exprType)
                        && (convertFrom.equals("int")
                            || convertFrom.equals("short")
                            || convertFrom.equals("byte"))) {
                    ret = make().TypeCast(syms().longType, ret);
                } else if (isCeylonFloat(exprType)&& 
                        convertFrom.equals("float")) {
                    ret = make().TypeCast(syms().doubleType, ret);
                }
            }
        }
        return ret;
    }
    
    private final class InvocationTermTransformer implements TermTransformer {
        private final Invocation invocation;
        private final CallBuilder callBuilder;
        
        private InvocationTermTransformer(
                Invocation invocation,
                CallBuilder callBuilder) {
            this.invocation = invocation;
            this.callBuilder = callBuilder;
        }

        @Override
        public JCExpression transform(JCExpression primaryExpr, String selector) {
            TransformedInvocationPrimary transformedPrimary = invocation.transformPrimary(primaryExpr, selector);
            callBuilder.argumentsAndTypes(transformArgumentList(invocation, transformedPrimary, callBuilder));
            JCExpression resultExpr;
            if (invocation instanceof NamedArgumentInvocation) {
                resultExpr = transformNamedArgumentInvocationOrInstantiation((NamedArgumentInvocation)invocation, callBuilder, transformedPrimary);
            } else {
                resultExpr = transformPositionalInvocationOrInstantiation(invocation, callBuilder, transformedPrimary);
            }
            return resultExpr;
        }
    }

    private static class VarianceCastResult {
        Type castType;
        
        VarianceCastResult(Type castType){
            this.castType = castType;
        }
        
        private VarianceCastResult(){}
        
        boolean isBetterCastAvailable(){
            return castType != null;
        }
    }
    
    private static final VarianceCastResult RawCastVarianceResult = new VarianceCastResult();

    private VarianceCastResult getVarianceCastResult(Type expectedType, Type exprType) {
        // exactly the same type, doesn't need casting
        if(expectedType == null || exprType.isExactly(expectedType))
            return null;
        // if we're not trying to put it into an interface, there's no need
        if(!(expectedType.getDeclaration() instanceof Interface))
            return null;
        // the interface must have type arguments, otherwise we can't use raw types
        if(expectedType.getTypeArguments().isEmpty())
            return null;
        // see if any of those type arguments has variance
        boolean hasVariance = false;
        for(TypeParameter t : expectedType.getTypeArguments().keySet()){
            if(expectedType.isContravariant(t) || expectedType.isCovariant(t)){
                hasVariance = true;
                break;
            }
        }
        if(!hasVariance)
            return null;
        // see if we're inheriting the interface twice with different type parameters
        java.util.List<Type> satisfiedTypes = new LinkedList<Type>();
        for(Type superType : simplifyType(exprType).getSupertypes()){
            if(Decl.equal(superType.getDeclaration(), expectedType.getDeclaration()))
                satisfiedTypes.add(superType);
        }
        // discard the supertypes that have the same erasure
        for(int i=0;i<satisfiedTypes.size();i++){
            Type pt = satisfiedTypes.get(i);
            for(int j=i+1;j<satisfiedTypes.size();j++){
                Type other = satisfiedTypes.get(j);
                if(pt.isExactly(other) || haveSameErasure(pt, other)){
                    satisfiedTypes.remove(j);
                    break;
                }
            }
        }
        // we need at least two instantiations
        if(satisfiedTypes.size() <= 1)
            return null;
        boolean needsCast = false;
        // we need at least one that differs
        for(Type superType : satisfiedTypes){
            if(!exprType.isExactly(superType)){
                needsCast = true;
                break;
            }
        }
        // no cast needed if they are all the same type
        if(!needsCast)
            return null;
        // find the better cast match
        for(Type superType : satisfiedTypes){
            if(expectedType.isExactly(superType))
                return new VarianceCastResult(superType);
        }
        // nothing better than a raw cast (Stef: not sure that can happen)
        return RawCastVarianceResult;
    }

    private boolean haveSameErasure(Type pt, Type other) {
        TypeDeclaration decl1 = pt.getDeclaration();
        TypeDeclaration decl2 = other.getDeclaration();
        if(decl1 == null || decl2 == null)
            return false;
        // do we erase both to object?
        boolean erased1 = willEraseToObject(pt);
        boolean erased2 = willEraseToObject(other);
        if(erased1)
            return erased2;
        if(erased2)
            return false;
        // declarations must be the same
        // (use simplifyType() so we ignore the difference between T and T?)
        if (!simplifyType(pt).getDeclaration().equals(simplifyType(other).getDeclaration())) {
            return false;
        }
        // now see their type arguments
        java.util.List<Type> tal1 = pt.getTypeArgumentList();
        java.util.List<Type> tal2 = other.getTypeArgumentList();
        if(tal1.size() != tal2.size())
            return false;
        for(int i=0;i<tal1.size();i++){
            if(!haveSameErasure(tal1.get(i), tal2.get(i)))
                return false;
        }
        // all the same
        return true;
    }
    
    //
    // Literals
    

    JCExpression ceylonLiteral(String s) {
        JCLiteral lit = make().Literal(s);
        return lit;
    }

    static String literalValue(Tree.StringLiteral string) {
        return string.getText();
    }
    
    static String literalValue(Tree.QuotedLiteral string) {
        return string.getText().substring(1, string.getText().length()-1);
    }
    
    static int literalValue(Tree.CharLiteral ch) {
        // codePoint is at index 1 because the text is `X` (including quotation marks, so we skip them)
        return ch.getText().codePointAt(1);
    }
    
    static double literalValue(Tree.FloatLiteral literal) throws ErroneousException {
        double value = Double.parseDouble(literal.getText());
        // Don't need to handle the negative infinity and negative zero cases 
        // because Ceylon Float literals have no sign
        if (value == Double.POSITIVE_INFINITY) {
            throw new ErroneousException(literal, "literal so large it is indistinguishable from infinity: '"+ literal.getText() + "' (use infinity)");
        } else if (value == 0.0 && !literal.getText().equals("0.0")) {
            throw new ErroneousException(literal, "literal so small it is indistinguishable from zero: '" + literal.getText() + "' (use 0.0)");
        }
        return value;
    }
    
    static long literalValue(Tree.NaturalLiteral literal) throws ErroneousException {
        return literalValue(literal, literal.getText());
    }
    
    static private long literalValue(Tree.NaturalLiteral literal, String text) throws ErroneousException {
        if(text.startsWith("#")){
            return literalValue(literal, 16, "invalid hexadecimal literal: '" + text + "' has more than 64 bits");
        }
        if(text.startsWith("$")){
            return literalValue(literal, 2, "invalid binary literal: '" + text + "' has more than 64 bits");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ErroneousException(literal, "literal outside representable range: '" + text + "' is too large to be represented as an 'Integer'");
        }
        
    }
    
    private static long literalValue(Tree.NaturalLiteral literal, int radix, String error) throws ErroneousException{
        String value = literal.getText().substring(1);
        try{
            return Convert.string2long(value, radix);
        }catch(NumberFormatException x){
            throw new ErroneousException(literal, error);
        }
    }
    
    static Long literalValue(Tree.NegativeOp op) throws ErroneousException {
        if (op.getTerm() instanceof Tree.NaturalLiteral) {
            // To cope with -9223372036854775808 we can't just parse the 
            // number separately from the sign
            String lit = op.getTerm().getText();
            if (!lit.startsWith("#") && !lit.startsWith("$")) { 
                return literalValue((Tree.NaturalLiteral)op.getTerm(), "-" + lit);
            }
        }
        return null;
    }
    
    public JCExpression transform(Tree.StringLiteral string) {
        at(string);
        return ceylonLiteral(string.getText());
    }

    public JCExpression transform(Tree.QuotedLiteral string) {
        at(string);
        return ceylonLiteral(literalValue(string));
    }
    
    public JCExpression transform(Tree.CharLiteral lit) {
        return make().Literal(TypeTags.INT, literalValue(lit));
    }

    public JCExpression transform(Tree.FloatLiteral lit) {
        try {
            return make().Literal(literalValue(lit));
        } catch (ErroneousException e) {
            // We should never get here since the error should have been 
            // reported by the UnsupportedVisitor and the containing statement
            // replaced with a throw.
            return e.makeErroneous(this);
        }
    }
    
    public JCExpression transform(Tree.NaturalLiteral lit) {
        try {
            at(lit);
            return make().Literal(literalValue(lit));
        } catch (ErroneousException e) {
            // We should never get here since the error should have been 
            // reported by the UnsupportedVisitor and the containing statement
            // replaced with a throw.
            return e.makeErroneous(this);
        }
    }
    
    JCExpression transform(Tree.Literal literal) {
        if (literal instanceof Tree.StringLiteral) {
            return transform((Tree.StringLiteral)literal);
        } else if (literal instanceof Tree.NaturalLiteral) {
            return transform((Tree.NaturalLiteral)literal);
        } else if (literal instanceof Tree.CharLiteral) {
            return transform((Tree.CharLiteral)literal);
        } else if (literal instanceof Tree.FloatLiteral) {
            return transform((Tree.FloatLiteral)literal);
        } else if (literal instanceof Tree.QuotedLiteral) {
            return transform((Tree.QuotedLiteral)literal);
        }
        throw BugException.unhandledNodeCase(literal);
    }

    public JCTree transform(Tree.PackageLiteral expr) {
        at(expr);
        
        Package pkg = (Package) expr.getImportPath().getModel();
        return makePackageLiteralCall(pkg);
    }

    public JCTree transform(Tree.ModuleLiteral expr) {
        at(expr);

        Module mod = (Module) expr.getImportPath().getModel();
        return makeModuleLiteralCall(mod);
    }

    public JCTree transform(Tree.MemberLiteral expr) {
        at(expr);
        
        Declaration declaration = expr.getDeclaration();
        if(declaration == null)
            return makeErroneous(expr, "compiler bug: missing declaration");
        if(declaration.isToplevel()){
            return makeTopLevelValueOrFunctionLiteral(expr);
        }else if(expr.getWantsDeclaration()){
            return makeMemberValueOrFunctionDeclarationLiteral(expr, declaration);
        }else{
            // get its produced ref
            Reference producedReference = expr.getTarget();
            // it's a member we get from its container type
            Type containerType = producedReference.getQualifyingType();
            // if we have no container type it means we have an object member
            boolean objectMember = containerType.getDeclaration().isAnonymous();
            
            JCExpression memberCall;

            if(objectMember){
                // We don't care about the type args for the cast, nor for the reified container expr, because
                // we take the real reified container type from the container instance, and that one has the type
                // arguments
                containerType = ((Class)declaration.getContainer()).getType();
            }
            JCExpression typeCall = makeTypeLiteralCall(containerType, false, expr.getTypeModel());
            // make sure we cast it to ClassOrInterface
            TypeDeclaration classOrInterfaceDeclaration = (TypeDeclaration) typeFact().getLanguageModuleModelDeclaration(Decl.isConstructor(declaration) ? "ClassModel" : "ClassOrInterface");
            JCExpression classOrInterfaceTypeExpr = makeJavaType(
                    classOrInterfaceDeclaration.appliedReference(null, Arrays.asList(containerType)).getType());

            typeCall = make().TypeCast(classOrInterfaceTypeExpr, typeCall);
            // we will need a TD for the container
            // Note that we don't use Basic for the container for object members, because that's not how we represent
            // anonymous types.
            JCExpression reifiedContainerExpr = makeReifiedTypeArgument(containerType);
            
            // make a raw call and cast
            if (Decl.isConstructor(declaration)) {
                Type callableType = producedReference.getFullType();
                JCExpression reifiedArgumentsExpr = makeReifiedTypeArgument(typeFact().getCallableTuple(callableType));
                List<JCExpression> arguments = List.of(reifiedArgumentsExpr, ceylonLiteral(declaration.getName()));
                JCExpression classModel = makeSelect(typeCall, "getConstructor");
                memberCall = make().Apply(null, classModel, arguments);
            } else if(declaration instanceof Function){
                // we need to get types for each type argument
                JCExpression closedTypesExpr = null;
                if(expr.getTypeArgumentList() != null) {
                    java.util.List<Type> typeModels = expr.getTypeArgumentList().getTypeModels();
                    if (typeModels!=null) {
                        closedTypesExpr = getClosedTypesSequential(typeModels);
                    }
                }
                // we also need type descriptors for ret and args
                Type callableType = producedReference.getFullType();
                JCExpression reifiedReturnTypeExpr = makeReifiedTypeArgument(typeFact().getCallableReturnType(callableType));
                JCExpression reifiedArgumentsExpr = makeReifiedTypeArgument(typeFact().getCallableTuple(callableType));
                List<JCExpression> arguments;
                if(closedTypesExpr != null)
                    arguments = List.of(reifiedContainerExpr, reifiedReturnTypeExpr, reifiedArgumentsExpr, 
                            ceylonLiteral(declaration.getName()), closedTypesExpr);
                else
                    arguments = List.of(reifiedContainerExpr, reifiedReturnTypeExpr, reifiedArgumentsExpr, 
                            ceylonLiteral(declaration.getName()));
                memberCall = make().Apply(null, makeSelect(typeCall, "getMethod"), arguments);
            }else if(declaration instanceof Value){
                JCExpression reifiedGetExpr = makeReifiedTypeArgument(producedReference.getType());
                String getterName = "getAttribute";
                Type ptype;
                if(!((Value)declaration).isVariable())
                    ptype = typeFact().getNothingType();
                else
                    ptype = producedReference.getType();

                JCExpression reifiedSetExpr = makeReifiedTypeArgument(ptype);
                memberCall = make().Apply(null, makeSelect(typeCall, getterName), List.of(reifiedContainerExpr, reifiedGetExpr, reifiedSetExpr, 
                        ceylonLiteral(declaration.getName())));
            }else{
                return makeErroneous(expr, "Unsupported member type: "+declaration);
            }
//            if(objectMember){
//                // now get the instance and bind it
//                // I don't think we need any expected type since objects can't be erased
//                JCExpression object = transformExpression(expr.getObjectExpression());
//                // reset the location after we transformed the expression
//                memberCall = at(expr).Apply(null, makeSelect(memberCall, "bind"), List.of(object));
//            }
            // cast the member call because we invoke it with no Java generics
            memberCall = make().TypeCast(makeJavaType(expr.getTypeModel(), JT_RAW | JT_NO_PRIMITIVES), memberCall);
            memberCall = make().TypeCast(makeJavaType(expr.getTypeModel(), JT_NO_PRIMITIVES), memberCall);
            return memberCall;
        }
    }

    JCExpression makeMemberValueOrFunctionDeclarationLiteral(Node node, Declaration declaration) {
        return makeMemberValueOrFunctionDeclarationLiteral(node, declaration, true);
    }
    
    JCExpression makeMemberValueOrFunctionDeclarationLiteral(Node node, Declaration declaration, boolean f) {
        // it's a member we get from its container declaration
        if(declaration.getContainer() instanceof ClassOrInterface == false)
            return makeErroneous(node, "compiler bug: " + declaration.getContainer() + " is not a supported type parameter container");
        
        ClassOrInterface container = (ClassOrInterface) declaration.getContainer();
        // use the generated class to get to the declaration literal
        JCExpression metamodelCall = makeTypeDeclarationLiteral(container);
        JCExpression metamodelCast = makeJavaType(typeFact().getLanguageModuleDeclarationTypeDeclaration(
                Decl.isConstructor(declaration) ? "ClassDeclaration": "ClassOrInterfaceDeclaration").getType(), 
                JT_NO_PRIMITIVES);
        metamodelCall = make().TypeCast(metamodelCast, metamodelCall);

        String memberClassName;
        String memberAccessor;
        if(declaration instanceof Class)
            memberClassName = "ClassDeclaration";
        else if (Decl.isConstructor(declaration))
            memberClassName = "ConstructorDeclaration";
        else if(declaration instanceof Interface)
            memberClassName = "InterfaceDeclaration";
        else if(declaration instanceof Function)
            memberClassName = "FunctionDeclaration";
        else if(declaration instanceof Value){
            memberClassName = "ValueDeclaration";
        }else{
            return makeErroneous(node, "compiler bug: " + declaration + " is not a supported declaration literal");
        }
        if (Decl.isConstructor(declaration))
            memberAccessor = "getConstructorDeclaration";
        else
            memberAccessor = f ? "getMemberDeclaration" : "getDeclaredMemberDeclaration";
        
        TypeDeclaration metamodelDecl = (TypeDeclaration) typeFact().getLanguageModuleDeclarationDeclaration(memberClassName);
        JCExpression memberType = makeJavaType(metamodelDecl.getType());
        JCExpression reifiedMemberType = makeReifiedTypeArgument(metamodelDecl.getType());
        JCExpression memberCall = make().Apply(List.of(memberType), 
                                               makeSelect(metamodelCall, memberAccessor), 
                                               List.of(reifiedMemberType, ceylonLiteral(declaration.getName())));
        return memberCall;
    }

    private JCExpression makeTopLevelValueOrFunctionDeclarationLiteral(Declaration declaration) {
        // toplevel method or attribute: we need to fetch them from their module/package
        Package pkg = Decl.getPackageContainer(declaration.getContainer());

        // get the package
        JCExpression packageCall = makePackageLiteralCall(pkg);
        
        // now get the toplevel
        String getter = Decl.isMethod(declaration) ? "getFunction" : "getValue";
        JCExpression toplevelCall = make().Apply(null, makeSelect(packageCall, getter), 
                                                 List.<JCExpression>of(ceylonLiteral(declaration.getName())));
        
        return toplevelCall;
    }
    
    private JCTree makeTopLevelValueOrFunctionLiteral(Tree.MemberLiteral expr) {
        Declaration declaration = expr.getDeclaration();
        JCExpression toplevelCall = makeTopLevelValueOrFunctionDeclarationLiteral(declaration);
        
        if(!expr.getWantsDeclaration()){
            ListBuffer<JCExpression> closedTypeArgs = new ListBuffer<JCExpression>();
            // expr is of type Function<Type,Arguments> or Value<Get,Set> so we can get its type like that
            JCExpression reifiedType = makeReifiedTypeArgument(expr.getTypeModel().getTypeArgumentList().get(0));
            closedTypeArgs.append(reifiedType);
            if(Decl.isMethod(declaration)){
                // expr is of type Function<Type,Arguments> so we can get its arguments type like that
                Type argumentsType = typeFact().getCallableTuple(expr.getTypeModel());
                JCExpression reifiedArguments = makeReifiedTypeArgument(argumentsType);
                closedTypeArgs.append(reifiedArguments);
                if(expr.getTypeArgumentList() != null){
                    java.util.List<Type> typeModels = expr.getTypeArgumentList().getTypeModels();
                    if(typeModels!=null){
                        JCExpression closedTypesExpr = getClosedTypesSequential(typeModels);
                        // must apply it
                        closedTypeArgs.append(closedTypesExpr);
                    }
                }
            }else{
                JCExpression reifiedSet;
                Type ptype;
                if(!((Value)declaration).isVariable())
                    ptype = typeFact().getNothingType();
                else
                    ptype = expr.getTypeModel().getTypeArgumentList().get(0);
                
                reifiedSet = makeReifiedTypeArgument(ptype);
                closedTypeArgs.append(reifiedSet);
            }
            toplevelCall = make().Apply(null, makeSelect(toplevelCall, "apply"), closedTypeArgs.toList());
            // add cast
            Type exprType = expr.getTypeModel().resolveAliases();
            JCExpression typeClass = makeJavaType(exprType, JT_NO_PRIMITIVES);
            JCExpression rawTypeClass = makeJavaType(exprType, JT_NO_PRIMITIVES | JT_RAW);
            return make().TypeCast(typeClass, make().TypeCast(rawTypeClass, toplevelCall));
        }
        return toplevelCall;
    }

    private JCExpression makePackageLiteralCall(Package pkg) {
        // get the module
        Module module = pkg.getModule();
        JCExpression moduleCall = makeModuleLiteralCall(module);
        
        // now get the package
        return make().Apply(null, makeSelect(moduleCall, "findPackage"), 
                                             List.<JCExpression>of(ceylonLiteral(pkg.getNameAsString())));
    }

    private JCExpression makeModuleLiteralCall(Module module) {
        JCExpression modulesGetIdent = naming.makeFQIdent("ceylon", "language", "meta", "modules_", "get_");
        JCExpression modulesGet = make().Apply(null, modulesGetIdent, List.<JCExpression>nil());
        JCExpression call;
        if(module.isDefault()){
            call = make().Apply(null, makeSelect(modulesGet, "getDefault"), List.<JCExpression>nil());
        }else{
            call = make().Apply(null, makeSelect(modulesGet, "find"), 
                                      List.<JCExpression>of(ceylonLiteral(module.getNameAsString()),
                                                            ceylonLiteral(module.getVersion())));
        }
        // make sure we handle missing modules gracefully
        String version = module.getVersion();
        return makeMetamodelInvocation("checkModule", List.of(call, ceylonLiteral(module.getNameAsString()), version == null ? makeNull() : ceylonLiteral(version)), null);
    }

    private JCExpression getClosedTypesSequential(java.util.List<Type> typeModels) {
        ListBuffer<JCExpression> closedTypes = new ListBuffer<JCExpression>();
        for (Type producedType : typeModels) {
            closedTypes.add(makeTypeLiteralCall(producedType));
        }
        Type elementType = typeFact().getMetamodelTypeDeclaration().appliedType(null, Arrays.asList(typeFact().getAnythingType()));
        // now wrap into a sequential
        return makeSequence(closedTypes.toList(), elementType, CeylonTransformer.JT_CLASS_NEW);
    }

    private JCExpression makeTypeLiteralCall(Type producedType) {
        JCExpression typeLiteralIdent = naming.makeFQIdent("ceylon", "language", "meta", "typeLiteral_", "typeLiteral");
        JCExpression reifiedTypeArgument = makeReifiedTypeArgument(producedType.resolveAliases());
        // note that we don't pass it a Java type argument since it's not used
        return make().Apply(null, typeLiteralIdent, List.of(reifiedTypeArgument));
    }

    JCExpression makeTypeLiteralCall(Type type, boolean addCast, Type exprType) {
        // construct a call to typeLiteral<T>() and cast if required
        JCExpression call = makeTypeLiteralCall(type);
        if(addCast){
            // if we have a type that is not nothingType and not Type, we need to cast
            exprType = exprType.resolveAliases();
            if(!exprType.isUnion()
                    && !exprType.isExactly(typeFact().getMetamodelNothingTypeDeclaration().getType())
                    && !exprType.isExactly(typeFact().getMetamodelTypeDeclaration().getType())){
                JCExpression typeClass = makeJavaType(exprType, JT_NO_PRIMITIVES);
                return make().TypeCast(typeClass, call);
            }
        }
        return call;
    }

    public JCTree transform(Tree.TypeLiteral expr) {
        at(expr);
        if(!expr.getWantsDeclaration()){
            if (expr.getDeclaration() instanceof Constructor) {
                JCExpression classLiteral = makeTypeLiteralCall(expr.getType().getTypeModel().getQualifyingType(), false, expr.getTypeModel());
                TypeDeclaration classModelDeclaration = (TypeDeclaration)typeFact().getLanguageModuleModelDeclaration(
                        expr.getType().getTypeModel().getQualifyingType().getDeclaration().isMember() ? "MemberClass" : "Class");
                JCTypeCast typeCast = make().TypeCast(
                        makeJavaType(classModelDeclaration.appliedType(null, 
                                List.of(expr.getType().getTypeModel().getQualifyingType(), 
                                        typeFact().getNothingType()))), 
                        classLiteral);
                Type callableType = expr.getTypeModel().getFullType();
                JCExpression reifiedArgumentsExpr = makeReifiedTypeArgument(typeFact().getCallableTuple(callableType));
                return make().Apply(null, 
                        naming.makeQualIdent(typeCast, "getConstructor"),
                        List.<JCExpression>of(
                                reifiedArgumentsExpr,
                                make().Literal(expr.getDeclaration().getName())));
            } else {
                return makeTypeLiteralCall(expr.getType().getTypeModel(), true, expr.getTypeModel());
            }
        }else if(expr.getDeclaration() instanceof TypeParameter){
            // we must get it from its container
            TypeParameter declaration = (TypeParameter)expr.getDeclaration();
            Node node = expr;
            return makeTypeParameterDeclaration(node, declaration);
        }else if (expr.getDeclaration() instanceof Constructor
                || expr instanceof Tree.NewLiteral) {
            Constructor ctor;
            if (expr.getDeclaration() instanceof Constructor) {
                ctor = (Constructor)expr.getDeclaration();
            } else {
                ctor = Decl.getDefaultConstructor((Class)expr.getDeclaration());
            }
            JCExpression metamodelCall = makeTypeDeclarationLiteral(Decl.getConstructedClass(ctor));
            metamodelCall = make().TypeCast(
                    makeJavaType(typeFact().getClassDeclarationType(), JT_RAW), metamodelCall);
            return make().Apply(null, 
                    naming.makeQualIdent(metamodelCall, "getConstructorDeclaration"), 
                    List.<JCExpression>of(make().Literal(ctor.getName() == null ? "" : ctor.getName())));
        }else if(expr.getDeclaration() instanceof ClassOrInterface
                 || expr.getDeclaration() instanceof TypeAlias){
            // use the generated class to get to the declaration literal
            JCExpression metamodelCall = makeTypeDeclarationLiteral((TypeDeclaration) expr.getDeclaration());
            Type exprType = expr.getTypeModel().resolveAliases();
            // now cast if required
            if(!exprType.isExactly(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("NestableDeclaration")).getType())){
                JCExpression type = makeJavaType(exprType, JT_NO_PRIMITIVES);
                return make().TypeCast(type, metamodelCall);
            }
            return metamodelCall;
        }else{
            return makeErroneous(expr, "compiler bug: " + expr.getDeclaration() + " is an unsupported declaration type");
        }
    }

    /**
     * Makes an expression equivalent to the result of {@code `given T`} 
     * @param node
     * @param declaration
     * @return
     */
    JCExpression makeTypeParameterDeclaration(Node node,
            TypeParameter declaration) {
        Scope container = declaration.getContainer();
        if(container instanceof Declaration){
            JCExpression containerExpr;
            Declaration containerDeclaration = (Declaration) container;
            if(containerDeclaration instanceof ClassOrInterface
                    || containerDeclaration instanceof TypeAlias){
                JCExpression metamodelCall = makeTypeDeclarationLiteral((TypeDeclaration) containerDeclaration);
                JCExpression metamodelCast = makeJavaType(typeFact().getLanguageModuleDeclarationTypeDeclaration("GenericDeclaration").getType(), JT_NO_PRIMITIVES);
                containerExpr = make().TypeCast(metamodelCast, metamodelCall);
            }else if(containerDeclaration.isToplevel()) {
                containerExpr = makeTopLevelValueOrFunctionDeclarationLiteral(containerDeclaration);
            }else{
                containerExpr = makeMemberValueOrFunctionDeclarationLiteral(node, containerDeclaration);
            }
            // now it must be a ClassOrInterfaceDeclaration or a FunctionDeclaration, both of which have the method we need
            return at(node).Apply(null, makeSelect(containerExpr, "getTypeParameterDeclaration"), List.of(ceylonLiteral(declaration.getName())));
        }else{
            return makeErroneous(node, "compiler bug: " + container + " is not a supported type parameter container");
        }
    }

    JCExpression makeTypeDeclarationLiteral(TypeDeclaration declaration) {
        JCExpression classLiteral = makeUnerasedClassLiteral(declaration);
        return makeMetamodelInvocation("getOrCreateMetamodel", List.of(classLiteral), null);
    }

    public JCExpression transformStringExpression(Tree.StringTemplate expr) {
        at(expr);
        JCExpression builder;
        builder = make().NewClass(null, null, naming.makeFQIdent("java","lang","StringBuilder"), List.<JCExpression>nil(), null);

        java.util.List<Tree.StringLiteral> literals = expr.getStringLiterals();
        java.util.List<Tree.Expression> expressions = expr.getExpressions();
        for (int ii = 0; ii < literals.size(); ii += 1) {
            Tree.StringLiteral literal = literals.get(ii);
            if (!literal.getText().isEmpty()) {// ignore empty string literals
                at(literal);
                builder = make().Apply(null, makeSelect(builder, "append"), List.<JCExpression>of(transform(literal)));
            }
            if (ii == expressions.size()) {
                // The loop condition includes the last literal, so break out
                // after that because we've already exhausted all the expressions
                break;
            }
            Tree.Expression expression = expressions.get(ii);
            at(expression);
            // Here in both cases we don't need a type cast for erasure
            if (isCeylonBasicType(expression.getTypeModel())
                    && expression.getUnboxed()) {// TODO: Test should be erases to String, long, int, boolean, char, byte, float, double
                // If erases to a Java primitive just call append, don't box it just to call format. 
                String method = isCeylonCharacter(expression.getTypeModel()) ? "appendCodePoint" : "append";
                builder = make().Apply(null, makeSelect(builder, method), List.<JCExpression>of(
                        transformExpression(expression, BoxingStrategy.UNBOXED, null)));
            } else {
                JCMethodInvocation formatted = make().Apply(null, makeSelect(transformExpression(expression), "toString"), List.<JCExpression>nil());
                builder = make().Apply(null, makeSelect(builder, "append"), List.<JCExpression>of(formatted));
            }
        }

        return make().Apply(null, makeSelect(builder, "toString"), List.<JCExpression>nil());
    }

    public JCExpression transform(Tree.SequenceEnumeration value) {
        return transform(value, null);
    }
    
    private JCExpression transform(Tree.SequenceEnumeration value, Type expectedType) {
        at(value);
        if (value.getSequencedArgument() != null) {
            Tree.SequencedArgument sequencedArgument = value.getSequencedArgument();
            java.util.List<Tree.PositionalArgument> list = sequencedArgument.getPositionalArguments();
            if(list.isEmpty())
                return makeErroneous(value, "compiler bug: empty iterable literal with sequenced arguments: "+value);
            Type seqElemType = typeFact().getIteratedType(value.getTypeModel());
            seqElemType = wrapInOptionalForInterop(seqElemType, expectedType, containsUncheckedNulls(list));
            Type absentType = typeFact().getIteratedAbsentType(value.getTypeModel());
            return makeLazyIterable(sequencedArgument, seqElemType, absentType, 0);
        } else {
            return makeEmpty();
        }
    }

    private boolean containsUncheckedNulls(java.util.List<Tree.PositionalArgument> list) {
        for(Tree.PositionalArgument arg : list){
            if(arg instanceof Tree.ListedArgument){
                if(containsUncheckedNulls(((Tree.ListedArgument) arg).getExpression()))
                    return true;
            }else if(arg instanceof Tree.Comprehension){
                if(containsUncheckedNulls((Tree.Comprehension)arg))
                    return true;
            }else if(arg instanceof Tree.SpreadArgument){
                if(containsUncheckedNulls(((Tree.SpreadArgument) arg).getExpression()))
                    return true;
            }
        }
        return false;
    }

    private boolean containsUncheckedNulls(Tree.Term term){
        if(term instanceof Tree.Expression){
            return containsUncheckedNulls(((Tree.Expression) term).getTerm());
        }else if(term instanceof Tree.SequenceEnumeration){
            Tree.SequencedArgument sequencedArgument = ((Tree.SequenceEnumeration) term).getSequencedArgument();
            if(sequencedArgument == null)
                return false;
            return containsUncheckedNulls(sequencedArgument.getPositionalArguments());
        }else
            return hasUncheckedNulls(term);
    }
    
    private boolean containsUncheckedNulls(Tree.Comprehension comp) {
        Tree.ComprehensionClause clause = comp.getInitialComprehensionClause();
        while(clause instanceof Tree.ExpressionComprehensionClause == false){
            if(clause instanceof Tree.ForComprehensionClause)
                clause = ((Tree.ForComprehensionClause) clause).getComprehensionClause();
            else if(clause instanceof Tree.IfComprehensionClause)
                clause = ((Tree.IfComprehensionClause) clause).getComprehensionClause();
            else
                return false;// error recovery
        }
        if(clause instanceof Tree.ExpressionComprehensionClause)
            return containsUncheckedNulls(((Tree.ExpressionComprehensionClause) clause).getExpression());
        return false;
    }

    public JCExpression transform(Tree.Tuple value) {
        Tree.SequencedArgument sequencedArgument = value.getSequencedArgument();
        if(sequencedArgument != null){
            java.util.List<Tree.PositionalArgument> args = sequencedArgument.getPositionalArguments();
            return makeTuple(value.getTypeModel(), args);
        }
        // nothing in there
        return makeEmpty();
    }

    private JCExpression sequentialEmptiness(JCExpression sequential, 
            Type expectedType, Type sequentialType) {
        int flags = 0;
        // make sure we detect that we're downcasting a sequential into a sequence if we know the comprehension is non-empty
        if(expectedType.getSupertype(typeFact().getSequenceDeclaration()) != null)
            flags = EXPR_DOWN_CAST;
        return applyErasureAndBoxing(sequential, sequentialType, false, true, BoxingStrategy.BOXED, expectedType, flags);
    }
    
    public JCExpression comprehensionAsSequential(Tree.Comprehension comprehension, Type expectedType) {
        JCExpression sequential = iterableToSequential(transformComprehension(comprehension));
        Type elementType = comprehension.getInitialComprehensionClause().getTypeModel();
        Type sequentialType = typeFact().getSequentialType(elementType);
        return sequentialEmptiness(sequential, expectedType, sequentialType);
    }
    
    private JCExpression makeTuple(Type tupleType, java.util.List<Tree.PositionalArgument> expressions) {
        if (typeFact().isEmptyType(tupleType)) {
            return makeEmpty();// A tuple terminated by empty
        }
        
        JCExpression tail = null;
        List<JCExpression> elems = List.<JCExpression>nil();
        for (int i = 0; i < expressions.size(); i++) {
            Tree.PositionalArgument expr = expressions.get(i);
            if (expr instanceof Tree.ListedArgument) {
                JCExpression elem = transformExpression(((Tree.ListedArgument) expr).getExpression());
                elems = elems.append(elem);
            } else if (expr instanceof Tree.SpreadArgument) {
                Tree.SpreadArgument spreadExpr = (Tree.SpreadArgument) expr;
                // make sure we get a spread part of the right type
                Type spreadType = spreadExpr.getExpression().getTypeModel();
                Type sequentialSpreadType = null;
                // try to get a Sequence
                if (typeFact().isNonemptyIterableType(spreadType))
                    sequentialSpreadType = spreadType.getSupertype(typeFact().getSequenceDeclaration());
                // failing that, try Sequential
                if(sequentialSpreadType == null)
                    sequentialSpreadType = spreadType.getSupertype(typeFact().getSequentialDeclaration());
                
                if(sequentialSpreadType != null){
                    tail = transformExpression(spreadExpr.getExpression(), BoxingStrategy.BOXED, sequentialSpreadType);
                }else {
                    // must at least be an Iterable then
                    Type iterableSpreadType = spreadType.getSupertype(typeFact().getIterableDeclaration());
                    tail = transformExpression(spreadExpr.getExpression(), BoxingStrategy.BOXED, iterableSpreadType);
                    tail = utilInvocation().sequentialOf(makeReifiedTypeArgument(typeFact().getIteratedType(iterableSpreadType)), tail);
                    Type elementType = typeFact().getIteratedType(spreadExpr.getTypeModel());
                    Type sequentialType = typeFact().getSequentialType(elementType);
                    Type expectedType = spreadExpr.getTypeModel();
                    if (typeFact().isNonemptyIterableType(spreadExpr.getTypeModel())) {
                        expectedType = typeFact().getSequenceType(elementType);
                    } else if (typeFact().isIterableType(spreadExpr.getTypeModel())) {
                        expectedType = typeFact().getSequentialType(elementType);
                    }
                    tail = sequentialEmptiness(tail, expectedType, sequentialType);
                }
            } else if (expr instanceof Tree.Comprehension) {
                Tree.Comprehension comp = (Tree.Comprehension) expr;
                Type elementType = expr.getTypeModel(); 
                Type expectedType = comp.getInitialComprehensionClause().getPossiblyEmpty() 
                        ? typeFact().getSequentialType(elementType)
                        : typeFact().getSequenceType(elementType);
                tail = comprehensionAsSequential(comp, expectedType);
            } else {
                return makeErroneous(expr, "compiler bug: " + expr.getNodeType() + " is not a supported tuple argument");
            }
        }
        
        if (!elems.isEmpty()) {
            JCExpression reifiedTypeArg = makeReifiedTypeArgument(tupleType.getTypeArgumentList().get(0));
            List<JCExpression> args = List.<JCExpression>of(reifiedTypeArg);
            args = args.append(make().NewArray(make().Type(syms().objectType), List.<JCExpression>nil(), elems));
            if (tail != null) {
                args = args.append(tail);
            }
            JCExpression typeExpr = makeJavaType(tupleType, JT_TYPE_ARGUMENT);
            /* Tuple.instance(reifiedElement, new Object[]{elem, elem, elem}, tail) */
            return make().TypeCast(typeExpr, make().Apply(
                    List.<JCExpression>nil(), 
                    naming.makeQualIdent(make().QualIdent(syms().ceylonTupleType.tsym), "instance"), 
                    args));
        } else {
            return tail;
        }
    }
    
    public JCTree transform(Tree.This expr) {
        at(expr);
        if (needDollarThis(expr.getScope())) {
            return naming.makeQuotedThis();
        }
        if (isWithinSyntheticClassBody()) {
            return naming.makeQualifiedThis(makeJavaType(expr.getTypeModel()));
        } 
        return naming.makeThis();
    }

    public JCTree transform(Tree.Super expr) {
        throw new BugException(expr, "unreachable");
    }

    public JCTree transform(Tree.Outer expr) {
        at(expr);
        
        Type outerClass = com.redhat.ceylon.model.typechecker.model.ModelUtil.getOuterClassOrInterface(expr.getScope());
        return makeOuterExpr(outerClass);
    }

    JCExpression makeOuterExpr(Type outerClass) {
        final TypeDeclaration outerDeclaration = outerClass.getDeclaration();
        if (outerDeclaration instanceof Interface) {
            return makeQualifiedDollarThis(outerClass);
        }
        return naming.makeQualifiedThis(makeJavaType(outerClass));
    }

    //
    // Unary and Binary operators that can be overridden
    
    //
    // Unary operators

    public JCExpression transform(Tree.NotOp op) {
        // No need for an erasure cast since Term must be Boolean and we never need to erase that
        JCExpression term = transformExpression(op.getTerm(), CodegenUtil.getBoxingStrategy(op), null);
        JCUnary jcu = at(op).Unary(JCTree.NOT, term);
        return jcu;
    }

    public JCExpression transform(Tree.OfOp op) {
        if (op.getTerm() instanceof Tree.Super) {
            // This should be unreachable
            throw new BugException(op, "unreachable");
        } 
        Type expectedType = op.getType().getTypeModel();
        return transformExpression(op.getTerm(), CodegenUtil.getBoxingStrategy(op), expectedType, EXPR_DOWN_CAST);
    }

    public JCExpression transform(Tree.IsOp op) {
        // make sure we do not insert null checks if we're going to allow testing for null
        Type expectedType = getOptionalTypeForInteropIfAllowed(op.getType().getTypeModel(), op.getTerm().getTypeModel(), op.getTerm());
        // we don't need any erasure type cast for an "is" test
        JCExpression expression = transformExpression(op.getTerm(), BoxingStrategy.BOXED, expectedType);
        at(op);
        Naming.SyntheticName varName = naming.temp();
        JCExpression test = makeOptimizedTypeTest(null, varName, op.getType().getTypeModel(), op.getTerm().getTypeModel());
        return makeLetExpr(varName, List.<JCStatement>nil(), make().Type(syms().objectType), expression, test);
    }

    public JCTree transform(Tree.Nonempty op) {
        // we don't need any erasure type cast for a "nonempty" test
        JCExpression expression = transformExpression(op.getTerm());
        at(op);
        Naming.SyntheticName varName = naming.temp();
        JCExpression test = makeNonEmptyTest(varName.makeIdent());
        return makeLetExpr(varName, List.<JCStatement>nil(), make().Type(syms().objectType), expression, test);
    }

    public JCTree transform(Tree.Exists op) {
        // for the purpose of checking if something is null, we need it boxed and optional, otherwise
        // for some Java calls if we consider it non-optional we will get an unwanted null check
        Type termType = op.getTerm().getTypeModel();
        if(!typeFact().isOptionalType(termType)){
            termType = typeFact().getOptionalType(termType);
        }
        JCExpression expression = transformExpression(op.getTerm(), BoxingStrategy.BOXED, termType);
        at(op);
        return  make().Binary(JCTree.NE, expression, makeNull());
    }

    public JCExpression transform(Tree.PositiveOp op) {
        return transformOverridableUnaryOperator(op, op.getUnit().getInvertableDeclaration());
    }

    public JCExpression transform(Tree.NegativeOp op) {
        at(op);
        if (op.getTerm() instanceof Tree.NaturalLiteral) {
            try {
                Long l = literalValue(op);
                if (l != null) {
                    return make().Literal(l);
                }
            } catch (ErroneousException e) {
                // We should never get here since the error should have been 
                // reported by the UnsupportedVisitor and the containing statement
                // replaced with a throw.
                return e.makeErroneous(this);
            }
        }
        if(op.getTerm() instanceof Tree.QualifiedMemberExpression){
            JCExpression ret = checkForByteLiterals((Tree.QualifiedMemberExpression)op.getTerm());
            if(ret != null)
                return at(op).Unary(JCTree.NEG, ret);
        }
        return transformOverridableUnaryOperator(op, op.getUnit().getInvertableDeclaration());
    }

    public JCExpression transform(Tree.UnaryOperatorExpression op) {
        return transformOverridableUnaryOperator(op, (Type)null);
    }

    private JCExpression transformOverridableUnaryOperator(Tree.UnaryOperatorExpression op, Interface compoundType) {
        Type leftType = getSupertype(op.getTerm(), compoundType);
        return transformOverridableUnaryOperator(op, leftType);
    }
    
    private JCExpression transformOverridableUnaryOperator(Tree.UnaryOperatorExpression op, Type expectedType) {
        at(op);
        Tree.Term term = op.getTerm();

        OperatorTranslation operator = Operators.getOperator(op.getClass());
        if (operator == null) {
            return makeErroneous(op, "compiler bug: " + op.getClass() + " is an unhandled operator class");
        }

        JCExpression ret;
        if(operator.getUnOpOptimisationStrategy(op, op.getTerm(), this).useJavaOperator()){
            // optimisation for unboxed types
            JCExpression expr = transformExpression(term, BoxingStrategy.UNBOXED, expectedType, EXPR_WIDEN_PRIM);
            // unary + is essentially a NOOP
            if(operator == OperatorTranslation.UNARY_POSITIVE)
                return expr;
            ret = make().Unary(operator.javacOperator, expr);
            ret = unAutoPromote(ret, op.getTypeModel());
        } else {
            if(operator == OperatorTranslation.UNARY_POSITIVE) {
                // +x is essentiall a NOOP, but in this case the expected type
                // is the self type of Invertible, so use the type of op
                return transformExpression(term, BoxingStrategy.BOXED, op.getTypeModel());
            }
            ret = make().Apply(null, makeSelect(transformExpression(term, BoxingStrategy.BOXED, expectedType), 
                    Naming.getGetterName(operator.ceylonMethod)), List.<JCExpression> nil());
        }
        return ret;
    }

    //
    // Binary operators
    
    public JCExpression transform(Tree.NotEqualOp op) {
        OperatorTranslation operator = Operators.OperatorTranslation.BINARY_EQUAL;
        OptimisationStrategy optimisationStrategy = operator.getBinOpOptimisationStrategy(op, op.getLeftTerm(), op.getRightTerm(), this);
        
        // we want it unboxed only if the operator is optimised
        // we don't care about the left erased type, since equals() is on Object
        JCExpression left = transformExpression(op.getLeftTerm(), optimisationStrategy.getBoxingStrategy(), null, EXPR_WIDEN_PRIM);
        // we don't care about the right erased type, since equals() is on Object
        JCExpression expr = transformOverridableBinaryOperator(op.getLeftTerm(), op.getRightTerm(), null, operator, optimisationStrategy, left, op.getTypeModel());
        return at(op).Unary(JCTree.NOT, expr);
    }

    public JCExpression transform(Tree.EqualOp op) {
        // we don't care about the left/right type since they're both Object
        return transformOverridableBinaryOperator(op, null, null);
    }

    public JCExpression transform(Tree.SegmentOp op) {
        // we need to get the range bound type
        final Type type = getTypeArgument(getSupertype(op.getLeftTerm(), op.getUnit().getEnumerableDeclaration()));
        JCExpression startExpr = transformExpression(op.getLeftTerm(), BoxingStrategy.BOXED, type);
        JCExpression lengthExpr = transformExpression(op.getRightTerm(), BoxingStrategy.UNBOXED, typeFact().getIntegerType());
        return make().Apply(List.<JCExpression>of(makeJavaType(type, JT_TYPE_ARGUMENT)), 
                naming.makeLanguageFunction("measure"), 
                List.<JCExpression>of(makeReifiedTypeArgument(type), startExpr, lengthExpr));
    }

    public JCExpression transform(Tree.RangeOp op) {
        // we need to get the range bound type
        final Type type = getTypeArgument(getSupertype(op.getLeftTerm(), op.getUnit().getEnumerableDeclaration()));
        JCExpression lower = transformExpression(op.getLeftTerm(), BoxingStrategy.BOXED, type);
        JCExpression upper = transformExpression(op.getRightTerm(), BoxingStrategy.BOXED, type);
        return make().Apply(List.<JCExpression>of(makeJavaType(type, JT_TYPE_ARGUMENT)), 
                naming.makeLanguageFunction("span"), 
                List.<JCExpression>of(makeReifiedTypeArgument(type), lower, upper));
    }

    public JCExpression transform(Tree.EntryOp op) {
        // no erasure cast needed for both terms
        JCExpression key = transformExpression(op.getLeftTerm());
        JCExpression elem = transformExpression(op.getRightTerm());
        Type leftType = op.getLeftTerm().getTypeModel();
        Type rightType = op.getRightTerm().getTypeModel();
        Type entryType = typeFact().getEntryType(leftType, rightType);
        JCExpression typeExpr = makeJavaType(entryType, CeylonTransformer.JT_CLASS_NEW);
        return at(op).NewClass(null, null, typeExpr , List.<JCExpression> of(makeReifiedTypeArgument(leftType), makeReifiedTypeArgument(rightType), key, elem), null);
    }

    public JCExpression transform(Tree.DefaultOp op, Type expectedType) {
        Term elseTerm = unwrapExpressionUntilTerm(op.getRightTerm());
        Type typeModel = typeFact().denotableType(op.getTypeModel());
        if (unwrapExpressionUntilTerm(op.getLeftTerm()) instanceof Tree.ThenOp) {
            // Optimize cond then foo else bar (avoids unnecessary boxing in particular)
            Tree.ThenOp then = (Tree.ThenOp)unwrapExpressionUntilTerm(op.getLeftTerm());
            Term condTerm = then.getLeftTerm();
            Term thenTerm = then.getRightTerm();
            JCExpression cond = transformExpression(condTerm, BoxingStrategy.UNBOXED, condTerm.getTypeModel());
            JCExpression thenpart = transformExpression(thenTerm, CodegenUtil.getBoxingStrategy(op), 
                    typeModel);
            JCExpression elsepart = transformExpression(elseTerm, CodegenUtil.getBoxingStrategy(op), 
                    typeModel);
            return make().Conditional(cond, thenpart, elsepart);
        }
        JCExpression left = transformExpression(op.getLeftTerm(), BoxingStrategy.BOXED, typeFact().getOptionalType(typeModel));
        // make sure we do not insert null checks if we're going to allow testing for null
        Type rightExpectedType = getOptionalTypeForInteropIfAllowed(expectedType, typeModel, elseTerm);
        JCExpression right = transformExpression(elseTerm, BoxingStrategy.BOXED, rightExpectedType);
        Naming.SyntheticName varName = naming.temp();
        JCExpression varIdent = varName.makeIdent();
        JCExpression test = at(op).Binary(JCTree.NE, varIdent, makeNull());
        JCExpression cond = make().Conditional(test , varIdent, right);
        JCExpression typeExpr = makeJavaType(typeModel, JT_NO_PRIMITIVES);
        return makeLetExpr(varName, null, typeExpr, left, cond);
    }

    public JCTree transform(Tree.ThenOp op) {
        JCExpression left = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, typeFact().getBooleanType());
        JCExpression right = transformExpression(op.getRightTerm(), CodegenUtil.getBoxingStrategy(op), op.getTypeModel());
        return make().Conditional(left , right, makeNull());
    }
    
    public JCTree transform(Tree.InOp op) {
        if (isCeylonInteger(op.getLeftTerm().getTypeModel())) {
            if (op.getRightTerm() instanceof Tree.RangeOp
                && isCeylonInteger(((Tree.RangeOp)op.getRightTerm()).getLeftTerm().getTypeModel())
                && isCeylonInteger(((Tree.RangeOp)op.getRightTerm()).getRightTerm().getTypeModel())) {
                return makeOptimizedInIntegerRange(op, syms().longType);
            } else if (op.getRightTerm() instanceof Tree.SegmentOp
                    && isCeylonInteger(((Tree.SegmentOp)op.getRightTerm()).getLeftTerm().getTypeModel())
                    && isCeylonInteger(((Tree.SegmentOp)op.getRightTerm()).getRightTerm().getTypeModel())) {
                // x in y:z with x, y, z all Integer
                return makeOptimizedInIntegerOrCharacterMeasure(op, syms().ceylonIntegerType, syms().longType);
            }
        } else if (isCeylonCharacter(op.getLeftTerm().getTypeModel())) {
            if (op.getRightTerm() instanceof Tree.RangeOp
                    && isCeylonCharacter(((Tree.RangeOp)op.getRightTerm()).getLeftTerm().getTypeModel())
                    && isCeylonCharacter(((Tree.RangeOp)op.getRightTerm()).getRightTerm().getTypeModel())) {
                // x in y..z with x, y, z all Character
                return makeOptimizedInCharacterRange(op);
            } else if (op.getRightTerm() instanceof Tree.SegmentOp
                    && isCeylonCharacter(((Tree.SegmentOp)op.getRightTerm()).getLeftTerm().getTypeModel())
                    && isCeylonInteger(((Tree.SegmentOp)op.getRightTerm()).getRightTerm().getTypeModel())) {
                // x in y:z with x, y both Character, z all Integer
                return makeOptimizedInIntegerOrCharacterMeasure(op, syms().ceylonCharacterType, syms().intType);
            }
        }
        JCExpression left = transformExpression(op.getLeftTerm(), BoxingStrategy.BOXED, typeFact().getObjectType());
        JCExpression right = transformExpression(op.getRightTerm(), BoxingStrategy.BOXED, op.getRightTerm().getTypeModel()
        		.getSupertype(typeFact().getCategoryDeclaration()));
        Naming.SyntheticName varName = naming.temp();
        JCExpression varIdent = varName.makeIdent();
        JCExpression contains = at(op).Apply(null, makeSelect(right, "contains"), List.<JCExpression> of(varIdent));
        JCExpression typeExpr = makeJavaType(op.getLeftTerm().getTypeModel(), JT_NO_PRIMITIVES);
        return makeLetExpr(varName, null, typeExpr, left, contains);
    }

    protected JCTree makeOptimizedInIntegerOrCharacterMeasure(Tree.InOp op, com.sun.tools.javac.code.Type ceylonType, com.sun.tools.javac.code.Type javaType) {
        Tree.SegmentOp rangeOp = (Tree.SegmentOp)op.getRightTerm();
        SyntheticName xName = naming.temp("x");
        SyntheticName yName = naming.temp("y");
        SyntheticName zName = naming.temp("z");
        SyntheticName wName = naming.temp("w");
        JCExpression x = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, typeFact().getObjectType());
        JCExpression y = transformExpression(rangeOp.getLeftTerm(), BoxingStrategy.UNBOXED, rangeOp.getLeftTerm().getTypeModel());
        JCExpression z = transformExpression(rangeOp.getRightTerm(), BoxingStrategy.UNBOXED, rangeOp.getRightTerm().getTypeModel());
        JCExpression w = make().Apply(null, 
                naming.makeSelect(make().QualIdent(ceylonType.tsym), "offset"),
                List.<JCExpression>of(xName.makeIdent(), 
                        yName.makeIdent()));
        return make().LetExpr(List.<JCStatement>of(
                makeVar(xName, make().Type(javaType), x),
                makeVar(yName, make().Type(javaType), y),
                makeVar(zName, make().Type(syms().longType), z),
                makeVar(wName, make().Type(syms().longType), w)),
                make().Binary(JCTree.AND, 
                        make().Binary(JCTree.GT, zName.makeIdent(), make().Literal(0L)),
                        make().Binary(JCTree.AND, 
                                make().Binary(JCTree.LE, make().Literal(0L), wName.makeIdent()),
                                make().Binary(JCTree.LT, wName.makeIdent(), zName.makeIdent()))
                        
                                ));
    }

    protected JCTree makeOptimizedInIntegerRange(Tree.InOp op, com.sun.tools.javac.code.Type type) {
        // x in y..z with x, y, z all Integer
        com.sun.tools.javac.code.Type ceylonType = syms().ceylonIntegerType;
        Tree.RangeOp rangeOp = (Tree.RangeOp)op.getRightTerm();
        JCExpression x = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, typeFact().getObjectType());
        JCExpression first = transformExpression(rangeOp.getLeftTerm(), BoxingStrategy.UNBOXED, rangeOp.getLeftTerm().getTypeModel());
        JCExpression last = transformExpression(rangeOp.getRightTerm(), BoxingStrategy.UNBOXED, rangeOp.getRightTerm().getTypeModel());
        SyntheticName xName = naming.temp("x");
        SyntheticName firstName = naming.temp("y");
        SyntheticName lastName = naming.temp("z");
        SyntheticName recursiveName = naming.temp("recursive");
        return make().LetExpr(List.<JCStatement>of(
                makeVar(xName, make().Type(type), x),
                makeVar(firstName, make().Type(type), first),
                makeVar(lastName, make().Type(type), last),
                makeVar(recursiveName, make().Type(syms().booleanType), make().Binary(JCTree.AND,
                        make().Binary(JCTree.GT,
                                firstName.makeIdent(),
                                make().Binary(JCTree.PLUS, firstName.makeIdent(), make().Literal(1L))),
                        make().Binary(JCTree.GT,
                                make().Binary(JCTree.MINUS, lastName.makeIdent(), make().Literal(1L)),
                                lastName.makeIdent())))),
                make().Conditional(recursiveName.makeIdent(), 
                        // x.offset(first) <= last.offset(first)
                        make().Binary(JCTree.LE,
                                make().Apply(null, 
                                        naming.makeSelect(make().QualIdent(ceylonType.tsym), "offset"),
                                        List.<JCExpression>of(
                                                xName.makeIdent(),
                                                firstName.makeIdent())),
                                make().Apply(null, 
                                        naming.makeSelect(make().QualIdent(ceylonType.tsym), "offset"),
                                        List.<JCExpression>of(
                                                lastName.makeIdent(),
                                                firstName.makeIdent()))),
                        make().Binary(JCTree.OR, 
                                make().Binary(JCTree.AND, 
                                        make().Binary(JCTree.LE, firstName.makeIdent(), xName.makeIdent()),
                                        make().Binary(JCTree.LE, xName.makeIdent(), lastName.makeIdent())),
                                make().Binary(JCTree.AND, 
                                        make().Binary(JCTree.LE, lastName.makeIdent(), xName.makeIdent()),
                                        make().Binary(JCTree.LE, xName.makeIdent(), firstName.makeIdent())
                                        ))));
    }
    
    protected JCTree makeOptimizedInCharacterRange(Tree.InOp op) {
        com.sun.tools.javac.code.Type type = syms().intType;
        com.sun.tools.javac.code.Type ceylonType = syms().ceylonCharacterType;
        // x in y..z with x, y, z all Character
        Tree.RangeOp rangeOp = (Tree.RangeOp)op.getRightTerm();
        JCExpression x = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, typeFact().getObjectType());
        JCExpression first = transformExpression(rangeOp.getLeftTerm(), BoxingStrategy.UNBOXED, rangeOp.getLeftTerm().getTypeModel());
        JCExpression last = transformExpression(rangeOp.getRightTerm(), BoxingStrategy.UNBOXED, rangeOp.getRightTerm().getTypeModel());
        SyntheticName xName = naming.temp("x");
        SyntheticName firstName = naming.temp("first");
        SyntheticName lastName = naming.temp("last");
        SyntheticName recursiveName = naming.temp("recursive");
        return make().LetExpr(List.<JCStatement>of(
                makeVar(xName, make().Type(type), x),
                makeVar(firstName, make().Type(type), first),
                makeVar(lastName, make().Type(type), last),
                // annoyingly then a Span evaluates `contains` (= `containsElement`)
                // it first evaluates `recursive`, 
                // which uses `successor` and `predecessor` which throw on overflow
                // so we have to replicate that **short-circuit** logic here
                makeVar(recursiveName, make().Type(syms().booleanType), make().Binary(JCTree.AND,
                        make().Binary(JCTree.GT,
                                firstName.makeIdent(),
                                make().Apply(null, 
                                            naming.makeSelect(make().QualIdent(ceylonType.tsym), "getSuccessor"),
                                            List.<JCExpression>of(firstName.makeIdent()))),
                        make().Binary(JCTree.GT,
                                make().Apply(null, 
                                            naming.makeSelect(make().QualIdent(ceylonType.tsym), "getPredecessor"),
                                            List.<JCExpression>of(lastName.makeIdent())),
                                lastName.makeIdent())))),
                make().Conditional(make().Binary(JCTree.LT, firstName.makeIdent(), lastName.makeIdent()),
                        make().Binary(JCTree.AND, 
                                make().Binary(JCTree.LE, 
                                        xName.makeIdent(), 
                                        lastName.makeIdent()),
                                make().Binary(JCTree.GE, 
                                        xName.makeIdent(), 
                                        firstName.makeIdent())),
                        make().Binary(JCTree.AND, 
                                make().Binary(JCTree.GE, 
                                        xName.makeIdent(), 
                                        lastName.makeIdent()),
                                make().Binary(JCTree.LE, 
                                        xName.makeIdent(), 
                                        firstName.makeIdent()))
                                ));
    }
    
    // Logical operators
    
    public JCExpression transform(Tree.LogicalOp op) {
        OperatorTranslation operator = Operators.getOperator(op.getClass());
        if(operator == null){
            return makeErroneous(op, "compiler bug: " + op.getNodeType() + " is not a supported logical operator");
        }
        // Both terms are Booleans and can't be erased to anything
        JCExpression left = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, null);
        return transformLogicalOp(op, operator, left, op.getRightTerm());
    }

    private JCExpression transformLogicalOp(Node op, OperatorTranslation operator, 
            JCExpression left, Tree.Term rightTerm) {
        // Both terms are Booleans and can't be erased to anything
        JCExpression right = transformExpression(rightTerm, BoxingStrategy.UNBOXED, null);

        return at(op).Binary(operator.javacOperator, left, right);
    }

    // Comparison operators
    
    public JCExpression transform(Tree.IdenticalOp op){
        // The only thing which might be unboxed is boolean, and we can follow the rules of == for optimising it,
        // which are simple and require that both types be booleans to be unboxed, otherwise they must be boxed
        OptimisationStrategy optimisationStrategy = OperatorTranslation.BINARY_EQUAL.getBinOpOptimisationStrategy(op, op.getLeftTerm(), op.getRightTerm(), this);
        JCExpression left = transformExpression(op.getLeftTerm(), optimisationStrategy.getBoxingStrategy(), null);
        JCExpression right = transformExpression(op.getRightTerm(), optimisationStrategy.getBoxingStrategy(), null);
        return at(op).Binary(JCTree.EQ, left, right);
    }
    
    public JCExpression transform(Tree.ComparisonOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getComparableDeclaration());
    }

    public JCExpression transform(Tree.CompareOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getComparableDeclaration());
    }

    public JCExpression transform(Tree.WithinOp op) {
        Tree.Term middle = op.getTerm();
        Type middleType = middle.getTypeModel();
        
        Tree.Bound lowerBound = op.getLowerBound();
        OperatorTranslation lowerOp = Operators.getOperator(lowerBound instanceof Tree.OpenBound ? Tree.SmallerOp.class : Tree.SmallAsOp.class);
        
        Tree.Bound upperBound = op.getUpperBound();
        OperatorTranslation upperOp = Operators.getOperator(upperBound instanceof Tree.OpenBound ? Tree.SmallerOp.class : Tree.SmallAsOp.class);
        
        // If any of the terms is optimizable, then use optimized
        OptimisationStrategy opt;
        if (upperOp.isTermOptimisable(lowerBound.getTerm(), this) == OptimisationStrategy.OPTIMISE
                || upperOp.isTermOptimisable(middle, this) == OptimisationStrategy.OPTIMISE
                || upperOp.isTermOptimisable(upperBound.getTerm(), this) == OptimisationStrategy.OPTIMISE) {
            opt = OptimisationStrategy.OPTIMISE;
        } else {
            opt = OptimisationStrategy.NONE;
        }
        
        SyntheticName middleName = naming.alias("middle");
        List<JCStatement> vars = List.<JCStatement>of(makeVar(middleName, 
                makeJavaType(middleType, opt.getBoxingStrategy() == BoxingStrategy.UNBOXED ? 0 : JT_NO_PRIMITIVES), 
                transformExpression(middle, opt.getBoxingStrategy(), middleType)));
        
        JCExpression lower = transformBound(middleName, lowerOp, opt, middle, lowerBound, false);
        JCExpression upper = transformBound(middleName, upperOp, opt, middle, upperBound, true);
        at(op);
        OperatorTranslation andOp = Operators.getOperator(Tree.AndOp.class);
        OptimisationStrategy optimisationStrategy = OptimisationStrategy.OPTIMISE;
        return make().LetExpr(vars, transformOverridableBinaryOperator(andOp, optimisationStrategy, lower, upper, null, null, op.getTypeModel()));
    }

    public JCExpression transformBound(SyntheticName middle, final OperatorTranslation operator, final OptimisationStrategy optimisationStrategy, Tree.Term middleTerm, Tree.Bound bound, boolean isUpper) {
        ;
        final JCExpression left;
        final JCExpression right;
        if (isUpper) {
            left = middle.makeIdent();
            right = transformExpression(bound.getTerm(), optimisationStrategy.getBoxingStrategy(), null);
            
        } else {
            left = transformExpression(bound.getTerm(), optimisationStrategy.getBoxingStrategy(), null);
            right = middle.makeIdent();
        }
        at(bound);
        return transformOverridableBinaryOperator(operator, optimisationStrategy, left, right, null, null, bound.getTypeModel());
    }

    public JCExpression transform(Tree.ScaleOp op) {
        OperatorTranslation operator = Operators.getOperator(Tree.ScaleOp.class);
        Tree.Term scalableTerm = op.getRightTerm();
        Type scalableTermType = getSupertype(scalableTerm, typeFact().getScalableDeclaration());
        SyntheticName scaleableName = naming.alias("scalable");
        JCVariableDecl scaleable = makeVar(scaleableName, 
                makeJavaType(scalableTermType, JT_NO_PRIMITIVES), 
                transformExpression(scalableTerm, BoxingStrategy.BOXED, scalableTermType));
        
        Tree.Term scaleTerm = op.getLeftTerm();
        SyntheticName scaleName = naming.alias("scale");
        Type scaleType = getTypeArgument(scalableTermType, 0);
        JCExpression scaleValue;
        if (isCeylonInteger(scaleTerm.getTypeModel())
                && isCeylonFloat(scaleType)) {
            // Disgusting coercion
            scaleValue = transformExpression(scaleTerm, BoxingStrategy.UNBOXED, scalableTerm.getTypeModel());
            scaleValue = boxType(scaleValue, typeFact().getFloatType());
        } else {
            scaleValue = transformExpression(scaleTerm, BoxingStrategy.BOXED, scaleType);
        }
        JCVariableDecl scale = makeVar(scaleName, 
                makeJavaType(scaleType, JT_NO_PRIMITIVES),
                scaleValue);
        
        at(op);
        return make().LetExpr(List.<JCStatement>of(scale, scaleable), 
                transformOverridableBinaryOperator(operator, OptimisationStrategy.NONE, scaleableName.makeIdent(), scaleName.makeIdent(), null, null, op.getTypeModel()));
    }
    
    // Arithmetic operators
    
    public JCExpression transform(Tree.ArithmeticOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getNumericDeclaration());
    }
    
    public JCExpression transform(Tree.SumOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getSummableDeclaration());
    }

    public JCExpression transform(Tree.DifferenceOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getInvertableDeclaration());
    }

    public JCExpression transform(Tree.RemainderOp op) {
        return transformOverridableBinaryOperator(op, op.getUnit().getIntegralDeclaration());
    }
    
    public JCExpression transform(Tree.PowerOp op) {
        if (Strategy.inlinePowerAsMultiplication(op)) {
            try {
                Long power = getIntegerLiteralPower(op);
                if(power != null)
                    return transformOptimizedIntegerPower(op.getLeftTerm(), power);
            } catch (ErroneousException e) {
                // fall through and let the default transformation handle this
            }
        }
        return transformOverridableBinaryOperator(op, op.getUnit().getExponentiableDeclaration(), 1);
    }

    /**
     * Returns the literal value of the power in the given power expression, 
     * or null if the power is not an integer literal (or negation of an 
     * integer literal)
     * @throws ErroneousException
     */
    static java.lang.Long getIntegerLiteralPower(Tree.PowerOp op)
            throws ErroneousException {
        java.lang.Long power;
        Tree.Term term = unwrapExpressionUntilTerm(op.getRightTerm());
        if (term instanceof Tree.NaturalLiteral) {
            power = literalValue((Tree.NaturalLiteral)term);
        } else if (term instanceof Tree.NegativeOp &&
                ((Tree.NegativeOp)term).getTerm() instanceof Tree.NaturalLiteral) {
            power = literalValue((Tree.NegativeOp)term);
        } else {
            power = null;
        }
        return power;
    }
    
    private JCExpression transformOptimizedIntegerPower(Tree.Term base,
            Long power) {
        JCExpression baseExpr = transformExpression(base, BoxingStrategy.UNBOXED, base.getTypeModel());
        if (power == 1) {
            return baseExpr;
        }
        SyntheticName baseAlias = naming.alias("base");
        JCExpression multiplications = baseAlias.makeIdent(); 
        while (power > 1) {
            power--;
            multiplications = make().Binary(JCTree.MUL, multiplications, baseAlias.makeIdent());
        }
        return make().LetExpr(makeVar(baseAlias, 
                    makeJavaType(base.getTypeModel()), 
                    baseExpr), 
                multiplications);
    }

    public JCExpression transform(Tree.BitwiseOp op) {
        Type leftType = getSupertype(op.getLeftTerm(), typeFact().getSetDeclaration());
        Type rightType = getSupertype(op.getRightTerm(), typeFact().getSetDeclaration());
    	return transformOverridableBinaryOperator(op, leftType, rightType);
    }    

    // Overridable binary operators
    

    private JCExpression transformOverridableBinaryOperator(Tree.BinaryOperatorExpression op, Interface compoundType) {
        return transformOverridableBinaryOperator(op, compoundType, 0);
    }
    
    private JCExpression transformOverridableBinaryOperator(Tree.BinaryOperatorExpression op, Interface compoundType, int typeArgumentToUse) {
        Type leftType = getSupertype(op.getLeftTerm(), compoundType);
        Type leftSelf = leftType.getDeclaration().getSelfType();
        if (leftSelf != null) {
            leftType = leftType.getTypeArguments().get(leftSelf.getDeclaration());
        }
        // the right type always only depends on the LHS so let's not try to find it on the right side because it may
        // be undecidable: https://github.com/ceylon/ceylon-compiler/issues/1535
        Type rightType = getTypeArgument(getSupertype(op.getLeftTerm(), compoundType), typeArgumentToUse);
        // we do have a special case which is when the LHS is Float and RHS is Integer and the typechecker allowed coercion
        if(getSupertype(op.getLeftTerm(), typeFact().getFloatDeclaration()) != null
                && getSupertype(op.getRightTerm(), typeFact().getIntegerDeclaration()) != null){
            // keep the RHS type then, since floats are not integers, the whole thing is resolved in the Java impl of Float with
            // special hidden operator methods
            rightType = typeFact().getIntegerType();
            // Also keep the LHS type as Float since it's final and the special methods wouldn't be found in any supertype of compountType
            leftType = typeFact().getFloatType();
        }
        return transformOverridableBinaryOperator(op, leftType, rightType);
    }

    private JCExpression transformOverridableBinaryOperator(Tree.BinaryOperatorExpression op, Type leftType, Type rightType) {
        OperatorTranslation operator = Operators.getOperator(op.getClass());
        if (operator == null) {
            return makeErroneous(op, "compiler bug: " + op.getClass() +" is an unhandled operator");
        }
        OptimisationStrategy optimisationStrategy = operator.getBinOpOptimisationStrategy(op, op.getLeftTerm(), op.getRightTerm(), this);

        at(op);
        JCExpression left = transformExpression(op.getLeftTerm(), optimisationStrategy.getBoxingStrategy(), leftType, EXPR_WIDEN_PRIM);
        JCExpression right = transformExpression(op.getRightTerm(), optimisationStrategy.getBoxingStrategy(), rightType, EXPR_WIDEN_PRIM);
        return transformOverridableBinaryOperator(operator, optimisationStrategy, left, right, op.getLeftTerm(), op.getRightTerm(), op.getTypeModel());
    }

    private JCExpression transformOverridableBinaryOperator(Tree.Term leftTerm, Tree.Term rightTerm, Type rightType,
            OperatorTranslation operator, OptimisationStrategy optimisationStrategy, 
            JCExpression left, Type expectedType) {
        JCExpression right = transformExpression(rightTerm, optimisationStrategy.getBoxingStrategy(), rightType, EXPR_WIDEN_PRIM);
        return transformOverridableBinaryOperator(operator, optimisationStrategy, left, right, leftTerm, rightTerm, expectedType);
    }

    private JCExpression transformOverridableBinaryOperator(OperatorTranslation originalOperator,
            OptimisationStrategy optimisationStrategy, 
            JCExpression left, JCExpression right,
            Tree.Term leftTerm, Tree.Term rightTerm, Type expectedType) {
        JCExpression result = null;
        
        // optimise if we can
        if(optimisationStrategy.useJavaOperator()){
            result = make().Binary(originalOperator.javacOperator, left, right);
            if (rightTerm != null) {
                result = unAutoPromote(result, expectedType);
            }
            return result;
        }

        boolean loseComparison = 
                originalOperator == OperatorTranslation.BINARY_SMALLER 
                || originalOperator == OperatorTranslation.BINARY_SMALL_AS 
                || originalOperator == OperatorTranslation.BINARY_LARGER
                || originalOperator == OperatorTranslation.BINARY_LARGE_AS;

        // for comparisons we need to invoke compare()
        OperatorTranslation actualOperator = originalOperator;
        if (loseComparison) {
            actualOperator = Operators.OperatorTranslation.BINARY_COMPARE;
        }

        List<JCExpression> args = List.of(right);
        List<JCExpression> typeArgs = null;
        
        // Set operators need reified generics
        if(originalOperator == OperatorTranslation.BINARY_UNION 
                || originalOperator == OperatorTranslation.BINARY_INTERSECTION
                || originalOperator == OperatorTranslation.BINARY_COMPLEMENT){
            Type otherSetElementType = typeFact().getIteratedType(rightTerm.getTypeModel());
            args = args.prepend(makeReifiedTypeArgument(otherSetElementType));
            typeArgs = List.<JCExpression>of(makeJavaType(otherSetElementType, JT_TYPE_ARGUMENT));
        }
        
        if (optimisationStrategy.useValueTypeMethod()) {
            Type leftType = leftTerm.getTypeModel();
            if (optimisationStrategy == OptimisationStrategy.OPTIMISE_VALUE_TYPE
                    && leftType.getDeclaration().getSelfType() != null) {
                leftType = leftType.getTypeArguments().get(leftType.getDeclaration().getSelfType().getDeclaration());
            }
            result = make().Apply(typeArgs, naming.makeQualIdent(makeJavaType(leftType, JT_NO_PRIMITIVES), actualOperator.ceylonMethod), args.prepend(left));
        } else {
            result = make().Apply(typeArgs, makeSelect(left, actualOperator.ceylonMethod), args);
        }

        if (loseComparison) {
            // We cheat slightly bu using == instead of equals, but since those values
            // don't override equals the effect is the same
            result = make().Binary(originalOperator.javacValueOperator, result, makeLanguageValue(originalOperator.ceylonValue));
        }

        return result;
    }

    //
    // Operator-Assignment expressions

    public JCExpression transform(final Tree.ArithmeticAssignmentOp op){
        final AssignmentOperatorTranslation operator = Operators.getAssignmentOperator(op.getClass());
        if(operator == null){
            return makeErroneous(op, "compiler bug: "+op.getNodeType() + " is not a supported arithmetic assignment operator");
        }

        // see if we can optimise it
        if(op.getUnboxed() && CodegenUtil.isDirectAccessVariable(op.getLeftTerm())){
            return optimiseAssignmentOperator(op, operator);
        }
        
        // we can use unboxed types if both operands are unboxed
        final boolean boxResult = !op.getUnboxed();
        
        // find the proper type
        Interface compoundType = op.getUnit().getNumericDeclaration();
        if(op instanceof Tree.AddAssignOp){
            compoundType = op.getUnit().getSummableDeclaration();
        }else if(op instanceof Tree.SubtractAssignOp){
            compoundType = op.getUnit().getInvertableDeclaration();
        }else if(op instanceof Tree.RemainderAssignOp){
            compoundType = op.getUnit().getIntegralDeclaration();
        }
        
        final Type leftType = getSupertype(op.getLeftTerm(), compoundType);
        final Type resultType = getMostPreciseType(op.getLeftTerm(), getTypeArgument(leftType, 0));
        // Normally we don't look at the RHS type because it can lead to unknown types, but if we want to extract its
        // underlying type we have to, and we deal with any eventual unknown type. Presumably unknown types will not have
        // any useful underlying type anyways.
        // Note  that looking at the RHS allows us to not have the issue of using the LHS type wrongly for the RHS type when
        // the LHS type is Float and the RHS type is Integer with implicit Float coercion
        Type rightSupertype = getSupertype(op.getRightTerm(), compoundType);
        if (rightSupertype == null || rightSupertype.isUnknown()) {
            // supertype could be null if, e.g. right type is Nothing
            rightSupertype = leftType;
        }
        Type rightTypeArgument = getTypeArgument(rightSupertype);
        if(rightTypeArgument == null || rightTypeArgument.isUnknown())
            rightTypeArgument = getTypeArgument(leftType);
        final Type rightType = getMostPreciseType(op.getLeftTerm(), rightTypeArgument);

        // we work on boxed types
        return transformAssignAndReturnOperation(op, op.getLeftTerm(), boxResult, 
                leftType, resultType, 
                new AssignAndReturnOperationFactory(){
            @Override
            public JCExpression getNewValue(JCExpression previousValue) {
                // make this call: previousValue OP RHS
                JCExpression ret = transformOverridableBinaryOperator(op.getLeftTerm(), op.getRightTerm(), rightType,
                        operator.binaryOperator, 
                        boxResult ? OptimisationStrategy.NONE : OptimisationStrategy.OPTIMISE, 
                        previousValue, op.getTypeModel());
                return ret;
            }
        });
    }

    public JCExpression transform(final Tree.BitwiseAssignmentOp op){
        final AssignmentOperatorTranslation operator = Operators.getAssignmentOperator(op.getClass());
        if(operator == null){
            return makeErroneous(op, "compiler bug: "+op.getNodeType() +" is not a supported bitwise assignment operator");
        }
    	
        Type valueType = op.getLeftTerm().getTypeModel();
        final Type rightType = getSupertype(op.getRightTerm(), typeFact().getSetDeclaration());
        
        return transformAssignAndReturnOperation(op, op.getLeftTerm(), false, valueType, valueType, new AssignAndReturnOperationFactory() {
            @Override
            public JCExpression getNewValue(JCExpression previousValue) {
            	JCExpression result = transformOverridableBinaryOperator(op.getLeftTerm(), op.getRightTerm(), rightType, operator.binaryOperator, OptimisationStrategy.NONE, previousValue, op.getTypeModel());
            	return result;
            }
        });
    }

    public JCExpression transform(final Tree.LogicalAssignmentOp op){
        final AssignmentOperatorTranslation operator = Operators.getAssignmentOperator(op.getClass());
        if(operator == null){
            return makeErroneous(op, "compiler bug: "+op.getNodeType() + " is not a supported logical assignment operator");
        }
        
        // optimise if we can
        if(CodegenUtil.isDirectAccessVariable(op.getLeftTerm())){
            return optimiseAssignmentOperator(op, operator);
        }
        
        Type valueType = op.getLeftTerm().getTypeModel();
        // we work on unboxed types
        return transformAssignAndReturnOperation(op, op.getLeftTerm(), false, 
                valueType, valueType, new AssignAndReturnOperationFactory(){
            @Override
            public JCExpression getNewValue(JCExpression previousValue) {
                // make this call: previousValue OP RHS
                return transformLogicalOp(op, operator.binaryOperator, 
                        previousValue, op.getRightTerm());
            }
        });
    }

    private JCExpression optimiseAssignmentOperator(final Tree.AssignmentOp op, final AssignmentOperatorTranslation operator) {
        // we don't care about their types since they're unboxed and we know it
        JCExpression left = transformExpression(op.getLeftTerm(), BoxingStrategy.UNBOXED, null);
        JCExpression right = transformExpression(op.getRightTerm(), BoxingStrategy.UNBOXED, null);
        return at(op).Assignop(operator.javacOperator, left, right);
    }

    // Postfix operator
    
    public JCExpression transform(Tree.PostfixOperatorExpression expr) {
        OperatorTranslation operator = Operators.getOperator(expr.getClass());
        if(operator == null){
            return makeErroneous(expr, "compiler bug "+expr.getNodeType() + " is not yet supported");
        }
        
        OptimisationStrategy optimisationStrategy = operator.getUnOpOptimisationStrategy(expr, expr.getTerm(), this);
        boolean canOptimise = optimisationStrategy.useJavaOperator();
        
        // only fully optimise if we don't have to access the getter/setter
        if(canOptimise && CodegenUtil.isDirectAccessVariable(expr.getTerm())){
            JCExpression term = transformExpression(expr.getTerm(), BoxingStrategy.UNBOXED, expr.getTypeModel(), EXPR_WIDEN_PRIM);
            return at(expr).Unary(operator.javacOperator, term);
        }
        
        Tree.Term term = unwrapExpressionUntilTerm(expr.getTerm());
        
        Interface compoundType = expr.getUnit().getOrdinalDeclaration();
        Type valueType = getSupertype(expr.getTerm(), compoundType);
        Type returnType = getMostPreciseType(term, getTypeArgument(valueType, 0));

        List<JCVariableDecl> decls = List.nil();
        List<JCStatement> stats = List.nil();
        JCExpression result = null;
        // we can optimise that case a bit sometimes
        boolean boxResult = !canOptimise;

        // attr++
        // (let $tmp = attr; attr = $tmp.getSuccessor(); $tmp;)
        if(term instanceof Tree.BaseMemberExpression
                // special case for java statics Foo.attr where Foo does not need to be evaluated
                || (term instanceof Tree.QualifiedMemberExpression
                        && ((Tree.QualifiedMemberExpression)term).getStaticMethodReference())){
            JCExpression getter;
            if(term instanceof Tree.BaseMemberExpression)
                getter = transform((Tree.BaseMemberExpression)term, null);
            else
                getter = transformMemberExpression((Tree.QualifiedMemberExpression)term, null, null);
            at(expr);
            // Type $tmp = attr
            JCExpression exprType = makeJavaType(returnType, boxResult ? JT_NO_PRIMITIVES : 0);
            Name varName = naming.tempName("op");
            // make sure we box the results if necessary
            getter = applyErasureAndBoxing(getter, term, boxResult ? BoxingStrategy.BOXED : BoxingStrategy.UNBOXED, returnType);
            JCVariableDecl tmpVar = make().VarDef(make().Modifiers(0), varName, exprType, getter);
            decls = decls.prepend(tmpVar);

            // attr = $tmp.getSuccessor()
            JCExpression successor;
            if(canOptimise){
                // use +1/-1 if we can optimise a bit
                successor = make().Binary(operator == OperatorTranslation.UNARY_POSTFIX_INCREMENT ? JCTree.PLUS : JCTree.MINUS, 
                        make().Ident(varName), makeInteger(1));
                successor = unAutoPromote(successor, returnType);
            }else{
                successor = make().Apply(null, 
                                         makeSelect(make().Ident(varName), operator.ceylonMethod), 
                                         List.<JCExpression>nil());
                // make sure the result is boxed if necessary, the result of successor/predecessor is always boxed
                successor = boxUnboxIfNecessary(successor, true, term.getTypeModel(), CodegenUtil.getBoxingStrategy(term));
            }
            JCExpression assignment = transformAssignment(expr, term, successor);
            stats = stats.prepend(at(expr).Exec(assignment));

            // $tmp
            result = make().Ident(varName);
        }
        else if(term instanceof Tree.QualifiedMemberExpression){
            // e.attr++
            // (let $tmpE = e, $tmpV = $tmpE.attr; $tmpE.attr = $tmpV.getSuccessor(); $tmpV;)
            Tree.QualifiedMemberExpression qualified = (Tree.QualifiedMemberExpression) term;
            boolean isSuper = isSuperOrSuperOf(qualified.getPrimary());
            boolean isPackage = isPackageQualified(qualified);
            // transform the primary, this will get us a boxed primary 
            JCExpression e = transformQualifiedMemberPrimary(qualified);
            at(expr);
            
            // Type $tmpE = e
            JCExpression exprType = makeJavaType(qualified.getTarget().getQualifyingType(), JT_NO_PRIMITIVES);
            Name varEName = naming.tempName("opE");
            JCVariableDecl tmpEVar = make().VarDef(make().Modifiers(0), varEName, exprType, e);

            // Type $tmpV = $tmpE.attr
            JCExpression attrType = makeJavaType(returnType, boxResult ? JT_NO_PRIMITIVES : 0);
            Name varVName = naming.tempName("opV");
            JCExpression getter;
            if (isSuper) {
                getter = transformMemberExpression(qualified, transformSuper(qualified), null);
            } else if (isPackage) {
                getter = transformMemberExpression(qualified, null, null);
            } else {
                getter = transformMemberExpression(qualified, make().Ident(varEName), null);
            }
            // make sure we box the results if necessary
            getter = applyErasureAndBoxing(getter, term, boxResult ? BoxingStrategy.BOXED : BoxingStrategy.UNBOXED, returnType);
            JCVariableDecl tmpVVar = make().VarDef(make().Modifiers(0), varVName, attrType, getter);

            decls = decls.prepend(tmpVVar);
            if (!isSuper && !isPackage) {
                // define all the variables
                decls = decls.prepend(tmpEVar);
            }
            
            // $tmpE.attr = $tmpV.getSuccessor()
            JCExpression successor;
            if(canOptimise){
                // use +1/-1 if we can optimise a bit
                successor = make().Binary(operator == OperatorTranslation.UNARY_POSTFIX_INCREMENT ? JCTree.PLUS : JCTree.MINUS, 
                        make().Ident(varVName), makeInteger(1));
                successor = unAutoPromote(successor, returnType);
            }else{
                successor = make().Apply(null, 
                                         makeSelect(make().Ident(varVName), operator.ceylonMethod), 
                                         List.<JCExpression>nil());
                //  make sure the result is boxed if necessary, the result of successor/predecessor is always boxed
                successor = boxUnboxIfNecessary(successor, true, term.getTypeModel(), CodegenUtil.getBoxingStrategy(term));
            }
            JCExpression assignment = transformAssignment(expr, term, isSuper ? transformSuper(qualified) : make().Ident(varEName), successor);
            stats = stats.prepend(at(expr).Exec(assignment));
            
            // $tmpV
            result = make().Ident(varVName);
        }else{
            return makeErroneous(term, "compiler bug: " + term.getNodeType() + " is not supported yet");
        }
        // e?.attr++ is probably not legal
        // a[i]++ is not for M1 but will be:
        // (let $tmpA = a, $tmpI = i, $tmpV = $tmpA.item($tmpI); $tmpA.setItem($tmpI, $tmpV.getSuccessor()); $tmpV;)
        // a?[i]++ is probably not legal
        // a[i1..i1]++ and a[i1...]++ are probably not legal
        // a[].attr++ and a[].e.attr++ are probably not legal

        return make().LetExpr(decls, stats, result);
    }
    
    // Prefix operator
    
    public JCExpression transform(final Tree.PrefixOperatorExpression expr) {
        final OperatorTranslation operator = Operators.getOperator(expr.getClass());
        if(operator == null){
            return makeErroneous(expr, "compiler bug: "+expr.getNodeType() + " is not supported yet");
        }
        
        OptimisationStrategy optimisationStrategy = operator.getUnOpOptimisationStrategy(expr, expr.getTerm(), this);
        final boolean canOptimise = optimisationStrategy.useJavaOperator();
        
        Tree.Term term = expr.getTerm();
        // only fully optimise if we don't have to access the getter/setter
        if(canOptimise && CodegenUtil.isDirectAccessVariable(term)){
            JCExpression jcTerm = transformExpression(term, BoxingStrategy.UNBOXED, expr.getTypeModel(), EXPR_WIDEN_PRIM);
            return at(expr).Unary(operator.javacOperator, jcTerm);
        }

        Interface compoundType = expr.getUnit().getOrdinalDeclaration();
        Type valueType = getSupertype(term, compoundType);
        final Type returnType = getMostPreciseType(term, getTypeArgument(valueType, 0));
        
        // we work on boxed types unless we could have optimised
        return transformAssignAndReturnOperation(expr, term, !canOptimise, 
                valueType, returnType, new AssignAndReturnOperationFactory(){
            @Override
            public JCExpression getNewValue(JCExpression previousValue) {
                // use +1/-1 if we can optimise a bit
                if(canOptimise){
                    JCExpression ret = make().Binary(operator == OperatorTranslation.UNARY_PREFIX_INCREMENT ? JCTree.PLUS : JCTree.MINUS, 
                            previousValue, makeInteger(1));
                    ret = unAutoPromote(ret, returnType);
                    return ret;
                }
                // make this call: previousValue.getSuccessor() or previousValue.getPredecessor()
                return make().Apply(null, makeSelect(previousValue, operator.ceylonMethod), List.<JCExpression>nil());
            }
        });
    }
    
    //
    // Function to deal with expressions that have side-effects
    
    private interface AssignAndReturnOperationFactory {
        JCExpression getNewValue(JCExpression previousValue);
    }
    
    private JCExpression transformAssignAndReturnOperation(Node operator, Tree.Term term, 
            boolean boxResult, Type valueType, Type returnType, 
            AssignAndReturnOperationFactory factory){
        
        List<JCVariableDecl> decls = List.nil();
        List<JCStatement> stats = List.nil();
        JCExpression result = null;
        // attr
        // (let $tmp = OP(attr); attr = $tmp; $tmp)
        if(term instanceof Tree.BaseMemberExpression
            // special case for java statics Foo.attr where Foo does not need to be evaluated
            || (term instanceof Tree.QualifiedMemberExpression
                    && ((Tree.QualifiedMemberExpression)term).getStaticMethodReference())){
            JCExpression getter;
            if(term instanceof Tree.BaseMemberExpression)
                getter = transform((Tree.BaseMemberExpression)term, null);
            else
                getter = transformMemberExpression((Tree.QualifiedMemberExpression)term, null, null);
            at(operator);
            // Type $tmp = OP(attr);
            JCExpression exprType = makeJavaType(returnType, boxResult ? JT_NO_PRIMITIVES : 0);
            Name varName = naming.tempName("op");
            // make sure we box the results if necessary
            getter = applyErasureAndBoxing(getter, term, boxResult ? BoxingStrategy.BOXED : BoxingStrategy.UNBOXED, valueType);
            JCExpression newValue = factory.getNewValue(getter);
            // no need to box/unbox here since newValue and $tmpV share the same boxing type
            JCVariableDecl tmpVar = make().VarDef(make().Modifiers(0), varName, exprType, newValue);
            decls = decls.prepend(tmpVar);

            // attr = $tmp
            // make sure the result is unboxed if necessary, $tmp may be boxed
            JCExpression value = make().Ident(varName);
            BoxingStrategy boxingStrategy = CodegenUtil.getBoxingStrategy(term);
            value = applyErasureAndBoxing(value, returnType, boxResult, boxingStrategy, valueType);
            JCExpression assignment = transformAssignment(operator, term, value);
            stats = stats.prepend(at(operator).Exec(assignment));
            
            // $tmp
            // return, with the box type we asked for
            result = make().Ident(varName);
        }
        else if(term instanceof Tree.QualifiedMemberExpression){
            // e.attr
            // (let $tmpE = e, $tmpV = OP($tmpE.attr); $tmpE.attr = $tmpV; $tmpV;)
            Tree.QualifiedMemberExpression qualified = (Tree.QualifiedMemberExpression) term;
            boolean isSuper = isSuperOrSuperOf(qualified.getPrimary());
            // transform the primary, this will get us a boxed primary 
            JCExpression e = transformQualifiedMemberPrimary(qualified);
            at(operator);
            
            // Type $tmpE = e
            JCExpression exprType = makeJavaType(qualified.getTarget().getQualifyingType(), JT_NO_PRIMITIVES);
            Name varEName = naming.tempName("opE");
            JCVariableDecl tmpEVar = make().VarDef(make().Modifiers(0), varEName, exprType, e);

            // Type $tmpV = OP($tmpE.attr)
            JCExpression attrType = makeJavaType(returnType, boxResult ? JT_NO_PRIMITIVES : 0);
            Name varVName = naming.tempName("opV");
            JCExpression getter = transformMemberExpression(qualified, isSuper ? transformSuper(qualified) : make().Ident(varEName), null);
            // make sure we box the results if necessary
            getter = applyErasureAndBoxing(getter, term, boxResult ? BoxingStrategy.BOXED : BoxingStrategy.UNBOXED, valueType);
            JCExpression newValue = factory.getNewValue(getter);
            // no need to box/unbox here since newValue and $tmpV share the same boxing type
            JCVariableDecl tmpVVar = make().VarDef(make().Modifiers(0), varVName, attrType, newValue);

            // define all the variables
            decls = decls.prepend(tmpVVar);
            if (!isSuper) {
                decls = decls.prepend(tmpEVar);
            }
            
            // $tmpE.attr = $tmpV
            // make sure $tmpV is unboxed if necessary
            JCExpression value = make().Ident(varVName);
            BoxingStrategy boxingStrategy = CodegenUtil.getBoxingStrategy(term);
            value = applyErasureAndBoxing(value, returnType, boxResult, boxingStrategy, valueType);
            JCExpression assignment = transformAssignment(operator, term, isSuper ? transformSuper(qualified) : make().Ident(varEName), value);
            stats = stats.prepend(at(operator).Exec(assignment));
            
            // $tmpV
            // return, with the box type we asked for
            result = make().Ident(varVName);
        }else{
            return makeErroneous(operator, "compiler bug: " + term.getNodeType() + " is not a supported assign and return operator");
        }
        // OP(e?.attr) is probably not legal
        // OP(a[i]) is not for M1 but will be:
        // (let $tmpA = a, $tmpI = i, $tmpV = OP($tmpA.item($tmpI)); $tmpA.setItem($tmpI, $tmpV); $tmpV;)
        // OP(a?[i]) is probably not legal
        // OP(a[i1..i1]) and OP(a[i1...]) are probably not legal
        // OP(a[].attr) and OP(a[].e.attr) are probably not legal

        return make().LetExpr(decls, stats, result);
    }


    public JCExpression transform(Tree.Parameter param) {
        // Transform the expression marking that we're inside a defaulted parameter for $this-handling
        //needDollarThis  = true;
        JCExpression expr;
        at(param);
        
        if (Strategy.hasDefaultParameterValueMethod(param.getParameterModel())) {
            Tree.SpecifierOrInitializerExpression spec = Decl.getDefaultArgument(param);
            Scope container = param.getParameterModel().getModel().getContainer();
            boolean classParameter = container instanceof ClassOrInterface;
            ClassOrInterface oldWithinDefaultParameterExpression = withinDefaultParameterExpression;
            if(classParameter)
                withinDefaultParameterExpression((ClassOrInterface) container);
            if (param instanceof Tree.FunctionalParameterDeclaration) {
                Tree.FunctionalParameterDeclaration fpTree = (Tree.FunctionalParameterDeclaration) param;
                Tree.SpecifierExpression lazy = (Tree.SpecifierExpression)spec;
                Function fp = (Function)fpTree.getParameterModel().getModel();
                
                expr = CallableBuilder.anonymous(gen(), param, (Function)fpTree.getTypedDeclaration().getDeclarationModel(), lazy.getExpression(), 
                        ((Tree.MethodDeclaration)fpTree.getTypedDeclaration()).getParameterLists(),
                        getTypeForFunctionalParameter(fp),
                        true).build();
            } else {
                expr = transformExpression(spec.getExpression(), 
                        CodegenUtil.getBoxingStrategy(param.getParameterModel().getModel()), 
                        param.getParameterModel().getType());
            }
            if(classParameter)
                withinDefaultParameterExpression(oldWithinDefaultParameterExpression);
        } else {
            expr = makeErroneous(param, "compiler bug: no default parameter value method");
        }
        //needDollarThis = false;
        return expr;
    }
    
    protected final JCExpression transformArg(SimpleInvocation invocation, int argIndex) {
        final Tree.Term expr = invocation.getArgumentExpression(argIndex);
        if (invocation.hasParameter(argIndex)) {
            Type type = invocation.getParameterType(argIndex);
            if (invocation.isParameterSequenced(argIndex)
                    // Java methods need their underlying type preserved
                    && !invocation.isJavaMethod()) {
                if (!invocation.isArgumentSpread(argIndex)) {
                    // If the parameter is sequenced and the argument is not ...
                    // then the expected type of the *argument* is the type arg to Iterator
                    type = typeFact().getIteratedType(type);
                } else  if (invocation.getArgumentType(argIndex).getSupertype(typeFact().getSequentialDeclaration())
                        == null) {
                    // On the other hand, if the parameter is sequenced and the argument is spread,
                    // but not sequential, then transformArguments() will use getSequence(),
                    // so we only need to expect an Iterable type
                    type = com.redhat.ceylon.model.typechecker.model.ModelUtil.appliedType(
                            typeFact().getIterableDeclaration(),
                            typeFact().getIteratedType(type), typeFact().getIteratedAbsentType(type));
                }
            }
            BoxingStrategy boxingStrategy = invocation.getParameterBoxingStrategy(argIndex);
            int flags = 0;
            if(!invocation.isParameterRaw(argIndex))
                flags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_NOT_RAW;
            if(invocation.isParameterWithConstrainedTypeParameters(argIndex))
                flags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_HAS_CONSTRAINED_TYPE_PARAMETERS;
            if(invocation.isParameterWithDependentCovariantTypeParameters(argIndex))
                flags |= ExpressionTransformer.EXPR_EXPECTED_TYPE_HAS_DEPENDENT_COVARIANT_TYPE_PARAMETERS;
            JCExpression ret = transformExpression(expr, 
                    boxingStrategy, 
                    type, flags);
            return ret;
        } else {
            // Overloaded methods don't have a reference to a parameter
            // so we have to treat them differently. Also knowing it's
            // overloaded we know we're dealing with Java code so we unbox
            Type type = expr.getTypeModel();
            return expressionGen().transformExpression(expr, 
                    BoxingStrategy.UNBOXED, 
                    type);
        }
    }
    
    private final List<ExpressionAndType> transformArgumentList(Invocation invocation, TransformedInvocationPrimary transformedPrimary, CallBuilder callBuilder) {
        return transformArguments(invocation, transformedPrimary, callBuilder);   
    }
    
    private final List<ExpressionAndType> transformArguments(Invocation invocation,
            TransformedInvocationPrimary transformedPrimary, CallBuilder callBuilder) {
        ListBuffer<ExpressionAndType> result = ListBuffer.<ExpressionAndType>lb();
        withinInvocation(false);
        appendImplicitArguments(invocation, transformedPrimary, result);
        // Explicit arguments
        if (invocation instanceof SuperInvocation) {
            withinSuperInvocation(((SuperInvocation)invocation).getSub());
            result.addAll(transformArgumentsForSimpleInvocation((SimpleInvocation)invocation, callBuilder));
            withinSuperInvocation(null);
        } else if (invocation instanceof NamedArgumentInvocation) {
            result.addAll(transformArgumentsForNamedInvocation((NamedArgumentInvocation)invocation));
        } else if (invocation instanceof CallableSpecifierInvocation) {
            result.addAll(transformArgumentsForCallableSpecifier((CallableSpecifierInvocation)invocation));
        } else if (invocation instanceof SimpleInvocation) {
            if(invocation.isUnknownArguments())
                result.add(transformUnknownArguments((SimpleInvocation) invocation, callBuilder));
            else
                result.addAll(transformArgumentsForSimpleInvocation((SimpleInvocation)invocation, callBuilder));
        } else {
            throw BugException.unhandledCase(invocation);
        }
        withinInvocation(true);
        return result.toList();
    }

    private void appendImplicitArguments(
            Invocation invocation,
            TransformedInvocationPrimary transformedPrimary,
            ListBuffer<ExpressionAndType> result) {
        // Implicit arguments
        // except for Java array constructors
        Declaration primaryDeclaration = invocation.getPrimaryDeclaration();
        Tree.Term primary = invocation.getPrimary();
        if(primaryDeclaration instanceof Class == false
                || !isJavaArray(((Class) primaryDeclaration).getType())){
            invocation.addReifiedArguments(result);
        }
        if (!(primary instanceof Tree.BaseTypeExpression)
                && !(primary instanceof Tree.QualifiedTypeExpression)
                && Invocation.onValueType(this, primary, primaryDeclaration) 
                && transformedPrimary != null) {
            result.add(new ExpressionAndType(transformedPrimary.expr,
                    makeJavaType(primary.getTypeModel())));   
        }
    }
    
    private ExpressionAndType transformUnknownArguments(SimpleInvocation invocation, CallBuilder callBuilder){

        // doesn't really matter, assume Object, it's not used
        Type iteratedType = typeFact().getObjectType();
        // the single spread argument which is allowed
        JCExpression expr = invocation.getTransformedArgumentExpression(0);
        expr = make().TypeCast(makeJavaType(typeFact().getSequentialDeclaration().getType(), JT_RAW), expr);
        JCExpression type = makeJavaType(typeFact().getSequenceType(iteratedType).getType());
        return new ExpressionAndType(expr, type);
    }
    
    private List<ExpressionAndType> transformArgumentsForSimpleInvocation(SimpleInvocation invocation, CallBuilder callBuilder) {
        final Constructor superConstructor = invocation.getConstructor();
        CtorDelegation constructorDelegation;
        if (invocation instanceof SuperInvocation) {
            constructorDelegation = ((SuperInvocation)invocation).getDelegation();
        } else {
            constructorDelegation = null;
        }
        
        List<ExpressionAndType> result = List.<ExpressionAndType>nil();
        if (!(invocation instanceof SuperInvocation)
                || !((SuperInvocation)invocation).isDelegationDelegation() ) {
            int numArguments = invocation.getNumArguments();
            if (invocation.getNumParameters() == 0) {
                // skip transforming arguments
                // (Usually, numArguments would already be null, but it's possible to call a
                //  parameterless function with a *[] argument - see #1593.)
                numArguments = 0;
            }
            boolean wrapIntoArray = false;
            ListBuffer<JCExpression> arrayWrap = new ListBuffer<JCExpression>();
            
            for (int argIndex = 0; argIndex < numArguments; argIndex++) {
                BoxingStrategy boxingStrategy = invocation.getParameterBoxingStrategy(argIndex);
                Type parameterType = invocation.getParameterType(argIndex);
                // for Java methods of variadic primitives, it's better to wrap them ourselves into an array
                // to avoid ambiguity of foo(1,2) for foo(int...) and foo(Object...) methods
                if(!wrapIntoArray
                        && invocation.isParameterSequenced(argIndex)
                        && invocation.isJavaMethod()
                        && boxingStrategy == BoxingStrategy.UNBOXED
                        && willEraseToPrimitive(typeFact().getDefiniteType(parameterType))
                        && !invocation.isSpread())
                    wrapIntoArray = true;
    
                ExpressionAndType exprAndType;
                if (invocation.isArgumentSpread(argIndex)) {
                    if (!invocation.isParameterSequenced(argIndex)) {
                        result = transformSpreadTupleArgument(invocation, callBuilder,
                                result, argIndex);
                        break;
                    }
                    if(invocation.isJavaMethod()){
                        // if it's a java method we need a special wrapping
                        exprAndType = transformSpreadArgument(invocation,
                                numArguments, argIndex, boxingStrategy,
                                parameterType);
                        argIndex = numArguments;
                    }else{
                        Type argType = invocation.getArgumentType(argIndex);
                        if (argType.getSupertype(typeFact().getSequentialDeclaration()) != null) {
                            exprAndType = transformArgument(invocation, argIndex,
                                    boxingStrategy);
                        } else if (argType.getSupertype(typeFact().getIterableDeclaration()) != null) {
                            exprAndType = transformArgument(invocation, argIndex,
                                    boxingStrategy);
                            JCExpression sequential = iterableToSequential(exprAndType.expression);
                            if(invocation.isParameterVariadicPlus(argIndex)){
                                Type iteratedType = typeFact().getIteratedType(argType);
                                sequential = utilInvocation().castSequentialToSequence(sequential, iteratedType);
                            }
                            exprAndType = new ExpressionAndType(sequential, exprAndType.type);
                        } else {
                            exprAndType = new ExpressionAndType(makeErroneous(invocation.getNode(), "compiler bug: unexpected spread argument"), makeErroneous(invocation.getNode(), "compiler bug: unexpected spread argument"));
                        }
                    }
                } else if (!invocation.isParameterSequenced(argIndex)
                        // if it's sequenced, Java and there's no spread at all, pass it along
                        || (invocation.isParameterSequenced(argIndex) && invocation.isJavaMethod() && !invocation.isSpread())) {
                    exprAndType = transformArgument(invocation, argIndex,
                            boxingStrategy);
                    // Callable has a variadic 1-param method that if you invoke it with a Java Object[] will confuse javac and give
                    // preference to the variadic method instead of the $call$(Object) one, so we force a cast to Object to resolve it
                    // This is not required for primitive arrays since they are not Object[]
                    if(numArguments == 1 
                            && invocation.isIndirect()){
                        Type argumentType = invocation.getArgumentType(0);
                        if(isJavaObjectArray(argumentType)
                                || isNull(argumentType)){
                            exprAndType = new ExpressionAndType(make().TypeCast(makeJavaType(typeFact().getObjectType()), exprAndType.expression),
                                    exprAndType.type);
                        }
                    }else if(invocation.isParameterSequenced(argIndex) && invocation.isJavaMethod() && !invocation.isSpread()){
                        // in fact, the very same problem happens when passing null or object arrays to a java variadic method
                        Type argumentType = invocation.getArgumentType(argIndex);
                        if(isJavaObjectArray(argumentType)
                                || isNull(argumentType)){
                            // remove any ambiguity
                            exprAndType = new ExpressionAndType(make().TypeCast(makeJavaType(parameterType), exprAndType.expression),
                                    exprAndType.type);
                        }
                    }
                } else {
                    // we must have a sequenced param
                    if(invocation.isSpread()){
                        exprAndType = transformSpreadArgument(invocation,
                                numArguments, argIndex, boxingStrategy,
                                parameterType);
                        argIndex = numArguments;
                    }else{
                        exprAndType = transformVariadicArgument(invocation,
                                numArguments, argIndex, parameterType);
                        argIndex = numArguments;
                    }
                }
                if(!wrapIntoArray) {
                    if (argIndex== 0 
                            && invocation.isCallable()
                            && !invocation.isArgumentSpread(numArguments-1)
                            ) {
                        exprAndType = new ExpressionAndType(
                                make().TypeCast(make().Type(syms().objectType), exprAndType.expression),
                                make().Type(syms().objectType));
                    }
                    result = result.append(exprAndType);
                } else {
                    arrayWrap.append(exprAndType.expression);
                }
            }
            if (invocation.isIndirect()
                    && invocation.isParameterSequenced(numArguments)
                    && !invocation.isArgumentSpread(numArguments-1)
                    && ((IndirectInvocation)invocation).getNumParameters() > numArguments) {
                // Calling convention for indirect variadic invocation's requires
                // explicit variadic argument (can't use the overloading trick)
                result = result.append(new ExpressionAndType(makeEmptyAsSequential(true), make().Erroneous()));
            }
            if(wrapIntoArray){
                // must have at least one arg, so take the last one
                Type parameterType = invocation.getParameterType(numArguments-1);
                JCExpression arrayType = makeJavaType(parameterType, JT_RAW);
                
                JCNewArray arrayExpr = make().NewArray(arrayType, List.<JCExpression>nil(), arrayWrap.toList());
                JCExpression arrayTypeExpr = make().TypeArray(makeJavaType(parameterType, JT_RAW));
                result = result.append(new ExpressionAndType(arrayExpr, arrayTypeExpr));
            }
            
        } else {
            for (Parameter p : constructorDelegation.getConstructor().getParameterList().getParameters()) {
                result = result.append(new ExpressionAndType(naming.makeName(p.getModel(), Naming.NA_IDENT | Naming.NA_ALIASED), null));
            }
        }
        
        boolean concreteDelegation = invocation instanceof SuperInvocation
                && ((SuperInvocation)invocation).getDelegation().isConcreteSelfDelegation();
        if (superConstructor == null && 
                concreteDelegation) {
            Constructor delegateTo = ((SuperInvocation)invocation).getDelegation().getConstructor();
            result = result.prepend(
                    new ExpressionAndType(naming.makeNamedConstructorName(delegateTo, true),
                            naming.makeNamedConstructorType(delegateTo, true)));
        } else if (
                superConstructor != null 
                && constructorDelegation != null
                && constructorDelegation.isSelfDelegation()) {
            result = result.prepend(
                    new ExpressionAndType(naming.makeNamedConstructorName(superConstructor, concreteDelegation),
                            naming.makeNamedConstructorType(superConstructor, concreteDelegation)));
        } else if (
                superConstructor != null 
                        && !Decl.isDefaultConstructor(superConstructor)) {
            result = result.prepend(
                    new ExpressionAndType(naming.makeNamedConstructorName(superConstructor, concreteDelegation),
                            naming.makeNamedConstructorType(superConstructor, concreteDelegation)));
        }
        
        return result;
    }

    private ExpressionAndType transformVariadicArgument(
            SimpleInvocation invocation, int numArguments, int argIndex,
            Type parameterType) {
        ExpressionAndType exprAndType;
        final Type iteratedType = typeFact().getIteratedType(parameterType);
        final JCExpression expr;
        final JCExpression type;
        // invoking f(a, b, c), where declared f(A a, B* b)
        // collect each remaining argument and box with an ArraySequence<T>
        List<JCExpression> x = List.<JCExpression>nil();
        for (int ii = argIndex ; ii < numArguments; ii++) {
            x = x.append(invocation.getTransformedArgumentExpression(ii));
        }
        expr = makeSequence(x, iteratedType, JT_TYPE_ARGUMENT);
        type = makeJavaType(typeFact().getSequenceType(iteratedType).getType());
        exprAndType = new ExpressionAndType(expr, type);
        return exprAndType;
    }

    private ExpressionAndType transformSpreadArgument(
            SimpleInvocation invocation, int numArguments, int argIndex,
            BoxingStrategy boxingStrategy, Type parameterType) {
        ExpressionAndType exprAndType;
        final Type iteratedType = typeFact().getIteratedType(parameterType);
        final JCExpression expr;
        final JCExpression type;
        // optimise "*javaArray.iterable" into "javaArray" for java variadic parameters, since we can pass them just along
        if(invocation.isJavaMethod()
                && numArguments == argIndex+1
                && !invocation.isArgumentComprehension(argIndex)){
            Expression argumentExpression = invocation.getArgumentExpression(argIndex);
            Term argument = Decl.unwrapExpressionsUntilTerm(argumentExpression);
            if (argument instanceof Tree.QualifiedMemberExpression) {
                Tree.QualifiedMemberExpression qualifiedMemberArgument = (Tree.QualifiedMemberExpression)argument;
                if ("iterable".equals(qualifiedMemberArgument.getIdentifier().getText())
                    && isJavaArray(qualifiedMemberArgument.getPrimary().getTypeModel())) {
                    // just pass the array as-is
                    // we don't care at all about unboxing or casting since we can't be dealing with boxing
                    // and we generate our own cast, at least for non-primitive arrays where it may be ambiguous,
                    // we could avoid the cast for non-type-parameter and non-Object arrays, but that's more expensive
                    // to check for
                    JCExpression primary = transformExpression(qualifiedMemberArgument.getPrimary());
                    type = makeJavaType(typeFact().getSequenceType(iteratedType).getType());
                    if(isJavaObjectArray(qualifiedMemberArgument.getPrimary().getTypeModel())){
                        expr = make().TypeCast(makeJavaType(qualifiedMemberArgument.getPrimary().getTypeModel()), primary);
                    }else{
                        expr = primary;
                    }
                    return new ExpressionAndType(expr, type);
                }
            }
        }
        // invoking f(a, *b), where declared f(A a, B* b)
        // we can have several remaining arguments and the last one is spread
        List<JCExpression> x = List.<JCExpression>nil();
        for (int ii = argIndex ; ii < numArguments; ii++) {
            JCExpression argExpr = invocation.getTransformedArgumentExpression(ii);
            // the last parameter is spread and must be put first
            if(ii < numArguments - 1){
                x = x.append(argExpr);
            }else{
                // convert to a Sequential if required
                Type argType = invocation.getArgumentType(ii);
                if(!typeFact().isSequentialType(argType))
                    argExpr = iterableToSequential(argExpr);
                x = x.prepend(argExpr);
            }
        }
        if(invocation.isJavaMethod()){
            // collect all the initial arguments and wrap into a Java array
            // first arg is the spread part
            JCExpression last = x.head;
            // remove it from x
            x = x.tail;
            
            Type lastType = invocation.getArgumentType(numArguments-1);

            // must translate it into a Util call
            expr = sequenceToJavaArray(invocation, last, parameterType, boxingStrategy, lastType, x);
        }else{
            JCExpression typeExpr = makeJavaType(iteratedType, JT_TYPE_ARGUMENT);
            JCExpression sequentialExpr = utilInvocation().sequentialInstance(typeExpr, makeReifiedTypeArgument(iteratedType), x.head, x.tail);
            if (invocation.isParameterVariadicPlus(argIndex)) {
                expr = utilInvocation().castSequentialToSequence(sequentialExpr, iteratedType);
            } else {
                expr = sequentialExpr;
            }
        }
        type = makeJavaType(typeFact().getSequenceType(iteratedType).getType());
        exprAndType = new ExpressionAndType(expr, type);
        return exprAndType;
    }

    private List<ExpressionAndType> transformSpreadTupleArgument(
            SimpleInvocation invocation, CallBuilder callBuilder,
            List<ExpressionAndType> result, final int argIndex) {
        BoxingStrategy boxingStrategy;
        // Spread tuple Argument
        // invoking f(*args), where declared f(A a, B a) (last param not sequenced)
        final Tree.Expression tupleArgument = invocation.getArgumentExpression(argIndex);
        final int minimumTupleArguments = typeFact().getTupleMinimumLength(tupleArgument.getTypeModel());
        final boolean tupleUnbounded = typeFact().isTupleLengthUnbounded(tupleArgument.getTypeModel());
        final Type callableType = invocation.getPrimary().getTypeModel().getFullType();
        
        // Only evaluate the tuple expr once
        SyntheticName tupleAlias = naming.alias("tuple");
        JCExpression tupleType;
        JCExpression tupleExpr = transformExpression(tupleArgument, BoxingStrategy.BOXED, null);
        tupleType = makeJavaType(typeFact().getSequentialDeclaration().getType(), JT_RAW);
        tupleExpr = make().TypeCast(makeJavaType(typeFact().getSequentialDeclaration().getType(), JT_RAW), tupleExpr);
        
        callBuilder.appendStatement(makeVar(tupleAlias, tupleType, tupleExpr));
        
        if (callBuilder.getArgumentHandling() == 0) {
            // XXX Hack: Only do this if we're not already doing 
            // something funky with arguments e.g. SpreadOp
            callBuilder.argumentHandling(CallBuilder.CB_LET, naming.alias("spreadarg"));
        }
        callBuilder.voidMethod(invocation.getReturnType() == null 
                || Decl.isUnboxedVoid(invocation.getPrimaryDeclaration())
                || isWithinSuperInvocation()); 
        
        /* Cases:
            *[] -> () => nothing
            *[] -> (Integer=) => nothing
            *[] -> (Integer*) => nothing
            *[Integer] -> (Integer) => extract
            *[Integer] -> (Integer=) => extract
            *[Integer] -> (Integer*) => pass the tuple as-is
            *[Integer*] -> (Integer*) => pass the tuple as-is
            *[Integer+] -> (Integer*) => pass the tuple as-is
            *[Integer] -> (Integer, Integer*) => extract and drop the tuple
            *[Integer,Integer] -> (Integer, Integer) => extract
            *[Integer,Integer] -> (Integer=, Integer=) => extract
            *[Integer,Integer] -> (Integer, Integer*) => extract and pass the tuple rest
            *[Integer,Integer*] -> (Integer, Integer*) => extract and pass the tuple rest
            *[Integer,Integer+] -> (Integer, Integer*) => extract and pass the tuple rest
        */
        
        int spreadArgIndex = argIndex;
        final int maxParameters = getNumParametersOfCallable(callableType);
        boolean variadic = maxParameters > 0 && invocation.isParameterSequenced(maxParameters-1);
        // we extract from the tuple not more than we have tuple members, but even less than that if we don't
        // have enough parameters to put them in
        final int argumentsToExtract = Math.min(argIndex + minimumTupleArguments, variadic ? maxParameters - 1 : maxParameters); 
        for (; spreadArgIndex < argumentsToExtract; spreadArgIndex++) {
            boxingStrategy = invocation.getParameterBoxingStrategy(spreadArgIndex);
            Type paramType = getParameterTypeOfCallable(callableType, spreadArgIndex);
            JCExpression tupleIndex = boxType(make().Literal((long)spreadArgIndex-argIndex), 
                    typeFact().getIntegerType());
            JCExpression tupleElement = make().Apply(null, 
                    naming.makeQualIdent(tupleAlias.makeIdent(), "get"),
                    List.<JCExpression>of(tupleIndex));
            
            tupleElement = applyErasureAndBoxing(tupleElement, 
                    typeFact().getAnythingType(), 
                    true, boxingStrategy, paramType);
            JCExpression argType = makeJavaType(paramType, boxingStrategy == BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0);
            result = result.append(new ExpressionAndType(tupleElement, argType));
        }
        // if we're variadic AND
        // - the tuple is unbounded (which means we must have an unknown number of elements left to pass)
        // - OR the tuple is bounded but we did not pass them all
        if (variadic 
                && (tupleUnbounded || argumentsToExtract < (minimumTupleArguments + argIndex))) {
            boxingStrategy = invocation.getParameterBoxingStrategy(spreadArgIndex);
            Type paramType = getParameterTypeOfCallable(callableType, spreadArgIndex);
            JCExpression tupleElement = tupleAlias.makeIdent();
            // argIndex = 1, tuple = [Integer], params = [Integer, Integer*], spreadArgIndex = 1 => no span
            // argIndex = 0, tuple = [Integer+], params = [Integer, Integer*], spreadArgIndex = 1 => spanFrom(1)
            if(spreadArgIndex - argIndex > 0){
                JCExpression tupleIndex = boxType(make().Literal((long)spreadArgIndex-argIndex), 
                        typeFact().getIntegerType());
                tupleElement = make().Apply(null, naming.makeQualIdent(tupleElement, "spanFrom"),
                        List.<JCExpression>of(tupleIndex));
            }
            tupleElement = applyErasureAndBoxing(tupleElement, 
                    typeFact().getAnythingDeclaration().getType(), 
                    true, boxingStrategy, paramType);
            JCExpression argType = makeJavaType(paramType, boxingStrategy == BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0);
            
            JCExpression expr;
            if(invocation.isJavaMethod()){
                // no need to handle leading arguments since that is handled by transformSpreadArgument
                // if ever we have leading arguments we never end up in this method
                expr = sequenceToJavaArray(invocation, tupleElement, paramType, boxingStrategy, paramType, List.<JCExpression>nil());
            }else{
                expr = tupleElement;
            }
            result = result.append(new ExpressionAndType(expr, argType));
        } else if (variadic
                && invocation.isIndirect()
                && argumentsToExtract >= minimumTupleArguments
                && !tupleUnbounded) {
            result = result.append(new ExpressionAndType(makeEmptyAsSequential(true), makeJavaType(typeFact().getSequenceType(typeFact().getAnythingDeclaration().getType()), JT_RAW)));
        }
        return result;
    }

    private ExpressionAndType transformArgument(SimpleInvocation invocation,
            int argIndex, BoxingStrategy boxingStrategy) {
        ExpressionAndType exprAndType;
        JCExpression expr = invocation.getTransformedArgumentExpression(argIndex);
        JCExpression type = makeJavaType(invocation.getParameterType(argIndex), boxingStrategy == BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0);
        Class ctedClass = Decl.getConstructedClass(invocation.getPrimaryDeclaration());
        if (argIndex == 0
                && typeFact().isOptionalType(invocation.getParameterType(argIndex))
                && invocation.getArgumentType(argIndex).isSubtypeOf(typeFact().getNullType())
                && ctedClass != null && (ctedClass.hasConstructors()|| ctedClass.isSerializable())) {
            // we've invoking the default constructor, whose first parameter has optional type
            // with a null argument: That will be ambiguous wrt any named constructors
            // with otherwise identical signitures, so we need a typecast to
            // disambiguate
            expr = make().TypeCast(makeJavaType(invocation.getParameterType(argIndex), boxingStrategy == BoxingStrategy.BOXED ? JT_NO_PRIMITIVES : 0), expr);
        }
        exprAndType = new ExpressionAndType(expr, type);
        return exprAndType;
    }
    
    private List<ExpressionAndType> transformArgumentsForNamedInvocation(NamedArgumentInvocation invocation) {
        List<ExpressionAndType> result = List.<ExpressionAndType>nil();
        for (ExpressionAndType argAndType : invocation.getArgumentsAndTypes()) {
            result = result.append(argAndType);
        }
        return result;
    }
    
    private List<ExpressionAndType> transformArgumentsForCallableSpecifier(CallableSpecifierInvocation invocation) {
        List<ExpressionAndType> result = List.<ExpressionAndType>nil();
        int argIndex = 0;
        for(Parameter parameter : invocation.getMethod().getFirstParameterList().getParameters()) {
            Type exprType = expressionGen().getTypeForParameter(parameter, null, this.TP_TO_BOUND);
            Parameter declaredParameter = invocation.getMethod().getFirstParameterList().getParameters().get(argIndex);
            
            JCExpression arg = naming.makeName(parameter.getModel(), Naming.NA_IDENT);
            
            arg = expressionGen().applyErasureAndBoxing(
                    arg, 
                    exprType, 
                    !parameter.getModel().getUnboxed(), 
                    BoxingStrategy.BOXED,// Callables always have boxed params 
                    declaredParameter.getType());
            result = result.append(new ExpressionAndType(arg, makeJavaType(declaredParameter.getType())));
            argIndex++;
        }
        return result;
    }
    
    public final JCExpression transformInvocation(final Invocation invocation) {
        boolean prevFnCall = withinInvocation(true);
        try {
            final CallBuilder callBuilder = CallBuilder.instance(this);
            if (invocation.getPrimary() instanceof Tree.StaticMemberOrTypeExpression){
                transformTypeArguments(callBuilder, 
                        (Tree.StaticMemberOrTypeExpression)invocation.getPrimary());
            }
            if (invocation instanceof CallableSpecifierInvocation) {
                return transformCallableSpecifierInvocation(callBuilder, (CallableSpecifierInvocation)invocation);
            } else {
                at(invocation.getNode());
                Tree.Term primary = Decl.unwrapExpressionsUntilTerm(invocation.getPrimary());
                JCExpression result = transformTermForInvocation(primary, new InvocationTermTransformer(invocation, callBuilder));
                return result;
                
            }
        } finally {
            withinInvocation(prevFnCall);
        }
    }

    protected JCExpression transformPositionalInvocationOrInstantiation(Invocation invocation, CallBuilder callBuilder, TransformedInvocationPrimary transformedPrimary) {
        JCExpression resultExpr;
        if (invocation.isMemberRefInvocation()) {
            resultExpr = transformInvocation(invocation, callBuilder, transformedPrimary);
        } else if (invocation.getPrimary() instanceof Tree.BaseTypeExpression) {
            resultExpr = transformBaseInstantiation(invocation, callBuilder, transformedPrimary);
        } else if (invocation.getPrimary() instanceof Tree.QualifiedTypeExpression) {
            resultExpr = transformQualifiedInstantiation(invocation, callBuilder, transformedPrimary);
        } else {   
            resultExpr = transformInvocation(invocation, callBuilder, transformedPrimary);
        }
        
        if(invocation.handleBoxing)
            resultExpr = applyErasureAndBoxing(resultExpr, invocation.getReturnType(), 
                    invocation.erased, !invocation.unboxed, invocation.boxingStrategy, invocation.getReturnType(), 0);
        return resultExpr;
    }

    private JCExpression transformInvocation(Invocation invocation, CallBuilder callBuilder,
            TransformedInvocationPrimary transformedPrimary) {
        invocation.location(callBuilder);
        if (Decl.isConstructorPrimary(invocation.getPrimary())) {
            Tree.StaticMemberOrTypeExpression qte = (Tree.StaticMemberOrTypeExpression)invocation.getPrimary();
            // instantiator
            Constructor ctor = Decl.getConstructor(qte.getDeclaration());
            if (Strategy.generateInstantiator(ctor)) {
                callBuilder.typeArguments(List.<JCExpression>nil());
                java.util.List<Type> typeModels = qte.getTypeArguments().getTypeModels();
                if (typeModels!=null) {
                    for (Type tm : typeModels) {
                        callBuilder.typeArgument(makeJavaType(tm, AbstractTransformer.JT_TYPE_ARGUMENT));
                    }
                }
                callBuilder.invoke(naming.makeInstantiatorMethodName(transformedPrimary.expr, Decl.getConstructedClass(ctor)));
            } else {
                if (Decl.getConstructedClass(invocation.getPrimaryDeclaration()).isMember()
                        && invocation.getPrimary() instanceof Tree.QualifiedMemberOrTypeExpression
                        && !(((Tree.QualifiedMemberOrTypeExpression)invocation.getPrimary()).getPrimary() instanceof Tree.BaseTypeExpression)) {
                    callBuilder.instantiate(new ExpressionAndType(transformedPrimary.expr, null),
                            makeJavaType(invocation.getReturnType(), JT_CLASS_NEW | (transformedPrimary.expr ==  null ? 0 : JT_NON_QUALIFIED)));
                } else {
                    callBuilder.instantiate(
                            makeJavaType(invocation.getReturnType(), JT_CLASS_NEW)/*transformedPrimary.expr*/);
                }
            }
        } else
        if(invocation.getQmePrimary() != null 
                && isJavaArray(invocation.getQmePrimary().getTypeModel())
                && transformedPrimary.selector != null
                && (transformedPrimary.selector.equals("get")
                    || transformedPrimary.selector.equals("set"))){
            if(transformedPrimary.selector.equals("get"))
                callBuilder.arrayRead(transformedPrimary.expr);
            else if(transformedPrimary.selector.equals("set")){
                callBuilder.arrayWrite(transformedPrimary.expr);
                Type arrayType = invocation.getQmePrimary().getTypeModel().resolveAliases();
                if(isJavaObjectArray(arrayType) && invocation instanceof PositionalInvocation){
                    Type elementType = arrayType.getTypeArgumentList().get(0);
                    Type argumentType = ((PositionalInvocation)invocation).getArgumentType(1);
                    if(!argumentType.isSubtypeOf(typeFact().getOptionalType(elementType)))
                        callBuilder.javaArrayWriteNeedsCast(true);
                }
            }else
                return makeErroneous(invocation.getNode(), "compiler bug: extraneous array selector: "+transformedPrimary.selector);
        } else if (invocation.isUnknownArguments()) {
            // if we have an unknown parameter list, like Callble<Ret,Args>, need to prepend the callable
            // to the argument list, and invoke Util.apply
            // note that ATM the typechecker only allows a single argument to be passed in spread form in this
            // case so we don't need to look at parameter types
            JCExpression callableTypeExpr = makeJavaType(invocation.getPrimary().getTypeModel());
            ExpressionAndType callableArg = new ExpressionAndType(transformedPrimary.expr, callableTypeExpr);
            Type returnType = invocation.getReturnType();
            JCExpression returnTypeExpr = makeJavaType(returnType, JT_NO_PRIMITIVES);
            callBuilder.prependArgumentAndType(callableArg);
            callBuilder.typeArgument(returnTypeExpr);
            callBuilder.invoke(make().Select(make().QualIdent(syms().ceylonUtilType.tsym), 
                                             names().fromString("apply")));
        } else if (invocation.isOnValueType()) {
            JCExpression primTypeExpr = makeJavaType(invocation.getQmePrimary().getTypeModel(), JT_NO_PRIMITIVES | JT_VALUE_TYPE);
            callBuilder.invoke(naming.makeQuotedQualIdent(primTypeExpr, transformedPrimary.selector));

        } else {
            callBuilder.invoke(naming.makeQuotedQualIdent(transformedPrimary.expr, transformedPrimary.selector));
        }
        return callBuilder.build();
    }

    private JCExpression transformQualifiedInstantiation(Invocation invocation, CallBuilder callBuilder,
            TransformedInvocationPrimary transformedPrimary) {
        
        Tree.QualifiedTypeExpression qte = (Tree.QualifiedTypeExpression)invocation.getPrimary();
        Declaration declaration = qte.getDeclaration();
        invocation.location(callBuilder);
        if (Decl.isJavaStaticPrimary(invocation.getPrimary())) {
            callBuilder.instantiate(transformedPrimary.expr);
        } else if (!Strategy.generateInstantiator(declaration)) {
            if (Decl.isConstructorPrimary(invocation.getPrimary())) {
                if (Decl.getConstructedClass(invocation.getPrimaryDeclaration()).isMember()
                        /*&& invocation.getPrimary() instanceof Tree.QualifiedTypeExpression
                        && !(((Tree.QualifiedTypeExpression)invocation.getPrimary()).getPrimary() instanceof Tree.BaseTypeExpression)*/) {
                    callBuilder.instantiate(new ExpressionAndType(transformedPrimary.expr, null),
                            makeJavaType(invocation.getReturnType(), JT_CLASS_NEW | (transformedPrimary.expr ==  null ? 0 : JT_NON_QUALIFIED)));
                } else {
                    callBuilder.instantiate(
                            makeJavaType(invocation.getReturnType(), JT_CLASS_NEW)/*transformedPrimary.expr*/);
                }
            } else { 
                JCExpression qualifier;
                JCExpression qualifierType;
                if (declaration.getContainer() instanceof Interface) {
                    // When doing qualified invocation through an interface we need
                    // to get the companion.
                    Interface qualifyingInterface = (Interface)declaration.getContainer();
                    qualifier = transformedPrimary.expr;
                    qualifierType = makeJavaType(qualifyingInterface.getType(), JT_COMPANION);
                } else {
                    qualifier = transformedPrimary.expr;
                    if (declaration.getContainer() instanceof TypeDeclaration) {
                        qualifierType = makeJavaType(((TypeDeclaration)declaration.getContainer()).getType());
                    } else {
                        qualifierType = null;
                    }
                }
                Type classType = (Type)qte.getTarget();
                JCExpression type;
                // special case for package-qualified things that are not really qualified
                if(qualifier == null){
                    type = makeJavaType(classType, AbstractTransformer.JT_CLASS_NEW);
                }else{
                    // Note: here we're not fully qualifying the class name because the JLS says that if "new" is qualified the class name
                    // is qualified relative to it
                    type = makeJavaType(classType, AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_NON_QUALIFIED);
                }
                callBuilder.instantiate(new ExpressionAndType(qualifier, qualifierType), type);
            }
        } else {
            // instantiator
            callBuilder.typeArguments(List.<JCExpression>nil());
            java.util.List<Type> typeModels = qte.getTypeArguments().getTypeModels();
            if (typeModels!=null) {
                for (Type tm : typeModels) {
                    callBuilder.typeArgument(makeJavaType(tm, AbstractTransformer.JT_TYPE_ARGUMENT));
                }
            }
            callBuilder.invoke(naming.makeInstantiatorMethodName(transformedPrimary.expr, Decl.getConstructedClass(declaration)));
        }
        JCExpression result = callBuilder.build();
        if (Strategy.isInstantiatorUntyped(declaration)) {
            result = make().TypeCast(makeJavaType(invocation.getReturnType()), result);
        }
        return result;
    }

    private JCExpression transformBaseInstantiation(Invocation invocation, CallBuilder callBuilder,
            TransformedInvocationPrimary transformedPrimary) {
        JCExpression resultExpr;
        Tree.BaseTypeExpression type = (Tree.BaseTypeExpression)invocation.getPrimary();
        Declaration declaration = type.getDeclaration();
        invocation.location(callBuilder);
        if (Strategy.generateInstantiator(declaration)) {
            resultExpr = callBuilder
                    .typeArguments(List.<JCExpression>nil())
                    .invoke(naming.makeInstantiatorMethodName(transformedPrimary.expr, (Class)declaration))
                    .build();
            if (Strategy.isInstantiatorUntyped(declaration)) {
                // $new method declared to return Object, so needs typecast
                resultExpr = make().TypeCast(makeJavaType(
                        ((TypeDeclaration)declaration).getType()), resultExpr);
            }
        } else {
            Type classType = (Type)type.getTarget();
            if(isJavaArray(classType)){
                JCExpression typeExpr = makeJavaType(classType, AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_RAW);
                callBuilder.javaArrayInstance(typeExpr);
                if(isJavaObjectArray(classType)){
                    Type elementType = classType.getTypeArgumentList().get(0);
                    MultidimensionalArray multiArray = getMultiDimensionalArrayInfo(elementType);
                    if(multiArray != null)
                        elementType = multiArray.type;
                    // if it is an array of Foo<X> we need a raw instanciation and cast
                    // array of Foo is fine, array of Nothing too
                    if(elementType.getDeclaration() instanceof ClassOrInterface
                            || elementType.isNothing()){
                        if(!elementType.getTypeArgumentList().isEmpty())
                            callBuilder.javaArrayInstanceNeedsCast(makeJavaType(classType, AbstractTransformer.JT_NO_PRIMITIVES));
                    }else{
                        // if it's an array of union, intersection or type param we need a runtime allocation
                        callBuilder.javaArrayInstanceIsGeneric(makeReifiedTypeArgument(elementType), 
                                multiArray != null ? multiArray.dimension + 1 : 1);
                    }
                }
            }else{
                if (Decl.isConstructor(classType.getDeclaration())) {
                    classType = classType.getExtendedType();
                }
                JCExpression typeExpr = makeJavaType(classType, AbstractTransformer.JT_CLASS_NEW);
                callBuilder.instantiate(typeExpr);
            }
            resultExpr = callBuilder.build();
        }
        return resultExpr;
    }
    
    private JCExpression transformCallableSpecifierInvocation(CallBuilder callBuilder, CallableSpecifierInvocation invocation) {
        at(invocation.getNode());
        JCExpression result = callBuilder
            .invoke(naming.makeQuotedQualIdent(invocation.getCallable(), Naming.getCallableMethodName(invocation.getMethod())))
            .argumentsAndTypes(transformArgumentList(invocation, null, callBuilder))
            .build();
        if(invocation.handleBoxing)
            result = applyErasureAndBoxing(result, invocation.getReturnType(), 
                    !invocation.unboxed, invocation.boxingStrategy, invocation.getReturnType());
        return result;
    }
    
    private final void transformTypeArguments(
            CallBuilder callBuilder,
            Tree.StaticMemberOrTypeExpression mte) {
        java.util.List<TypeParameter> tps = null;
        Declaration declaration = mte.getDeclaration();
        if (declaration instanceof Generic) {
            tps = ((Generic)declaration).getTypeParameters();
        }
        if (tps != null) {
            for (TypeParameter tp : tps) {
                Type ta = mte.getTarget().getTypeArguments().get(tp);
                java.util.List<Type> bounds = null;
                boolean needsCastForBounds = false;
                if(!tp.getSatisfiedTypes().isEmpty()){
                    bounds = new ArrayList<Type>(tp.getSatisfiedTypes().size());
                    for(Type bound : tp.getSatisfiedTypes()){
                        // substitute the right type arguments
                        bound = substituteTypeArgumentsForTypeParameterBound(mte.getTarget(), bound);
                        bounds.add(bound);
                        needsCastForBounds |= needsCast(ta, bound, false, false, false);
                    }
                }
                boolean hasMultipleBounds;
                Type firstBound;
                if(bounds != null){
                    hasMultipleBounds = bounds.size() > 1;
                    firstBound = bounds.isEmpty() ? null : bounds.get(0);
                }else{
                    hasMultipleBounds = false;
                    firstBound = null;
                }
                if (willEraseToObject(ta) || needsCastForBounds) {
                    boolean boundsSelfDependent = isBoundsSelfDependant(tp);
                    if (hasDependentTypeParameters(tps, tp)
                            // if we must use the bounds and we have more than one, we cannot use one to satisfy them all
                            // and we cannot represent the intersection type in Java so give up
                            || hasMultipleBounds
                            // if we are going to use the first bound and it is self-dependent, we will make it raw
                            || boundsSelfDependent
                            || (firstBound != null && willEraseToObject(firstBound))) {
                        // we just can't satisfy the bounds if there are more than one, just pray,
                        // BUT REMEMBER THERE IS NO SUCH THING AS A RAW METHOD CALL IN JAVA
                        // so at some point we'll have to introduce an intersection type AST node to satisfy multiple bounds
                        if(hasMultipleBounds){
                            callBuilder.typeArguments(List.<JCExpression>nil());
                            return;
                        }
                        // if we have a bound
                        if(firstBound != null){
                            // if it's self-dependent we cannot satisfy it without a raw type
                            if(boundsSelfDependent)
                                callBuilder.typeArgument(makeJavaType(firstBound, JT_TYPE_ARGUMENT|JT_RAW));
                            else
                                callBuilder.typeArgument(makeJavaType(firstBound, JT_TYPE_ARGUMENT));
                        }else{
                            // no bound, let's go with Object then
                            callBuilder.typeArgument(makeJavaType(typeFact().getObjectType(), JT_TYPE_ARGUMENT));
                        }
                    }else if (firstBound == null) {
                        callBuilder.typeArgument(makeJavaType(ta, JT_TYPE_ARGUMENT));
                    } else {
                        callBuilder.typeArgument(makeJavaType(firstBound, JT_TYPE_ARGUMENT));
                    }
                } else {
                    callBuilder.typeArgument(makeJavaType(ta, JT_TYPE_ARGUMENT));
                }
            }
        }
    }

    boolean erasesTypeArguments(Reference producedReference) {
        java.util.List<TypeParameter> tps = null;
        Declaration declaration = producedReference.getDeclaration();
        if (declaration instanceof Generic) {
            tps = ((Generic)declaration).getTypeParameters();
        }
        if (tps != null) {
            for (TypeParameter tp : tps) {
                Type ta = producedReference.getTypeArguments().get(tp);
                java.util.List<Type> bounds = null;
                boolean needsCastForBounds = false;
                if(!tp.getSatisfiedTypes().isEmpty()){
                    bounds = new ArrayList<Type>(tp.getSatisfiedTypes().size());
                    for(Type bound : tp.getSatisfiedTypes()){
                        // substitute the right type arguments
                        bound = substituteTypeArgumentsForTypeParameterBound(producedReference, bound);
                        bounds.add(bound);
                        needsCastForBounds |= needsCast(ta, bound, false, false, false);
                    }
                }
                if (willEraseToObject(ta) || needsCastForBounds) {
                    return true;
                }
            }
        }
        return false;
    }

    protected JCExpression transformNamedArgumentInvocationOrInstantiation(NamedArgumentInvocation invocation, 
            CallBuilder callBuilder,
            TransformedInvocationPrimary transformedPrimary) {
        JCExpression resultExpr = transformPositionalInvocationOrInstantiation(invocation, callBuilder, transformedPrimary);
        // apply the default parameters
        if (invocation.getVars() != null && !invocation.getVars().isEmpty()) {
            if ((invocation.getReturnType() == null 
                    || Decl.isUnboxedVoid(invocation.getPrimaryDeclaration()))
                    && !Decl.isMpl((Functional) invocation.getPrimaryDeclaration())) {
                // void methods get wrapped like (let $arg$1=expr, $arg$0=expr in call($arg$0, $arg$1); null)
                resultExpr = make().LetExpr( 
                        invocation.getVars().append(make().Exec(resultExpr)).toList(), 
                        makeNull());
            } else {
                // all other methods like (let $arg$1=expr, $arg$0=expr in call($arg$0, $arg$1))
                resultExpr = make().LetExpr( 
                        invocation.getVars().toList(),
                        resultExpr);
            }
        }
        return resultExpr;
    }
    
    //
    // Invocations
    public void transformSuperInvocation(Tree.ExtendedType extendedType, ClassDefinitionBuilder classBuilder) {
        HasErrorException error = errors().getFirstExpressionErrorAndMarkBrokenness(extendedType);
        if (error != null) {
            classBuilder.getInitBuilder().delegateCall(this.makeThrowUnresolvedCompilationError(error));
            return;
        }
        if (extendedType.getInvocationExpression() != null 
                && extendedType.getInvocationExpression().getPositionalArgumentList() != null) {
            Declaration primaryDeclaration = ((Tree.MemberOrTypeExpression)extendedType.getInvocationExpression().getPrimary()).getDeclaration();
            java.util.List<ParameterList> paramLists = ((Functional)primaryDeclaration).getParameterLists();
            if(paramLists.isEmpty()){
                classBuilder.getInitBuilder().delegateCall(at(extendedType).Exec(makeErroneous(extendedType, "compiler bug: missing parameter list in extends clause: " + primaryDeclaration.getName() + " must be invoked")));
            } else {
                boolean prevFnCall = withinInvocation(true);
                try {
                    JCExpression superExpr = transformConstructorDelegation(extendedType, 
                            new CtorDelegation(null, primaryDeclaration), 
                            extendedType.getInvocationExpression(), classBuilder, false);
                    classBuilder.getInitBuilder().delegateCall(at(extendedType).Exec(superExpr));
                } finally {
                    withinInvocation(prevFnCall);
                }
            }
        }
    }

    /**
     * Transform a delegated constructor call ({@code extends XXX()})
     * which may be either a superclass initializer/constructor or a 
     * same-class constructor. 
     * @param extendedType
     * @param delegation The kind of delegation 
     * @param invocation
     * @param classBuilder
     * @return
     */
    JCExpression transformConstructorDelegation(Node extendedType,
            CtorDelegation delegation,
            Tree.InvocationExpression invocation, ClassDefinitionBuilder classBuilder, boolean forDelegationConstructor) {
        Declaration primaryDeclaration = ((Tree.MemberOrTypeExpression)invocation.getPrimary()).getDeclaration();
        java.util.List<ParameterList> paramLists = ((Functional)primaryDeclaration).getParameterLists();
        if(paramLists.isEmpty()){
            classBuilder.getInitBuilder().delegateCall(at(extendedType).Exec(makeErroneous(extendedType, "compiler bug: super class " + primaryDeclaration.getName() + " is missing parameter list")));
            return null;
        }
        SuperInvocation builder = new SuperInvocation(this,
                classBuilder.getForDefinition(),
                delegation, invocation,
                paramLists.get(0), forDelegationConstructor);
        
        CallBuilder callBuilder = CallBuilder.instance(this);
        boolean prevFnCall = withinInvocation(true);
        try {
            if (invocation.getPrimary() instanceof Tree.StaticMemberOrTypeExpression){
                transformTypeArguments(callBuilder, 
                        (Tree.StaticMemberOrTypeExpression)invocation.getPrimary());
            }
            at(builder.getNode());
            JCExpression expr = null;
            Scope outerDeclaration;
            if (Decl.isConstructor(primaryDeclaration)) {
                outerDeclaration= builder.getPrimaryDeclaration().getContainer().getContainer();
            } else {
                outerDeclaration= builder.getPrimaryDeclaration().getContainer();
            }
            
            if ((Strategy.generateInstantiator(builder.getPrimaryDeclaration())
                    || builder.getPrimaryDeclaration() instanceof Class)
                    && outerDeclaration instanceof Interface) {
                // If the subclass is inner to an interface then it will be 
                // generated inner to the companion and we need to qualify the 
                // super(), *unless* the subclass is nested within the same 
                // interface as it's superclass.
                Scope outer = builder.getSub().getContainer();
                while (!(outer instanceof Package)) {
                    if (outer == outerDeclaration) {
                        expr = naming.makeSuper();
                        break;
                    }
                    outer = outer.getContainer();
                }
                if (expr == null) {
                    if (delegation.isSelfDelegation()) {
                        throw new BugException();
                    }
                    Interface iface = (Interface)outerDeclaration;
                    JCExpression superQual;
                    if (Decl.getClassOrInterfaceContainer(classBuilder.getForDefinition(), false) instanceof Interface) {
                        superQual = naming.makeCompanionAccessorCall(naming.makeQuotedThis(), iface);
                    } else {
                        superQual = naming.makeCompanionFieldName(iface);
                    }
                    expr = naming.makeQualifiedSuper(superQual);
                }
            } else {
                expr = delegation.isSelfDelegation() ? naming.makeThis() : naming.makeSuper();
            }
            final List<JCExpression> superArguments = transformSuperInvocationArguments(
                    classBuilder, builder, callBuilder);
            JCExpression superExpr = callBuilder.invoke(expr)
                .arguments(superArguments)
                .build();
            return superExpr;
            //classBuilder.getInitBuilder().superCall(at(extendedType).Exec(superExpr));
        } finally {
            withinInvocation(prevFnCall);
        }
    }

    /**
     * Transforms the arguments for the invocation of a superclass initializer 
     * (call to {@code super()}). 
     * 
     * This is complicated by the need to avoid 
     * #929, so when a backward branch is needed in the evaluation of any 
     * argument expression we generate methods on the companion class 
     * (one for each argument) to evaluate the arguments so that the uninitialized 
     * {@code this} is not on the operand stack. 
     */
    private List<JCExpression> transformSuperInvocationArguments(
            ClassDefinitionBuilder classBuilder, SuperInvocation invocation, CallBuilder callBuilder) {
        // We could create a TransformedPrimary(expr, "super") here if needed
        List<ExpressionAndType> superArgumentsAndTypes = transformArgumentList(invocation, null, callBuilder);
        final List<JCExpression> superArguments = ExpressionAndType.toExpressionList(superArgumentsAndTypes);
        return superArguments;
    }
    
    public JCExpression transform(Tree.InvocationExpression ce) {
        JCExpression ret = checkForInvocationExpressionOptimisation(ce);
        if(ret != null)
            return ret;
        
        Tree.Term primary = Decl.unwrapExpressionsUntilTerm(ce.getPrimary());
        Declaration primaryDeclaration = null;
        Reference producedReference = null;
        if (primary instanceof Tree.MemberOrTypeExpression) {
            producedReference = ((Tree.MemberOrTypeExpression)primary).getTarget();
            primaryDeclaration = ((Tree.MemberOrTypeExpression)primary).getDeclaration();
        }
        Invocation invocation;
        if (ce.getPositionalArgumentList() != null) {
            if ((isIndirectInvocation(ce)
                    || isWithinDefaultParameterExpression(primaryDeclaration.getContainer()))
                    && !Decl.isJavaStaticPrimary(ce.getPrimary())){
                // indirect invocation
                invocation = new IndirectInvocation(this, 
                        primary, primaryDeclaration,
                        ce);
            } else {
                // direct invocation
                java.util.List<Parameter> parameters = ((Functional)primaryDeclaration).getFirstParameterList().getParameters();
                invocation = new PositionalInvocation(this, 
                        primary, primaryDeclaration,producedReference,
                        ce,
                        parameters);
            }
        } else if (ce.getNamedArgumentList() != null) {
            invocation = new NamedArgumentInvocation(this, 
                    primary, 
                    primaryDeclaration,
                    producedReference,
                    ce);
        } else {
            return makeErroneous(ce, "no arguments");
        }
        return transformInvocation(invocation);
    }

    public JCExpression transformFunctional(Tree.StaticMemberOrTypeExpression expr,
            Functional functional) {
        return CallableBuilder.methodReference(gen(), expr, 
                    functional.getFirstParameterList());
    }

    //
    // Member expressions

    public static interface TermTransformer {
        JCExpression transform(JCExpression primaryExpr, String selector);
    }

    // Qualified members
    
    public JCExpression transform(Tree.QualifiedMemberExpression expr) {
        // check for an optim
        JCExpression ret = checkForQualifiedMemberExpressionOptimisation(expr);
        if(ret != null)
            return ret;
        if (expr.getPrimary() instanceof Tree.BaseTypeExpression) {
            Tree.BaseTypeExpression primary = (Tree.BaseTypeExpression)expr.getPrimary();
            return transformMemberReference(expr, primary);
        } else if (expr.getPrimary() instanceof Tree.QualifiedTypeExpression) {
            Tree.QualifiedTypeExpression primary = (Tree.QualifiedTypeExpression)expr.getPrimary();
            return transformMemberReference(expr, primary);
        }
        return transform(expr, null);
    }

    JCExpression transformMemberReference(
            Tree.QualifiedMemberOrTypeExpression expr,
            Tree.MemberOrTypeExpression primary) {
        Declaration member = expr.getDeclaration();
        Type qualifyingType = primary.getTypeModel();
        Tree.TypeArguments typeArguments = expr.getTypeArguments();
        boolean prevSyntheticClassBody = withinSyntheticClassBody(true);
        try {
            if (member.isStaticallyImportable()) {
                if (member instanceof Function) {
                    Function method = (Function)member;
                    Reference producedReference = method.appliedReference(qualifyingType, typeArguments.getTypeModels());
                    return CallableBuilder.javaStaticMethodReference(
                            gen(), 
                            expr.getTypeModel(), 
                            method, 
                            producedReference).build();
                } else if (member instanceof FieldValue) {
                    return naming.makeName(
                            (TypedDeclaration)member, Naming.NA_FQ | Naming.NA_WRAPPER_UNQUOTED);
                } else if (member instanceof Value) {
                    CallBuilder callBuilder = CallBuilder.instance(this);
                    JCExpression qualExpr = naming.makeTypeDeclarationExpression(null, (TypeDeclaration)member.getContainer(), DeclNameFlag.QUALIFIED);
                    callBuilder.invoke(naming.makeQualifiedName(qualExpr, (TypedDeclaration)member, Naming.NA_GETTER | Naming.NA_MEMBER));
                    return callBuilder.build();
                } else if (member instanceof Class) {
                    Reference producedReference = expr.getTarget();
                    return CallableBuilder.javaStaticMethodReference(
                            gen(), 
                            expr.getTypeModel(), 
                            (Class)member, 
                            producedReference).build();
                }
            } 
            if (member instanceof Value) {
                if (expr.getStaticMethodReference()
                        && Decl.isEnumeratedConstructor((Value)member)) {
                    CallBuilder callBuilder = CallBuilder.instance(this);
                    JCExpression qualExpr;
                    Class class1 = (Class) member.getContainer();
                    if (class1.isToplevel()) {
                        qualExpr = naming.makeTypeDeclarationExpression(null, (TypeDeclaration)member.getContainer(), DeclNameFlag.QUALIFIED);
                        callBuilder.invoke(naming.makeQualifiedName(qualExpr, (TypedDeclaration)member, Naming.NA_GETTER | Naming.NA_MEMBER));
                    } else if (class1.isMember()){
                        // Stef: this is fugly but I couldn't find better. This makes sure that Outer.Inner.enumeratedConstructor
                        // creates a Callable<Outer.Inner,[Outer]> that returns the enumeratedConstructor given an outer instance
                        if(primary instanceof Tree.QualifiedMemberOrTypeExpression
                                && ((Tree.QualifiedMemberOrTypeExpression) primary).getPrimary() instanceof Tree.BaseTypeExpression)
                            return CallableBuilder.unboundValueMemberReference(
                                gen(),
                                expr,
                                expr.getTypeModel(), 
                                ((TypedDeclaration)member)).build();
                        else{
                            qualExpr = primary instanceof Tree.QualifiedMemberOrTypeExpression ? transformExpression(((Tree.QualifiedMemberOrTypeExpression)primary).getPrimary()) : null;
                            callBuilder.invoke(naming.makeQualifiedName(qualExpr, (TypedDeclaration)member, Naming.NA_GETTER | Naming.NA_MEMBER));
                        }
                    } else {
                        callBuilder.fieldRead(naming.makeName((TypedDeclaration)member, Naming.NA_IDENT));
                    }
                    
                    return callBuilder.build();
                } else {
                    return CallableBuilder.unboundValueMemberReference(
                            gen(),
                            expr,
                            expr.getTypeModel(), 
                            ((TypedDeclaration)member)).build();
                }
            } else if (Decl.isConstructor(member)) {
                Reference producedReference = expr.getTarget();
                return CallableBuilder.unboundFunctionalMemberReference(
                        gen(), 
                        expr,
                        expr.getTypeModel(), 
                        Decl.getConstructor(member), 
                        producedReference).build();
            } else if (member instanceof Function) {
                Function method = (Function)member;
                if (!method.isParameter()) {
                    Reference producedReference = method.appliedReference(qualifyingType, typeArguments.getTypeModels());
                    return CallableBuilder.unboundFunctionalMemberReference(
                            gen(), 
                            expr,
                            expr.getTypeModel(), 
                            method, 
                            producedReference).build();
                } else {
                    Reference producedReference = method.appliedReference(qualifyingType, typeArguments.getTypeModels());
                    return CallableBuilder.unboundFunctionalMemberReference(
                            gen(), 
                            expr,
                            expr.getTypeModel(), 
                            method, 
                            producedReference).build();
                }
            } else if (member instanceof Class) {
                Reference producedReference = expr.getTarget();
                return CallableBuilder.unboundFunctionalMemberReference(
                        gen(), 
                        expr,
                        expr.getTypeModel(), 
                        (Class)member, 
                        producedReference).build();
            } else {
                return makeErroneous(expr, "compiler bug: member reference of " + expr + " not supported yet");
            }
        } finally {
            withinSyntheticClassBody(prevSyntheticClassBody);
        }
    }
    
    private JCExpression transform(Tree.QualifiedMemberExpression expr, TermTransformer transformer) {
        JCExpression result;
        if (expr.getMemberOperator() instanceof Tree.SafeMemberOp) {
            Naming.SyntheticName tmpVarName = naming.alias("safe");
            JCExpression typeExpr = makeJavaType(expr.getTarget().getQualifyingType(), JT_NO_PRIMITIVES);
            JCExpression transExpr = transformMemberExpression(expr, tmpVarName.makeIdent(), transformer);
            if (isFunctionalResult(expr.getTypeModel())) {
                return transExpr;
            }
            // the marker we get for boxing on a QME with a SafeMemberOp is always unboxed
            // since it returns an optional type, but that doesn't tell us if the underlying
            // expr is or not boxed
            boolean isBoxed = !CodegenUtil.isUnBoxed((TypedDeclaration)expr.getDeclaration());
            transExpr = boxUnboxIfNecessary(transExpr, isBoxed, expr.getTarget().getType(), BoxingStrategy.BOXED);
            JCExpression testExpr = make().Binary(JCTree.NE, tmpVarName.makeIdent(), makeNull());
            JCExpression condExpr = make().Conditional(testExpr, transExpr, makeNull());
            JCExpression primaryExpr = transformQualifiedMemberPrimary(expr);
            result = makeLetExpr(tmpVarName, null, typeExpr, primaryExpr, condExpr);
        } else if (expr.getMemberOperator() instanceof Tree.SpreadOp) {
            result = transformSpreadOperator(expr, transformer);
        } else {
            JCExpression primaryExpr = transformQualifiedMemberPrimary(expr);
            result = transformMemberExpression(expr, primaryExpr, transformer);
        }
        return result;
    }

    
    
    private JCExpression transformSpreadOperator(final Tree.QualifiedMemberExpression expr, TermTransformer transformer) {
        at(expr);
        
        boolean spreadMethodReferenceOuter = !expr.equals(this.spreading) && !isWithinInvocation() && isCeylonCallableSubtype(expr.getTypeModel());
        boolean spreadMethodReferenceInner = expr.equals(this.spreading) && isWithinInvocation();
        Tree.QualifiedMemberExpression oldSpreading = spreading;
        if (spreadMethodReferenceOuter) {
            spreading = expr;
        }
        try {
            Naming.SyntheticName varBaseName = naming.alias("spread");
            ListBuffer<JCStatement> letStmts = ListBuffer.<JCStatement>lb();
            final Naming.SyntheticName srcIterableName;
            if (spreadMethodReferenceInner) {
                // use the var we initialized in the outer
                srcIterableName = this.memberPrimary;
            } else {
                srcIterableName = varBaseName.suffixedBy(Suffix.$iterable$);
            }
            if (spreadMethodReferenceOuter) {
                // if we're in the outer, note then name of the var for use in the inner.
                this.memberPrimary = srcIterableName;
            }
            Naming.SyntheticName srcIteratorName = varBaseName.suffixedBy(Suffix.$iterator$);
            Type srcElementType = expr.getTarget().getQualifyingType();
            JCExpression srcIterableTypeExpr = makeJavaType(typeFact().getIterableType(srcElementType), JT_NO_PRIMITIVES);
            JCExpression srcIterableExpr;
            boolean isSuperOrSuperOf = false;
            if (spreadMethodReferenceInner) {
                srcIterableExpr = srcIterableName.makeIdent();
            } else {
                boolean isSuper = isSuper(expr.getPrimary());
                isSuperOrSuperOf = isSuper || isSuperOf(expr.getPrimary());
                if(isSuperOrSuperOf){
                    // in this case we can't capture the iterable because it may be a mixin impl class, but it's constant
                    // so we just refer to it later
                    if(isSuper){
                        Declaration member = expr.getPrimary().getTypeModel().getDeclaration().getMember("iterator", null, false);
                        srcIterableExpr = transformSuper(expr, (TypeDeclaration) member.getContainer());
                    }else
                        srcIterableExpr = transformSuperOf(expr, expr.getPrimary(), "iterator");
                }else{
                    srcIterableExpr = transformExpression(expr.getPrimary(), BoxingStrategy.BOXED, typeFact().getIterableType(srcElementType));
                }
            }
            // do not capture the iterable for super invocations: see above
            if (!spreadMethodReferenceInner && !isSuperOrSuperOf) {
                JCVariableDecl srcIterable = null;
                srcIterable = makeVar(Flags.FINAL, srcIterableName, srcIterableTypeExpr, srcIterableExpr);
                letStmts.prepend(srcIterable);
            }
            Type resultElementType = expr.getTarget().getType();
            Type resultAbsentType = typeFact().getIteratedAbsentType(expr.getPrimary().getTypeModel());
            
            // private Iterator<srcElementType> iterator = srcIterableName.iterator();
            JCVariableDecl srcIterator = makeVar(Flags.FINAL, srcIteratorName, makeJavaType(typeFact().getIteratorType(srcElementType)), 
                    make().Apply(null,
                            // for super we do not capture it because we can't and it's constant anyways
                            naming.makeQualIdent(isSuperOrSuperOf ? srcIterableExpr : srcIterableName.makeIdent(), "iterator"),
                            List.<JCExpression>nil()));
            
            Naming.SyntheticName iteratorResultName = varBaseName.suffixedBy(Suffix.$element$);
            /* public Object next() {
             *     Object result;
             *     if (!((result = iterator.next()) instanceof Finished)) {
             *         result = transformedMember(result);
             *     }
             *     return result;
             */
            
            /* Any arguments in the member of the spread would get re-evaluated on each iteration
             * so we need to shift them to the scope of the Let to ensure they're evaluated once. 
             */
            boolean aliasArguments = (transformer instanceof InvocationTermTransformer)
                    && ((InvocationTermTransformer)transformer).invocation.getNode() instanceof Tree.InvocationExpression
                    && ((Tree.InvocationExpression)((InvocationTermTransformer)transformer).invocation.getNode()).getPositionalArgumentList() != null;
            if (aliasArguments) {
                ((InvocationTermTransformer)transformer).callBuilder.argumentHandling(
                        CallBuilder.CB_ALIAS_ARGS, varBaseName);
            }
            
            JCNewClass iterableClass;
            boolean prevSyntheticClassBody = expressionGen().withinSyntheticClassBody(true);
            try {
                JCExpression transformedElement = applyErasureAndBoxing(iteratorResultName.makeIdent(), typeFact().getAnythingType(), CodegenUtil.hasTypeErased(expr.getPrimary()),
                        true, BoxingStrategy.BOXED, 
                        srcElementType, 0);
                transformedElement = transformMemberExpression(expr, transformedElement, transformer);
                
                // This short-circuit is here for spread invocations
                // The code has been called recursively and the part after this if-statement will
                // be handled by the previous recursion
                if (spreadMethodReferenceOuter) {
                    return make().LetExpr(letStmts.toList(), transformedElement);
                }
                
                transformedElement = applyErasureAndBoxing(transformedElement, resultElementType, 
                        // don't trust the erased flag of expr, as it reflects the result type of the overall spread expr,
                        // not necessarily of the applied member
                        CodegenUtil.hasTypeErased((TypedDeclaration)expr.getTarget().getDeclaration()), 
                        !CodegenUtil.isUnBoxed(expr), BoxingStrategy.BOXED, resultElementType, 0);
                
                MethodDefinitionBuilder nextMdb = MethodDefinitionBuilder.systemMethod(this, "next");
                nextMdb.isOverride(true);
                nextMdb.annotationFlags(Annotations.IGNORE);
                nextMdb.modifiers(Flags.PUBLIC | Flags.FINAL);
                nextMdb.resultType(null, make().Type(syms().objectType));
                nextMdb.body(List.of(
                        makeVar(iteratorResultName, 
                            make().Type(syms().objectType), null),
                        make().If(
                                make().Unary(JCTree.NOT, 
                                make().TypeTest(make().Assign(
                                        iteratorResultName.makeIdent(), 
                                        make().Apply(null,
                                                naming.makeQualIdent(srcIteratorName.makeIdent(), "next"),
                                                List.<JCExpression>nil())), 
                                        make().Type(syms().ceylonFinishedType))), 
                                make().Block(0, List.<JCStatement>of(make().Exec(make().Assign(iteratorResultName.makeIdent(), 
                                        transformedElement)))), 
                                null),
                        make().Return(iteratorResultName.makeIdent())));
                JCMethodDecl nextMethod = nextMdb.build();
                
                // new AbstractIterator()
                JCNewClass iteratorClass = make().NewClass(null, 
                        null, 
                        make().TypeApply(make().QualIdent(syms().ceylonAbstractIteratorType.tsym),
                                List.of(makeJavaType(resultElementType, JT_TYPE_ARGUMENT))),
                        List.of(makeReifiedTypeArgument(resultElementType)),
                        make().AnonymousClassDef(make().Modifiers(0), List.of(srcIterator, nextMethod)));
                        
                MethodDefinitionBuilder iteratorMdb = MethodDefinitionBuilder.systemMethod(this, "iterator");
                iteratorMdb.isOverride(true);
                iteratorMdb.annotationFlags(Annotations.IGNORE);
                iteratorMdb.modifiers(Flags.PUBLIC | Flags.FINAL);
                iteratorMdb.resultType(null, makeJavaType(typeFact().getIteratorType(resultElementType))); 
                iteratorMdb.body(make().Return(iteratorClass));
                        
                // new AbstractIterable()
                iterableClass = make().NewClass(null, 
                        null, 
                        make().TypeApply(make().QualIdent(syms().ceylonAbstractIterableType.tsym),
                                List.of(makeJavaType(resultElementType, JT_TYPE_ARGUMENT), makeJavaType(resultAbsentType, JT_TYPE_ARGUMENT))),
                        List.of(makeReifiedTypeArgument(resultElementType), makeReifiedTypeArgument(resultAbsentType)), 
                        make().AnonymousClassDef(make().Modifiers(0), List.<JCTree>of(iteratorMdb.build())));
            } finally {
                expressionGen().withinSyntheticClassBody(prevSyntheticClassBody);
            }
            
            if (aliasArguments) {
                letStmts = letStmts.appendList(((InvocationTermTransformer)transformer).callBuilder.getStatements());
            }
            
            JCMethodInvocation result = make().Apply(null, 
                    naming.makeQualIdent(iterableClass, "sequence"),
                    List.<JCExpression>nil());
            JCExpression spread = letStmts.isEmpty() ? result : make().LetExpr(letStmts.toList(), result);
            
            // Do we *statically* know the result must be a Sequence 
            final boolean primaryIsSequence = typeFact().isNonemptyIterableType(expr.getPrimary().getTypeModel());
            Type returnElementType = expr.getTarget().getType();
            if(primaryIsSequence){
                int flags = EXPR_DOWN_CAST;
                spread = applyErasureAndBoxing(spread, 
                        typeFact().getSequentialType(returnElementType),
                        false,
                        true,
                        BoxingStrategy.BOXED, 
                        primaryIsSequence ? 
                                typeFact().getSequenceType(returnElementType) 
                                : typeFact().getSequentialType(returnElementType),
                                flags);
            }
            return spread;
        } finally {
            spreading = oldSpreading;
        }
        
    }

    JCExpression transformQualifiedMemberPrimary(Tree.QualifiedMemberOrTypeExpression expr) {
        if(expr.getTarget() == null)
            return makeErroneous(expr, "compiler bug: "
                    // make sure we don't die of a missing declaration too
                    + (expr.getDeclaration() != null ? expr.getDeclaration().getName() : expr)
                    + " has a null target");

        // do not consider the primary to be an invocation since in foo.x() we're invoking x, not foo.
        boolean previousWithinInvocation = withinInvocation(false);
        try{
            // consider package qualifiers as non-prefixed, we always qualify them anyways, this is
            // only useful for the typechecker resolving
            
            Tree.Primary primary = expr.getPrimary();
            if (Decl.isConstructor(expr.getDeclaration())) {
                Constructor ctor = Decl.getConstructor(expr.getDeclaration());
                if (primary instanceof Tree.QualifiedMemberOrTypeExpression) {
                    // foo.Class.Ctor => foo
                    primary = ((Tree.QualifiedMemberOrTypeExpression)primary).getPrimary();
                } else if (primary instanceof Tree.BaseMemberOrTypeExpression) {
                    // Class.Ctor => null
                    return null;
                }
            }
            if(isPackage(primary))
                return null;
            
            Type type = expr.getTarget().getQualifyingType();
            if(expr.getMemberOperator() instanceof Tree.SafeMemberOp && !isOptional(type)){
                Type optionalType = typeFact().getOptionalType(type);
                optionalType.setUnderlyingType(type.getUnderlyingType());
                type = optionalType;
            }
            BoxingStrategy boxing = expr.getMemberOperator() instanceof Tree.SafeMemberOp == false 
                    && Decl.isValueTypeDecl(primary)
                    && CodegenUtil.isUnBoxed(primary)
                    ? BoxingStrategy.UNBOXED : BoxingStrategy.BOXED;
            JCExpression result;
            if (isSuper(primary)) {
                result = transformSuper(expr);
            } else if (isSuperOf(primary)) {
                result = transformSuperOf(expr, expr.getPrimary(), expr.getDeclaration().getName());
            } else if (isThis(primary)
                    && !expr.getDeclaration().isCaptured() 
                    && !expr.getDeclaration().isShared()
                    && Decl.getDeclarationScope(expr.getScope()) instanceof Constructor) {
                result = null;
            } else if (Decl.isJavaStaticPrimary(primary)) {
                // Java static field or method access
                result = transformJavaStaticMember((Tree.QualifiedMemberOrTypeExpression)primary, expr.getTypeModel());
            } else {
                result = transformExpression(primary, boxing, type);
            }
            return result;
        }finally{
            withinInvocation(previousWithinInvocation);
        }
    }

    private JCExpression transformJavaStaticMember(Tree.QualifiedMemberOrTypeExpression qmte, Type staticType) {
        Declaration decl = qmte.getDeclaration();
        if (decl instanceof FieldValue) {
            Value member = (Value)decl;
            return naming.makeName(member, Naming.NA_FQ | Naming.NA_WRAPPER_UNQUOTED);
        } else if (decl instanceof Value) {
            Value member = (Value)decl;
            CallBuilder callBuilder = CallBuilder.instance(this);
            Type qualifyingType = ((TypeDeclaration)member.getContainer()).getType();
            callBuilder.invoke(naming.makeQualifiedName(
                    makeJavaType(qualifyingType, JT_RAW | JT_NO_PRIMITIVES),
                    member, 
                    Naming.NA_GETTER | Naming.NA_MEMBER));
            return utilInvocation().checkNull(callBuilder.build());
        } else if (decl instanceof Function) {
            Function method = (Function)decl;
            final ParameterList parameterList = method.getFirstParameterList();
            Type qualifyingType = qmte.getPrimary().getTypeModel();
            Tree.TypeArguments typeArguments = qmte.getTypeArguments();
            Reference producedReference = method.appliedReference(qualifyingType, typeArguments.getTypeModels());
            return utilInvocation().checkNull(makeJavaStaticInvocation(gen(),
                    method, producedReference, parameterList));
        } else if (decl instanceof Class) {
            Class class_ = (Class)decl;
            if (class_.isStaticallyImportable()) {
                return naming.makeTypeDeclarationExpression(null, class_, Naming.DeclNameFlag.QUALIFIED);
            } else {
                final ParameterList parameterList = class_.getFirstParameterList();
                Reference producedReference = qmte.getTarget();
                return utilInvocation().checkNull(makeJavaStaticInvocation(gen(),
                        class_, producedReference, parameterList));
            }
        } else {
            return makeErroneous(qmte, "compiler bug: unsupported static");
        }
    }

    JCExpression makeJavaStaticInvocation(CeylonTransformer gen,
            final Functional methodOrClass,
            Reference producedReference,
            final ParameterList parameterList) {
        CallBuilder callBuilder = CallBuilder.instance(gen);
        if (methodOrClass instanceof Function) {
            callBuilder.invoke(gen.naming.makeName(
                    (Function)methodOrClass, Naming.NA_FQ | Naming.NA_WRAPPER_UNQUOTED));
        } else if (methodOrClass instanceof Class) {
            callBuilder.instantiate(
                    gen.makeJavaType(((Class)methodOrClass).getType(), JT_RAW | JT_NO_PRIMITIVES));
        }
        ListBuffer<ExpressionAndType> reified = ListBuffer.lb();
        
        DirectInvocation.addReifiedArguments(gen, producedReference, reified);
        for (ExpressionAndType reifiedArgument : reified) {
            callBuilder.argument(reifiedArgument.expression);
        }
        
        for (Parameter parameter : parameterList.getParameters()) {
            callBuilder.argument(gen.naming.makeQuotedIdent(parameter.getName()));
        }
        JCExpression innerInvocation = callBuilder.build();
        return innerInvocation;
    }
    
    /**
     * Removes the parentheses from the given term
     */
    static Tree.Term eliminateParens(Tree.Term term) {
        while (term instanceof Tree.Expression) {
            term = ((Tree.Expression) term).getTerm();
        }
        return term;
    }
    
    private static boolean isThis(Tree.Primary primary) {
        return eliminateParensAndWidening(primary) instanceof Tree.This;
    }
    
    static boolean isPackage(Tree.Primary primary) {
        return eliminateParens(primary) instanceof Tree.Package;
    }
    
    static boolean isPackageQualified(Tree.QualifiedMemberOrTypeExpression qmte) {
        return isPackage(qmte.getPrimary());
    }
    
    /** 
     * Is the given primary a {@code super of Foo}
     * expression (modulo parentheses and multiple {@code of} 
     */
    private static boolean isSuperOf(Tree.Primary primary) {
        return primary instanceof Tree.Expression
                && eliminateParensAndWidening(((Tree.Expression)primary).getTerm()) instanceof Tree.Super;
    }
    
    /** 
     * Is the given primary a {@code super} expression
     * (modulo parentheses)
     */
    private static boolean isSuper(Tree.Primary primary) {
        return eliminateParens(primary) instanceof Tree.Super;
    }
    
    /** 
     * Is the given primary a {@code super} or {@code super of Foo} 
     * expression (modulo parentheses and multiple {@code of}
     */
    static boolean isSuperOrSuperOf(Tree.Primary primary) {
        return isSuper(primary) || isSuperOf(primary);
    }
    
    private JCExpression transformSuperOf(Node node, Tree.Primary superPrimary, String forMemberName) {
        Tree.Term superOf = eliminateParens(superPrimary);
        if (!(superOf instanceof Tree.OfOp)) {
            throw new BugException();
        }
        Tree.Type superType = ((Tree.OfOp)superOf).getType();
        if (!(eliminateParens(((Tree.OfOp)superOf).getTerm()) instanceof Tree.Super)) {
            throw new BugException();
        }
        TypeDeclaration inheritedFrom = superType.getTypeModel().getDeclaration();
        if (inheritedFrom instanceof Interface) {
            inheritedFrom = (TypeDeclaration)inheritedFrom.getMember(forMemberName, null, false).getContainer();
        }
        return widenSuper(node, inheritedFrom);
    }

    private JCExpression widenSuper(
            Node superOfQualifiedExpr,
            TypeDeclaration inheritedFrom) {
        JCExpression result;
        if (inheritedFrom instanceof Class) {
            if(isWithinSyntheticClassBody()){
                // super refers to the closest ClassOrInterface
                Scope scope = superOfQualifiedExpr.getScope();
                while (!(scope instanceof Package)) {
                    if (scope instanceof ClassOrInterface) {
                        break;
                    }
                    scope = scope.getContainer();
                }
                if(scope instanceof ClassOrInterface)
                    result = naming.makeQualifiedSuper(makeJavaType(((ClassOrInterface) scope).getType(), JT_RAW));
                else
                    result = naming.makeSuper();
            }else{
                result = naming.makeSuper();
            }
        } else if (inheritedFrom instanceof Interface) {
            Interface iface = (Interface)inheritedFrom;
            JCExpression qualifier = null;
            if (needDollarThis(superOfQualifiedExpr.getScope())) {
                qualifier = naming.makeQuotedThis();
                if (iface.equals(typeFact().getIdentifiableDeclaration())) {
                    result = naming.makeQualifiedSuper(qualifier);
                } else {
                    result = naming.makeCompanionAccessorCall(qualifier, iface);
                }
            } else {
                if (iface.equals(typeFact().getIdentifiableDeclaration())) {
                    result = naming.makeQualifiedSuper(qualifier);
                } else {
                    result = naming.makeCompanionFieldName(iface);
                }
            }
        } else {
            result = makeErroneous(superOfQualifiedExpr, "compiler bug: " + (inheritedFrom == null ? "null" : inheritedFrom.getClass().getName()) + " is an unhandled case in widen()");
        }
        return result;
    }

    public JCExpression transformSuper(Tree.QualifiedMemberOrTypeExpression expression) {
        TypeDeclaration inheritedFrom = (TypeDeclaration)expression.getDeclaration().getContainer();
        return transformSuper(expression, inheritedFrom);
    }
    
    public JCExpression transformSuper(Node node, TypeDeclaration superDeclaration) {
        return widenSuper(node, superDeclaration);
    }
    
    // Base members
    
    public JCExpression transform(Tree.BaseMemberExpression expr) {
        return transform(expr, null);
    }

    private JCExpression transform(Tree.BaseMemberOrTypeExpression expr, TermTransformer transformer) {
        return transformMemberExpression(expr, null, transformer);
    }

    // Type members
    
    public JCExpression transform(Tree.QualifiedTypeExpression expr) {
        if (expr.getPrimary() instanceof Tree.BaseTypeExpression) {
            Tree.BaseTypeExpression primary = (Tree.BaseTypeExpression)expr.getPrimary();
            return transformMemberReference(expr, primary);            
        } else if (expr.getPrimary() instanceof Tree.QualifiedTypeExpression) {
            Tree.QualifiedTypeExpression primary = (Tree.QualifiedTypeExpression)expr.getPrimary();
            return transformMemberReference(expr, primary);
        }
        return transform(expr, null);
    }
    
    public JCExpression transform(Tree.BaseTypeExpression expr) {
        return transform(expr, null);
    }
    
    private JCExpression transform(Tree.QualifiedTypeExpression expr, TermTransformer transformer) {
        JCExpression primaryExpr = transformQualifiedMemberPrimary(expr);
        return transformMemberExpression(expr, primaryExpr, transformer);
    }
    
    // Generic code for all primaries
    
    public JCExpression transformTermForInvocation(Tree.Term term, TermTransformer transformer) {
        if (term instanceof Tree.QualifiedMemberExpression) {
            return transform((Tree.QualifiedMemberExpression)term, transformer);
        } else if (term instanceof Tree.BaseMemberExpression) {
            return transform((Tree.BaseMemberExpression)term, transformer);
        } else if (term instanceof Tree.BaseTypeExpression) {
            return transform((Tree.BaseTypeExpression)term, transformer);
        } else if (term instanceof Tree.QualifiedTypeExpression) {
            return transform((Tree.QualifiedTypeExpression)term, transformer);
        } else {
            // do not consider our term to be part of an invocation, we want it to be a Callable
            boolean oldWi = withinInvocation(false);
            JCExpression primaryExpr;
            try{
                primaryExpr = transformExpression(term);
                if (transformer != null) {
                    primaryExpr = transformer.transform(primaryExpr, null);
                }
            }finally{
                withinInvocation(oldWi);
            }
            return primaryExpr;
        }
    }
    
    private JCExpression transformMemberExpression(Tree.StaticMemberOrTypeExpression expr, JCExpression primaryExpr, TermTransformer transformer) {
        JCExpression result = null;

        // do not throw, an error will already have been reported
        Declaration decl = expr.getDeclaration();
        if (decl == null) {
            return makeErroneous(expr, "compiler bug: expression with no declaration");
        }
        
        // Try to find the original declaration, in case we have conditionals that refine the type of objects without us
        // creating a tmp variable (in which case we have a substitution for it)
        while(decl instanceof TypedDeclaration){
            TypedDeclaration typedDecl = (TypedDeclaration) decl;
            if(!naming.isSubstituted(decl) && typedDecl.getOriginalDeclaration() != null){
                decl = ((TypedDeclaration) decl).getOriginalDeclaration();
            }else{
                break;
            }
        }
        
        // Make sure we're using the correct declaration in case of natives
        // (the header might look like a field while the implementation is a getter)
        if (decl.isNativeHeader()) {
            Declaration d = ModelUtil.getNativeDeclaration(decl, Backend.Java);
            if (d != null) {
                decl = d;
            }
        }
        
        // Explanation: primaryExpr and qualExpr both specify what is to come before the selector
        // but the important difference is that primaryExpr is used for those situations where
        // the result comes from the actual Ceylon code while qualExpr is used for those situations
        // where we need to refer to synthetic objects (like wrapper classes for toplevel methods)
        
        JCExpression qualExpr = null;
        String selector = null;
        // true for Java interop using fields, and for super constructor parameters, which must use
        // parameters rather than getter methods
        boolean mustUseField = false;
        // true for default parameter methods
        boolean mustUseParameter = false;
        if (decl instanceof Functional
                && (!(decl instanceof Class) || ((Class)decl).getParameterList() != null)
                && (!(decl instanceof Function) || !decl.isParameter() 
                        || functionalParameterRequiresCallable((Function)decl, expr)) 
                && isFunctionalResult(expr.getTypeModel())) {
            result = transformFunctional(expr, (Functional)decl);
        } else if (Decl.isGetter(decl)) {
            // invoke the getter
            if (decl.isToplevel()) {
                primaryExpr = null;
                qualExpr = naming.makeName((Value)decl, Naming.NA_FQ | Naming.NA_WRAPPER | Naming.NA_MEMBER);
                selector = null;
            } else if (Decl.withinClassOrInterface(decl) && !Decl.isLocalToInitializer(decl)) {
                selector = naming.selector((Value)decl);
            } else {
                // method local attr
                if (!isRecursiveReference(expr)) {
                    primaryExpr = naming.makeQualifiedName(primaryExpr, (Value)decl, Naming.NA_Q_LOCAL_INSTANCE);
                }
                selector = naming.selector((Value)decl);
            }
        } else if (Decl.isValueOrSharedOrCapturedParam(decl)) {
            if (decl.isToplevel()) {
                // ERASURE
                if (isNullValue(decl)) {
                    result = makeNull();
                } else if (isBooleanTrue(decl)) {
                    result = makeBoolean(true);
                } else if (isBooleanFalse(decl)) {
                    result = makeBoolean(false);
                } else {
                    // it's a toplevel attribute
                    primaryExpr = naming.makeName((TypedDeclaration)decl, Naming.NA_FQ | Naming.NA_WRAPPER);
                    selector = naming.selector((TypedDeclaration)decl);
                }
            } else if (Decl.isClassAttribute(decl) || Decl.isClassParameter(decl)) {
                mustUseField = Decl.isJavaField(decl)
                        || (isWithinSuperInvocation() 
                                && primaryExpr == null
                                && withinSuperInvocation == decl.getContainer());
                mustUseParameter = (primaryExpr == null && isWithinDefaultParameterExpression(decl.getContainer()));
                if (mustUseField || mustUseParameter){
                    if(decl instanceof FieldValue) {
                        selector = ((FieldValue)decl).getRealName();
                    } else if (isWithinSuperInvocation()
                            && ((Value)decl).isVariable()
                            && ((Value)decl).isCaptured()) {
                        selector = Naming.getAliasedParameterName(((Value)decl).getInitializerParameter());
                    } else {
                        selector = decl.getName();
                    }
                } else {
                    // invoke the getter, using the Java interop form of Util.getGetterName because this is the only case
                    // (Value inside a Class) where we might refer to JavaBean properties
                    selector = naming.selector((TypedDeclaration)decl);
                }
            } else if (decl.isCaptured() || decl.isShared()) {
                TypedDeclaration typedDecl = ((TypedDeclaration)decl);
                TypeDeclaration typeDecl = typedDecl.getType().getDeclaration();
                mustUseField = Decl.isBoxedVariable((TypedDeclaration)decl);
                if (Decl.isLocalNotInitializer(typeDecl)
                        && typeDecl.isAnonymous()
                        // we need the box if it's a captured object
                        && !typedDecl.isSelfCaptured()) {
                    // accessing a local 'object' declaration, so don't need a getter 
                } else if (decl.isCaptured() 
                        && !((TypedDeclaration) decl).isVariable()
                        // captured objects are never variable but need the box
                        && !typedDecl.isSelfCaptured()) {
                    // accessing a local that is not getter wrapped
                } else {
                    primaryExpr = naming.makeQualifiedName(primaryExpr, (TypedDeclaration)decl, Naming.NA_Q_LOCAL_INSTANCE);
                    selector = naming.selector((TypedDeclaration)decl);
                }
            }
        } else if (Decl.isMethodOrSharedOrCapturedParam(decl)) {
            mustUseParameter = (primaryExpr == null
                    && decl.isParameter()
                    && isWithinDefaultParameterExpression(decl.getContainer()));
            if (!decl.isParameter()
                    && (Decl.isLocalNotInitializer(decl) || (Decl.isLocalToInitializer(decl) && ((Function)decl).isDeferred()))) {
                primaryExpr = null;
                int flags = Naming.NA_MEMBER;
                if (!isRecursiveReference(expr)) {
                    // Only want to quote the method name 
                    // e.g. enum.$enum()
                    flags |= Naming.NA_WRAPPER_UNQUOTED;
                }else if(!isReferenceInSameScope(expr)){
                    // always qualify it with this
                    flags |= Naming.NA_WRAPPER | Naming.NA_WRAPPER_WITH_THIS;
                }
                qualExpr = naming.makeName((Function)decl, flags);
                selector = null;
            } else if (decl.isToplevel()) {
                primaryExpr = null;
                qualExpr = naming.makeName((Function)decl, Naming.NA_FQ | Naming.NA_WRAPPER | Naming.NA_MEMBER);
                selector = null;
            } else if (!isWithinInvocation()) {
                selector = null;
            } else {
                // not toplevel, not within method, must be a class member
                selector = naming.selector((Function)decl);
            }
        }
        boolean isCtor = decl instanceof Function && ((Function)decl).getTypeDeclaration() instanceof Constructor;
        if (result == null) {
            boolean useGetter = !(decl instanceof Function || isCtor) && !mustUseField && !mustUseParameter;
            if (qualExpr == null && selector == null
                    && !(isCtor)) {
                useGetter = Decl.isClassAttribute(decl) && CodegenUtil.isErasedAttribute(decl.getName());
                if (useGetter) {
                    selector = naming.selector((TypedDeclaration)decl);
                } else {
                    selector = naming.substitute(decl);
                }
            }
            
            if (qualExpr == null) {
                qualExpr = primaryExpr;
            }
            
            // FIXME: Stef has a strong suspicion that the four next methods
            // should be merged since they all add a this qualifier in different
            // cases
            if(!mustUseParameter){
                qualExpr = addQualifierForObjectMembersOfInterface(expr, decl, qualExpr);

                qualExpr = addInterfaceImplAccessorIfRequired(qualExpr, expr, decl);

                qualExpr = addThisOrObjectQualifierIfRequired(qualExpr, expr, decl);

                if (qualExpr == null && needDollarThis(expr)) {
                    qualExpr = makeQualifiedDollarThis((Tree.BaseMemberExpression)expr);
                }
            }
            
            if (qualExpr == null && decl.isStaticallyImportable()
                    // make sure we only do this for things contained in a type, as otherwise
                    // it breaks for qualified calls to static methods in interfaces in Java 8
                    // it only breaks for interfaces because they are statically importable
                    // and not classes
                    && decl.getContainer() instanceof TypeDeclaration) {
                qualExpr = naming.makeTypeDeclarationExpression(null, (TypeDeclaration)decl.getContainer(), DeclNameFlag.QUALIFIED);
            }
            if (Decl.isPrivateAccessRequiringUpcast(expr)) {
                qualExpr = makePrivateAccessUpcast(expr, qualExpr);
            }
            
            if (transformer != null) {
                result = transformer.transform(qualExpr, selector);
            } else {
                Tree.Primary qmePrimary = null;
                if (expr instanceof Tree.QualifiedMemberOrTypeExpression) {
                    qmePrimary = ((Tree.QualifiedMemberOrTypeExpression)expr).getPrimary();
                }
                boolean safeMemberJavaArray = expr instanceof Tree.QualifiedMemberExpression 
                        && ((Tree.QualifiedMemberExpression)expr).getMemberOperator() instanceof Tree.SafeMemberOp
                        && isJavaArray(qmePrimary.getTypeModel());
                
                if ((safeMemberJavaArray || Decl.isValueTypeDecl(qmePrimary))
                        // Safe operators always work on boxed things, so don't use value types
                        && (safeMemberJavaArray || (expr instanceof Tree.QualifiedMemberOrTypeExpression == false)
                            || ((Tree.QualifiedMemberOrTypeExpression)expr).getMemberOperator() instanceof Tree.SafeMemberOp == false)
                        // We never want to use value types on boxed things, unless they are java arrays
                        && (CodegenUtil.isUnBoxed(qmePrimary) || isJavaArray(qmePrimary.getTypeModel()))
                        // Java arrays length property does not go via value types
                        && (!isJavaArray(qmePrimary.getTypeModel())
                                || (!"length".equals(selector) && !"hashCode".equals(selector)))) {
                    JCExpression primTypeExpr = makeJavaType(qmePrimary.getTypeModel(), JT_NO_PRIMITIVES | JT_VALUE_TYPE);
                    result = makeQualIdent(primTypeExpr, selector);
                    result = make().Apply(List.<JCTree.JCExpression>nil(),
                            result,
                            List.<JCTree.JCExpression>of(qualExpr));
                } else if (expr instanceof Tree.QualifiedMemberOrTypeExpression
                        && isThrowableMessage((Tree.QualifiedMemberOrTypeExpression)expr)) {
                    result = utilInvocation().throwableMessage(qualExpr);
                } else if (expr instanceof Tree.QualifiedMemberOrTypeExpression
                        && isThrowableSuppressed((Tree.QualifiedMemberOrTypeExpression)expr)) {
                    result = utilInvocation().suppressedExceptions(qualExpr);
                } else {
                    result = makeQualIdent(qualExpr, selector);
                    if (useGetter) {
                        result = make().Apply(List.<JCTree.JCExpression>nil(),
                                result,
                                List.<JCTree.JCExpression>nil());
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * We may need to force a qualified this prefix (direct or outer) in the following cases:
     * 
     * - Required because of mixin inheritance with different type arguments (the same is already
     *   done for qualified references, but not for direct references)
     * - The compiler generates anonymous local classes for things like
     *   Callables and Comprehensions. When referring to a member foo 
     *   within one of those things we need a qualified {@code this}
     *   to ensure we're accessing the outer instances member, not 
     *   a member of the anonymous local class that happens to have the same name.
     */
    private JCExpression addThisOrObjectQualifierIfRequired(
            JCExpression qualExpr, Tree.StaticMemberOrTypeExpression expr,
            Declaration decl) {
        if (qualExpr == null 
                // statics are not members that can be inherited
                && !decl.isStaticallyImportable()
                && !Decl.isConstructor(decl)
                && decl.isMember()
                // dodge variable refinements with assert/is (these will be turned to locals
                // and have a name mapping)
                && expr.getTarget().getDeclaration() == decl
                && !Decl.isLocalToInitializer(decl)
                && !isWithinSuperInvocation()) {
            // First check whether the expression is captured from an enclosing scope
            TypeDeclaration outer = null;
            // get the ClassOrInterface container of the declaration
            Scope stop = Decl.getClassOrInterfaceContainer(decl, false);
            if (stop instanceof TypeDeclaration) {// reified scope
                Scope scope = expr.getScope();
                while (!(scope instanceof Package)) {
                    if (scope.equals(stop)) {
                        outer = (TypeDeclaration)stop;
                        break;
                    }
                    scope = scope.getContainer();
                }
            }
            // If not it might be inherited...
            if (outer == null) {
                outer = expr.getScope().getInheritingDeclaration(decl);
            }
            if (outer != null) {
                Type targetType = expr.getTarget().getQualifyingType();
                Type declarationContainerType = ((TypeDeclaration)outer).getType();
                // check if we need a variance cast
                VarianceCastResult varianceCastResult = getVarianceCastResult(targetType, declarationContainerType);
                // if we are within a comprehension body, or if we need a variance cast
                if(isWithinSyntheticClassBody() || varianceCastResult != null){
                    if (decl.isShared() && outer instanceof Interface) {
                        // always prefer qualified
                        qualExpr = makeQualifiedDollarThis(declarationContainerType);
                    } else {
                        // Class or companion class,
                        qualExpr = naming.makeQualifiedThis(makeJavaType(((TypeDeclaration)outer).getType(), 
                                JT_RAW | (outer instanceof Interface ? JT_COMPANION : 0)));
                    }
                    // add the variance cast if required
                    if(varianceCastResult != null){
                        qualExpr = applyVarianceCasts(qualExpr, targetType, varianceCastResult, 0);
                    }
                }
            } else if (decl.isClassMember()
                    && ((Class)decl.getContainer()).isAnonymous()
                    && ((Class)decl.getContainer()).isToplevel()) {
                Class container = (Class)decl.getContainer();
                Value value = (Value)((Package)container.getContainer()).getMember(container.getName(), null, false);
                qualExpr = make().Apply(null,
                        naming.makeName(value, Naming.NA_FQ | Naming.NA_WRAPPER | Naming.NA_MEMBER),
                        List.<JCExpression>nil());    
            } else if (decl.isMember() && !expr.getStaticMethodReference()) {
                throw new BugException(expr, decl.getQualifiedNameString() + " was unexpectedly a member");
            }
        }
        return qualExpr;
    }

    /**
     * §3.2.2 Every interface is a subtype of c.l.Object, so 
     * within an Interface {@code string} means {@code $this.toString()}
     * @param expr
     * @param decl
     * @param qualExpr
     * @return
     */
    // Interface we must use $this's implementation of equals, hash and string
    private JCExpression addQualifierForObjectMembersOfInterface(
            Tree.StaticMemberOrTypeExpression expr, Declaration decl,
            JCExpression qualExpr) {
        if (expr instanceof Tree.BaseMemberExpression
                && qualExpr == null
                && typeFact().getObjectDeclaration().equals(Decl.getClassOrInterfaceContainer(decl))) {
            Scope scope = expr.getScope();
            while (Decl.isLocalNotInitializerScope(scope)) {
                scope = scope.getContainer();
            }
            if (scope instanceof Interface) {
                qualExpr = naming.makeQuotedThis();
            }
        }
        return qualExpr;
    }

    /**
     * Determines whether we need to generate an AbstractCallable when taking 
     * a method reference to a method that's declared as a FunctionalParameter
     */
    private boolean functionalParameterRequiresCallable(Function functionalParameter, Tree.StaticMemberOrTypeExpression expr) {
        if (!functionalParameter.isParameter()) {
            throw new BugException();
        }
        boolean hasMethod = JvmBackendUtil.createMethod(functionalParameter);
        if (!hasMethod) {
            // A functional parameter that's not method wrapped will already be Callable-wrapped
            return false;
        }
        // Optimization: If we're in a scope where the Callable field is visible
        // we don't need to create a method ref        
        Scope scope = expr.getScope();
        while (true) {
            if (scope instanceof Package) {
                break;
            }
            if (scope.equals(functionalParameter.getContainer())) {
                return false;
            }
            scope = scope.getContainer();
        }
        // Otherwise we do require an AbstractCallable.
        return true;
    }

    //
    // Array access

    private JCExpression addInterfaceImplAccessorIfRequired(JCExpression qualExpr, Tree.StaticMemberOrTypeExpression expr, Declaration decl) {
        // Partial fix for https://github.com/ceylon/ceylon-compiler/issues/1023
        // For interfaces we sometimes need to access either the interface instance or its $impl class
        if (decl instanceof Constructor) {
            decl = (Class)Decl.container(decl);
        }
        Scope declContainer = Decl.container(decl);
        if(qualExpr != null
                // this is only for interface containers
                && declContainer instanceof Interface
                // we only ever need the $impl if the declaration is not shared
                && !decl.isShared()){
            Interface declaration = (Interface) declContainer;
            // access the interface $impl instance
            qualExpr = naming.makeCompanionAccessorCall(qualExpr, declaration);
            // When the interface is local the accessor returns Object
            // so we need to cast it to the type of the companion
            if (Decl.isAncestorLocal(declaration)) {
                Type type;
                // try to find the best type
                if(expr instanceof Tree.QualifiedMemberOrTypeExpression)
                    type = ((Tree.QualifiedMemberOrTypeExpression) expr).getPrimary().getTypeModel();
                else
                    type = declaration.getType();
                qualExpr = make().TypeCast(makeJavaType(type, JT_COMPANION), qualExpr);
            }
        }
        return qualExpr;
    }

    private JCExpression makeQualifiedDollarThis(Tree.BaseMemberExpression expr) {
        Declaration decl = expr.getDeclaration();
        Interface interf = (Interface) Decl.getClassOrInterfaceContainer(decl);
        // find the target container interface that is or satisfies the given interface
        Scope scope = expr.getScope();
        boolean needsQualified = false;
        while(scope != null){
            if(scope instanceof Interface){
                if(Decl.equalScopeDecl(scope, interf) || ((Interface)scope).inherits(interf)){
                    break;
                }
                // we only need to qualify it if we're aiming for a $this of an outer interface than the interface we are caught in
                needsQualified = true;
            }
            scope = scope.getContainer();
        }
        if(!needsQualified)
            return naming.makeQuotedThis();
        interf = (Interface) scope;
        return makeQualifiedDollarThis(interf.getType());
    }
    
    private JCExpression makeQualifiedDollarThis(Type targetType){
        JCExpression qualifiedCompanionThis = naming.makeQualifiedThis(makeJavaType(targetType, JT_COMPANION | JT_RAW));
        return naming.makeQualifiedDollarThis(qualifiedCompanionThis);
    }

    private boolean needDollarThis(Tree.StaticMemberOrTypeExpression expr) {
        if (expr instanceof Tree.BaseMemberExpression) {
            // We need to add a `$this` prefix to the member expression if:
            // * The member was declared on an interface I and
            // * The member is being used in the companion class of I or 
            //   // REMOVED: some subinterface of I, and
            //   some member type of I, and
            // * The member is shared (non-shared means its only on the companion class)
            // FIXME: https://github.com/ceylon/ceylon-compiler/issues/1019
            final Declaration decl = expr.getDeclaration();
            if(!Decl.withinInterface(decl))
                return false;
            
            // Find the method/getter/setter where the expr is being used
            Scope scope = expr.getScope();
            while (scope != null && scope instanceof Interface == false) {
                scope = scope.getContainer();
            }
            // Is it being used in an interface (=> impl)
            if (scope instanceof Interface) {
                return decl.isShared();
            }
        }
        return false;
    }
    
    private boolean needDollarThis(Scope scope) {
        while (Decl.isLocalNotInitializerScope(scope)) {
            scope = scope.getContainer();
        }
        return scope instanceof Interface;
    }

    public JCTree transform(Tree.IndexExpression access) {
        // depends on the operator
        Tree.ElementOrRange elementOrRange = access.getElementOrRange();
        boolean isElement = elementOrRange instanceof Tree.Element;
        
        // let's see what types there are
        Type leftType = access.getPrimary().getTypeModel();
        // find the corresponding supertype
        Interface leftSuperTypeDeclaration;
        if(isElement)
            leftSuperTypeDeclaration = typeFact().getCorrespondenceDeclaration();
        else
            leftSuperTypeDeclaration = typeFact().getRangedDeclaration();
        Type leftCorrespondenceOrRangeType = leftType.getSupertype(leftSuperTypeDeclaration);
        Type rightType = getTypeArgument(leftCorrespondenceOrRangeType, 0);
        
        // now find the access code
        JCExpression safeAccess;
        
        if(isElement){
            // can we use getFromFirst() to avoid boxing the index?
            boolean listOptim =  
                    leftType.isSubtypeOf(typeFact().getListDeclaration().appliedType(
                            null, Collections.singletonList(typeFact().getAnythingType())));
            
            Type leftTypeForGetCall = listOptim ? leftType.getSupertype(typeFact().getListDeclaration()) : leftCorrespondenceOrRangeType;
            
            Tree.Primary primary = access.getPrimary();
            JCExpression lhs;
            boolean isSuper = isSuper(primary);
            if(isSuper || isSuperOf(primary)){
                TypeDeclaration leftDeclaration = null;
                // this is super special: if we use the optim and call super we need to make sure that "getFromFirst" already
                // has a concrete super implementation
                if(listOptim){
                    Declaration member = leftType.getDeclaration().getMember("getFromFirst", null, false);
                    if(member == null || member.isFormal()){
                        listOptim = false;
                    }else{
                        leftDeclaration = (TypeDeclaration) member.getContainer();
                    }
                }
                // if we did not already find the right supertype, try with "get"
                if (leftDeclaration == null){
                    Declaration member = leftType.getDeclaration().getMember("get", null, false);
                    leftDeclaration = (TypeDeclaration) member.getContainer();
                }
                if(isSuper)
                    lhs = transformSuper(access, leftDeclaration);
                else
                    lhs = transformSuperOf(access, access.getPrimary(), listOptim ? "getFromFirst" : "get");
            }else{
                lhs = transformExpression(access.getPrimary(), BoxingStrategy.BOXED, leftTypeForGetCall);
            }
            
            Tree.Element element = (Tree.Element) elementOrRange;
            
            // do the index
            JCExpression index = transformExpression(element.getExpression(), listOptim ? BoxingStrategy.UNBOXED : BoxingStrategy.BOXED, rightType);

            // tmpVar.item(index)
            safeAccess = at(access).Apply(List.<JCTree.JCExpression>nil(), 
                                          makeSelect(lhs, listOptim ? "getFromFirst" : "get"), List.of(index));
            // Because tuple index access has the type of the indexed element
            // (not the union of types in the sequential) a typecast may be required.
            Type sequentialElementType = getTypeArgument(leftCorrespondenceOrRangeType, 1);
            Type expectedType = access.getTypeModel();
            int flags = 0;
            if(!expectedType.isExactly(sequentialElementType)
                    // could be optional too, for regular Correspondence item access
                    && !expectedType.isExactly(typeFact().getOptionalType(sequentialElementType)))
                flags |= EXPR_DOWN_CAST;
            safeAccess = applyErasureAndBoxing(safeAccess, 
                                               sequentialElementType, 
                                               CodegenUtil.hasTypeErased(access), true, BoxingStrategy.BOXED, 
                                               expectedType, flags);
        }else{
            // do the indices
            Tree.ElementRange range = (Tree.ElementRange) elementOrRange;
            JCExpression start = transformExpression(range.getLowerBound(), BoxingStrategy.BOXED, rightType);

            // is this a span or segment?
            String method;
            final List<JCExpression> args;
            if (range.getLowerBound() != null 
                    && range.getLength() != null) {
                method = "measure";
                JCExpression length = transformExpression(range.getLength(), BoxingStrategy.UNBOXED, typeFact().getIntegerType());
                args = List.of(start, length);
            } else if (range.getLowerBound() == null) {
                method = "spanTo";
                JCExpression end = transformExpression(range.getUpperBound(), BoxingStrategy.BOXED, rightType);
                args = List.of(end);
            } else if (range.getUpperBound() == null) {
                method = "spanFrom";
                args = List.of(start);
            } else if (range.getLowerBound() != null 
                    && range.getUpperBound() != null) {
                method = "span"; 
                JCExpression end = transformExpression(range.getUpperBound(), BoxingStrategy.BOXED, rightType);
                args = List.of(start, end);
            } else {
                method = "unknown";
                args = List.<JCExpression>of(makeErroneous(range, "compiler bug: unhandled range"));
            }

            JCExpression lhs;

            Tree.Primary primary = access.getPrimary();
            boolean isSuper = isSuper(primary);
            if(isSuper || isSuperOf(primary)){
                Declaration member = leftType.getDeclaration().getMember(method, null, false);
                TypeDeclaration leftDeclaration = (TypeDeclaration) member.getContainer();
                if(isSuper)
                    lhs = transformSuper(access, leftDeclaration);
                else
                    lhs = transformSuperOf(access, access.getPrimary(), method);
            }else{
                lhs = transformExpression(access.getPrimary(), BoxingStrategy.BOXED, leftCorrespondenceOrRangeType);
            }
            
            // Because tuple open span access has the type of the indexed element
            // (not a sequential of the union of types in the ranged) a typecast may be required.
            Type rangedSpanType = getTypeArgument(leftCorrespondenceOrRangeType, 2);
            Type expectedType = access.getTypeModel();
            int flags = 0;
            if(!expectedType.isExactly(rangedSpanType)){
                flags |= EXPR_DOWN_CAST;
                // make sure we barf properly if we missed a heuristics
                if(method.equals("spanFrom")){
                    // make a "Util.<method>(lhs, start, end)" call
                    at(access);
                    safeAccess = utilInvocation().tuple_spanFrom(args.prepend(lhs));
                }else{
                    safeAccess = makeErroneous(access, "compiler bug: only the spanFrom method should be specialised for Tuples");
                }
            }else{
                // make a "lhs.<method>(start, end)" call
                safeAccess = at(access).Apply(List.<JCTree.JCExpression>nil(), 
                        makeSelect(lhs, method), args);
            }
            safeAccess = applyErasureAndBoxing(safeAccess, 
                                               rangedSpanType, 
                                               CodegenUtil.hasTypeErased(access), true, BoxingStrategy.BOXED, 
                                               expectedType, flags);
        }

        return safeAccess;
    }

    //
    // Assignment

    public JCExpression transform(Tree.AssignOp op) {
        return transformAssignment(op, op.getLeftTerm(), op.getRightTerm());
    }

    private JCExpression transformAssignment(Node op, Tree.Term leftTerm, Tree.Term rightTerm) {
        // Remember and disable inStatement for RHS
        boolean tmpInStatement = inStatement;
        inStatement = false;
        
        // FIXME: can this be anything else than a Tree.MemberOrTypeExpression or Tree.ParameterizedExpression?
        final JCExpression rhs;
        BoxingStrategy boxing;
        if (leftTerm instanceof Tree.MemberOrTypeExpression) {
            TypedDeclaration decl = (TypedDeclaration) ((Tree.MemberOrTypeExpression)leftTerm).getDeclaration();
            boxing = CodegenUtil.getBoxingStrategy(decl);
            if (decl instanceof Value) {
                Value val = (Value)decl;
                if (val.getSetter() != null && val.getSetter().getUnboxed() != null) {
                    boxing = CodegenUtil.getBoxingStrategy(val.getSetter());
                }
            }
            Type targetType = tmpInStatement ? leftTerm.getTypeModel() : rightTerm.getTypeModel();
            // if we're dealing with widening do not trust the type of the declaration and get the real type
            if(CodegenUtil.hasUntrustedType(decl)){
                TypedReference typedRef = (TypedReference) ((Tree.MemberOrTypeExpression)leftTerm).getTarget();
                TypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
                targetType = nonWideningType(typedRef, nonWideningTypedRef);
            }
            rhs = transformExpression(rightTerm, boxing, targetType, 
                                      decl.hasUncheckedNullType() ? EXPR_TARGET_ACCEPTS_NULL : 0);
        } else {
            // instanceof Tree.ParameterizedExpression
            boxing = CodegenUtil.getBoxingStrategy(leftTerm);
            Tree.ParameterizedExpression paramExpr = (Tree.ParameterizedExpression)leftTerm;
            FunctionOrValue decl = (FunctionOrValue) ((Tree.MemberOrTypeExpression)paramExpr.getPrimary()).getDeclaration();
            CallableBuilder callableBuilder = CallableBuilder.anonymous(
                    gen(),
                    paramExpr,
                    decl,
                    (Tree.Expression)rightTerm,
                    paramExpr.getParameterLists(),
                    paramExpr.getPrimary().getTypeModel(),
                    decl instanceof Function ? !((Function)decl).isDeferred() : false);
            rhs = callableBuilder.build();
        }

        if (tmpInStatement) {
            return transformAssignment(op, leftTerm, rhs);
        } else {
            Type valueType = rightTerm.getTypeModel();
            if(isNull(valueType))
                valueType = leftTerm.getTypeModel();
            return transformAssignAndReturnOperation(op, leftTerm, boxing == BoxingStrategy.BOXED, 
                    leftTerm.getTypeModel(), valueType, new AssignAndReturnOperationFactory(){
                @Override
                public JCExpression getNewValue(JCExpression previousValue) {
                    return rhs;
                }
            });
        }
    }
    
    private JCExpression transformAssignment(final Node op, Tree.Term leftTerm, JCExpression rhs) {
        // left hand side can be either BaseMemberExpression, QualifiedMemberExpression or array access (M2)
        // TODO: array access (M2)
        JCExpression expr = null;
        if(leftTerm instanceof Tree.BaseMemberExpression) {
            if (needDollarThis((Tree.BaseMemberExpression)leftTerm)) {
                expr = naming.makeQuotedThis();
            }
        } else if(leftTerm instanceof Tree.QualifiedMemberExpression) {
            Tree.QualifiedMemberExpression qualified = ((Tree.QualifiedMemberExpression)leftTerm);
            if (isPackageQualified(qualified)) {
                expr = null;
            } else if (isSuper(qualified.getPrimary())) {
                expr = transformSuper(qualified);
            } else if (isSuperOf(qualified.getPrimary())) {
                expr = transformSuperOf(qualified, qualified.getPrimary(), qualified.getDeclaration().getName());
            } else if (isThis(qualified.getPrimary())
                    && !qualified.getDeclaration().isCaptured() 
                    && !qualified.getDeclaration().isShared() ) {
                expr = null;
            } else if (!qualified.getDeclaration().isStaticallyImportable()) {
                expr = transformExpression(qualified.getPrimary(), BoxingStrategy.BOXED, qualified.getTarget().getQualifyingType());
                if (Decl.isPrivateAccessRequiringUpcast(qualified)) {
                    expr = makePrivateAccessUpcast(qualified, expr);
                }
            }
        } else if(leftTerm instanceof Tree.ParameterizedExpression) {
            // Nothing to do here
            expr = null;
        } else {
            return makeErroneous(op, "compiler bug: "+op.getNodeType() + " is not yet supported");
        }
        return transformAssignment(op, leftTerm, expr, rhs);
    }
    
    private JCExpression transformAssignment(Node op, Tree.Term leftTerm, JCExpression lhs, JCExpression rhs) {
        JCExpression result = null;

        // FIXME: can this be anything else than a Tree.StaticMemberOrTypeExpression or Tree.ParameterizedExpression?
        TypedDeclaration decl;
        if (leftTerm instanceof Tree.StaticMemberOrTypeExpression) {
            decl = (TypedDeclaration) ((Tree.StaticMemberOrTypeExpression)leftTerm).getDeclaration();
            lhs = addInterfaceImplAccessorIfRequired(lhs, (Tree.StaticMemberOrTypeExpression) leftTerm, decl);
            lhs = addThisOrObjectQualifierIfRequired(lhs, (Tree.StaticMemberOrTypeExpression)leftTerm, decl);
        } else {
            // instanceof Tree.ParameterizedExpression
            decl = (TypedDeclaration) ((Tree.MemberOrTypeExpression)((Tree.ParameterizedExpression)leftTerm).getPrimary()).getDeclaration();
        }

        boolean variable = decl.isVariable();
        
        at(op);
        String selector = naming.selector(decl, Naming.NA_SETTER);
        if (decl.isToplevel()) {
            // must use top level setter
            lhs = naming.makeName(decl, Naming.NA_FQ | Naming.NA_WRAPPER);
        } else if (Decl.isGetter(decl)) {
            if (Decl.isTransient(decl) && !decl.isVariable()) {
                JCExpression attr = gen().transformAttributeGetter(decl, rhs);
                result = at(op).Assign(naming.makeQualifiedName(lhs, decl, Naming.NA_WRAPPER), attr);
            } else {
                // must use the setter
                if (Decl.isLocal(decl)) {
                    lhs = naming.makeQualifiedName(lhs, decl, Naming.NA_WRAPPER | Naming.NA_SETTER);
                } else if (decl.isStaticallyImportable()) {
                    lhs = naming.makeTypeDeclarationExpression(null, (TypeDeclaration)decl.getContainer(), DeclNameFlag.QUALIFIED);
                }
            }
        } else if (decl instanceof Function && Decl.isDeferred(decl)) {
            if (Decl.isLocal(decl)) {
                // Deferred method initialization of a local function
                // The Callable field has the same name as the method, so use NA_MEMBER
                result = at(op).Assign(naming.makeQualifiedName(lhs, decl, Naming.NA_WRAPPER_UNQUOTED | Naming.NA_MEMBER), rhs);
            } else {
                // Deferred method initialization of a class function
                result = at(op).Assign(naming.makeQualifiedName(lhs, decl, Naming.NA_MEMBER), rhs);
            }
        } else if ((variable || decl.isLate()) && (Decl.isClassAttribute(decl))) {
            // must use the setter, nothing to do, unless it's a java field
            if(Decl.isJavaField(decl)){
                if (decl.isStaticallyImportable()) {
                    // static field
                    result = at(op).Assign(naming.makeName(decl, Naming.NA_FQ | Naming.NA_WRAPPER_UNQUOTED), rhs);
                }else{
                    // normal field
                    result = at(op).Assign(naming.makeQualifiedName(lhs, decl, Naming.NA_IDENT), rhs);
                }
            }

        } else if (variable && (decl.isCaptured() || decl.isShared())) {
            // must use the qualified setter
            if (Decl.isBoxedVariable(decl)) {
                result = at(op).Assign(naming.makeName(decl, Naming.NA_Q_LOCAL_INSTANCE | Naming.NA_MEMBER | Naming.NA_SETTER), rhs);
            } else if (Decl.isLocalNotInitializer(decl)) {
                lhs = naming.makeQualifiedName(lhs, decl, Naming.NA_WRAPPER);
            } else if (isWithinSuperInvocation()
                    && decl.isCaptured()
                    && decl.isVariable()) {
                lhs = naming.makeUnquotedIdent(Naming.getAliasedParameterName(((Value)decl).getInitializerParameter()));
                result = at(op).Assign(lhs, rhs);
            }
        } else {
            result = at(op).Assign(naming.makeQualifiedName(lhs, decl, Naming.NA_IDENT), rhs);
        }
        
        if (result == null) {
            result = make().Apply(List.<JCTree.JCExpression>nil(),
                    makeQualIdent(lhs, selector),
                    List.<JCTree.JCExpression>of(rhs));
        }
        
        return result;
    }

    /** Creates an anonymous class that extends Iterable and implements the specified comprehension.
     */
    public JCExpression transformComprehension(Tree.Comprehension comp) {
        return transformComprehension(comp, null);
    }

    JCExpression transformComprehension(Tree.Comprehension comp, Type expectedType) {
        Type elementType = comp.getInitialComprehensionClause().getTypeModel();
        // get rid of anonymous types
        elementType = typeFact().denotableType(elementType);
        elementType = wrapInOptionalForInterop(elementType, expectedType, containsUncheckedNulls(comp));
        return new ComprehensionTransformation(comp, elementType).transformComprehension();
    }

    private Type wrapInOptionalForInterop(Type elementType, Type expectedType, boolean containsUncheckedNull) {
        if(expectedType != null && containsUncheckedNull && iteratesOverOptional(expectedType) && !typeFact().isOptionalType(elementType))
            return typeFact().getOptionalType(elementType);
        return elementType;
    }

    private boolean iteratesOverOptional(Type iterableType) {
        Type seqElemType = typeFact().getIteratedType(iterableType);
        return isOptional(seqElemType);
    }
    
    class ComprehensionTransformation {
        private final Tree.Comprehension comp;
        final Type targetIterType;
        final Type absentIterType;
        int idx = 0;
        Tree.ExpressionComprehensionClause excc = null;
        Naming.SyntheticName prevItemVar = null;
        Naming.SyntheticName ctxtName = null;
        Naming.SyntheticName lastIteratorCtxtName = null;
        //Iterator fields
        final ListBuffer<JCTree> fields = new ListBuffer<JCTree>();
        final ListBuffer<Substitution> fieldSubst = new ListBuffer<Substitution>();
        private JCExpression error;
        private JCStatement initIterator;
        // A list of variable declarations local to the next() method so that
        // the variable captured by whatever gets transformed there holds the value
        // at *that point* on the iteration, and not the (variable) value of 
        // the iterator. See #986
        private final ListBuffer<JCStatement> valueCaptures = ListBuffer.<JCStatement>lb();
        public ComprehensionTransformation(final Tree.Comprehension comp, Type elementType) {
            this.comp = comp;
            targetIterType = typeFact().getIterableType(elementType);
            absentIterType = comp.getInitialComprehensionClause().getFirstTypeModel();
        }
    
        public JCExpression transformComprehension() {
            at(comp);
            // make sure "this" will be qualified since we're introducing a new surrounding class
            boolean oldWithinSyntheticClassBody = withinSyntheticClassBody(true);
            try{
                Tree.ComprehensionClause clause = comp.getInitialComprehensionClause();
                while (clause != null) {
                    final Naming.SyntheticName iterVar = naming.synthetic(Prefix.$iterator$, idx);
                    Naming.SyntheticName itemVar = null;
                    if (clause instanceof Tree.ForComprehensionClause) {
                        final Tree.ForComprehensionClause fcl = (Tree.ForComprehensionClause)clause;
                        itemVar = transformForClause(fcl, iterVar);
                        if (error != null) {
                            return error;
                        }
                        clause = fcl.getComprehensionClause();
                    } else if (clause instanceof Tree.IfComprehensionClause) {
                        transformIfClause((Tree.IfComprehensionClause)clause);
                        if (error != null) {
                            return error;
                        }
                        clause = ((Tree.IfComprehensionClause)clause).getComprehensionClause();
                        itemVar = prevItemVar;
                    } else if (clause instanceof Tree.ExpressionComprehensionClause) {
                        //Just keep a reference to the expression
                        excc = (Tree.ExpressionComprehensionClause)clause;
                        at(excc);
                        clause = null;
                    } else {
                        return makeErroneous(clause, "compiler bug: comprehension clauses of type " + clause.getClass().getName() + " are not yet supported");
                    }
                    idx++;
                    if (itemVar != null) prevItemVar = itemVar;
                }

                Type iteratedType = typeFact().getIteratedType(targetIterType);

                //Define the next() method for the Iterator
                fields.add(makeNextMethod(iteratedType));
                //Define the inner iterator class

                JCMethodDecl getIterator = makeGetIterator(iteratedType);
                JCExpression iterable = makeAnonymousIterable(iteratedType, getIterator);
                for (Substitution subs : fieldSubst) {
                    subs.close();
                }
                return iterable;
            }finally{
                withinSyntheticClassBody(oldWithinSyntheticClassBody);
            }
        }
        
        /**
         * Builds the {@code next()} method of the {@code AbstractIterator}
         */
        private JCMethodDecl makeNextMethod(Type iteratedType) {
            List<JCStatement> of = valueCaptures.append(make().Return(transformExpression(excc.getExpression(), BoxingStrategy.BOXED, iteratedType))).toList();
            JCStatement stmt = make().If(
                    make().Apply(null,
                        ctxtName.makeIdentWithThis(), List.<JCExpression>nil()),
                    make().Block(0, of),
                    make().Return(makeFinished()));
            return make().MethodDef(make().Modifiers(Flags.PUBLIC | Flags.FINAL), names().fromString("next"),
                makeJavaType(typeFact().getObjectType()), List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(), List.<JCExpression>nil(), make().Block(0, List.<JCStatement>of(stmt)), null);
        }
        /**
         * Builds a {@code getIterator()} method which contains a local class 
         * extending {@code AbstractIterator} and initialises the iter$0 field
         * to a new instance of that local class.
         * 
         * Doesn't use an anonymous class due to #974.
         * @param iteratedType
         * @return
         */
        private JCMethodDecl makeGetIterator(Type iteratedType) {
            Type iteratorType = typeFact().getIteratorType(iteratedType);
            JCExpression iteratorTypeExpr = make().TypeApply(makeIdent(syms().ceylonAbstractIteratorType),
                    List.<JCExpression>of(makeJavaType(iteratedType, JT_TYPE_ARGUMENT)));
            JCExpression iterator = make().NewClass(null, List.<JCExpression>nil(), iteratorTypeExpr, 
                    List.<JCExpression>of(makeReifiedTypeArgument(iteratedType)), 
                    make().AnonymousClassDef(make().Modifiers(0), 
                            fields.toList().prepend(
                                    make().Block(0L,
                                            initIterator == null ? List.<JCStatement>nil() : List.<JCStatement>of(initIterator)) 
                                    )));
            JCBlock iteratorBlock = make().Block(0, List.<JCStatement>of(
                    make().Return(iterator)));
            return make().MethodDef(make().Modifiers(Flags.PUBLIC | Flags.FINAL), names().fromString("iterator"),
                    makeJavaType(iteratorType, JT_CLASS_NEW|JT_EXTENDS),
                List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(), List.<JCExpression>nil(),
                iteratorBlock, null);
        }
        /**
         * Builds an anonymous subclass of AbstractIterable whose 
         * {@code getIterator()} uses the given getIteratorBody.
         * @param iteratedType
         * @param iteratorType
         * @param getIteratorBody
         * @return
         */
        private JCExpression makeAnonymousIterable(Type iteratedType,
                JCMethodDecl getIterator) {
            JCExpression iterable = make().NewClass(null, null,
                    make().TypeApply(makeIdent(syms().ceylonAbstractIterableType),
                        List.<JCExpression>of(makeJavaType(iteratedType, JT_TYPE_ARGUMENT),
                                makeJavaType(absentIterType, JT_NO_PRIMITIVES))),
                                List.<JCExpression>of(makeReifiedTypeArgument(iteratedType), 
                                        makeReifiedTypeArgument(absentIterType)), 
                    make().AnonymousClassDef(make().Modifiers(0), 
                            List.<JCTree>of(getIterator)));
            return iterable;
        }

        class IfComprehensionCondList extends CondList {

            private final ListBuffer<JCStatement> varDecls = ListBuffer.lb();
            /**
             * A list of statements that are placed in the main body, before the conditions.
             */
            private final List<JCStatement> preCheck;
            /**
             * A list of statements that are placed in the innermost condition's body.
             */
            private final List<JCStatement> insideCheck;
            /**
             * A list of statements that are placed in the main body, after the conditions.
             */
            private final List<JCStatement> postCheck;
            
            /**
             * An IfComprehensionCondList suitable for "inner" if comprehension clauses.
             * Checks {@code condExpr} before checking the {@code conditions}, and {@code break;}s if the conditions apply.
             * Intended to be placed in a {@code while (true) } loop, to keep checking the conditions until they apply
             * or {@code condExpr} doesn't.
             */
            public IfComprehensionCondList(java.util.List<Tree.Condition> conditions, JCExpression condExpr, Name breakLabel) {
                this(conditions,
                    // check condExpr before the conditions
                    List.<JCStatement>of(make().If(make().Unary(JCTree.NOT, condExpr), make().Break(breakLabel), null)),
                    // break if a condition matches
                    List.<JCStatement>of(make().Break(breakLabel)),
                    null);
            }
            
            /**
             * General-purpose constructor. Places {@code precheck} before the conditions and their variable declarations,
             * {@code insideCheck} in the body of the innermost condition (executed only if all {@code conditions} apply), and
             * {@code postCheck} after the conditions.
             */
            public IfComprehensionCondList(java.util.List<Tree.Condition> conditions,
                    List<JCStatement> preCheck, List<JCStatement> insideCheck, List<JCStatement> postCheck) {
                statementGen().super(conditions, (Tree.Block)null);
                if(preCheck == null) preCheck = List.<JCStatement>nil();
                if(insideCheck == null) insideCheck = List.<JCStatement>nil();
                if(postCheck == null) postCheck = List.<JCStatement>nil();
                this.preCheck = preCheck;
                this.insideCheck = insideCheck;
                this.postCheck = postCheck;
            }

            @Override
            protected List<JCStatement> transformInnermost(Tree.Condition condition) {
                Cond transformedCond = getConditionTransformer(condition);
                // The innermost condition's test should be transformed before
                // variable substitution
                
                JCExpression test = transformedCond.makeTest();
                List<VarDefBuilder> vars = addVarSubs(transformedCond.getVarTrans());
                return transformCommon(transformedCond.getVarTrans(),
                        test,
                        insideCheck,
                        vars);
            }
            
            protected List<JCStatement> transformIntermediate(Tree.Condition condition, java.util.List<Tree.Condition> rest) {
                Cond transformedCond = getConditionTransformer(condition);
                JCExpression test = transformedCond.makeTest();
                List<VarDefBuilder> vars = addVarSubs(transformedCond.getVarTrans());
                return transformCommon(transformedCond.getVarTrans(), test, transformList(rest), vars);
            }

            private List<VarDefBuilder> addVarSubs(VarTrans vartrans) {
                if (vartrans.hasResultDecl()) {
                    List<VarDefBuilder> vars = vartrans.getVarDefBuilders();
                    for (VarDefBuilder v : vars) {
                        fieldSubst.add(v.alias());
                    }
                    return vars;
                }
                return null;
            }
            
            protected List<JCStatement> transformCommon(VarTrans vartrans, 
                    JCExpression test, List<JCStatement> stmts,
                    List<VarDefBuilder> vars) {
                
                JCStatement decl = vartrans.makeTestVarDecl(0, true);
                if (decl != null) {
                    varDecls.append(decl);
                }
                if (vars != null) {
                    for (VarDefBuilder v : vars) {
                        fields.add(v.buildField());
                        valueCaptures.add(v.buildFromField());
                        stmts = stmts.prepend(make().Exec(v.buildAssign()));
                    }
                }
                stmts = List.<JCStatement>of(make().If(
                        test, 
                        make().Block(0, stmts), 
                        null));
                return stmts;
            }
            
            public List<JCStatement> getResult() {
                List<JCStatement> stmts = transformList(conditions);
                ListBuffer<JCStatement> result = ListBuffer.lb();
                result.appendList(preCheck);
                result.appendList(varDecls);
                result.appendList(stmts);
                result.appendList(postCheck);
                return result.toList();   
            }

        }
        
        private void transformIfClause(Tree.IfComprehensionClause clause) {
            List<JCStatement> body;
            if (prevItemVar == null) {
            	List<JCStatement> initBlock;
            	if (clause == comp.getInitialComprehensionClause()) {
            		//No previous context
            		assert (ctxtName == null);
            		ctxtName = naming.synthetic(Prefix.$next$, idx);
            		//define a variable that records if the expression was already evaluated
            		SyntheticName exhaustedName = ctxtName.suffixedBy(Suffix.$exhausted$);
                    JCVariableDecl exhaustedDef = make().VarDef(make().Modifiers(Flags.PRIVATE),
                            exhaustedName.asName(), makeJavaType(typeFact().getBooleanType()), null);
                    fields.add(exhaustedDef);
                    JCStatement returnIfExhausted = make().If(exhaustedName.makeIdent(), make().Return(makeBoolean(false)), null);
                    JCStatement setExhaustedTrue = make().Exec(make().Assign(exhaustedName.makeIdent(), makeBoolean(true)));
                    initBlock =  List.<JCStatement>of(
                    		//if we already evaluated the expression, return
                    		returnIfExhausted,
                            //record that we will have evaluated the expression
                            setExhaustedTrue);
            	} else {
            		assert (ctxtName != null);
            		JCStatement returnIfExhausted = make().If(
            				//if the previous comprehension is false or was already evaluated...
            				make().Unary(JCTree.NOT, make().Apply(null,
            						ctxtName.makeIdentWithThis(), List.<JCExpression>nil())),
            				//return false
                    		make().Return(makeBoolean(false)), null);
            		ctxtName = naming.synthetic(Prefix.$next$, idx);
            		initBlock = List.<JCStatement>of(returnIfExhausted);
            	}
                
                JCStatement returnTrue = make().Return(makeBoolean(true));
                JCStatement returnFalse = make().Return(makeBoolean(false));
                
                body = new IfComprehensionCondList(clause.getConditionList().getConditions(),
                    initBlock,
                    List.<JCStatement>of(
                        //if the conditions apply: return true
                        returnTrue),
                    List.<JCStatement>of(
                        //the conditions did not apply: return false
                        returnFalse)).getResult();
            } else {
                //Filter contexts need to check if the previous context applies and then check the condition
                JCExpression condExpr = make().Apply(null,
                    ctxtName.makeIdentWithThis(), List.<JCExpression>nil());
                ctxtName = naming.synthetic(Prefix.$next$, idx);
                Name label = names().fromString("ifcomp_"+idx);
                IfComprehensionCondList ifComprehensionCondList = new IfComprehensionCondList(clause.getConditionList().getConditions(), condExpr, label);
                List<JCStatement> ifs = ifComprehensionCondList.getResult();
                JCStatement loop = make().Labelled(label, make().WhileLoop(makeBoolean(true), make().Block(0, ifs)));
                body = List.<JCStatement>of(loop,
                    make().Return(make().Unary(JCTree.NOT, prevItemVar.suffixedBy(Suffix.$exhausted$).makeIdent())));
        	}
            MethodDefinitionBuilder mb = MethodDefinitionBuilder.systemMethod(ExpressionTransformer.this, ctxtName.getName())
                .ignoreModelAnnotations()
                .modifiers(Flags.PRIVATE | Flags.FINAL)
                .resultType(null, makeJavaType(typeFact().getBooleanType()))
                .body(body);
            fields.add(mb.build());
        }

        private SyntheticName transformForClause(final Tree.ForComprehensionClause clause,
                final Naming.SyntheticName iterVar) {
            final Tree.ForComprehensionClause fcl = clause;
            Tree.SpecifierExpression specexpr = fcl.getForIterator().getSpecifierExpression();
            Type iterType = specexpr.getExpression().getTypeModel();
            JCExpression iterTypeExpr = makeJavaType(typeFact().getIteratorType(
                    typeFact().getIteratedType(iterType)));
            Type iterableType = iterType.getSupertype(typeFact().getIterableDeclaration());
            JCExpression iterableExpr = transformExpression(specexpr.getExpression(), BoxingStrategy.BOXED, iterableType);
            if (clause == comp.getInitialComprehensionClause()) {
                //The first iterator can be initialized as a field
                fields.add(make().VarDef(make().Modifiers(Flags.PRIVATE | Flags.FINAL), iterVar.asName(), iterTypeExpr,
                    null));
                initIterator = make().Exec(make().Assign(iterVar.makeIdent(), make().Apply(null, makeSelect(iterableExpr, "iterator"), 
                        List.<JCExpression>nil())));
            } else {
                //The subsequent iterators need to be inside a method,
                //in case they depend on the current element of the previous iterator
                fields.add(make().VarDef(make().Modifiers(Flags.PRIVATE), iterVar.asName(), iterTypeExpr, null));
                List<JCStatement> block = List.<JCStatement>nil();
                if (lastIteratorCtxtName != null) {
                    block = block.append(make().If(lastIteratorCtxtName.suffixedBy(Suffix.$exhausted$).makeIdent(),
                            make().Return(makeBoolean(false)),
                            null));
                }
                block = block.appendList(List.<JCStatement>of(
                        make().If(make().Binary(JCTree.NE, iterVar.makeIdent(), makeNull()),
                                make().Return(makeBoolean(true)),
                                null),
                        make().If(make().Unary(JCTree.NOT, make().Apply(null, ctxtName.makeIdentWithThis(), List.<JCExpression>nil())),
                                make().Return(makeBoolean(false)),
                                null),
                        make().Exec(make().Assign(iterVar.makeIdent(), 
                                                  make().Apply(null,
                                                               makeSelect(iterableExpr, "iterator"), 
                                                               List.<JCExpression>nil()))),
                        make().Return(makeBoolean(true))
                ));
                JCBlock body = make().Block(0l, block);
                fields.add(make().MethodDef(make().Modifiers(Flags.PRIVATE | Flags.FINAL),
                        iterVar.asName(), makeJavaType(typeFact().getBooleanType()), 
                        List.<JCTree.JCTypeParameter>nil(),
                        List.<JCTree.JCVariableDecl>nil(), List.<JCExpression>nil(), body, null));
            }
            Naming.SyntheticName tmpItem = naming.temp("item");
            List<VarDefBuilder> vdbs = List.nil();
            ListBuffer<JCStatement> elseBody = new ListBuffer<JCStatement>();
            Tree.ForIterator forIterator = fcl.getForIterator();
            Naming.SyntheticName itemVar = naming.synthetic(forIterator);
            if (forIterator instanceof Tree.ValueIterator) {
                Tree.Variable variable = ((Tree.ValueIterator) forIterator).getVariable();
                VarDefBuilder vdb = statementGen().transformVariable(variable, tmpItem.makeIdent());
                vdbs = vdbs.append(vdb);
            } else if (forIterator instanceof Tree.PatternIterator) {
                Tree.PatternIterator patIter = (Tree.PatternIterator)forIterator;
                Tree.Pattern pat = patIter.getPattern();
                vdbs = vdbs.appendList(statementGen().transformPattern(pat, tmpItem.makeIdent()));
            } else {
                error = makeErroneous(fcl, "compiler bug: iterators of type " + forIterator.getNodeType() + " not yet supported");
                return null;
            }
            for (VarDefBuilder vdb : vdbs) {
                valueCaptures.append(vdb.buildFromField());
                fields.add(vdb.buildField());
                elseBody.add(make().Exec(vdb.buildAssign()));
            }
            fields.add(make().VarDef(make().Modifiers(Flags.PRIVATE), itemVar.suffixedBy(Suffix.$exhausted$).asName(),
                    makeJavaType(typeFact().getBooleanType()), null));
            elseBody.add(make().Return(makeBoolean(true)));
            
            //Now the context for this iterator
            ListBuffer<JCStatement> contextBody = new ListBuffer<JCStatement>();
    
            //Assign the next item to an Object variable
            contextBody.add(make().VarDef(make().Modifiers(Flags.FINAL), tmpItem.asName(),
                    makeJavaType(typeFact().getObjectType()),
                    make().Apply(null, makeSelect(iterVar.makeIdent(), "next"), 
                            List.<JCExpression>nil())));
            //Then we check if it's exhausted
            contextBody.add(make().Exec(make().Assign(itemVar.suffixedBy(Suffix.$exhausted$).makeIdent(),
                    make().Binary(JCTree.EQ, tmpItem.makeIdent(), makeFinished()))));
            ListBuffer<JCStatement> innerBody = new ListBuffer<JCStatement>();
            if (idx>0) {
                //Subsequent contexts run once for every iteration of the previous loop
                //This will reset our previous context by getting a new iterator if the previous loop isn't done
                innerBody.add(make().Exec(make().Assign(iterVar.makeIdent(), makeNull())));
            }else{
                innerBody.add(make().Return(makeBoolean(false)));
            }
            //Assign the next item to the corresponding variables if not exhausted yet
            contextBody.add(make().If(itemVar.suffixedBy(Suffix.$exhausted$).makeIdent(),
                make().Block(0, innerBody.toList()),
                make().Block(0, elseBody.toList())));
            
            List<JCTree.JCStatement> methodBody;
            if (idx>0) {
                //Subsequent iterators may depend on the item from the previous loop so we make sure we have one
                methodBody = List.<JCStatement>of(make().WhileLoop(make().Apply(null, iterVar.makeIdentWithThis(), List.<JCExpression>nil()),
                        make().Block(0, contextBody.toList())));
                // It can happen that we never get into the body because the outer iterator is exhausted, if so, mark
                // this one exhausted too
                if (lastIteratorCtxtName != null) {
                    // FIXME: this may actually not be useful to check for exhaustion because in theory we can only get out
                    // of the loop because the previous supplier is exhausted.
                    methodBody = methodBody.append(make().If(lastIteratorCtxtName.suffixedBy(Suffix.$exhausted$).makeIdent(), 
                            make().Exec(make().Assign(itemVar.suffixedBy(Suffix.$exhausted$).makeIdent(), makeBoolean(true))), 
                            null));
                }else{
                    methodBody = methodBody.append(make().Exec(make().Assign(itemVar.suffixedBy(Suffix.$exhausted$).makeIdent(), makeBoolean(true))));
                }
                methodBody = methodBody.append(make().Return(makeBoolean(false)));
            }else
                methodBody = contextBody.toList();
            //Create the context method that returns the next item for this iterator
            lastIteratorCtxtName = ctxtName = itemVar;
            fields.add(make().MethodDef(make().Modifiers(Flags.PRIVATE | Flags.FINAL), itemVar.asName(),
                makeJavaType(typeFact().getBooleanType()),
                List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(), List.<JCExpression>nil(),
                make().Block(0, methodBody), null));
            return itemVar;
        }
    }

    //
    // Type helper functions

    private Type getSupertype(Tree.Term term, TypeDeclaration compoundType){
        return term.getTypeModel().getSupertype(compoundType);
    }

    private Type getTypeArgument(Type leftType) {
        if (leftType!=null && leftType.getTypeArguments().size()==1) {
            return leftType.getTypeArgumentList().get(0);
        }
        return null;
    }

    private Type getTypeArgument(Type leftType, int i) {
        if (leftType!=null && leftType.getTypeArguments().size() > i) {
            return leftType.getTypeArgumentList().get(i);
        }
        return null;
    }

    private JCExpression unAutoPromote(JCExpression ret, Type returnType) {
        // +/- auto-promotes to int, so if we're using java types we'll need a cast
        return applyJavaTypeConversions(ret, typeFact().getIntegerType(), 
                returnType, BoxingStrategy.UNBOXED, false, 0);
    }

    private Type getMostPreciseType(Tree.Term term, Type defaultType) {
        // special case for interop when we're dealing with java types
        Type termType = term.getTypeModel();
        if(termType.getUnderlyingType() != null)
            return termType;
        return defaultType;
    }

    //
    // Helper functions
    
    private boolean isRecursiveReference(Tree.StaticMemberOrTypeExpression expr) {
        Declaration decl = expr.getDeclaration();
        Scope s = expr.getScope();
        // do we have decl as our container anywhere in the scope?
        while (s != null && !Decl.equalScopeDecl(s, decl)) {
            s = s.getContainer();
        }
        return Decl.equalScopeDecl(s, decl);
    }

    private boolean isReferenceInSameScope(Tree.StaticMemberOrTypeExpression expr) {
        Declaration decl = expr.getDeclaration();
        Scope s = expr.getScope();
        // are we in the same Declaration container?
        while (s != null && s instanceof Declaration == false) {
            s = s.getContainer();
        }
        return Decl.equalScopeDecl(s, decl);
    }

    boolean isWithinInvocation() {
        return withinInvocation;
    }
    
    boolean isFunctionalResult(Type type) {
        return !isWithinInvocation()
            && isCeylonCallableSubtype(type);   
    }

    boolean withinInvocation(boolean withinInvocation) {
        boolean result = this.withinInvocation;
        this.withinInvocation = withinInvocation;
        return result;
    }

    boolean isWithinSyntheticClassBody() {
        return withinSyntheticClassBody;
    }

    boolean withinSyntheticClassBody(boolean withinSyntheticClassBody) {
        boolean result = this.withinSyntheticClassBody;
        this.withinSyntheticClassBody = withinSyntheticClassBody;
        return result;
    }

    boolean isWithinSuperInvocation() {
        return withinSuperInvocation != null;
    }

    void withinSuperInvocation(ClassOrInterface forDefinition) {
        this.withinSuperInvocation = forDefinition;
    }

    boolean isWithinDefaultParameterExpression(Scope container) {
        return Decl.equalScopeDecl(container, withinDefaultParameterExpression);
    }

    void withinDefaultParameterExpression(ClassOrInterface forDefinition) {
        this.withinDefaultParameterExpression = forDefinition;
    }

    //
    // Optimisations

    private JCExpression checkForQualifiedMemberExpressionOptimisation(Tree.QualifiedMemberExpression expr) {
        JCExpression ret = checkForBitwiseOperators(expr, expr, null);
        if(ret != null)
            return ret;
        ret = checkForCharacterAsInteger(expr);
        if(ret != null)
            return ret;
        ret = checkForByteLiterals(expr);
        if(ret != null)
            return ret;
        return null;
    }

    /*private JCExpression checkForArrayOnJavaArray(Tree.QualifiedMemberExpression expr) {
        if ("array".equals(expr.getIdentifier().getText())) {
            if (expr.getPrimary() instanceof Tree.BaseMemberExpression) {
                if (Decl.isJavaArray(expr.getPrimary().getTypeModel().getDeclaration())) {
                    return transform((Tree.BaseMemberExpression)expr.getPrimary());
                }
            }
        }
        return null;
    }*/

    boolean isThrowableSuppressed(Tree.QualifiedMemberOrTypeExpression expr) {
        return typeFact().getThrowableDeclaration().getDirectMember("suppressed", null, false).equals(
                expr.getDeclaration().getRefinedDeclaration());
    }

    boolean isThrowableMessage(Tree.QualifiedMemberOrTypeExpression expr) {
        return typeFact().getThrowableDeclaration().getDirectMember("message", null, false).equals(
                expr.getDeclaration().getRefinedDeclaration());
    }

    private JCExpression checkForInvocationExpressionOptimisation(Tree.InvocationExpression ce) {
        // FIXME: temporary hack for byte literals
        JCExpression ret = checkForByteLiterals(ce);
        if(ret != null)
            return ret;
        // FIXME: temporary hack for bitwise operators literals
        ret = checkForBitwiseOperators(ce);
        if(ret != null)
            return ret;
        return null;
    }

    private JCExpression checkForByteLiterals(Tree.QualifiedMemberExpression expr) {
        // must be a call on Integer
        Tree.Term left = expr.getPrimary();
        if(left == null || !isCeylonInteger(left.getTypeModel()))
            return null;
        // must be on "byte"
        if(!expr.getIdentifier().getText().equals("byte"))
            return null;
        // must be a normal member op "."
        if(expr.getMemberOperator() instanceof Tree.MemberOp == false)
            return null;
        // must be unboxed
        if(!expr.getUnboxed() || !left.getUnboxed())
            return null;
        // This can't be Tree.NegativeOp as the normal precedence of -1.byte is -(1.byte), not (-1).byte
        // and must be a number literal
        if(left instanceof Tree.NaturalLiteral == false)
            return null;
        // all good
        at(expr);
        try{
            long value = literalValue((Tree.NaturalLiteral) left);
            // in the case of -128 to 127 we don't need to cast to byte by using an int literal, but only for
            // assignment, not for method calls, so it's simpler to always cast
            return make().TypeCast(syms().byteType, make().Literal(value));
        } catch (ErroneousException e) {
            // We should never get here since the error should have been 
            // reported by the UnsupportedVisitor and the containing statement
            // replaced with a throw.
            return e.makeErroneous(this);
        }
    }
    
    private JCExpression checkForByteLiterals(Tree.InvocationExpression ce) {
        // same test as in BoxingVisitor.isByteLiteral()
        if(ce.getPrimary() instanceof Tree.BaseTypeExpression
                && ce.getPositionalArgumentList() != null){
            java.util.List<Tree.PositionalArgument> positionalArguments = ce.getPositionalArgumentList().getPositionalArguments();
            if(positionalArguments.size() == 1){
                PositionalArgument argument = positionalArguments.get(0);
                if(argument instanceof Tree.ListedArgument
                        && ((Tree.ListedArgument) argument).getExpression() != null){
                    Term term = ((Tree.ListedArgument)argument).getExpression().getTerm();
                    boolean negative = false;
                    if(term instanceof Tree.NegativeOp){
                        negative = true;
                        term = ((Tree.NegativeOp) term).getTerm();
                    }
                    if(term instanceof Tree.NaturalLiteral){
                        Declaration decl = ((Tree.BaseTypeExpression)ce.getPrimary()).getDeclaration();
                        if(decl instanceof Class){
                            String name = decl.getQualifiedNameString();
                            if(name.equals("ceylon.language::Byte")){
                                at(ce);
                                try{
                                    long value = literalValue((Tree.NaturalLiteral) term);
                                    if(negative)
                                        value = -value;
                                    // in the case of -128 to 127 we don't need to cast to byte by using an int literal, but only for
                                    // assignment, not for method calls, so it's simpler to always cast
                                    return make().TypeCast(syms().byteType, make().Literal(value));
                                } catch (ErroneousException e) {
                                    // We should never get here since the error should have been 
                                    // reported by the UnsupportedVisitor and the containing statement
                                    // replaced with a throw.
                                    return e.makeErroneous(this);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private JCExpression checkForCharacterAsInteger(Tree.QualifiedMemberExpression expr) {
        // must be a call on Character
        Tree.Term left = expr.getPrimary();
        if(left == null || !isCeylonCharacter(left.getTypeModel()))
            return null;
        // must be on "integer"
        if(!expr.getIdentifier().getText().equals("integer"))
            return null;
        // must be a normal member op "."
        if(expr.getMemberOperator() instanceof Tree.MemberOp == false)
            return null;
        // must be unboxed
        if(!expr.getUnboxed() || !left.getUnboxed())
            return null;
        // and must be a character literal
        if(left instanceof Tree.CharLiteral == false)
            return null;
        // all good
        return transform((Tree.CharLiteral)left);
    }

    private JCExpression checkForBitwiseOperators(Tree.InvocationExpression ce) {
        if(!(ce.getPrimary() instanceof Tree.QualifiedMemberExpression))
            return null;
        Tree.QualifiedMemberExpression qme = (Tree.QualifiedMemberExpression) ce.getPrimary();
        // must be a positional arg (FIXME: why?)
        if(ce.getPositionalArgumentList() == null
                || ce.getPositionalArgumentList().getPositionalArguments() == null
                || ce.getPositionalArgumentList().getPositionalArguments().size() != 1)
            return null;
        Tree.PositionalArgument arg = ce.getPositionalArgumentList().getPositionalArguments().get(0);
        if(arg instanceof Tree.ListedArgument == false)
            return null;
        Tree.Expression right = ((Tree.ListedArgument)arg).getExpression();
        return checkForBitwiseOperators(ce, qme, right);
    }
    
    private JCExpression checkForBitwiseOperators(Tree.Term node, Tree.QualifiedMemberExpression qme, Tree.Term right) {
        // must be a call on Integer
        Tree.Term left = qme.getPrimary();
        if(left == null) {
            return null;
        }
        String signature;
        Type binaryType;
        if (isCeylonInteger(left.getTypeModel())) {
            // must be a supported method/attribute
            binaryType = typeFact().getIntegerType();
            String name = qme.getIdentifier().getText();
            signature = "ceylon.language.Integer."+name;
        } else if (isCeylonByte(left.getTypeModel())) {
            binaryType = typeFact().getByteType();
            String name = qme.getIdentifier().getText();
            signature = "ceylon.language.Byte."+name;
        } else {
            return null;
        }
        // see if we have an operator for it
        OperatorTranslation operator = Operators.getOperator(signature);
        if(operator != null){
            JCExpression result;
            if(operator.getArity() == 2){
                if(right == null)
                    return null;
                OptimisationStrategy optimisationStrategy = operator.getBinOpOptimisationStrategy(node, left, right, this);
                // check that we can optimise it
                if(!optimisationStrategy.useJavaOperator())
                    return null;
                
                JCExpression leftExpr = transformExpression(left, optimisationStrategy.getBoxingStrategy(), binaryType, EXPR_WIDEN_PRIM);
                JCExpression rightExpr = transformExpression(right, optimisationStrategy.getBoxingStrategy(), binaryType, EXPR_WIDEN_PRIM);

                if (operator.valueMask != 0) {
                    leftExpr = make().Binary(JCTree.BITAND, leftExpr, makeInteger(operator.valueMask));
                }
                
                result =  make().Binary(operator.javacOperator, leftExpr, rightExpr);
            }else{
                // must be unary
                if(right != null)
                    return null;
                OptimisationStrategy optimisationStrategy = operator.getUnOpOptimisationStrategy(node, left, this);
                // check that we can optimise it
                if(!optimisationStrategy.useJavaOperator())
                    return null;
                
                JCExpression leftExpr = transformExpression(left, optimisationStrategy.getBoxingStrategy(), binaryType, EXPR_WIDEN_PRIM);

                if (operator.valueMask != 0) {
                    leftExpr = make().Binary(JCTree.BITAND, leftExpr, makeInteger(operator.valueMask));
                }
                
                result = make().Unary(operator.javacOperator, leftExpr);
            }
            if (isCeylonByte(binaryType)) {
                result = make().TypeCast(syms().byteType, result);
            }
            return result;
        }
        return null;
    }
    /** 
     * Transform the annotations on the given annotated declaration for 
     * inclusion on the given target element type 
     */
    public List<JCAnnotation> transformAnnotations(boolean nat, 
            OutputElement target, 
            Tree.Declaration annotated) {
        EnumSet<OutputElement> outputs;
        if (annotated instanceof Tree.AnyClass) {
            outputs = AnnotationUtil.outputs((Tree.AnyClass)annotated);
        } else if (annotated instanceof Tree.AnyInterface) {
            outputs = AnnotationUtil.outputs((Tree.AnyInterface)annotated);
        } else if (annotated instanceof Tree.TypeAliasDeclaration) {
            outputs = AnnotationUtil.outputs((Tree.TypeAliasDeclaration)annotated);
        } else if (annotated instanceof Tree.Constructor) {
            outputs = AnnotationUtil.outputs((Tree.Constructor)annotated);
        } else if (annotated instanceof Tree.Enumerated) {
            outputs = AnnotationUtil.outputs((Tree.Enumerated)annotated);
        } else if (annotated instanceof Tree.AnyMethod) {
            outputs = AnnotationUtil.outputs((Tree.AnyMethod)annotated);
        } else if (annotated instanceof Tree.AttributeDeclaration) {
            outputs = AnnotationUtil.outputs((Tree.AttributeDeclaration)annotated);
        } else if (annotated instanceof Tree.AttributeGetterDefinition) {
            outputs = AnnotationUtil.outputs((Tree.AttributeGetterDefinition)annotated);
        } else if (annotated instanceof Tree.AttributeSetterDefinition) {
            outputs = AnnotationUtil.outputs((Tree.AttributeSetterDefinition)annotated);
        } else if (annotated instanceof Tree.ObjectDefinition) {
            outputs = AnnotationUtil.outputs((Tree.ObjectDefinition)annotated);
        } else {
            throw BugException.unhandledNodeCase(annotated);
        }
        return transform(nat, target, annotated.getAnnotationList(), outputs);
    }
    /** 
     * Transform the annotations on the given package declaration for 
     * inclusion on the given target element type 
     */
    public List<JCAnnotation> transformAnnotations(boolean nat,
            OutputElement target, 
            Tree.PackageDescriptor annotated) {
        return transform(nat, target, annotated.getAnnotationList(), AnnotationUtil.outputs((Tree.PackageDescriptor)annotated));
    }
    /** 
     * Transform the annotations on the given module import declaration for 
     * inclusion on the given target element type 
     */
    public List<JCAnnotation> transformAnnotations(boolean nat,
            OutputElement target, 
            Tree.ImportModule annotated) {
        return transform(nat, target, annotated.getAnnotationList(), AnnotationUtil.outputs((Tree.ImportModule)annotated));
    }
    /** 
     * Transform the annotations on the given module declaration for inclusion 
     * on the given target element type 
     */
    public List<JCAnnotation> transformAnnotations(boolean nat,
            OutputElement target, 
            Tree.ModuleDescriptor annotated) {
        return transform(nat, target, annotated.getAnnotationList(), AnnotationUtil.outputs((Tree.ModuleDescriptor)annotated));
    }
    private List<JCAnnotation> transform(boolean nat, 
            OutputElement target, 
            Tree.AnnotationList annotationList,
            EnumSet<OutputElement> outputs) {
        if (annotationList == null) {
            return List.nil();
        }
        if ((gen().disableAnnotations & CeylonTransformer.DISABLE_USER_ANNOS) != 0) {
            return List.nil();
        }
        LinkedHashMap<Class, ListBuffer<JCAnnotation>> annotationSet = new LinkedHashMap<>();
        if (annotationList != null) {
            if (nat && annotationList.getAnonymousAnnotation() != null) {
                transformAnonymousAnnotation(annotationList.getAnonymousAnnotation(), annotationSet);
            }
            if (annotationList.getAnnotations() != null) {
                for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                    EnumSet<OutputElement> possibleTargets = AnnotationUtil.interopAnnotationTargeting(outputs, annotation, false);
                    if ((nat
                            && possibleTargets == null)
                            || 
                            (possibleTargets != null 
                                && possibleTargets.equals(EnumSet.of(target)))) {
                        transformAnnotation(annotation, annotationSet);
                    }
                }
            }
        }
        ListBuffer<JCAnnotation> result = ListBuffer.lb();
        for (Class annotationClass : annotationSet.keySet()) {
            ListBuffer<JCAnnotation> annotations = annotationSet.get(annotationClass);
            if (isSequencedAnnotation(annotationClass)) {
                JCAnnotation wrapperAnnotation = make().Annotation(
                        makeJavaType(annotationClass.getType(), JT_ANNOTATIONS), 
                        List.<JCExpression>of(make().NewArray(null,  null, (List)annotations.toList())));
                result.append(wrapperAnnotation);
            } else {
                if (annotations.size() > 1) {
                    makeErroneous(annotationList, "compiler bug: multiple occurances of non-sequenced annotation class " + annotationClass.getQualifiedNameString());
                }
                result.appendList(annotations);
            }
        }
        
        // Special case: Generate a @java.lang.Deprecated() if Ceylon deprecated
        if (annotationList != null) {
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                if (nat && isDeprecatedAnnotation(annotation.getPrimary())) {
                    result.append(make().Annotation(make().Type(syms().deprecatedType), List.<JCExpression>nil()));
                }
            }
        }
        
        return result.toList();
    }
    
    void transformAnnotation(Tree.Annotation invocation, 
            Map<Class, ListBuffer<JCAnnotation>> annotationSet) {
        at(invocation);
        try {
            JCAnnotation annotation = AnnotationInvocationVisitor.transformConstructor(this, invocation);
            if (annotation != null) {
                Class annotationClass = AnnotationInvocationVisitor.annoClass(invocation);
                putAnnotation(annotationSet, annotation, annotationClass);
            }
        } catch (BugException e) {
            e.addError(invocation);
        }
    }

    /**
     * Returns true if the given primary is {@code ceylon.language.deprecated()}
     */
    private boolean isDeprecatedAnnotation(Tree.Primary primary) {
        return primary instanceof Tree.BaseMemberExpression
                && typeFact().getLanguageModuleDeclaration("deprecated").equals(((Tree.BaseMemberExpression)primary).getDeclaration());
    }

    private void putAnnotation(
            Map<Class, ListBuffer<JCAnnotation>> annotationSet,
            JCAnnotation annotation, Class annotationClass) {
        ListBuffer<JCAnnotation> list = annotationSet.get(annotationClass);
        if (list == null) {
            list = ListBuffer.lb();
        }
        annotationSet.put(annotationClass, list.append(annotation));
    }


    public void transformAnonymousAnnotation(Tree.AnonymousAnnotation annotation, Map<Class, ListBuffer<JCAnnotation>> annos) {
        Type docType = ((TypeDeclaration)typeFact().getLanguageModuleDeclaration("DocAnnotation")).getType();
        JCAnnotation docAnnotation = at(annotation).Annotation(
                makeJavaType(docType,  JT_ANNOTATION), 
                List.<JCExpression>of(make().Assign(naming.makeUnquotedIdent("description"),
                        transform(annotation.getStringLiteral()))));
        putAnnotation(annos, docAnnotation, (Class)docType.getDeclaration());
    }
    
    public JCExpression makeMetaLiteralStringLiteralForAnnotation(Tree.MetaLiteral literal) {
        String ref = getSerializedMetaLiteral(literal);
        if (ref != null) {
            return make().Literal(ref);
        }
        return makeErroneous(literal, "compiler bug: " + literal.getNodeType() + " is not a supported meta literal");
    }

    public static Referenceable getMetaLiteralReferenceable(Tree.MetaLiteral ml) {
        if (ml instanceof Tree.TypeLiteral) {
            return ml.getDeclaration();
        } else if (ml instanceof Tree.MemberLiteral) {
            return ml.getDeclaration();
        } else if (ml instanceof Tree.PackageLiteral) {
            return ((Tree.PackageLiteral)ml).getImportPath().getModel();
        } else if (ml instanceof Tree.ModuleLiteral) {
            return ((Tree.ModuleLiteral)ml).getImportPath().getModel();
        } 
        return null;
    }
    
    public static String getSerializedMetaLiteral(Tree.MetaLiteral ml) {
        return serializeReferenceable(getMetaLiteralReferenceable(ml));
    }
    
    public static String serializeReferenceable(Referenceable ref) {
        StringBuilder sb = new StringBuilder();
        if (ref instanceof Declaration) {
            appendDeclarationLiteralForAnnotation((Declaration)ref, sb);
        } else if (ref instanceof Package) {
            appendDeclarationLiteralForAnnotation((Package)ref, sb);
        } else if (ref instanceof Module) {
            appendDeclarationLiteralForAnnotation((Module)ref, sb);
        }
        return sb.toString();
    }
    
    public JCExpression makeDeclarationLiteralForAnnotation(Package decl) {
        StringBuilder sb = new StringBuilder();
        appendDeclarationLiteralForAnnotation(decl, sb);
        return make().Literal(sb.toString());
    }
    
    public JCExpression makeDeclarationLiteralForAnnotation(Module decl) {
        StringBuilder sb = new StringBuilder();
        appendDeclarationLiteralForAnnotation(decl, sb);
        return make().Literal(sb.toString());
    }
    
    /*
 * ref              ::= version? module ;
 *                      // note: version is optional to support looking up the
 *                      // runtime version of a package, once we support this
 * version          ::= ':' SENTINEL ANYCHAR* SENTINEL ;
 * module           ::= dottedIdent package? ;
 * dottedIdent      ::= ident ('.' ident)* ;
 * package          ::= ':' ( relativePackage | absolutePackage ) ? ( ':' declaration ) ? ;
 *                      // note: if no absolute or relative package given, it's the 
 *                      // root package of the module
 * relativePackage  ::= dottedIdent ;
 * absolutePackage  ::= '.' dottedIdent ;
 *                      // note: to suport package names which don't start 
 *                      // with the module name
 * declaration      ::= type | function | value ;
 * type             ::= class | interface ;
 * class            ::= 'C' ident ( '.' member )?
 * interface        ::= 'I' ident ( '.' member )?
 * member           ::= declaration ;
 * function         ::= 'F' ident ;
 * value            ::= 'V' ident ;
     */
    /**
     * Appends into the given builder a String representation of the given 
     * module, suitable for parsing my the DeclarationParser.
     */
    private static void appendDeclarationLiteralForAnnotation(Module module,
            StringBuilder sb) {
        char sentinel = findSentinel(module);
        sb.append(":").append(sentinel).append(module.getVersion()).append(sentinel).append(module.getNameAsString());
    }

    /**
     * Computes a sentinel for the verion number
     */
    private static char findSentinel(Module module) {
        for (char ch : ":\"\'/#!$%\\@~+=*".toCharArray()) {
            if (module.getVersion().indexOf(ch) == -1) {
                return ch;
            }
        }
        // most unlikely end end up here
        char ch = 1;
        while (true) {
            if (module.getVersion().indexOf(ch) == -1) {
                return ch;
            }
            ch++;
        }
    }
    
    /**
     * Appends into the given builder a String representation of the given 
     * package, suitable for parsing my the DeclarationParser.
     */
    private static void appendDeclarationLiteralForAnnotation(Package pkg,
            StringBuilder sb) {
        appendDeclarationLiteralForAnnotation(pkg.getModule(), sb);
        sb.append(':');
        String moduleName = pkg.getModule().getNameAsString();
        String packageName = pkg.getNameAsString();
        if (packageName.equals(moduleName)) {
        } else if (packageName.startsWith(moduleName)) {
            sb.append(packageName.substring(moduleName.length()+1));
        } else {
            sb.append('.').append(packageName);
        }
    }
    
    /**
     * Appends into the given builder a String representation of the given 
     * declaration, suitable for parsing my the DeclarationParser.
     */
    private static void appendDeclarationLiteralForAnnotation(Declaration decl, StringBuilder sb) {
        Scope container = decl.getContainer();
        while (true) {
            if (container instanceof Declaration) {
                appendDeclarationLiteralForAnnotation((Declaration)container, sb);
                sb.append(".");
                break;
            } else if (container instanceof Package) {
                appendDeclarationLiteralForAnnotation((Package)container, sb);
                sb.append(":");
                break;
            }
            container = container.getContainer();
        }
        if (decl instanceof Class) {
            sb.append("C").append(decl.getName());
        } else if (decl instanceof Interface) {
            sb.append("I").append(decl.getName());
        } else if (decl instanceof TypeAlias) {
            sb.append("A").append(decl.getName());
        } else if (decl instanceof Value) {
            sb.append("V").append(decl.getName());
        } else if (decl instanceof Function) {
            sb.append("F").append(decl.getName());
        } else if (decl instanceof TypeParameter) {
            sb.append("P").append(decl.getName());
        } else if (decl instanceof Constructor) {
            sb.append("c").append(decl.getName());
        } else {
            throw BugException.unhandledDeclarationCase(decl);
        }
    }
    
    JCExpression makePrivateAccessUpcast(Tree.StaticMemberOrTypeExpression qmte, JCExpression qual) {
        Type pt = Decl.getPrivateAccessType(qmte);
        // By definition the member has private access, so if it's an interface
        // member we want the companion.
        return make().TypeCast(makeJavaType(pt, JT_COMPANION | JT_RAW), qual);
    }

    public JCTree transform(Tree.ObjectExpression expr) {
        at(expr);
        List<JCTree> klass = classGen().transformObjectExpression(expr);
        at(expr);
        JCExpression newCall = make().NewClass(null, null, makeUnquotedIdent(naming.escapeClassName(expr.getAnonymousClass().getName())+"_"), List.<JCTree.JCExpression>nil(), null);
        return make().LetExpr((List)klass, newCall);
    }

    public JCExpression transform(Tree.IfExpression op) {
        return transformIf(op, op.getTypeModel());
    }
    
    // this one does not trust the expected type
    public JCExpression transform(Tree.IfExpression op, Type expectedType) {
        return transformIf(op, getExpectedTypeForJavaNullChecks(op, expectedType));
    }
    
    // this one trusts the expected type
    private JCExpression transformIf(Tree.IfExpression op, Type expectedType) {
        String tmpVar = naming.newTemp("ifResult");
        Tree.Expression thenPart = op.getIfClause().getExpression();
        Tree.Expression elsePart = op.getElseClause() != null ? op.getElseClause().getExpression() : null;
        Tree.Variable elseVar = op.getElseClause() != null ? op.getElseClause().getVariable() : null;
        java.util.List<Tree.Condition> conditions = op.getIfClause().getConditionList().getConditions();
        List<JCStatement> statements = statementGen().transformIf(conditions, thenPart, elseVar, elsePart, tmpVar, op, expectedType);
        at(op);
        JCExpression vartype = makeJavaType(typeFact().denotableType(expectedType), CodegenUtil.getBoxingStrategy(op) == BoxingStrategy.UNBOXED ? 0 : JT_NO_PRIMITIVES);
        return make().LetExpr(make().VarDef(make().Modifiers(0), names().fromString(tmpVar), vartype , null), statements, makeUnquotedIdent(tmpVar));
    }

    public JCExpression transform(Tree.SwitchExpression op) {
        return transformSwitch(op, op.getTypeModel());
    }

    // this one does not trust the expected type
    public JCExpression transform(Tree.SwitchExpression op, Type expectedType) {
        return transformSwitch(op, getExpectedTypeForJavaNullChecks(op, expectedType));
    }
    
    // this one trusts the expected type
    private JCExpression transformSwitch(Tree.SwitchExpression op, Type expectedType) {
        String tmpVar = naming.newTemp("ifResult");
        JCStatement switchExpr = statementGen().transform(op, op.getSwitchClause(), op.getSwitchCaseList(), tmpVar, op, expectedType);
        at(op);
        JCExpression vartype = makeJavaType(typeFact().denotableType(expectedType), CodegenUtil.getBoxingStrategy(op) == BoxingStrategy.UNBOXED ? 0 : JT_NO_PRIMITIVES);
        return make().LetExpr(make().VarDef(make().Modifiers(0), names().fromString(tmpVar), vartype , null), 
                              List.<JCStatement>of(switchExpr), makeUnquotedIdent(tmpVar));
    }

    public JCExpression transform(Tree.LetExpression op) {
        return transformLet(op, op.getTypeModel());
    }

    // this one does not trust the given expected type
    public JCExpression transform(LetExpression op, Type expectedType) {
        return transformLet(op, getExpectedTypeForJavaNullChecks(op, expectedType));
    }
    
    private Type getExpectedTypeForJavaNullChecks(Term op, Type expectedType) {
        // turns the expression type into an optional if it's not already, and if the
        // expected type allows nulls and the term contains unchecked nulls
        if(expectedType == null 
                || !isOptional(expectedType) 
                || !containsUncheckedNulls(op))
            return op.getTypeModel();
        else
            return typeFact().getOptionalType(op.getTypeModel());
    }

    // this one trusts the expected type
    private JCExpression transformLet(LetExpression op, Type expectedType) {
        ListBuffer<JCStatement> defs = new ListBuffer<JCStatement>();
        for(Tree.Statement stmt : op.getLetClause().getVariables()){
            defs.addAll(statementGen().transformVariableOrDestructure(stmt));
        }
        Tree.Term term = op.getLetClause().getExpression().getTerm();
        BoxingStrategy boxingStrategy = CodegenUtil.getBoxingStrategy(term);
        JCExpression expr = transformExpression(term, boxingStrategy, expectedType);
        at(op);
        if (isAnything(op.getTypeModel()) 
                && CodegenUtil.isUnBoxed(term)) {
            defs.add(make().Exec(expr));
            expr = makeNull();
        }
        return make().LetExpr(defs.toList(), expr);
    }
    
}
