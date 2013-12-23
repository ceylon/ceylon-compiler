package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.*;

import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnyAttribute;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeSetterDefinition;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCReturn;
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
        public abstract VariableTransformation field();
        
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

        protected ProducedType getType(Value value) {
            ProducedTypedReference typedRef = getTypedReference(value);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
            return nonWideningType;
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
                ProducedType type = getType(value.getDeclarationModel());
                JCExpression transExpr = expressionGen().transformExpression(expression.getExpression(), boxing, type);
                transExpr = CodegenUtil.isHashAttribute(value.getDeclarationModel()) ? convertToIntForHashAttribute(transExpr) : transExpr;
                stats = List.<JCStatement>of(make().Return(transExpr));
                builder.body(stats);
            }
        }
        
        protected void transformLateBody(Tree.AnyAttribute value, String exceptionMessage, MethodDefinitionBuilder builder) {
            JCStatement ret = make().Return(make().Indexed(makeUnquotedIdent(field().getVariableName(value.getDeclarationModel())), make().Literal(0)));
            JCCatch catcher = make().Catch(makeVar("ex", make().Type(syms().nullPointerExceptionType), null), 
                    make().Block(0, List.<JCStatement>of(make().Throw(
                            make().NewClass(null, List.<JCExpression>nil(),
                                    make().Type(syms().ceylonInitializationExceptionType),
                                    List.<JCExpression>of(make().Literal(exceptionMessage)),
                                    null)))));
            
            builder.body(make().Try(
                    make().Block(0, List.of(ret)),
                    List.of(catcher),
                    null));
        }
        
        protected void transformParameters(Value value, MethodDefinitionBuilder builder) {
            // No parameters
        }
        
        protected final void transformResultType(Value value, MethodDefinitionBuilder builder) {
            JCExpression attrType = makeType(value);
            builder.resultType(attrType, value);
        }
        
        protected JCExpression makeType(Value value) {
            return makeJavaType(getType(value));
        }
        
        protected void transformTypeParameters(Value value, MethodDefinitionBuilder builder) {
            // No type parameters
        }
        
        protected final void transformModifiers(Value value, MethodDefinitionBuilder builder) {
            long mods = getVisibility(value) & (PUBLIC | PROTECTED | PRIVATE);
            if (isFinal(value)) {
                mods |= FINAL;
            }
            if (isStatic(value)) {
                mods |= STATIC;
            }
            if (isAbstract(value)) {
                mods |= ABSTRACT;
            }
            builder.modifiers(mods);
        }

        protected abstract boolean isAbstract(Value value);

        protected abstract boolean isStatic(Value value);
        
        protected abstract boolean isFinal(Value value);
        
        protected abstract long getVisibility(Value value);

        protected void transformAnnotations(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            Value model = value.getDeclarationModel();
            builder.userAnnotations(expressionGen().transform(value.getAnnotationList()));
            builder.isOverride(isOverride(model))
                .isTransient(Decl.isTransient(model))
                .modelAnnotations(model.getAnnotations());
        }

        protected boolean isOverride(Value model) {
            return model.isActual();
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
        private VariableTransformation toplevelField = null;
        
        @Override
        public VariableTransformation field() {
            if (toplevelField == null) {
                toplevelField = new ToplevelField();
            }
            return toplevelField;
        }
        @Override
        protected void transformAnnotations(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            // This is wrong: We say @Ignore, but then call super?
            builder.modelAnnotations(makeAtIgnore());
            super.transformAnnotations(value, builder);
        }
        
        @Override
        protected boolean isStatic(Value value) {
            return true;
        }
        
        @Override
        protected boolean isFinal(Value value) {
            return false;
        }
        
        @Override
        protected boolean isAbstract(Value value) {
            return false;
        }
        
        @Override
        protected long getVisibility(Value value) {
            return PUBLIC;
        }
        
        @Override
        protected void transformBody(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            if (value.getDeclarationModel().isTransient()) {
                super.transformBody(value, builder);
            } else {
                transformLateBody(value,  
                        value.getDeclarationModel().isLate() ? "Accessing uninitialized \'late\' attribute" : "Cyclic initialization", builder);
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
    
    class ClassGetter extends GetterTransformation {
        VariableTransformation classField = null;
        @Override
        public VariableTransformation field() {
            classField = new ClassField();
            return classField;
        }
        
        @Override
        protected JCExpression makeType(Value value) {
            ProducedType nonWideningType = getType(value);
            return makeTypeforMember(value, nonWideningType);
        }
        @Override
        public boolean isAbstract(Value value) {
            return value.isFormal();
        }
        @Override
        public long getVisibility(Value value) {
            return value.isShared() ? PUBLIC : PRIVATE;
        }
        @Override
        public boolean isFinal(Value value) {
            return !value.isDefault() && !value.isFormal();
        }
        @Override
        public boolean isStatic(Value value) {
            return false;
        }
        @Override
        protected void transformBody(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            if (Decl.isIndirect(value)) {
                builder.body(generateIndirectGetterBlock(value.getDeclarationModel()));
            } else  if (value.getDeclarationModel().isTransient()) {
                super.transformBody(value, builder);
            } else if (value.getDeclarationModel().isLate()) {
                transformLateBody(value,  
                        "Accessing uninitialized \'late\' attribute", builder);
            } else {
                JCExpression expr = makeUnquotedIdent(field().getVariableName(value.getDeclarationModel()));
                expr = CodegenUtil.isHashAttribute(value.getDeclarationModel()) ? convertToIntForHashAttribute(expr) : expr;
                builder.body(make().Return(expr)); 
            }
        }
        
        private List<JCStatement> generateIndirectGetterBlock(Value v) {
            JCTree.JCExpression returnExpr;
            returnExpr = naming.makeQualIdent(naming.makeName(v, Naming.NA_WRAPPER), "get_");
            returnExpr = make().Apply(null, returnExpr, List.<JCExpression>nil());
            JCReturn returnValue = make().Return(returnExpr);
            List<JCStatement> stmts = List.<JCTree.JCStatement>of(returnValue);   
            return stmts;
        }
        
    }
    private ClassGetter classGetter = null;
    public ClassGetter classGetter() {
        if (classGetter == null) {
            classGetter = new ClassGetter();
        }
        return classGetter;
    }
    
    class InterfaceGetter extends GetterTransformation {

        @Override
        protected JCExpression makeType(Value value) {
            ProducedType nonWideningType = getType(value);
            return makeTypeforMember(value, nonWideningType);
        }
        
        @Override
        public JCMethodDecl transform(Tree.AnyAttribute value) {
            if (!value.getDeclarationModel().isShared()) {
                return null;
            }
            return super.transform(value);
        }
        
        @Override
        public VariableTransformation field() {
            return null;
        }
        
        @Override
        protected void transformBody(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            builder.noBody();
        }

        @Override
        protected boolean isAbstract(Value value) {
            return value.isFormal();
        }

        @Override
        protected boolean isStatic(Value value) {
            return false;
        }

        @Override
        protected boolean isFinal(Value value) {
            return false;
        }

        @Override
        protected long getVisibility(Value value) {
            return PUBLIC;
        }
        
    }
    
    private final InterfaceGetter interfaceGetter = new InterfaceGetter();
    
    class CompanionGetter extends GetterTransformation {

        @Override
        public JCMethodDecl transform(Tree.AnyAttribute value) {
            if (value.getDeclarationModel().isFormal()) {
                return null;
            }
            return super.transform(value);
        }
        
        @Override
        protected JCExpression makeType(Value value) {
            ProducedType nonWideningType = getType(value);
            return makeTypeforMember(value, nonWideningType);
        }
        
        @Override
        public VariableTransformation field() {
            return null;
        }
        
        @Override
        protected boolean isAbstract(Value value) {
            return false;
        }
        
        @Override
        protected boolean isStatic(Value value) {
            return false;
        }
        
        @Override
        protected boolean isFinal(Value value) {
            return true;
        }
        
        @Override
        protected long getVisibility(Value value) {
            return value.isShared() ? PUBLIC : PRIVATE;
        }
        
        @Override
        protected boolean isOverride(Value model) {
            return false;
        }
        
        @Override
        protected void transformAnnotations(Tree.AnyAttribute value, MethodDefinitionBuilder builder) {
            builder.noAnnotations();
            super.transformAnnotations(value, builder);
        }
    }
    
    private final CompanionGetter companionGetter = new CompanionGetter();
    
    class LocalGetter extends GetterTransformation{

        @Override
        public VariableTransformation field() {
            throw new RuntimeException();
        }

        @Override
        protected boolean isAbstract(Value value) {
            return false;
        }

        @Override
        protected boolean isStatic(Value value) {
            return false;
        }

        @Override
        protected boolean isFinal(Value value) {
            return false;
        }

        @Override
        protected long getVisibility(Value value) {
            return PUBLIC;
        }
        
    }
    private final LocalGetter localGetter = new LocalGetter();
    
    
    /* Setter transformations */
    
    /** Base class for transforming setters */
    abstract class SetterTransformation {
        public abstract GetterTransformation getter();
        public VariableTransformation field() {
            return getter().field();
        }
        
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
            String attrName = getParameterName(value);
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

        protected final String getParameterName(Value value) {
            return value.getName();
        }
        
        protected void transformResultType(Value value, MethodDefinitionBuilder builder) {
            // void
        }
        
        protected void transformTypeParameters(Value value, MethodDefinitionBuilder builder) {
        }
        
        protected final void transformModifiers(Value value, MethodDefinitionBuilder builder) {
            long mods = getVisibility(value) & (PUBLIC | PROTECTED | PRIVATE);
            if (isFinal(value)) {
                mods |= FINAL;
            }
            if (isStatic(value)) {
                mods |= STATIC;
            }
            if (isAbstract(value)) {
                mods |= ABSTRACT;
            }
            builder.modifiers(mods);
        }
        
        protected boolean isAbstract(Value value) {
            return getter().isAbstract(value);
        }
        protected boolean isStatic(Value value) {
            return getter().isStatic(value);
        }
        protected boolean isFinal(Value value) {
            return getter().isFinal(value);
        }
        protected long getVisibility(Value value) {
            return getter().getVisibility(value);
        }
        
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            // only actual if the superclass is also variable
            builder.isOverride(isOverride(value));
            if (setter != null) {
                builder.userAnnotations(expressionGen().transform(setter.getAnnotationList()));
            }
        }
        protected boolean isOverride(Value value) {
            return value.isActual() && ((TypedDeclaration)value.getRefinedDeclaration()).isVariable();
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
        public GetterTransformation getter() {
            return toplevelGetter();
        }
        
        @Override
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            builder.modelAnnotations(makeAtIgnore());
            super.transformAnnotations(value, setter, builder);
        }
        @Override
        protected void transformBody(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            if (setter!= null ) {
                super.transformBody(value, setter, builder);
            } else if (value.isVariable() 
                    || value.isLate()) {
                field().transformVariableOrLateBody(value, this, builder);
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
    
    
    class ClassSetter extends SetterTransformation{
        @Override
        public GetterTransformation getter() {
            return classGetter();
        }
        @Override
        protected boolean isAbstract(Value value) {
            return getter().isAbstract(value);
        }
        @Override
        protected boolean isStatic(Value value) {
            return getter().isStatic(value);
        }
        @Override
        protected boolean isFinal(Value value) {
            return getter().isFinal(value);
        }
        @Override
        protected long getVisibility(Value value) {
            return getter().getVisibility(value);
        }
        @Override
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            super.transformAnnotations(value, setter, builder);
            // Need @Ignore for a non-variable late value, 
            // so the model loader doesn't see the setter
            if (!value.isVariable() && value.isLate()) {
                builder.modelAnnotations(makeAtIgnore());
            }
        }
        
        @Override
        protected void transformBody(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            if (setter!= null ) {
                super.transformBody(value, setter, builder);
            } else if (value.isVariable() 
                    || value.isLate()) {
                field().transformVariableOrLateBody(value, this, builder);
            }
        }
    }
    private ClassSetter classSetter = null;
    public ClassSetter classSetter() {
        if (classSetter == null) {
            classSetter = new ClassSetter();
        }
        return classSetter;
    }
    
    
    class InterfaceSetter extends SetterTransformation{

        @Override
        public GetterTransformation getter() {
            return interfaceGetter;
        }

        @Override
        public JCMethodDecl transform(Value value, Tree.AttributeSetterDefinition setter) {
            if (!value.isShared()) {
                return null;
            }
            return super.transform(value, setter);
        }
        
        protected void transformBody(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            builder.noBody();
        }
    }
    private final InterfaceSetter interfaceSetter = new InterfaceSetter();
    
    class CompanionSetter extends SetterTransformation{

        @Override
        public GetterTransformation getter() {
            return companionGetter;
        }
        @Override
        public JCMethodDecl transform(Value value, Tree.AttributeSetterDefinition setter) {
            if (value.isFormal()) {
                return null;
            }
            return super.transform(value, setter);
        }
        
        protected boolean isOverride(Value value) {
            return false;
        }
        
        protected void transformAnnotations(Value value, Tree.AttributeSetterDefinition setter, MethodDefinitionBuilder builder) {
            builder.noModelAnnotations();
            super.transformAnnotations(value, setter, builder);
        }
    }
    private final CompanionSetter companionSetter = new CompanionSetter();
    
    class LocalSetter extends SetterTransformation{

        @Override
        public GetterTransformation getter() {
            return localGetter;
        }
        @Override
        public long getVisibility(Value value) {
            return PUBLIC;
        }
        
    }
    private final LocalSetter localSetter = new LocalSetter();
    
    /* Variable (and field) transformations */
    
    /**
     * Baseclass for value transformations which use a 
     * variable (which could be a member variable) 
     * for storing a value (i.e. Simple {@code Value}s).
     */
    abstract class VariableTransformation {
        public List<JCStatement> transformInit(Tree.AnyAttribute value) {
            return List.<JCStatement>nil();
        }
        
        public JCVariableDecl transformDeclaration(Tree.AnyAttribute value) {
            return make().VarDef(make().Modifiers(getDeclarationModifiers(value.getDeclarationModel()), transformAnnotations(value)),
                    names().fromString(getVariableName(value.getDeclarationModel())),
                    makeVariableType(value), 
                    makeDeclarationInitialValue(value));
        }
        
        /** Allocates storage when late */
        protected abstract JCExpression allocate(Value value);
        /** Tests the initialization of late storage */
        protected abstract JCExpression testInit(Value value, boolean initalized);
        /** Assigns the value to the storage */
        protected abstract JCExpression assign(Value value, SetterTransformation setterTransformation);
        /** 
         * For {@code late} variables, or {@code variable} top levels, 
         * we use an array to hold the value.
         */
        protected final void transformVariableOrLateBody(Value value,
                SetterTransformation setterTransformation,
                MethodDefinitionBuilder builder) {
            ListBuffer<JCStatement> body = ListBuffer.<JCStatement>lb();
            if (value.isLate()) {
                JCStatement init = make().Exec(allocate(value));
                if (value.isVariable()) {
                    body.add(make().If(
                            testInit(value, false), 
                            init, 
                            null));
                } else {
                    body.add(make().If(
                            testInit(value, true), 
                            make().Throw(make().NewClass(null, 
                                    List.<JCExpression>nil(), 
                                    make().Type(syms().ceylonInitializationExceptionType), List.<JCExpression>of(make().Literal("Re-initialization of \'late\' attribute")), 
                                    null)), 
                            null));
                    body.add(init);
                }
            }
            body.add(make().Exec(assign(value, setterTransformation)));
            builder.body(body.toList());
        }
        
        /**
         * Returns the name of the variable.
         */
        protected String getVariableName(Value value) {
            return value.getName();
        }
        /**
         * Makes the initial value for the variable to be used in the 
         * {@linkplain #transformDeclaration(AnyAttribute) variable declaration}
         * @param setter
         * @return
         */
        protected abstract JCExpression makeDeclarationInitialValue(Tree.AnyAttribute setter);
        /**
         * Makes the type of the variable used in the variable declaration.
         * This may not be the same as the type used for a corresponding 
         * getter.
         */
        protected abstract JCExpression makeVariableType(Tree.AnyAttribute value);
        /**
         * Returns the modifiers used in the declaration of the variable.
         */
        protected final long getDeclarationModifiers(Value value) {
            long flags = (getVisibility(value) & (PUBLIC | PROTECTED | PRIVATE));
            if (isStatic(value)) {
                flags |= STATIC;
            }
            if (isFinal(value)) {
                flags |= FINAL;
            }
            return flags;
        }
        /**
         * Returns the visibility flags for the variable declaration
         */
        protected long getVisibility(Value value) {
            return PRIVATE;
        }
        /**
         * Returns whether the variable declaration should be static
         */
        protected abstract boolean isStatic(Value value);
        /**
         * Returns whether the variable declaration should be final
         */
        protected boolean isFinal(Value value) {
            return !value.isVariable() && !value.isLate();
        }
        /**
         * Returns whether the annotations for the variable declaration
         */
        protected List<JCAnnotation> transformAnnotations(AnyAttribute value) {
            return List.<JCAnnotation>nil();
        }
    }

    class LocalVariable extends VariableTransformation {
        @Override
        public JCVariableDecl transformDeclaration(Tree.AnyAttribute value) {
            return super.transformDeclaration(value);
        }
        
        @Override
        protected JCExpression allocate(Value value) {
            throw new RuntimeException();
        }

        @Override
        protected JCExpression testInit(Value value, boolean initalized) {
            throw new RuntimeException();
        }

        @Override
        protected JCExpression assign(Value value,
                SetterTransformation setterTransformation) {
            throw new RuntimeException();
        }

        @Override
        protected JCExpression makeDeclarationInitialValue(AnyAttribute value) {
            JCExpression result = null;
            if (value instanceof Tree.AttributeDeclaration
                    && ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression() != null) {
                result = expressionGen().transformExpression(value.getDeclarationModel(), ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression().getExpression());
            } else if (value.getDeclarationModel().isParameter()) {
                Parameter p = CodegenUtil.findParamForDecl(value.getDeclarationModel());
                if (p != null) {
                    result = naming.makeName(p.getModel(), Naming.NA_MEMBER | Naming.NA_ALIASED);
                }
            } else if (value.getDeclarationModel().isVariable()) {
                result = statementGen().makeDefaultExprForType(value.getDeclarationModel().getType());
            }
            
            if (value.getDeclarationModel().isVariable()
                    && value.getDeclarationModel().isCaptured()) {
                JCExpression newBox = make().NewClass(
                        null, List.<JCExpression>nil(), 
                        makeVariableBoxType(value.getDeclarationModel()), result != null ? List.of(result) : List.<JCExpression>nil(), null);
                return newBox;
            }
            
            return result;
        }

        @Override
        protected JCExpression makeVariableType(AnyAttribute value) {
            if (value.getDeclarationModel().isVariable() && value.getDeclarationModel().isCaptured()) {
                return makeVariableBoxType(value.getDeclarationModel());
            }
            return makeJavaType(value.getDeclarationModel().getType());
        }

        protected final boolean isFinal(Value value) {
            return !value.isVariable() || value.isCaptured();
        }
        
        @Override
        protected boolean isStatic(Value value) {
            return false;
        }
        
        @Override
        protected long getVisibility(Value value) {
            return 0;
        }
    }
    private final LocalVariable localVariable = new LocalVariable();
    
    
    class ClassField extends VariableTransformation {
        // TODO The ctor need to use me to generate the initializing stmt
        protected String getVariableName(Value value) {
            if (value.isDeferred()) {
                return Naming.getAttrClassName(value, 0);
            }
            return value.getName();
        }
        
        @Override
        public JCVariableDecl transformDeclaration(Tree.AnyAttribute value) {
            if (value.getDeclarationModel().isFormal()) {
                return null;
            }
            return super.transformDeclaration(value);
        }
        @Override
        protected JCExpression makeVariableType(Tree.AnyAttribute s) {
            //////////////////////// encapsulate this fucker somehow
            Value value = s.getDeclarationModel();
            int typeFlags = 0;
            ProducedTypedReference typedRef = getTypedReference(value);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
            if (!CodegenUtil.isUnBoxed(nonWideningTypedRef.getDeclaration())) {
                typeFlags |= AbstractTransformer.JT_NO_PRIMITIVES;
            }
            
            
            ////////////////////////
            if (Decl.isIndirect(s)){
                nonWideningType = getGetterInterfaceType(value);
            }
            
            if (value.isLate()) {
                JCExpression attrTypeRaw = makeJavaType(nonWideningType, AbstractTransformer.JT_RAW);
                return make().TypeArray(attrTypeRaw);
            } else {
                JCExpression attrType = makeJavaType(nonWideningType, typeFlags);
                return attrType;
            }
        }
        @Override
        protected JCExpression makeDeclarationInitialValue(AnyAttribute setter) {
            // Always null, because we use the result of transformInit() in the ctor
            return null;
        }
        @Override
        public List<JCStatement> transformInit(Tree.AnyAttribute value) {
            if (value instanceof Tree.AttributeDeclaration
                    && ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression() != null) {
                JCExpression expr = expressionGen().transformExpression(value.getDeclarationModel(), ((Tree.AttributeDeclaration)value).getSpecifierOrInitializerExpression().getExpression());
                return List.<JCStatement>of(make().Exec(make().Assign(
                        makeQualIdent(naming.makeThis(), getVariableName(value.getDeclarationModel())),
                        expr)));
            }
                    
            return List.<JCStatement>nil();
        }
        @Override
        public JCExpression allocate(Value value) {
            return make().Assign(
                    makeQualIdent(naming.makeThis(), getVariableName(value)),
                    make().NewArray(makeJavaType(value.getType()), List.<JCExpression>of(make().Literal(1)), null));
        }
        @Override
        public JCExpression testInit(Value value, boolean initialized) {
            return make().Binary(initialized ? JCTree.NE : JCTree.EQ, makeQualIdent(naming.makeThis(), getVariableName(value)), makeNull());
        }
        @Override
        public JCExpression assign(Value value, SetterTransformation setterTransformation) {
            if (value.isLate()) {
                return make().Assign(
                        make().Indexed(makeQualIdent(naming.makeThis(), getVariableName(value)), make().Literal(0)),
                        makeUnquotedIdent(setterTransformation.getParameterName(value)));
            } else {
                return make().Assign(
                        makeQualIdent(naming.makeThis(), getVariableName(value)),
                        makeUnquotedIdent(setterTransformation.getParameterName(value)));
            }
        }
        @Override
        protected boolean isStatic(Value value) {
            return false;
        }
        /**
         * Returns whether the annotations for the variable declaration
         */
        protected List<JCAnnotation> transformAnnotations(AnyAttribute value) {
            if (!value.getDeclarationModel().isTransient()
                    && value.getDeclarationModel().isShared()) {
                return makeAtIgnore();
            } else {
                return super.transformAnnotations(value);
            }
            
        }
    }

    /**
     * Transformation for toplevel simple values.
     */
    class ToplevelField extends VariableTransformation {
        @Override
        protected String getVariableName(Value value) {
            return "value";
        }
        @Override
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
                        make().Exec(make().Assign(makeUnquotedIdent(getVariableName(value.getDeclarationModel())), 
                            make().NewArray(makeJavaType(value.getDeclarationModel().getType(), JT_RAW), 
                                    List.<JCExpression>nil(), 
                                    List.<JCExpression>of(init)))))));
            } else {
                return List.<JCStatement>nil();
            }
        }
        @Override
        protected JCExpression makeDeclarationInitialValue(Tree.AnyAttribute value) {
            return null;
        }
        @Override
        protected JCExpression makeVariableType(Tree.AnyAttribute value) {
            return make().TypeArray(makeJavaType(value.getDeclarationModel().getType()));
        }
        @Override
        protected boolean isStatic(Value value) {
            return true;
        }
        @Override
        public JCExpression allocate(Value value) {
            return make().Assign(
                    makeUnquotedIdent(getVariableName(value)),
                    make().NewArray(makeJavaType(value.getType()), List.<JCExpression>of(make().Literal(1)), null));
        }
        @Override
        public JCExpression testInit(Value value, boolean initialized) {
            return make().Binary(initialized ? JCTree.NE : JCTree.EQ, makeUnquotedIdent(getVariableName(value)), makeNull());
        }
        @Override
        public JCExpression assign(Value value, SetterTransformation setterTransformation) {
            return make().Assign(
                    make().Indexed(makeUnquotedIdent(getVariableName(value)), make().Literal(0)),
                    makeUnquotedIdent(setterTransformation.getParameterName(value)));
        }
    }

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
            classBuilder.defs(toplevelGetter().field().transformDeclaration(getter));
            classBuilder.defs((List)toplevelGetter().field().transformInit(getter));
        }
        classBuilder.defs(toplevelGetter().transform(getter));
        if (setter != null
                || getterModel.isVariable() || getterModel.isLate()) {
            classBuilder.defs(toplevelSetter().transform(getterModel, setter));
        }
        return classBuilder.build();
    }
    
    public void transformClassAttribute(ClassDefinitionBuilder classBuilder, Tree.AnyAttribute decl) {
        Value value = decl.getDeclarationModel();
        if ((!value.isTransient()
                && !value.isFormal())
                || Decl.isIndirect(decl)) {
            classBuilder.defs(classGetter().field().transformDeclaration(decl));
            classBuilder.init(classGetter().field().transformInit(decl));
        }
        classBuilder.defs(classGetter().transform(decl));
        if (value.isLate() || 
                (value.isVariable() && !value.isTransient())) {
            classBuilder.defs(classSetter().transform(decl.getDeclarationModel(), null));
        }
    }
    
    public void transformClassSetter(ClassDefinitionBuilder classBuilder, Tree.AttributeSetterDefinition decl) {
        classBuilder.defs(classSetter().transform(decl.getDeclarationModel().getGetter(), decl));
    }

    public void transformInterfaceAttribute(
            ClassDefinitionBuilder classBuilder, AnyAttribute decl) {
        classBuilder.defs(interfaceGetter.transform(decl));
        if (decl.getDeclarationModel().isVariable() && !decl.getDeclarationModel().isTransient()) {
            classBuilder.defs(interfaceSetter.transform(decl.getDeclarationModel(), null));
        }
    }

    public void transformCompanionAttribute(
            ClassDefinitionBuilder classBuilder, AnyAttribute decl) {
        classBuilder.defs(companionGetter.transform(decl));
    }

    public void transformInterfaceSetter(ClassDefinitionBuilder classBuilder,
            AttributeSetterDefinition decl) {
        classBuilder.defs(interfaceSetter.transform(decl.getDeclarationModel().getGetter(), decl));
    }

    public void transformCompanionSetter(
            ClassDefinitionBuilder classBuilder,
            AttributeSetterDefinition decl) {
        classBuilder.defs(companionSetter.transform(decl.getDeclarationModel().getGetter(), decl));
    }
    
    private JCExpression makeTypeforMember(Value value, ProducedType nonWideningType) {
        
        int typeFlags = 0;
        if (CodegenUtil.getBoxingStrategy((TypedDeclaration)value.getRefinedDeclaration()) == BoxingStrategy.BOXED) {
            typeFlags |= AbstractTransformer.JT_NO_PRIMITIVES;
        }
        JCExpression attrType;
        // make sure we generate int getters for hash
        if(CodegenUtil.isHashAttribute(value)){
            attrType = make().Type(syms().intType);
        }else{
            attrType = makeJavaType(nonWideningType, typeFlags);
        }
        return attrType;
    }

    public List<? extends JCTree> transformLocalValue(AnyAttribute decl) {
        Value model = decl.getDeclarationModel();
        ListBuffer<JCTree> lb = ListBuffer.<JCTree>lb();
        if (model.isTransient()) {
            // A local getter: We have to use a class+method
            if (!model.isDeferred()) {
                ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder.klass(ValueTransformer.this, Naming.getAttrClassName(model, 0), null);
                classBuilder
                    .modifiers(FINAL | (model.isShared() ? PUBLIC : 0))
                    .constructorModifiers(PRIVATE)
                    .annotations(makeAtAttribute())
                    .satisfies(List.of(getGetterInterfaceType(decl.getDeclarationModel())));
                classBuilder.defs(localGetter.transform(decl));
                lb.addAll(classBuilder.build());
                JCTree classInstantiate = makeLocalIdentityInstance(
                        makeJavaType(getGetterInterfaceType(decl.getDeclarationModel())),
                        Naming.getAttrClassName(model, 0), 
                        Naming.getAttrClassName(model, 0), 
                        decl.getDeclarationModel().isShared(), null);
                lb.add(classInstantiate);
            } else {
                int modifiers = 0;//model.isShared() ? 0 : FINAL;
                JCVariableDecl classInstantiate = make().VarDef(
                        make().Modifiers(modifiers), 
                        names().fromString(Naming.getAttrClassName(model, 0)), 
                        makeJavaType(getGetterInterfaceType(decl.getDeclarationModel())),
                        null);
                lb.add(classInstantiate);
            }
        } else {
            // A local value
            if (model.isParameter() && !(decl.getDeclarationModel().isVariable()
                    && decl.getDeclarationModel().isCaptured())) {
                // Already declared by the parameter
                return List.nil();
            }
            // We can just use a variable declaration...
            lb.add(localVariable.transformDeclaration(decl));
            // ... but to cope with javas definite specification analysis,
            // we might need two declarations.
            JCStatement outerSubs = statementGen().openOuterSubstitutionIfNeeded(
                    decl.getDeclarationModel(), 
                    decl.getType().getTypeModel(), 
                    makeJavaTypeAnnotations(decl.getDeclarationModel()), FINAL);
            if (outerSubs != null) {
                lb.add(outerSubs);
            }
        }
        return lb.toList();
    }
    
    public List<? extends JCTree> transformLocalSetter(
            AttributeSetterDefinition decl) {
        Value getter = decl.getDeclarationModel().getGetter();
        ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder.klass(ValueTransformer.this, Naming.getAttrClassName(getter, Naming.NA_SETTER), null);
        classBuilder
            .modifiers(FINAL | (getter.isShared() ? PUBLIC : 0))
            .constructorModifiers(PRIVATE)
            .annotations(makeAtAttribute());
        ClassDefinitionBuilder classDef = classBuilder.defs(localSetter.transform(getter, decl));
        JCTree classInstantiate = makeLocalIdentityInstance(
                makeQuotedIdent(Naming.getAttrClassName(decl.getDeclarationModel(), 0)),
                Naming.getAttrClassName(decl.getDeclarationModel(), 0),
                Naming.getAttrClassName(decl.getDeclarationModel(), 0),
                decl.getDeclarationModel().isShared(), null);
        return classDef.build().append(classInstantiate);
    }
}
