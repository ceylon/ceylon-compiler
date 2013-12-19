package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;

import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class ValueTransformer extends AbstractTransformer {

    public static ValueTransformer getInstance(Context context) {
        ValueTransformer trans = context.get(ValueTransformer.class);
        if (trans == null) {
            trans = new ValueTransformer(context);
            context.put(ValueTransformer.class, trans);
        }
        return trans;
    }

    private ValueTransformer(Context context) {
        super(context);
    }
    /*
    public void transform(Tree.AnyAttribute value) {
        Value decl = value.getDeclarationModel();
        if (Decl.isLocal(decl)) {
            if (!decl.isTransient()) {
                // A variable decl
                JCVariableDecl varTree = localVariable.transform(value);
            } else {
                // A local getter
                JCMethodDecl getterTree = localGetter.transform(value);
            }
        } else if (decl.isToplevel()) {
            JCMethodDecl getterTree = toplevelGetter.transform(value);
            if (!decl.isTransient()) {
                JCVariableDecl fieldTree = toplevelField.transform(value);
            }
        } else if (decl.isClassMember()) {
            JCMethodDecl getterTree = classGetter.transform(value);
            if (!decl.isTransient()) {
                JCVariableDecl fieldTree = classField.transform(value);
            }
        } else if (decl.isInterfaceMember()) {
            JCMethodDecl getterTree = interfaceGetter.transform(value);
        }
    }
    
    public void transform(Tree.AttributeSetterDefinition setter) {
        Setter decl = setter.getDeclarationModel();
        if (Decl.isLocal(decl)) {
            // A local setter
            JCMethodDecl setterTree = localSetter.transform(setter);
        } else if (decl.isToplevel()) {
            // need to add setter to the top level getter's class
            JCMethodDecl setterTree = toplevelSetter.transform(setter);
        } else if (decl.isClassMember()) {
            JCMethodDecl setterTree = classSetter.transform(setter);
        } else if (decl.isInterfaceMember()) {
            JCMethodDecl setterTree = interfaceSetter.transform(setter);
        }
    }*/

    /* Getter transformations */
    
    /** Base class for transforming getters */
    abstract class GetterTransformation {
        public JCMethodDecl transform(Tree.AnyAttribute value) {
            MethodDefinitionBuilder builder = makeBuilder(value.getDeclarationModel());
            transformAnnotations(value, builder);
            transformModifiers(value.getDeclarationModel(), builder);
            transformTypeParameters(value.getDeclarationModel(), builder);
            transformResultType(value.getDeclarationModel(), builder);
            transformParameters(value.getDeclarationModel(), builder);
            transformBody(value, builder);
            return builder.build();
        }

        protected void transformBody(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            List<JCStatement> stats;
            if (value instanceof Tree.AttributeGetterDefinition
                    && ((Tree.AttributeGetterDefinition)value).getBlock() != null) {
                Tree.Block block = ((Tree.AttributeGetterDefinition)value).getBlock();
                stats = statementGen().transformBlock(block);
                builder.body(stats);
            } else if (value instanceof Tree.AttributeDeclaration
                    && ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression() instanceof Tree.SpecifierExpression) {
                Tree.SpecifierExpression expression = (Tree.SpecifierExpression)((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression();
                BoxingStrategy boxing = CodegenUtil.getBoxingStrategy(value.getDeclarationModel());
                ProducedType type = value.getDeclarationModel().getType();
                JCExpression transExpr = expressionGen().transformExpression(expression.getExpression(), boxing, type);
                stats = List.<JCStatement>of(make().Return(transExpr));
                builder.body(stats);
            }
        }

        protected void transformParameters(Value value, MethodDefinitionBuilder builder) {
            // No parameters
        }

        protected void transformResultType(Value value, MethodDefinitionBuilder builder) {
            int typeFlags = 0;
            ProducedTypedReference typedRef = getTypedReference(value);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
            if (!CodegenUtil.isUnBoxed(nonWideningTypedRef.getDeclaration())) {
                typeFlags |= AbstractTransformer.JT_NO_PRIMITIVES;
            }
            boolean isHash = CodegenUtil.isHashAttribute(value);
            JCExpression attrType;
            JCExpression attrTypeRaw;
            // make sure we generate int getters for hash
            if(isHash){
                attrType = attrTypeRaw = make().Type(syms().intType);
            }else{
                attrType = makeJavaType(nonWideningType, typeFlags);
                attrTypeRaw = makeJavaType(nonWideningType, AbstractTransformer.JT_RAW);
            }
            builder.resultType(attrType, value);
        }

        protected void transformTypeParameters(Value value, MethodDefinitionBuilder builder) {
            // No type parameters
        }

        protected abstract void transformModifiers(Value value, MethodDefinitionBuilder builder);

        protected void transformAnnotations(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            Value model = value.getDeclarationModel();
            builder.userAnnotations(expressionGen().transform(value.getAnnotationList()));
            builder.isOverride(model.isActual())
                .isTransient(Decl.isTransient(model))
                .modelAnnotations(model.getAnnotations());
        }
        
        protected MethodDefinitionBuilder makeBuilder(Value value) {
            return MethodDefinitionBuilder.getter(ValueTransformer.this, value, false);
        }
    }
    
    /** 
     * Transformation for toplevel simple values and getters. 
     * Assumes the {@link ToplevelField} transformation generates the field 
     */
    class ToplevelGetter extends GetterTransformation{

        protected void transformAnnotations(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            // This is wrong: We say @Ignore, but then call super?
            builder.modelAnnotations(makeAtIgnore());
            super.transformAnnotations(value, builder);
        }
        
        @Override
        protected void transformModifiers(Value value,
                MethodDefinitionBuilder builder) {
            builder.modifiers(STATIC | PUBLIC);
        }
        
        @Override
        protected void transformBody(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            if (value.getDeclarationModel().isTransient()) {
                super.transformBody(value, builder);
            } else {
                JCStatement ret = make().Return(make().Indexed(makeUnquotedIdent("value"), make().Literal(0)));
                JCCatch catcher = make().Catch(makeVar("ex", make().Type(syms().nullPointerExceptionType), null), 
                        make().Block(0, List.<JCStatement>of(make().Throw(
                                make().NewClass(null, List.<JCExpression>nil(),
                                        make().Type(syms().ceylonInitializationExceptionType),
                                        List.<JCExpression>of(make().Literal(
                                                value.getDeclarationModel().isLate() ? "Accessing uninitialized \'late\' attribute" : "Cyclic initialization")),
                                        null)))));
                
                builder.body(make().Try(
                        make().Block(0, List.of(ret)),
                        List.of(catcher),
                        null));
            }
        }
    }
    private ToplevelGetter toplevelGetter = null;
    ToplevelGetter toplevelGetter() {
        if (toplevelGetter == null) {
            toplevelGetter = new ToplevelGetter();
        }
        return toplevelGetter;
    }
    /*
    class ClassGetter extends GetterTransformation{
        
    }
    private final ClassGetter classGetter = new ClassGetter();
    class InterfaceGetter extends GetterTransformation{
        
    }
    private final InterfaceGetter interfaceGetter = new InterfaceGetter();
    class LocalGetter extends GetterTransformation{
        
    }
    private final LocalGetter localGetter = new LocalGetter();
     */
    
    /* Setter transformations */
    
    /** Base class for transforming setters */
    abstract class SetterTransformation {
        public JCMethodDecl transform(Value value, Tree.AttributeSetterDefinition setter) {
            MethodDefinitionBuilder builder = makeBuilder(value);
            transformAnnotations(value, setter, builder);
            transformModifiers(value, builder);
            transformTypeParameters(value, builder);
            transformResultType(value, builder);
            transformParameters(value, builder);
            transformBody(value, setter, builder);
            return builder.build();
        }

        protected void transformBody(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            List<JCStatement> stats;
            if (setter.getBlock() != null) {
                stats = statementGen().transformBlock(setter.getBlock());
                builder.body(stats);
            } else if (setter.getSpecifierExpression() != null) {
                ProducedType type = value.getType();
                JCExpression transExpr = expressionGen().transformExpression(setter.getSpecifierExpression().getExpression(), BoxingStrategy.INDIFFERENT, type);
                stats = List.<JCStatement>of(make().Exec(transExpr));
                builder.body(stats);
            }
        }

        protected void transformParameters(Value value, MethodDefinitionBuilder builder) {
            String attrName = value.getName();
            ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.systemParameter(ValueTransformer.this, attrName);
            pdb.modifiers(FINAL);
            pdb.aliasName(attrName);
            ProducedTypedReference typedRef = getTypedReference(value);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
            pdb.type(MethodDefinitionBuilder.paramType(ValueTransformer.this, nonWideningTypedRef.getDeclaration(), nonWideningType, 0, true), 
                    ValueTransformer.this.makeJavaTypeAnnotations(value));
            builder.parameter(pdb);
        }
        
        protected void transformResultType(Value value, MethodDefinitionBuilder builder) {
            // void
        }
        
        protected void transformTypeParameters(Value value, MethodDefinitionBuilder builder) {
        }
        
        protected abstract void transformModifiers(Value value, MethodDefinitionBuilder builder);
        
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            // only actual if the superclass is also variable
            builder.isOverride(value.isActual() && ((TypedDeclaration)value.getRefinedDeclaration()).isVariable());
            if (setter != null) {
                builder.userAnnotations(expressionGen().transform(setter.getAnnotationList()));
            }
        }
        
        protected MethodDefinitionBuilder makeBuilder(
                Value value) {
            return MethodDefinitionBuilder
                    .setter(ValueTransformer.this, value);
        }
    }
    
    /** 
     * Transformation for toplevel simple values or setters.
     */
    class ToplevelSetter extends SetterTransformation{
        @Override
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            builder.modelAnnotations(makeAtIgnore());
            super.transformAnnotations(value, setter, builder);
        }
        @Override
        protected void transformModifiers(Value value,
                MethodDefinitionBuilder builder) {
            builder.modifiers(STATIC | PUBLIC);
        }
        @Override
        protected void transformBody(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            if (setter!= null ) {
                super.transformBody(value, setter, builder);
            } else if (value.isVariable() 
                    || value.isLate()) {
                ListBuffer<JCStatement> body = ListBuffer.<JCStatement>lb();
                JCStatement init = make().Exec(make().Assign(makeUnquotedIdent("value"),
                        make().NewArray(makeJavaType(value.getType(), JT_RAW), List.<JCExpression>of(make().Literal(1)), null)));
                if (value.isLate()) {
                    if (value.isVariable()) {
                        body.add(make().If(
                                make().Binary(JCTree.EQ, makeUnquotedIdent("value"), makeNull()), 
                                init, 
                                null));
                    } else {
                        body.add(make().If(
                                make().Binary(JCTree.NE, makeUnquotedIdent("value"), makeNull()), 
                                make().Throw(make().NewClass(null, 
                                        List.<JCExpression>nil(), 
                                        make().Type(syms().ceylonInitializationExceptionType), List.<JCExpression>of(make().Literal("Re-initialization of \'late\' attribute")), 
                                        null)), 
                                null));
                        body.add(init);
                    }
                }
                body.add(make().Exec(make().Assign(make().Indexed(makeUnquotedIdent("value"), make().Literal(0)), 
                        makeUnquotedIdent(value.getName()))));
                builder.body(body.toList());
            }
        }
    }
    private ToplevelSetter toplevelSetter = null;
    ToplevelSetter toplevelSetter() {
        if (toplevelSetter == null) {
            toplevelSetter = new ToplevelSetter();
        }
        return toplevelSetter;
    }
    
    
    
    /*
    class ClassSetter extends SetterTransformation{
        
    }
    private final ClassSetter classSetter = new ClassSetter();
    
    
    class InterfaceSetter extends SetterTransformation{
        
    }
    private final InterfaceSetter interfaceSetter = new InterfaceSetter();
    
    class LocalSetter extends SetterTransformation{
        
    }
    private final LocalSetter localSetter = new LocalSetter();
    */
    /* Variable (and field) transformations */
    
    /**
     * Baseclass for value transformation which need variables or fields.
     */
    abstract class VariableTransformation {
        public List<JCStatement> transformInit(Tree.AnyAttribute setter) {
            return List.<JCStatement>nil();
        }
        
        public JCVariableDecl transform(Tree.AnyAttribute setter) {
            return makeVar(
                    getModifiers(setter.getDeclarationModel()), 
                    "value",
                    makeType(setter.getDeclarationModel()), 
                    makeInitialValue(setter));
        }

        protected abstract JCExpression makeInitialValue(Tree.AnyAttribute setter);

        protected JCExpression makeType(Value value) {
            return makeJavaType(value.getType());
        }

        protected String getVariableName(Value value) {
            return Naming.getSetterName(value);
        }

        protected abstract long getModifiers(Value value);
    }
    
    /**
     * Transformation for toplevel simple values.
     */
    class ToplevelField extends VariableTransformation {
        public List<JCStatement> transformInit(Tree.AnyAttribute value) {
            JCExpression init = null;
            if (value instanceof Tree.AttributeDeclaration) {
                Tree.SpecifierOrInitializerExpression specOrInit = ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression();
                if (specOrInit != null) {
                    Tree.Expression expr = specOrInit.getExpression();
                    init = expressionGen().transformExpression(value.getDeclarationModel(), expr.getTerm());
                }
            }
            
            if (init != null) {
                return List.<JCStatement>of(make().Block(STATIC, List.<JCStatement>of(
                        make().Exec(make().Assign(makeUnquotedIdent("value"), 
                            make().NewArray(makeJavaType(value.getDeclarationModel().getType(), JT_RAW), 
                                    List.<JCExpression>nil(), 
                                    List.<JCExpression>of(init)))))));
            } else {
                return List.<JCStatement>nil();
            }
        }
        @Override
        protected JCExpression makeInitialValue(Tree.AnyAttribute value) {
            return null;
        }
        
        protected JCExpression makeType(Value value) {
            return make().TypeArray(makeJavaType(value.getType()));
        }
        
        @Override
        protected long getModifiers(Value value) {
            return PRIVATE | STATIC | (value.isVariable() || value.isLate() ? 0 : FINAL);
        }
    }
    private ToplevelField toplevelField = null;
    ToplevelField toplevelField() {
        if (toplevelField == null) {
            toplevelField = new ToplevelField();
        }
        return toplevelField;
    }
    /*
    class ClassField extends VariableTransformation {
        @Override
        protected long getModifiers(Value value) {
            return value.isVariable() ? 0 : FINAL;
        }
        
        @Override
        protected JCExpression makeInitialValue(AnyAttribute setter) {
            return null;
        }
    }
    private final ClassField classField = new ClassField();
    
    class LocalVariable extends VariableTransformation {
        @Override
        protected long getModifiers(Value value) {
            return STATIC | (value.isVariable() ? 0 : FINAL);
        }
        
        @Override
        protected JCExpression makeInitialValue(Tree.AnyAttribute value) {
            JCExpression result = null;
            if (value instanceof Tree.AttributeDeclaration
                    && ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression() instanceof Tree.InitializerExpression) {
                Tree.InitializerExpression init = (Tree.InitializerExpression)((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression();
                // TODO Erasure and boxing
                result = expressionGen().transformExpression(init.getExpression().getTerm());
            }
            return result;
        }
    }
    private final LocalVariable localVariable = new LocalVariable();
    */
    
    /**
     * Transformation for a toplevel value. Generates a wrapper class around
     * a getter method (generated by {@link ToplevelGetter}) a 
     * setter method (generated by {@link ToplevelSetter}) and a field
     * (generated by {@link ToplevelField}) as required.
     */
    public List<JCTree> transformToplevel(Tree.AnyAttribute getter, Tree.AttributeSetterDefinition setter) {
        Value getterModel = getter.getDeclarationModel();
        ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder.klass(ValueTransformer.this, Naming.getAttrClassName(getterModel, 0), null);
        classBuilder
            .modifiers(FINAL | (getterModel.isShared() ? PUBLIC : 0))
            .constructorModifiers(PRIVATE)
            .annotations(makeAtAttribute());
        
        if (getter instanceof Tree.AttributeDeclaration
                && !getterModel.isTransient()) {
            classBuilder.defs(toplevelField().transform(getter));
            classBuilder.defs((List)toplevelField().transformInit(getter));
        }
        classBuilder.defs(toplevelGetter().transform(getter));
        if (setter != null
                || getterModel.isVariable() || getterModel.isLate()) {
            classBuilder.defs(toplevelSetter().transform(getterModel, setter));
        }
        return classBuilder.build();
    }
}
