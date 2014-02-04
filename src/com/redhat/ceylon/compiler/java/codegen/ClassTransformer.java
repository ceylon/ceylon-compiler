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

import static com.redhat.ceylon.compiler.java.codegen.Naming.DeclNameFlag.QUALIFIED;
import static com.sun.tools.javac.code.Flags.ABSTRACT;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.PROTECTED;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.redhat.ceylon.compiler.java.codegen.Naming.Substitution;
import com.redhat.ceylon.compiler.java.codegen.Naming.Suffix;
import com.redhat.ceylon.compiler.java.codegen.Naming.SyntheticName;
import com.redhat.ceylon.compiler.java.codegen.Naming.Unfix;
import com.redhat.ceylon.compiler.java.codegen.StatementTransformer.DeferredSpecification;
import com.redhat.ceylon.compiler.loader.model.LazyInterface;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassAlias;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.ControlBlock;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Generic;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.InterfaceAlias;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnyClass;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BaseMemberExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LazySpecifierExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SequencedArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypeParameterDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypeParameterList;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * This transformer deals with class/interface declarations
 */
public class ClassTransformer extends AbstractTransformer {

    public static ClassTransformer getInstance(Context context) {
        ClassTransformer trans = context.get(ClassTransformer.class);
        if (trans == null) {
            trans = new ClassTransformer(context);
            context.put(ClassTransformer.class, trans);
        }
        return trans;
    }

    private ClassTransformer(Context context) {
        super(context);
    }

    abstract class ClassOrInterfaceTransformation<T extends Tree.ClassOrInterface, D extends ClassOrInterface> {
        public List<JCTree> transform(T def) {
            ClassDefinitionBuilder classBuilder = makeBuilder(def);
            return transform(def, classBuilder);
        }
        
        protected List<JCTree> transform(T def, 
                ClassDefinitionBuilder classBuilder) {
            D model = (D)def.getDeclarationModel();
            transformUserAnnotations(def, classBuilder);
            transformMembers(def, classBuilder);
            transformModifiers(model, classBuilder);
            transformModelAnnotations(model, classBuilder);
            transformSuperTypes(model, classBuilder);
            transformCaseTypes(model, classBuilder);
            transformTypeParameters(def, model.getTypeParameters(), classBuilder);
            return classBuilder.build();
        }
        
        protected void transformTypeParameters(T def, java.util.List<TypeParameter> typeParameters, ClassDefinitionBuilder classBuilder) {
            for (TypeParameter param : typeParameters) {
                classBuilder.typeParameter(param);
            }
        }
        
        protected final void transformUserAnnotations(T def, ClassDefinitionBuilder classBuilder) {
            classBuilder.annotations(expressionGen().transform(def.getAnnotationList()));
        }
        
        protected void transformModelAnnotations(D model, ClassDefinitionBuilder builder) {
            addAtContainer(builder, model);
            builder.modelAnnotations(model.getAnnotations());
        }
        
        protected void transformModifiers(D model,
                ClassDefinitionBuilder classBuilder) {
            classBuilder.modifiers(transformClassDeclFlags(model));
        }
        
        protected final ClassDefinitionBuilder makeBuilder(T tree) {
            String ceylonClassName = ceylonClassName(tree);
            ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder
                    .klass(ClassTransformer.this, javaClassName(tree), ceylonClassName)
                    .forDefinition(tree);
            return classBuilder;
        }
        
        protected String ceylonClassName(T tree) {
            return naming.declName(tree.getDeclarationModel());
        }
        
        protected String javaClassName(T def) {
            return Naming.quoteClassName(ceylonClassName(def));
        }
        
        /** 
         * Transform the case types of this declaration. 
         * By default we just do the {@code satisfies} clause.
         */
        protected final void transformSuperTypes(D model, ClassDefinitionBuilder builder) {
            transformSatisfies(model, builder);
        }
        protected void transformSatisfies(D model, ClassDefinitionBuilder builder) {
            builder.satisfies(model.getSatisfiedTypes());
        }
        
        /** Transform the case types of this declaration */
        protected void transformCaseTypes(D model, ClassDefinitionBuilder builder) {
            builder.caseTypes(model.getCaseTypes(), model.getSelfType());
        }
        /** Transform the members of this declaration */
        protected void transformMembers(T def, ClassDefinitionBuilder classbuilder) {
            List<JCStatement> childDefs = classGen().visitClassOrInterfaceDefinition(def, classbuilder);
            classbuilder.init(childDefs);
        }
    }
    
    final class InterfaceTransformation 
            extends ClassOrInterfaceTransformation<Tree.InterfaceDefinition, Interface> {
        
        @Override
        public List<JCTree> transform(Tree.InterfaceDefinition def, ClassDefinitionBuilder classBuilder) {
            buildCompanion(def, def.getDeclarationModel(), classBuilder, def.getTypeParameterList());
            return super.transform(def, classBuilder);
        }
        
        @Override
        protected String javaClassName(Tree.InterfaceDefinition def) {
            return naming.declName(def.getDeclarationModel(), QUALIFIED).replaceFirst(".*\\.", "");
        }
        
        @Override
        protected void transformTypeParameters(Tree.InterfaceDefinition def, java.util.List<TypeParameter> typeParameters, ClassDefinitionBuilder classBuilder) {
            for(TypeParameter outerTypeParam : typeParametersOfAllContainers(def.getDeclarationModel(), false)){
                classBuilder.typeParameter(outerTypeParam, false);
            }
            for (TypeParameter param : typeParameters) {
                classBuilder.typeParameter(param);
            }
        }
        
        @Override
        protected void transformModelAnnotations(Interface model, ClassDefinitionBuilder classBuilder) {
            addAtMembers(classBuilder, model);
            super.transformModelAnnotations(model, classBuilder);
        }
        
        @Override
        protected void transformMembers(Tree.InterfaceDefinition def, ClassDefinitionBuilder classBuilder) {
            Interface model = def.getDeclarationModel();
            super.transformMembers(def, classBuilder);
            transformGetTypeMethod(model, classBuilder);
        }
        
        private void transformGetTypeMethod(Interface model,
                ClassDefinitionBuilder classBuilder) {
            at(null);
            if(supportsReifiedAlias(model)) {
                classBuilder.reifiedAlias(model.getType());
            }
        }
        
    }
    
    final class InterfaceAliasTransformation 
            extends ClassOrInterfaceTransformation<Tree.InterfaceDeclaration, InterfaceAlias> {
        
        @Override
        protected String javaClassName(Tree.InterfaceDeclaration def) {
            return naming.declName(def.getDeclarationModel(), QUALIFIED).replaceFirst(".*\\.", "");
        }
        
        @Override
        protected void transformTypeParameters(Tree.InterfaceDeclaration def, java.util.List<TypeParameter> typeParameters, ClassDefinitionBuilder classBuilder) {
            for(TypeParameter outerTypeParam : typeParametersOfAllContainers(def.getDeclarationModel(), false)){
                classBuilder.typeParameter(outerTypeParam, false);
            }
            super.transformTypeParameters(def, typeParameters, classBuilder);
        }
        
        @Override
        protected void transformModelAnnotations(InterfaceAlias model, ClassDefinitionBuilder classBuilder) {
            addAtMembers(classBuilder, model);
            
            ProducedType aliasedClass = model.getExtendedType();
            classBuilder.annotations(makeAtAlias(aliasedClass));
            
            super.transformModelAnnotations(model, classBuilder);
        }
    }
    
    /**
     * Transformation for a {@code Class}, with a pluggable transformation 
     * for the class initializer.
     */
    class ClassTransformation extends ClassOrInterfaceTransformation<Tree.ClassDefinition, Class> {
        
        private final InitializerTransformation init;
        
        ClassTransformation(InitializerTransformation init) {
            this.init = init;
        }
        
        @Override
        protected void transformSatisfies(Class model,
                ClassDefinitionBuilder classBuilder) {
            // Make sure top types satisfy reified type
            addReifiedTypeInterface(classBuilder, model);
            super.transformSatisfies(model, classBuilder);
        }
        
        protected final void transformMixins(Tree.ClassDefinition def, ClassDefinitionBuilder classBuilder) {
            Class model = def.getDeclarationModel();
            satisfaction(model, classBuilder);
            at(def);
            // Generate the inner members list for model loading
            addAtMembers(classBuilder, model);
        }
        
        @Override
        protected void transformMembers(Tree.ClassDefinition def, ClassDefinitionBuilder classBuilder) {
            init.transformInitializer(def, classBuilder);
            at(null);
            transformMixins(def, classBuilder);
            super.transformMembers(def, classBuilder);
            // If it's a Class without initializer parameters...
            if (Strategy.generateMain(def)) {
                // ... then add a main() method
                classBuilder.method(classGen().makeMainForClass(def.getDeclarationModel()));
            }
            transformGetTypeMethod(def.getDeclarationModel(), classBuilder);
        }
        
        private void transformGetTypeMethod(Class model,
                ClassDefinitionBuilder classBuilder) {
            at(null);
            classBuilder.addGetTypeMethod(model.getType());
            if(supportsReifiedAlias(model)) {
                classBuilder.reifiedAlias(model.getType());
            }
        }
    }
    
    /**
     * Transformation for a {@code Class}, with a pluggable transformation 
     * for the class initializer.
     */
    final class ClassAliasTransformation extends ClassOrInterfaceTransformation<Tree.ClassDeclaration, ClassAlias> {
        
        private final InitializerTransformation init;
        
        ClassAliasTransformation(InitializerTransformation init) {
            this.init = init;
        }
        
        @Override
        protected void transformSatisfies(ClassAlias model,
                ClassDefinitionBuilder classBuilder) {
            classBuilder.satisfies(model.getSatisfiedTypes());
        }
        
        @Override
        protected void transformModelAnnotations(ClassAlias model, ClassDefinitionBuilder builder) {
            ProducedType aliasedClass = model.getExtendedType();
            builder.annotations(makeAtAlias(aliasedClass));
            super.transformModelAnnotations(model, builder);
        }
        
        @Override
        protected void transformMembers(Tree.ClassDeclaration def, ClassDefinitionBuilder classBuilder) {
            init.transformInitializer(def, classBuilder);
            //transformMixins(def, classBuilder);
            super.transformMembers(def, classBuilder);
            // If it's a Class without initializer parameters...
            if (Strategy.generateMain(def)) {
                // ... then add a main() method
                classBuilder.method(classGen().makeMainForClass(def.getDeclarationModel()));
            }
        }
    }
    
    /**
     * Abstraction over different ways of transforming class initializers: "
     * constructors and instantiator methods
     */
    abstract class InitializerTransformation {
        
        public void transformInitializer(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            at(def);
            transformUltimate(def, classBuilder);
            transformPeripheral(def, classBuilder);
        }
        
        protected abstract void transformUltimate(AnyClass def,
                ClassDefinitionBuilder classBuilder);
        
        protected void transformPeripheral(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            Tree.ParameterList paramList = def.getParameterList();
            for (final Tree.Parameter param : paramList.getParameters()) {
                // Overloaded instantiators
                
                Parameter paramModel = param.getParameterModel();
                Parameter refinedParam = CodegenUtil.findParamForDecl(
                        (TypedDeclaration)CodegenUtil.getTopmostRefinedDeclaration(param.getParameterModel().getModel()));
                if (isDefaultedOrOverloaded(paramModel, refinedParam)) {
                    at(param);
                    transformOverload(def.getDeclarationModel(), param, classBuilder);
                    at(param);
                    transformDefaultParameterValueMethod(def.getDeclarationModel(), param, classBuilder);
                }
            }
        }
        
        protected abstract void transformDefaultParameterValueMethod(Class model, Tree.Parameter parameter, ClassDefinitionBuilder classBuilder);

        protected abstract void transformOverload(Class model, Tree.Parameter parameter, ClassDefinitionBuilder classBuilder);

        protected boolean isDefaultedOrOverloaded(Parameter paramModel, Parameter refinedParam) {
            return Strategy.hasDefaultParameterValueMethod(paramModel)
                    || Strategy.hasDefaultParameterOverload(paramModel)
                    || (refinedParam != null
                            && (Strategy.hasDefaultParameterValueMethod(refinedParam)
                                    || Strategy.hasDefaultParameterOverload(refinedParam)));
        }
        
    }
    /**
     * Transforms initializers as constructors
     */
    abstract class Constructor extends InitializerTransformation {
        
        @Override
        protected void transformUltimate(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            // Add reified type parameters to the constructor
            TypeParameterList typeParameterList = def.getTypeParameterList();
            if(typeParameterList != null) {
                classBuilder.reifiedTypeParameters(typeParameterList);
            }
            for (final Tree.Parameter param : def.getParameterList().getParameters()) {
                List<JCAnnotation> annotations = expressionGen().transform(Decl.getAnnotations(def, param));
                // Add a constructor parameter for each class parameter
                at(param);
                transformParameter(classBuilder, param.getParameterModel(), annotations);
                // If the parameter requires a membe, add that.
                makeAttributeForValueParameter(classBuilder, param, annotations);
                makeMethodForFunctionalParameter(classBuilder, def, param, annotations);
            }
        }
        @Override
        protected void transformDefaultParameterValueMethod(Class model, Tree.Parameter param, ClassDefinitionBuilder classBuilder) {
            if (Strategy.hasDefaultParameterValueMethod(param.getParameterModel())) {
                at(param);
                classBuilder.method(classDpvmTransformation.transform(
                        model,
                        model.getParameterList(),
                        param));
            }
        }
        @Override
        protected void transformOverload(Class model, Tree.Parameter param, ClassDefinitionBuilder classBuilder) {
            if (Strategy.hasDefaultParameterOverload(param.getParameterModel())) {
                at(null);
                new DefaultedArgumentConstructor(classBuilder).makeOverload(model,
                        model.getParameterList(),
                        param.getParameterModel(),
                        model.getTypeParameters(),
                        daoThis);
            }
        }
        
    }
    /**
     * Transforms initializer of top level classes as constructors, 
     * and adds an extra constructor if it's an annotation class.
     */
    class ToplevelClassConstructor extends Constructor {
        @Override
        protected void transformPeripheral(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            super.transformPeripheral(def, classBuilder); 
            if (Decl.isAnnotationClass(def)) {
                at(null);
                transformAnnotationClassConstructor(def, classBuilder);
            }
        }
    }
    
    class LocalClassConstructor extends Constructor {
        
        @Override
        protected void transformUltimate(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            capturedLocalParameters(def.getDeclarationModel(), classBuilder);
            outerReifiedTypeParameters(def.getDeclarationModel(), classBuilder);
            super.transformUltimate(def, classBuilder);
            for (Declaration captured : Decl.getCapturedLocals(def.getDeclarationModel())) {
                if (captured instanceof TypedDeclaration) {
                    if ((captured instanceof MethodOrValue)
                            && Decl.isLocal(captured)
                            && ((MethodOrValue)captured).isTransient()
                            && !((MethodOrValue)captured).isParameter()
                            && !((MethodOrValue)captured).isDeferred()) {
                        continue;
                    }
                    makeCapturedField((TypedDeclaration)captured, classBuilder);
                }
            }
        }
        private void makeCapturedField(TypedDeclaration captured, ClassDefinitionBuilder classBuilder) {
            String subsName = naming.substitute(captured);
            JCExpression fieldType;
            if (captured.isVariable()) {
                fieldType = makeVariableBoxType(captured);
            } else {
                fieldType = makeJavaType(captured.getType());
            }
            classBuilder.field(PRIVATE | FINAL, 
                    subsName,
                    fieldType,
                    null, 
                    false);
            classBuilder.init(make().Exec(make().Assign(
                    naming.makeQualIdent(naming.makeThis(), subsName),
                    naming.makeUnquotedIdent(subsName))));
        }
        @Override
        protected void transformDefaultParameterValueMethod(Class model, Tree.Parameter param, ClassDefinitionBuilder classBuilder) {
            super.transformDefaultParameterValueMethod(model, param, classBuilder.getContainingClassBuilder());
        }
    }
    
    /**
     * Transforms initializer of member classes as constructors
     * @see MemberClassInstantiator
     */
    class MemberClassConstructor extends Constructor {
        @Override
        protected void transformOverload(Class model, Tree.Parameter param, ClassDefinitionBuilder classBuilder) {
            boolean generateInstantiator = Strategy.generateInstantiator(model);
            boolean addOverloadedConstructor = 
                    Strategy.hasDefaultParameterOverload(param.getParameterModel()) && 
                        (!generateInstantiator || 
                                (generateInstantiator && Decl.withinInterface(model) && model.isFormal()));
            if (addOverloadedConstructor) {
                super.transformOverload(model, param, classBuilder);
            }
        }
        @Override
        protected void transformDefaultParameterValueMethod(Class model, Tree.Parameter param, ClassDefinitionBuilder classBuilder) {
            ClassDefinitionBuilder cbForDevaultValues;
            ClassDefinitionBuilder cbForDevaultValuesDecls = null;
            switch (Strategy.defaultParameterMethodOwner(model)) {
            case STATIC:
                cbForDevaultValues = classBuilder;
                break;
            case OUTER:
                cbForDevaultValues = classBuilder.getContainingClassBuilder();
                break;
            case OUTER_COMPANION:
                cbForDevaultValues = classBuilder.getContainingClassBuilder().getCompanionBuilder(Decl.getClassOrInterfaceContainer(model, true));
                cbForDevaultValuesDecls = classBuilder.getContainingClassBuilder();
                break;
            default:
                cbForDevaultValues = classBuilder.getCompanionBuilder(model);
            }
            boolean generateInstantiator = Strategy.generateInstantiator(model);
            Parameter paramModel = param.getParameterModel();
            Parameter refinedParam = CodegenUtil.findParamForDecl(
                    (TypedDeclaration)CodegenUtil.getTopmostRefinedDeclaration(param.getParameterModel().getModel()));
            if ((Strategy.hasDefaultParameterValueMethod(paramModel) 
                    || (generateInstantiator && refinedParam != null && Strategy.hasDefaultParameterValueMethod(refinedParam)))) {
                // we need dpvms for the instantiator
                if (!generateInstantiator || refinedParam == paramModel) {
                    // default value parameter method
                    super.transformDefaultParameterValueMethod(model, param, cbForDevaultValues);
                    if (cbForDevaultValuesDecls != null) {
                        // default value parameter method declaration (on interface)
                        cbForDevaultValuesDecls.method(
                        abstractClassDpvmTransformation.transform(model, model.getParameterList(), param));
                    }
                } else if (Strategy.hasDelegatedDpm(model)) {
                    java.util.List<Parameter> parameters = model.getParameterList().getParameters();
                    MethodDefinitionBuilder mdb = 
                    makeDelegateToCompanion((Interface)model.getRefinedDeclaration().getContainer(),
                            paramModel.getModel().getProducedTypedReference(model.getType(), null),
                            ((TypeDeclaration)model.getContainer()).getType(),
                            FINAL | transformClassDeclFlags(model), 
                            List.<TypeParameter>nil(), 
                            paramModel.getType(), 
                            naming.getDefaultedParamMethodName(model, paramModel),
                            parameters.subList(0, parameters.indexOf(paramModel)), 
                            false, 
                            naming.getDefaultedParamMethodName(model, paramModel));
                    cbForDevaultValues.method(mdb);
                }
            }
        }
    }
    
    /**
     * Generates instantiator methods for a member class, and 
     * possibly applies {@link #other another} transformation.
     * The other transformation would be {@link MemberClassConstructor}
     * for member classes, or {@link ClassAliasInitializer} for a member alias.
     * @see MemberClassInstantiator
     */
    class MemberClassInstantiator extends InitializerTransformation {
        
        InitializerTransformation other;
        
        MemberClassInstantiator(InitializerTransformation other) {
            this.other = other;
        }
        
        @Override
        public void transformInitializer(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            super.transformInitializer(def, classBuilder);
            if (other != null) {
                other.transformInitializer(def, classBuilder);
            }
        }
        
        @Override
        protected void transformUltimate(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            Class model = def.getDeclarationModel();
            boolean generateInstantiator = Strategy.generateInstantiator(model);
            if(generateInstantiator){
                classBuilder.constructorModifiers(PROTECTED);
                TypeParameterList typeParameterList = def.getTypeParameterList();
                ClassDefinitionBuilder decl;
                ClassDefinitionBuilder impl;
                if (model.isInterfaceMember()) {
                    decl = gen().current().getContainingClassBuilder();
                    impl = gen().current().getContainingClassBuilder().getCompanionBuilder((Interface)model.getContainer());
                } else {
                    decl = null;
                    impl = gen().current().getContainingClassBuilder();
                }
                if (Decl.withinInterface(model)) {
                    DefaultedArgumentInstantiator overloaded = new DefaultedArgumentInstantiator();
                    decl.method(overloaded.makeOverload(model,
                            model.getParameterList(),
                            null,
                            typeParameterListModel(typeParameterList),
                            daoAbstract));
                }
                if (!Decl.withinInterface(model) || !model.isFormal()) {
                    DefaultedArgumentInstantiator overloaded = new DefaultedArgumentInstantiator();
                    impl.method(overloaded.makeOverload(model,
                            model.getParameterList(),
                            null,
                            typeParameterListModel(typeParameterList),
                            !model.isFormal() ? daoThis : daoAbstract));
                }
            }
        }
        
        @Override
        protected void transformOverload(
                Class model,
                com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter param,
                ClassDefinitionBuilder classBuilder) {
            boolean generateInstantiator = Strategy.generateInstantiator(model);
            if (generateInstantiator) {
                ClassDefinitionBuilder decl;
                ClassDefinitionBuilder impl;
                if (model.isInterfaceMember()) {
                    decl = gen().current().getContainingClassBuilder();
                    impl = gen().current().getContainingClassBuilder().getCompanionBuilder((Interface)model.getContainer());
                } else {
                    decl = null;
                    impl = gen().current().getContainingClassBuilder();
                }
                if (Decl.withinInterface(model)) {
                    MethodDefinitionBuilder instBuilder = new DefaultedArgumentInstantiator().makeOverload(model,
                            model.getParameterList(),
                            param.getParameterModel(),
                            model.getTypeParameters(),
                            daoAbstract);
                    decl.method(instBuilder);
                }
                if (!Decl.withinInterface(model) || !model.isFormal()) {
                    MethodDefinitionBuilder instBuilder = new DefaultedArgumentInstantiator().makeOverload(model,
                            model.getParameterList(),
                            param.getParameterModel(),
                            model.getTypeParameters(),
                            daoThis);
                    impl.method(instBuilder);
                }
            }
        }
        
        @Override
        protected void transformDefaultParameterValueMethod(
                Class model,
                com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter parameter,
                ClassDefinitionBuilder classBuilder) {
            // The MemberClassConstructor transformation generates these
        }
    }
    
    class ClassAliasInitializer extends InitializerTransformation {
        
        /**
         * Builds the instantiator method for a class aliases. In 1.0 you can't
         * actually invoke these, they exist just so there's somewhere to put the
         * class alias annotations. In 1.2 (when we fix #1295) the
         * instantiators will actually do something.
         */
        private MethodDefinitionBuilder transformClassAliasInstantiator(
                final Tree.AnyClass def, Class model, ProducedType aliasedClass) {
            MethodDefinitionBuilder instantiator = MethodDefinitionBuilder.systemMethod(ClassTransformer.this, naming.getAliasInstantiatorMethodName(model));
            int f = 0;
            if (Strategy.defaultParameterMethodStatic(def.getDeclarationModel())) {
                f = STATIC;
            }
            instantiator.modifiers((transformClassDeclFlags(def) & ~FINAL)| f);
            for (TypeParameter tp : typeParametersOfAllContainers(model, true)) {
                instantiator.typeParameter(tp);
            }
            
            instantiator.resultType(null, makeJavaType(aliasedClass));
            instantiator.annotationFlags(Annotations.MODEL_AND_USER | Annotations.IGNORE);
            // We need to reify the parameters, at least so they have reified annotations
            
            
            for (final Tree.Parameter param : def.getParameterList().getParameters()) {
                // Overloaded instantiators
                Parameter paramModel = param.getParameterModel();
                at(param);
                
                List<JCAnnotation> annotations = expressionGen().transform(Decl.getAnnotations(def, param));
                transformParameter(instantiator, paramModel, annotations);
            }
            instantiator.body(make().Throw(makeNewClass(makeJavaType(typeFact().getExceptionDeclaration().getType(), JT_CLASS_NEW))));
            return instantiator;
        }
        
        @Override
        protected void transformUltimate(AnyClass def,
                ClassDefinitionBuilder classBuilder) {
            classBuilder.isAlias(true);
            classBuilder.constructorModifiers(PRIVATE);
            
            ClassAlias model = (ClassAlias)def.getDeclarationModel();
            ProducedType aliasedClass = model.getExtendedType();
            
            ClassDefinitionBuilder cbInstantiator = null;
            switch (Strategy.defaultParameterMethodOwner(model)) {
            case STATIC:
                cbInstantiator = classBuilder;
                break;
            case OUTER:
                cbInstantiator =  classBuilder.getContainingClassBuilder();
                break;
            case OUTER_COMPANION:
                cbInstantiator = classBuilder.getContainingClassBuilder().getCompanionBuilder(Decl.getClassOrInterfaceContainer(model, true));
                break;
            default:
                Assert.fail("", def);
            }
            cbInstantiator.method(transformClassAliasInstantiator(
                    def, model, aliasedClass));
        }

        @Override
        protected void transformDefaultParameterValueMethod(
                Class model,
                Tree.Parameter parameter,
                ClassDefinitionBuilder classBuilder) {
            // Nothing to do, currently
        }

        @Override
        protected void transformOverload(
                Class model,
                Tree.Parameter parameter,
                ClassDefinitionBuilder classBuilder) {
            // Nothing to do, currently
        }
    }
    
    ClassTransformation toplevelClassTransformation = new ClassTransformation(new ToplevelClassConstructor()) {

        @Override
        public List<JCTree> transform(Tree.ClassDefinition def) {
            List<JCTree> result = super.transform(def);
            if (Decl.isAnnotationClass(def)) {
                result = result.prependList(transformAnnotationClass(def));
            }
            return result;
        }
    };
    ClassAliasTransformation toplevelClassAliasTransformation = new ClassAliasTransformation(new ClassAliasInitializer());
    ClassTransformation memberClassTransformation = new ClassTransformation(new MemberClassInstantiator(new MemberClassConstructor()));
    ClassAliasTransformation memberClassAliasTransformation = new ClassAliasTransformation(new MemberClassInstantiator(new ClassAliasInitializer()) {
        @Override
        protected void transformOverload(
                Class model,
                com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter param,
                ClassDefinitionBuilder classBuilder) {
            // Nothing to do, currently
        }
    });
    class LocalClassTransformation extends ClassTransformation {

        LocalClassTransformation() {
            super(new LocalClassConstructor());
        }
        
        @Override
        protected void transformModifiers(Class model,
                ClassDefinitionBuilder classBuilder) {
            int mods = transformClassDeclFlags(model);
            if (localStatic(model)) {
                mods |= STATIC;
            }
            classBuilder.modifiers(mods);
        }
        
        protected void transformTypeParameters(Tree.ClassDefinition def, java.util.List<TypeParameter> typeParameters, ClassDefinitionBuilder classBuilder) {
            outerTypeParameters(def.getDeclarationModel(), classBuilder);
            super.transformTypeParameters(def, typeParameters, classBuilder);
        }
        
        @Override
        protected String ceylonClassName(Tree.ClassDefinition tree) {
            return tree.getIdentifier().getText() + "$" + naming.getLocalId(tree.getDeclarationModel().getContainer());
        }
        
    }
    ClassTransformation localClassTransformation = new LocalClassTransformation();
    ClassAliasTransformation localClassAliasTransformation = new ClassAliasTransformation(new ClassAliasInitializer());

    InterfaceTransformation interfaceTransformation = new InterfaceTransformation();
    InterfaceAliasTransformation interfaceAliasTransformation = new InterfaceAliasTransformation();
    
    public List<JCTree> transform(final Tree.ClassOrInterface def) {
        final ClassOrInterface model = def.getDeclarationModel();
        
        // we only create types for aliases so they can be imported with the model loader
        // and since we can't import local declarations let's just not create those types
        // in that case
        if(model.isAlias()
                && Decl.isAncestorLocal(def))
            return List.nil();
        
        naming.clearSubstitutions(model);

        if (model instanceof ClassAlias) {
            final ClassAliasTransformation transformation;
            if (model.isToplevel()) { 
                transformation = toplevelClassAliasTransformation;
            } else if (model.isMember()) {
                transformation = memberClassAliasTransformation;
            } else {
                transformation = localClassAliasTransformation;
            }
            return transformation.transform((Tree.ClassDeclaration)def);
        } else if (model instanceof Class) {
            final ClassTransformation transformation ;
            if (model.isToplevel()) { 
                transformation = toplevelClassTransformation;
            } else if (model.isMember()) {
                transformation = memberClassTransformation;
            } else {
                transformation = localClassTransformation;
            }
            return transformation.transform((Tree.ClassDefinition)def);
        } else if (model instanceof InterfaceAlias) {
            return interfaceAliasTransformation.transform((Tree.InterfaceDeclaration)def);
        } else if (model instanceof Interface) {
            return interfaceTransformation.transform((Tree.InterfaceDefinition)def);
        }
        throw new RuntimeException(def.toString());
    }
    
    /**
     * Generates a constructor for an annotation class which takes the 
     * annotation type as parameter.
     * @param classBuilder
     */
    private void transformAnnotationClassConstructor(
            Tree.AnyClass def,
            ClassDefinitionBuilder classBuilder) {
        Class klass = def.getDeclarationModel();
        MethodDefinitionBuilder annoCtor = classBuilder.addConstructor();
        annoCtor.ignoreModelAnnotations();
        // constructors are never final
        annoCtor.modifiers(transformClassDeclFlags(klass) & ~FINAL);
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.systemParameter(this, "anno");
        pdb.type(makeJavaType(klass.getType(), JT_ANNOTATION), null);
        annoCtor.parameter(pdb);
        
        // It's up to the caller to invoke value() on the Java annotation for a sequenced
        // annotation
        
        ListBuffer<JCExpression> args = ListBuffer.lb();
        for (Tree.Parameter parameter : def.getParameterList().getParameters()) {
            at(parameter);
            Parameter parameterModel = parameter.getParameterModel();
            JCExpression annoAttr = make().Apply(null, naming.makeQuotedQualIdent(naming.makeUnquotedIdent("anno"),
                    parameter.getParameterModel().getName()),
                    List.<JCExpression>nil());
            ProducedType parameterType = parameterModel.getType();
            JCExpression argExpr;
            if (typeFact().isIterableType(parameterType)
                    && !isCeylonString(parameterType)) {
                // Convert from array to Sequential
                ProducedType iteratedType = typeFact().getIteratedType(parameterType);
                if (isCeylonBasicType(iteratedType)) {
                    argExpr = makeUtilInvocation("sequentialInstanceBoxed", 
                            List.<JCExpression>of(annoAttr), 
                            null);
                } else if (Decl.isAnnotationClass(iteratedType.getDeclaration())) {
                    // Can't use Util.sequentialAnnotation becase we need to 'box'
                    // the Java annotations in their Ceylon annotation class
                    argExpr = make().Apply(null, naming.makeUnquotedIdent(naming.getAnnotationSequenceMethodName()), List.of(annoAttr));
                    ListBuffer<JCStatement> stmts = ListBuffer.lb();
                    SyntheticName array = naming.synthetic(Unfix.$array$);
                    SyntheticName sb = naming.synthetic(Unfix.$sb$);
                    SyntheticName element = naming.synthetic(Unfix.$element$);
                    stmts.append(makeVar(FINAL, sb, 
                            makeJavaType(typeFact().getSequenceBuilderType(iteratedType)),
                            make().NewClass(null, null, makeJavaType(typeFact().getSequenceBuilderType(iteratedType), JT_CLASS_NEW), List.<JCExpression>of(makeReifiedTypeArgument(iteratedType)), null)));
                    stmts.append(make().ForeachLoop(
                            makeVar(element, makeJavaType(iteratedType, JT_ANNOTATION), null), 
                            array.makeIdent(), 
                            make().Exec(make().Apply(null, naming.makeQualIdent(sb.makeIdent(), "append"), 
                                    List.<JCExpression>of(instantiateAnnotationClass(iteratedType, element.makeIdent()))))));
                    stmts.append(make().Return(make().Apply(null, naming.makeQualIdent(sb.makeIdent(), "getSequence"), List.<JCExpression>nil())));
                    classBuilder.method(
                            MethodDefinitionBuilder.systemMethod(this, naming.getAnnotationSequenceMethodName())
                                .ignoreModelAnnotations()
                                .modifiers(PRIVATE | STATIC)
                                .resultType(null, makeJavaType(typeFact().getSequentialType(iteratedType)))
                                .parameter(ParameterDefinitionBuilder.systemParameter(this, array.getName())
                                        .type(make().TypeArray(makeJavaType(iteratedType, JT_ANNOTATION)), null))
                                .body(stmts.toList()));
                } else if (isCeylonMetamodelDeclaration(iteratedType)) {
                    argExpr = makeMetamodelInvocation("parseMetamodelReferences", 
                            List.<JCExpression>of(makeReifiedTypeArgument(iteratedType), annoAttr), 
                            List.<JCExpression>of(makeJavaType(iteratedType, JT_TYPE_ARGUMENT)));
                } else if (Decl.isEnumeratedTypeWithAnonCases(iteratedType)) {
                    argExpr = makeMetamodelInvocation("parseEnumerationReferences", 
                            List.<JCExpression>of(makeReifiedTypeArgument(iteratedType), annoAttr), 
                            List.<JCExpression>of(makeJavaType(iteratedType, JT_TYPE_ARGUMENT)));
                } else {
                    argExpr = makeUtilInvocation("sequentialInstance", 
                            List.<JCExpression>of(makeReifiedTypeArgument(iteratedType), annoAttr), 
                            List.<JCExpression>of(makeJavaType(iteratedType, JT_TYPE_ARGUMENT)));
                }                
            } else if (Decl.isAnnotationClass(parameterType.getDeclaration())) {
                argExpr = instantiateAnnotationClass(parameterType, annoAttr);
            } else if (isCeylonMetamodelDeclaration(parameterType)) {
                argExpr = makeMetamodelInvocation("parseMetamodelReference", 
                            List.<JCExpression>of(annoAttr), 
                            List.<JCExpression>of(makeJavaType(parameterType, JT_TYPE_ARGUMENT)));
            } else if (Decl.isEnumeratedTypeWithAnonCases(parameterType)) {
                argExpr = makeMetamodelInvocation("parseEnumerationReference", 
                        List.<JCExpression>of(annoAttr), 
                        null);
            } else {
                argExpr = annoAttr;
                argExpr = expressionGen().applyErasureAndBoxing(annoAttr, parameterType.withoutUnderlyingType(), false, BoxingStrategy.UNBOXED, parameterType);
            }
            args.add(argExpr);
        }
        annoCtor.body(at(def).Exec(
                make().Apply(null,  naming.makeThis(), args.toList())));
    }

    private JCNewClass instantiateAnnotationClass(
            ProducedType annotationClass,
            JCExpression javaAnnotationInstance) {
        return make().NewClass(null, null, makeJavaType(annotationClass), 
                List.<JCExpression>of(javaAnnotationInstance), null);
    }

    /**
     * Transforms an annotation class into a Java annotation type.
     * <pre>
     * annotation class Foo(String s, Integer i=1) {}
     * </pre>
     * is transformed into
     * <pre>
     * @Retention(RetentionPolicy.RUNTIME)
     * @interface Foo$annotation$ {
     *     String s();
     *     long i() default 1;
     * }
     * </pre>
     * If the annotation class is a subtype of SequencedAnnotation a wrapper
     * annotation is also generated:
     * <pre>
     * @Retention(RetentionPolicy.RUNTIME)
     * @interface Foo$annotations${
     *     Foo$annotation$[] value();
     * }
     * </pre>
     */
    private List<JCTree> transformAnnotationClass(Tree.AnyClass def) {
        Class klass = def.getDeclarationModel();
        String annotationName = Naming.suffixName(Suffix.$annotation$, klass.getName());
        ClassDefinitionBuilder annoBuilder = ClassDefinitionBuilder.klass(this, annotationName, null);
        
        // annotations are never explicitly final in Java
        annoBuilder.modifiers(Flags.ANNOTATION | Flags.INTERFACE | (transformClassDeclFlags(def) & ~FINAL));
        annoBuilder.annotations(makeAtRetention(RetentionPolicy.RUNTIME));
        
        for (Tree.Parameter p : def.getParameterList().getParameters()) {
            annoBuilder.method(makeAnnotationMethod(p));
        }
        List<JCTree> result;
        if (isSequencedAnnotation(klass)) {
            result = annoBuilder.annotations(makeAtAnnotationTarget()).build();
            String wrapperName = Naming.suffixName(Suffix.$annotations$, klass.getName());
            ClassDefinitionBuilder sequencedBuilder = ClassDefinitionBuilder.klass(this, wrapperName, null);
            // annotations are never explicitely final in Java
            sequencedBuilder.modifiers(Flags.ANNOTATION | Flags.INTERFACE | (transformClassDeclFlags(def) & ~FINAL));
            sequencedBuilder.annotations(makeAtRetention(RetentionPolicy.RUNTIME));
            MethodDefinitionBuilder mdb = MethodDefinitionBuilder.systemMethod(this, naming.getSequencedAnnotationMethodName());
            mdb.annotationFlags(Annotations.MODEL_AND_USER);
            mdb.modifiers(PUBLIC | ABSTRACT);
            mdb.resultType(null, make().TypeArray(makeJavaType(klass.getType(), JT_ANNOTATION)));
            mdb.noBody();
            ClassDefinitionBuilder sequencedAnnotation = sequencedBuilder.method(mdb);
            sequencedAnnotation.annotations(transformAnnotationConstraints(klass));
            result = result.appendList(sequencedAnnotation.build());
            
        } else {
            result = annoBuilder.annotations(transformAnnotationConstraints(klass)).build();
        }
        
        return result;
    }
    
    private List<JCAnnotation> makeAtRetention(RetentionPolicy retentionPolicy) {
        return List.of(
                make().Annotation(
                        make().Type(syms().retentionType), 
                        List.of(naming.makeQuotedQualIdent(make().Type(syms().retentionPolicyType), retentionPolicy.name()))));
    }
    
    /** 
     * Makes {@code @java.lang.annotation.Target(types)} 
     * where types are the given element types.
     */
    private List<JCAnnotation> makeAtAnnotationTarget(ElementType... types) {
        List<JCExpression> typeExprs = List.<JCExpression>nil();
        for (ElementType type : types) {
            typeExprs = typeExprs.prepend(naming.makeQuotedQualIdent(make().Type(syms().elementTypeType), type.name()));
        }
        return List.of(
                make().Annotation(
                        make().Type(syms().targetType), 
                        List.<JCExpression>of(make().NewArray(null, null, typeExprs))));
    }
    
    private List<JCAnnotation> transformAnnotationConstraints(Class klass) {
        TypeDeclaration meta = (TypeDeclaration)typeFact().getLanguageModuleDeclaration("ConstrainedAnnotation");
        ProducedType constrainedType = klass.getType().getSupertype(meta);
        if (constrainedType != null) {
            ProducedType programElement = constrainedType.getTypeArgumentList().get(2);
            if (programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("ClassOrInterfaceDeclaration")).getType())
                    || programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("Package")).getType())
                    || programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("Module")).getType())) {
                return makeAtAnnotationTarget(ElementType.TYPE);
            } else if (programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("ValueDeclaration")).getType())
                    || programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("FunctionDeclaration")).getType())) {
                return makeAtAnnotationTarget(ElementType.METHOD);
            } else if (programElement.isSubtypeOf(((TypeDeclaration)typeFact().getLanguageModuleDeclarationDeclaration("Import")).getType())) {
                return makeAtAnnotationTarget(ElementType.FIELD);
            }
        }
        return List.<JCAnnotation>nil();
    }

    private JCExpression transformAnnotationParameterDefault(Tree.Parameter p) {
        Tree.SpecifierOrInitializerExpression defaultArgument = Decl.getDefaultArgument(p);
        Tree.Expression defaultExpression = defaultArgument.getExpression();
        Tree.Term term = defaultExpression.getTerm();
        JCExpression defaultLiteral = null;
        if (term instanceof Tree.Literal
                && !(term instanceof Tree.QuotedLiteral)) {
            defaultLiteral = expressionGen().transform((Tree.Literal)term);
        } else if (term instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = (Tree.BaseMemberExpression)term;
            Declaration decl = bme.getDeclaration();
            if (isBooleanTrue(decl)) {
                defaultLiteral = makeBoolean(true);
            } else if (isBooleanFalse(decl)) {
                defaultLiteral = makeBoolean(false);
            } else if (typeFact().isEmptyType(bme.getTypeModel())) {
                defaultLiteral = make().NewArray(null, null, List.<JCExpression>nil());
            } else if (Decl.isAnonCaseOfEnumeratedType(bme)) {
                defaultLiteral = makeClassLiteral(bme.getTypeModel());
            } else {
                defaultLiteral = make().Literal(bme.getDeclaration().getQualifiedNameString());
            }
        } else if (term instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression)term;
            defaultLiteral = make().Literal(mte.getDeclaration().getQualifiedNameString());
        } else if (term instanceof Tree.SequenceEnumeration) {
            Tree.SequenceEnumeration seq = (Tree.SequenceEnumeration)term;
            SequencedArgument sequencedArgument = seq.getSequencedArgument();
            defaultLiteral = makeArrayInitializer(sequencedArgument);
        } else if (term instanceof Tree.Tuple) {
            Tree.Tuple seq = (Tree.Tuple)term;
            SequencedArgument sequencedArgument = seq.getSequencedArgument();
            defaultLiteral = makeArrayInitializer(sequencedArgument);
        } else if (term instanceof Tree.InvocationExpression) {
            // Allow invocations of annotation constructors, so long as they're
            // themselves being invoked with permitted arguments
            Tree.InvocationExpression invocation = (Tree.InvocationExpression)term;
            defaultLiteral = AnnotationInvocationVisitor.transform(expressionGen(), invocation);
        } else if (term instanceof Tree.MemberLiteral) {
            defaultLiteral = expressionGen().makeMetaLiteralStringLiteralForAnnotation((Tree.MemberLiteral) term);
        } else if (term instanceof Tree.TypeLiteral) {
            defaultLiteral = expressionGen().makeMetaLiteralStringLiteralForAnnotation((Tree.TypeLiteral) term);
        }
        if (defaultLiteral == null) {
            defaultLiteral = makeErroneous(p, "compiler bug: " + p.getParameterModel().getName() + " has an unsupported defaulted parameter expression");
        }
        return defaultLiteral;
    }

    private JCExpression transformAnnotationMethodType(Tree.Parameter parameter) {
        ProducedType parameterType = parameter.getParameterModel().getType();
        JCExpression type = null;
        if (isScalarAnnotationParameter(parameterType)) {
            type = makeJavaType(parameterType.withoutUnderlyingType(), JT_ANNOTATION);
        } else if (isMetamodelReference(parameterType)) {
            type = make().Type(syms().stringType);
        } else if (Decl.isEnumeratedTypeWithAnonCases(parameterType)) {
            type = makeJavaClassTypeBounded(parameterType);
        } else if (typeFact().isIterableType(parameterType)) {
            ProducedType iteratedType = typeFact().getIteratedType(parameterType);
            if (isScalarAnnotationParameter(iteratedType)) {
                JCExpression scalarType = makeJavaType(iteratedType, JT_ANNOTATION);
                type = make().TypeArray(scalarType);
            } else if (isMetamodelReference(iteratedType)) {
                JCExpression scalarType = make().Type(syms().stringType);
                type = make().TypeArray(scalarType);
            } else if (Decl.isEnumeratedTypeWithAnonCases(iteratedType)) {
                JCExpression scalarType = makeJavaClassTypeBounded(iteratedType);
                type = make().TypeArray(scalarType);
            }
        }
        if (type == null) {
            type = makeErroneous(parameter, "compiler bug: " + parameter.getParameterModel().getName() + " has an unsupported annotation parameter type");
        }
        return type;
    }

    private boolean isMetamodelReference(ProducedType parameterType) {
        return isCeylonMetamodelDeclaration(parameterType);
    }
    /**
     * Makes a new Array expression suitable for use in initializing a Java array
     * using as elements the positional arguments 
     * of the given {@link Tree.SequencedArgument} (which must 
     * be {@link Tree.ListedArgument}s).
     * 
     * <pre>
     *     Whatever[] w = <strong>{ listedArg1, listedArg2, ... }</strong>
     *     //             ^---------- this bit ---------^
     * </pre>
     * 
     * @param sequencedArgument
     * @return The array initializer expression
     */
    JCExpression makeArrayInitializer(
            Tree.SequencedArgument sequencedArgument) {
        JCExpression defaultLiteral;
        ListBuffer<JCExpression> elements = ListBuffer.<JCTree.JCExpression>lb();
        if (sequencedArgument != null) {
            for (Tree.PositionalArgument arg : sequencedArgument.getPositionalArguments()) {
                if (arg instanceof Tree.ListedArgument) {
                    Tree.ListedArgument la = (Tree.ListedArgument)arg;
                    elements.append(expressionGen().transformExpression(la.getExpression().getTerm(), BoxingStrategy.UNBOXED, la.getExpression().getTypeModel()));
                } else {
                    elements = null;
                    break;
                }
            }
        }
        defaultLiteral = elements == null ? null : 
            make().NewArray(null, List.<JCExpression>nil(), elements.toList());
        return defaultLiteral;
    }

    private boolean isScalarAnnotationParameter(ProducedType parameterType) {
        return isCeylonBasicType(parameterType)
                || Decl.isAnnotationClass(parameterType.getDeclaration());
    }

    List<JCStatement> visitClassOrInterfaceDefinition(Node def, ClassDefinitionBuilder classBuilder) {
        // Transform the class/interface members
        CeylonVisitor visitor = gen().visitor;
        
        // don't visit if we have errors in the initialiser
        if(def instanceof Tree.ClassOrInterface && visitor.hasClassInitialiserErrors((Tree.ClassOrInterface)def))
            return List.nil();
        
        final ListBuffer<JCTree> prevDefs = visitor.defs;
        final boolean prevInInitializer = visitor.inInitializer;
        final ClassDefinitionBuilder prevClassBuilder = visitor.classBuilder;
        try {
            visitor.defs = new ListBuffer<JCTree>();
            visitor.inInitializer = true;
            visitor.classBuilder = classBuilder;
            
            def.visitChildren(visitor);
            return (List<JCStatement>)visitor.getResult().toList();
        } finally {
            visitor.classBuilder = prevClassBuilder;
            visitor.inInitializer = prevInInitializer;
            visitor.defs = prevDefs;
            naming.closeScopedSubstitutions(def.getScope());
        }
    }
    
    private void makeAttributeForValueParameter(ClassDefinitionBuilder classBuilder, Tree.Parameter parameterTree, List<JCAnnotation> annotations) {
        Parameter decl = parameterTree.getParameterModel();
        if (!(decl.getModel() instanceof Value)) {
            return;
        }        
        final Value value = (Value)decl.getModel();
        if (parameterTree instanceof Tree.ValueParameterDeclaration
                && (value.isShared() || value.isCaptured())) {
            makeFieldForParameter(classBuilder, decl);
            AttributeDefinitionBuilder adb = AttributeDefinitionBuilder.getter(this, decl.getName(), decl.getModel());
            adb.modifiers(classGen().transformAttributeGetSetDeclFlags(decl.getModel(), false));
            adb.userAnnotations(annotations);
            classBuilder.attribute(adb);
            if (value.isVariable()) {
                AttributeDefinitionBuilder setter = AttributeDefinitionBuilder.setter(this, decl.getName(), decl.getModel());
                setter.modifiers(classGen().transformAttributeGetSetDeclFlags(decl.getModel(), false));
                setter.userAnnotations(annotations);
                classBuilder.attribute(setter);
            }
        } else if (decl.isHidden()
                        // TODO Isn't this always true here? We know this is a parameter to a Class
                        && (decl.getDeclaration() instanceof TypeDeclaration)) {
            Declaration member = CodegenUtil.findMethodOrValueForParam(decl);
            if (Strategy.createField(decl, (Value)member)) {
                // The field itself is created by when we transform the AttributeDeclaration 
                // but it has to be initialized here so all the fields are initialized in parameter order
                classBuilder.init(make().Exec(
                        make().Assign(naming.makeQualifiedName(naming.makeThis(), value, Naming.NA_IDENT), 
                                makeUnquotedIdent(Naming.getAliasedParameterName(decl)))));
            }
        }
    }

    private int transformClassParameterDeclFlags(Parameter param) {
        return param.getModel().isVariable() ? 0 : FINAL;
    }
    
    private void makeFieldForParameter(ClassDefinitionBuilder classBuilder,
            Parameter decl) {
        MethodOrValue model = decl.getModel();
        classBuilder.defs(make().VarDef(make().Modifiers(transformClassParameterDeclFlags(decl) | PRIVATE, makeAtIgnore()), names().fromString(decl.getName()), 
                classGen().transformClassParameterType(decl), null));
        
        classBuilder.init(make().Exec(make().Assign(
                naming.makeQualifiedName(naming.makeThis(), model, Naming.NA_IDENT), 
                naming.makeName(model, Naming.NA_IDENT_PARAMETER_ALIASED))));
    }
    

    private <B extends ParameterizedBuilder<B>> void transformParameter(B classBuilder, Parameter param, List<JCAnnotation> annotations) {

        JCExpression type = classGen().transformClassParameterType(param);
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.explicitParameter(this, param);
        pdb.aliasName(Naming.getAliasedParameterName(param));
        pdb.sequenced(param.isSequenced());
        pdb.defaulted(param.isDefaulted());
        pdb.functional(param.getModel() instanceof Method);
        pdb.type(type, makeJavaTypeAnnotations(param.getModel()));
        pdb.modifiers(transformClassParameterDeclFlags(param));
        if (!(param.getModel().isShared() || param.getModel().isCaptured())) {
            // We load the model for shared parameters from the corresponding member
            pdb.modelAnnotations(param.getModel().getAnnotations());
            pdb.userAnnotations(annotations);
        }

        if (classBuilder instanceof ClassDefinitionBuilder
                && pdb.requiresBoxedVariableDecl()) {
            ((ClassDefinitionBuilder)classBuilder).init(pdb.buildBoxedVariableDecl());
        }
        classBuilder.parameter(pdb);
    }

    /**
     * Generate a method for a shared FunctionalParameter which delegates to the Callable 
     * @param klass 
     * @param annotations */
    private void makeMethodForFunctionalParameter(
            ClassDefinitionBuilder classBuilder, AnyClass klass, Tree.Parameter paramTree, List<JCAnnotation> annotations) {
        Parameter paramModel = paramTree.getParameterModel();
        
        if (Strategy.createMethod(paramModel)) {
            makeFieldForParameter(classBuilder, paramModel);
            Tree.MethodDeclaration methodDecl = (Tree.MethodDeclaration)Decl.getMemberDeclaration(klass, paramTree);
            classBuilder.defs(classMethodFromFunctionalParameterTransformation.transform(methodDecl));
        }
    }
    
    private void addReifiedTypeInterface(ClassDefinitionBuilder classBuilder, ClassOrInterface model) {
        if(model.getExtendedType() == null || willEraseToObject(model.getExtendedType()) || !Decl.isCeylon(model.getExtendedTypeDeclaration()))
            classBuilder.reifiedType();
    }

    /**
     * Transforms the type of the given class parameter
     * @param decl
     * @return
     */
    JCExpression transformClassParameterType(Parameter parameter) {
        MethodOrValue decl = parameter.getModel();
        Assert.that(decl.getContainer() instanceof Class);
        JCExpression type;
        MethodOrValue attr = decl;
        if (Decl.isValue(attr)) {
            ProducedTypedReference typedRef = getTypedReference(attr);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType paramType = nonWideningType(typedRef, nonWideningTypedRef);
            type = makeJavaType(nonWideningTypedRef.getDeclaration(), paramType, 0);
        } else {
            ProducedType paramType = decl.getType();
            type = makeJavaType(decl, paramType, 0);
        }
        return type;
    }

    private void addAtMembers(ClassDefinitionBuilder classBuilder, ClassOrInterface model) {
        List<JCExpression> members = List.nil();
        Package pkg = Decl.getPackageContainer(model);
        for(Declaration member : model.getMembers()){
            if(member instanceof ClassOrInterface == false
                    && member instanceof TypeAlias == false){
                continue;
            }
            TypeDeclaration innerType = (TypeDeclaration) member;
            JCAnnotation atMember = makeAtMember(innerType.getType());
            members = members.prepend(atMember);
        }
        classBuilder.annotations(makeAtMembers(members));
    }

    private void addAtContainer(ClassDefinitionBuilder classBuilder, TypeDeclaration model) {
        Package pkg = Decl.getPackageContainer(model);
        Scope scope = model.getContainer();
        if(scope == null || scope instanceof Package)
            return;
        if(scope instanceof ClassOrInterface){
            ClassOrInterface container = (ClassOrInterface) scope;
            List<JCAnnotation> atContainer = makeAtContainer(container.getType());
            classBuilder.annotations(atContainer);
        }else{
            classBuilder.annotations(makeAtLocalContainer());
        }
        
    }

    private void satisfaction(final Class model, ClassDefinitionBuilder classBuilder) {
        final java.util.List<ProducedType> satisfiedTypes = model.getSatisfiedTypes();
        Set<Interface> satisfiedInterfaces = new HashSet<Interface>();
        // start by saying that we already satisfied each interface from superclasses
        Class superClass = model.getExtendedTypeDeclaration();
        while(superClass != null){
            for(TypeDeclaration interfaceDecl : superClass.getSatisfiedTypeDeclarations()){
                collectInterfaces((Interface) interfaceDecl, satisfiedInterfaces);
            }
            superClass = superClass.getExtendedTypeDeclaration();
        }
        // now satisfy each new interface
        for (ProducedType satisfiedType : satisfiedTypes) {
            TypeDeclaration decl = satisfiedType.getDeclaration();
            if (!(decl instanceof Interface)) {
                continue;
            }
            // make sure we get the right instantiation of the interface
            satisfiedType = model.getType().getSupertype(decl);
            concreteMembersFromSuperinterfaces(model, classBuilder, satisfiedType, satisfiedInterfaces);
        }
        // now find the set of interfaces we implemented twice with more refined type parameters
        if(model.getExtendedTypeDeclaration() != null){
            // reuse that Set
            satisfiedInterfaces.clear();
            for(TypeDeclaration interfaceDecl : model.getSatisfiedTypeDeclarations()){
                collectInterfaces((Interface) interfaceDecl, satisfiedInterfaces);
            }
            // now see if we refined them
            /*for(Interface iface : satisfiedInterfaces){
                // skip those we can't do anything about
                if(!supportsReified(iface))
                    continue;
                ProducedType thisType = model.getType().getSupertype(iface);
                ProducedType superClassType = model.getExtendedType().getSupertype(iface);
                if(thisType != null
                        && superClassType != null
                        && !thisType.isExactly(superClassType)
                        && thisType.isSubtypeOf(superClassType)){
                    // we're refining it
                    classBuilder.refineReifiedType(thisType);
                }
            }*/
        }
    }

    private void collectInterfaces(Interface interfaceDecl, Set<Interface> satisfiedInterfaces) {
        if(satisfiedInterfaces.add(interfaceDecl)){
            for(TypeDeclaration newInterfaceDecl : interfaceDecl.getSatisfiedTypeDeclarations()){
                collectInterfaces((Interface) newInterfaceDecl, satisfiedInterfaces);
            }
        }
    }

    /**
     * Generates companion fields ($Foo$impl) and methods
     */
    private void concreteMembersFromSuperinterfaces(final Class model,
            ClassDefinitionBuilder classBuilder, 
            ProducedType satisfiedType, Set<Interface> satisfiedInterfaces) {
        satisfiedType = satisfiedType.resolveAliases();
        Interface iface = (Interface)satisfiedType.getDeclaration();
        if (satisfiedInterfaces.contains(iface)
                || iface.getType().isExactly(typeFact().getIdentifiableDeclaration().getType())) {
            return;
        }
     
        // If there is no $impl (e.g. implementing a Java interface) 
        // then don't instantiate it...
        if (hasImpl(iface)) {
            // ... otherwise for each satisfied interface, 
            // instantiate an instance of the 
            // companion class in the constructor and assign it to a
            // $Interface$impl field
            //transformInstantiateCompanions(classBuilder,
            //        model, iface, satisfiedType);
        }
        
        if(!Decl.isCeylon(iface)){
            // let's not try to implement CMI for Java interfaces
            return;
        }
        
        // For each super interface
        for (Declaration member : iface.getMembers()) {
            
            if (member instanceof Class
                    && Strategy.generateInstantiator(member)
                    && model.getDirectMember(member.getName(), null, false) == null) {
                // instantiator method implementation
                Class klass = (Class)member;
                generateInstantiatorDelegate(classBuilder, satisfiedType,
                        iface, klass, model.getType());
            } 
            
            // type aliases are on the $impl class
            if(member instanceof TypeAlias)
                continue;
            
            if (Strategy.onlyOnCompanion(member)) {
                // non-shared interface methods don't need implementing
                // (they're just private methods on the $impl)
                continue;
            }
            if (member instanceof Method) {
                Method method = (Method)member;
                final ProducedTypedReference typedMember = satisfiedType.getTypedMember(method, Collections.<ProducedType>emptyList());
                Method subMethod = (Method)model.getMember(method.getName(), null, false);
                final ProducedTypedReference refinedTypedMember = model.getType().getTypedMember(subMethod, Collections.<ProducedType>emptyList());
                final java.util.List<TypeParameter> typeParameters = subMethod.getTypeParameters();
                final java.util.List<Parameter> parameters = subMethod.getParameterLists().get(0).getParameters();
                boolean hasOverloads = false;
                if (!satisfiedInterfaces.contains((Interface)method.getContainer())) {
                    
                    for (Parameter param : parameters) {
                        if (Strategy.hasDefaultParameterValueMethod(param)) {
                            final ProducedTypedReference typedParameter = typedMember.getTypedParameter(param);
                            // If that method has a defaulted parameter, 
                            // we need to generate a default value method
                            // which also delegates to the $impl
                            final MethodDefinitionBuilder defaultValueDelegate = makeDelegateToCompanion(iface,
                                    typedParameter,
                                    model.getType(),
                                    PUBLIC | FINAL, 
                                    typeParameters, 
                                    typedParameter.getType(), 
                                    naming.getDefaultedParamMethodName(method, param), 
                                    parameters.subList(0, parameters.indexOf(param)),
                                    param.getModel().getTypeErased(),
                                    null);
                            classBuilder.method(defaultValueDelegate);
                        }
                        
                        if (Strategy.hasDefaultParameterOverload(param)) {
                            if ((method.isDefault() || method.isShared() && !method.isFormal())
                                    && (method == subMethod)) {
                                MethodDefinitionBuilder overload = new MixinDefaultedArgumentMethod(typedMember)
                                    .makeOverload((Method)typedMember.getDeclaration(),
                                        subMethod.getParameterLists().get(0),
                                        param,
                                        typeParameters,
                                        daoThis);
                                classBuilder.method(overload);
                            }
                            
                            hasOverloads = true;
                        }
                    }
                }
                // if it has the *most refined* default concrete member, 
                // then generate a method on the class
                // delegating to the $impl instance
                if (needsCompanionDelegate(model, member)) {
                    
                    final MethodDefinitionBuilder concreteMemberDelegate = makeDelegateToCompanion(iface,
                            typedMember,
                            model.getType(),
                            PUBLIC | (method.isDefault() ? 0 : FINAL),
                            method.getTypeParameters(), 
                            typedMember.getType(), 
                            naming.selector(method), 
                            method.getParameterLists().get(0).getParameters(),
                            ((Method) member).getTypeErased(),
                            null);
                    classBuilder.method(concreteMemberDelegate);
                }
                
                if (hasOverloads
                        && (method.isDefault() || method.isShared() && !method.isFormal())
                        && (method == subMethod)) {
                    final MethodDefinitionBuilder canonicalMethod = makeDelegateToCompanion(iface,
                            typedMember,
                            model.getType(),
                            PRIVATE,
                            method.getTypeParameters(), 
                            typedMember.getType(), 
                            naming.selector(method, Naming.NA_CANONICAL_METHOD), 
                            method.getParameterLists().get(0).getParameters(),
                            ((Method) member).getTypeErased(),
                            naming.selector(method));
                    classBuilder.method(canonicalMethod);
                }
            } else if (member instanceof Value
                    || member instanceof Setter) {
                TypedDeclaration attr = (TypedDeclaration)member;
                final ProducedTypedReference typedMember = satisfiedType.getTypedMember(attr, null);
                if (needsCompanionDelegate(model, member)) {
                    if (member instanceof Value) {
                        final MethodDefinitionBuilder getterDelegate = makeDelegateToCompanion(iface, 
                                typedMember,
                                model.getType(),
                                PUBLIC | (attr.isDefault() ? 0 : FINAL), 
                                Collections.<TypeParameter>emptyList(), 
                                ((TypedDeclaration)member.getRefinedDeclaration()).getType(), 
                                Naming.getGetterName(attr), 
                                Collections.<Parameter>emptyList(),
                                attr.getTypeErased(),
                                null);
                        classBuilder.method(getterDelegate);
                    }
                    if (member instanceof Setter) { 
                        final MethodDefinitionBuilder setterDelegate = makeDelegateToCompanion(iface, 
                                typedMember,
                                model.getType(),
                                PUBLIC | (((Setter)member).getGetter().isDefault() ? 0 : FINAL), 
                                Collections.<TypeParameter>emptyList(), 
                                typeFact().getAnythingDeclaration().getType(), 
                                Naming.getSetterName(attr), 
                                Collections.<Parameter>singletonList(((Setter)member).getParameter()),
                                ((Setter) member).getTypeErased(),
                                null);
                        classBuilder.method(setterDelegate);
                    }
                    if (Decl.isValue(member) 
                            && ((Value)attr).isVariable()) {
                        // I don't *think* this can happen because although a 
                        // variable Value can be declared on an interface it 
                        // will need to we refined as a Getter+Setter on a 
                        // subinterface in order for there to be a method in a 
                        // $impl to delegate to
                        throw new RuntimeException();
                    }
                }
            } else if (needsCompanionDelegate(model, member)) {
                log.error("ceylon", "Unhandled concrete interface member " + member.getQualifiedNameString() + " " + member.getClass());
            }
        }
        
        // Add $impl instances for the whole interface hierarchy
        satisfiedInterfaces.add(iface);
        for (ProducedType sat : iface.getSatisfiedTypes()) {
            sat = model.getType().getSupertype(sat.getDeclaration());
            concreteMembersFromSuperinterfaces(model, classBuilder, sat, satisfiedInterfaces);
        }
        
    }

    private void generateInstantiatorDelegate(
            ClassDefinitionBuilder classBuilder, ProducedType satisfiedType,
            Interface iface, Class klass, ProducedType currentType) {
        ProducedType typeMember = satisfiedType.getTypeMember(klass, Collections.<ProducedType>emptyList());
        java.util.List<TypeParameter> typeParameters = klass.getTypeParameters();
        java.util.List<Parameter> parameters = klass.getParameterLists().get(0).getParameters();
        
        String instantiatorMethodName = naming.getInstantiatorMethodName(klass);
        for (Parameter param : parameters) {
            if (Strategy.hasDefaultParameterValueMethod(param)) {
                final ProducedTypedReference typedParameter = typeMember.getTypedParameter(param);
                // If that method has a defaulted parameter, 
                // we need to generate a default value method
                // which also delegates to the $impl
                final MethodDefinitionBuilder defaultValueDelegate = makeDelegateToCompanion(iface,
                        typedParameter,
                        currentType,
                        PUBLIC | FINAL, 
                        typeParameters, 
                        typedParameter.getType(),
                        naming.getDefaultedParamMethodName(klass, param), 
                        parameters.subList(0, parameters.indexOf(param)),
                        param.getModel().getTypeErased(),
                        null);
                classBuilder.method(defaultValueDelegate);
            }
            if (Strategy.hasDefaultParameterOverload(param)) {
                final MethodDefinitionBuilder overload = makeDelegateToCompanion(iface,
                        typeMember,
                        currentType,
                        PUBLIC | FINAL, 
                        typeParameters,  
                        typeMember.getType(), 
                        instantiatorMethodName, 
                        parameters.subList(0, parameters.indexOf(param)),
                        false,
                        null);
                classBuilder.method(overload);
            }
        }
        final MethodDefinitionBuilder overload = makeDelegateToCompanion(iface,
                typeMember,
                currentType,
                PUBLIC | FINAL, 
                typeParameters,  
                typeMember.getType(), 
                instantiatorMethodName, 
                parameters,
                false,
                null);
        classBuilder.method(overload);
    }

    private boolean needsCompanionDelegate(final Class model, Declaration member) {
        final boolean mostRefined;
        Declaration m = model.getMember(member.getName(), null, false);
        if (member instanceof Setter && Decl.isGetter(m)) {
            mostRefined = member.equals(((Value)m).getSetter());
        } else {
            mostRefined = member.equals(m);
        }
        return mostRefined
                && (member.isDefault() || !member.isFormal());
    }

    /**
     * Generates a method which delegates to the companion instance $Foo$impl
     */
    private MethodDefinitionBuilder makeDelegateToCompanion(Interface iface,
            ProducedReference typedMember,
            ProducedType currentType,
            final long mods,
            final java.util.List<TypeParameter> typeParameters,
            final ProducedType methodType,
            final String methodName,
            final java.util.List<Parameter> parameters, 
            boolean typeErased,
            final String targetMethodName) {
        final MethodDefinitionBuilder concreteWrapper = MethodDefinitionBuilder.systemMethod(gen(), methodName);
        concreteWrapper.modifiers(mods);
        concreteWrapper.ignoreModelAnnotations();
        if ((mods & PRIVATE) == 0) {
            concreteWrapper.isOverride(true);
        }
        if(typeParameters != null)
            concreteWrapper.reifiedTypeParametersFromModel(typeParameters);
        if (!Decl.isLocal(iface)) {
            for (TypeParameter tp : typeParameters) {
                concreteWrapper.typeParameter(tp);
            }
        }
        boolean explicitReturn = false;
        Declaration member = typedMember.getDeclaration();
        ProducedType returnType = null;
        if (!isAnything(methodType) 
                || ((member instanceof Method || member instanceof Value) && !Decl.isUnboxedVoid(member)) 
                || (member instanceof Method && Strategy.useBoxedVoid((Method)member))) {
            explicitReturn = true;
            if(CodegenUtil.isHashAttribute(member)){
                // delegates for hash attributes are int
                concreteWrapper.resultType(null, make().Type(syms().intType));
                returnType = typedMember.getType();
            }else if (typedMember instanceof ProducedTypedReference) {
                ProducedTypedReference typedRef = (ProducedTypedReference) typedMember;
                concreteWrapper.resultTypeNonWidening(currentType, typedRef, typedMember.getType(), Decl.isLocal(iface)? JT_RAW_TP_BOUND : 0);
                // FIXME: this is redundant with what we computed in the previous line in concreteWrapper.resultTypeNonWidening
                ProducedTypedReference nonWideningTypedRef = gen().nonWideningTypeDecl(typedRef, currentType);
                returnType = gen().nonWideningType(typedRef, nonWideningTypedRef);
            } else {
                
                concreteWrapper.resultType(null, makeJavaType((ProducedType)typedMember, Decl.isLocal(iface)? JT_RAW_TP_BOUND : 0));
                returnType = (ProducedType) typedMember;
            }
        }
        
        ListBuffer<JCExpression> arguments = ListBuffer.<JCExpression>lb();
        
        arguments.add(naming.makeThis());
        
        if (member.isParameter()) {
            
            Declaration p = Decl.getDeclarationContainer(member).getRefinedDeclaration();
            addCapturedLocalArguments(arguments, p);
            addCapturedReifiedTypeParametersInternal(p, p, 
                    typedMember,
                    arguments, false);
        } else {
            addCapturedLocalArguments(arguments, member);
            addCapturedReifiedTypeParametersInternal(member, member, 
                    typedMember,
                    arguments, false);
        }
        
        
        if(typeParameters != null){
            for(TypeParameter tp : typeParameters){
                arguments.add(naming.makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
            }
        }
        for (Parameter param : parameters) {
            final ProducedTypedReference typedParameter = typedMember.getTypedParameter(param);
            ProducedType type;
            // if the supertype method itself got erased to Object, we can't do better than this
            if(gen().willEraseToObject(param.getType()) && !gen().willEraseToBestBounds(param))
                type = typeFact().getObjectDeclaration().getType();
            else
                type = typedParameter.getType();
            concreteWrapper.parameter(param, type, FINAL, Decl.isLocal(iface)? JT_RAW_TP_BOUND : 0, true);
            arguments.add(naming.makeName(param.getModel(), Naming.NA_MEMBER));
        }
        JCExpression qualifierThis = makeJavaType(iface.getType(), JT_COMPANION);
        // if the best satisfied type is not the one we think we implement, we may need to cast
        // our impl accessor to get the expected bounds of the qualifying type
        if(explicitReturn){
            ProducedType javaType = getBestSatisfiedType(currentType, iface);
            ProducedType ceylonType = typedMember.getQualifyingType();
            // don't even bother if the impl accessor is turned to raw because casting it to raw doesn't help
            if(!isTurnedToRaw(ceylonType)
                    // if it's exactly the same we don't need any cast
                    && !javaType.isExactly(ceylonType))
                // this will add the proper cast to the impl accessor
                qualifierThis = expressionGen().applyErasureAndBoxing(qualifierThis, currentType, 
                        false, true, BoxingStrategy.BOXED, ceylonType,
                        ExpressionTransformer.EXPR_WANTS_COMPANION);
        }
        JCExpression expr = make().Apply(
                null,  // TODO Type args
                makeSelect(qualifierThis, (targetMethodName != null) ? targetMethodName : methodName),
                arguments.toList());
        
        if (!explicitReturn) {
            concreteWrapper.body(gen().make().Exec(expr));
        } else {
            // deal with erasure and stuff
            BoxingStrategy boxingStrategy;
            boolean exprBoxed;
            if(member instanceof TypedDeclaration){
                TypedDeclaration typedDecl = (TypedDeclaration) member;
                exprBoxed = !CodegenUtil.isUnBoxed(typedDecl);
                boxingStrategy = CodegenUtil.getBoxingStrategy(typedDecl);
            }else{
                // must be a class or interface
                exprBoxed = true;
                boxingStrategy = BoxingStrategy.UNBOXED;
            }
            // if our interface impl is turned to raw, the whole call will be seen as raw by javac, so we may need
            // to force an additional cast
            if(isTurnedToRaw(typedMember.getQualifyingType()))
                typeErased = true;
            expr = gen().expressionGen().applyErasureAndBoxing(expr, methodType, typeErased, 
                                                               exprBoxed, boxingStrategy,
                                                               returnType, 0);
            concreteWrapper.body(gen().make().Return(expr));
        }
        return concreteWrapper;
    }

    private Boolean hasImpl(Interface iface) {
        if (gen().willEraseToObject(iface.getType())) {
            return false;
        }
        if (iface instanceof LazyInterface) {
            return ((LazyInterface)iface).isCeylon();
        }
        return true;
    }

    private ProducedType getBestSatisfiedType(ProducedType currentType, Interface iface) {
        ProducedType refinedSuperType = currentType.getSupertype(iface);
        ProducedType firstSatisfiedType = getFirstSatisfiedType(currentType, iface);
        // in the very special case of the first satisfied type having type arguments erased to Object and
        // the most refined one having free type parameters, we prefer the one with free type parameters
        // because Java prefers it and it's in range with what nonWideningType does
        Map<TypeParameter, ProducedType> refinedTAs = refinedSuperType.getTypeArguments();
        Map<TypeParameter, ProducedType> firstTAs = firstSatisfiedType.getTypeArguments();
        for(TypeParameter tp : iface.getTypeParameters()){
            ProducedType refinedTA = refinedTAs.get(tp);
            ProducedType firstTA = firstTAs.get(tp);
            if(willEraseToObject(firstTA) && isTypeParameter(refinedTA))
                return refinedSuperType;
        }
        return firstSatisfiedType;
    }

    private ProducedType getFirstSatisfiedType(ProducedType currentType, Interface iface) {
        ProducedType found = null;
        TypeDeclaration currentDecl = currentType.getDeclaration();
        if(currentDecl == iface)
            return currentType;
        if(currentDecl.getExtendedType() != null){
            ProducedType supertype = currentType.getSupertype(currentDecl.getExtendedTypeDeclaration());
            found = getFirstSatisfiedType(supertype, iface);
            if(found != null)
                return found;
        }
        for(ProducedType superInterfaceType : currentType.getSatisfiedTypes()){
            found = getFirstSatisfiedType(superInterfaceType, iface);
            if(found != null)
                return found;
        }
        return null;
    }

    private void buildCompanion(final Tree.ClassOrInterface def,
            final Interface model, ClassDefinitionBuilder classBuilder,
            Tree.TypeParameterList typeParameterList) {
        at(def);
        ClassDefinitionBuilder companionBuilder = classBuilder.getCompanionBuilder(model);
        // Make the constructor private
        companionBuilder.constructorModifiers(PRIVATE);
    }

    public List<JCStatement> transformRefinementSpecifierStatement(SpecifierStatement op, ClassDefinitionBuilder classBuilder) {
        List<JCStatement> result = List.<JCStatement>nil();
        // Check if this is a shortcut form of formal attribute refinement
        if (op.getRefinement()) {
            Tree.Term baseMemberTerm = op.getBaseMemberExpression();
            if(baseMemberTerm instanceof Tree.ParameterizedExpression)
                baseMemberTerm = ((Tree.ParameterizedExpression)baseMemberTerm).getPrimary();
            
            Tree.BaseMemberExpression expr = (BaseMemberExpression) baseMemberTerm;
            Declaration decl = expr.getDeclaration();
            
            if (Decl.isValue(decl)) {
                // Now build a "fake" declaration for the attribute
                Tree.AttributeDeclaration attrDecl = new Tree.AttributeDeclaration(null);
                attrDecl.setDeclarationModel((Value)decl);
                attrDecl.setIdentifier(expr.getIdentifier());
                attrDecl.setScope(op.getScope());
                attrDecl.setSpecifierOrInitializerExpression(op.getSpecifierExpression());
                
                // Make sure the boxing information is set correctly
                BoxingDeclarationVisitor v = new CompilerBoxingDeclarationVisitor(this);
                v.visit(attrDecl);
                
                // Generate the attribute
                transform(attrDecl, classBuilder);
            } else if (decl instanceof Method) {
                // Now build a "fake" declaration for the method
                Tree.MethodDeclaration methDecl = new Tree.MethodDeclaration(null);
                Method m = (Method)decl;
                methDecl.setDeclarationModel(m);
                methDecl.setIdentifier(expr.getIdentifier());
                methDecl.setScope(op.getScope());
                
                Tree.SpecifierExpression specifierExpression = op.getSpecifierExpression();
                methDecl.setSpecifierExpression(specifierExpression);
                
                if(specifierExpression instanceof Tree.LazySpecifierExpression == false){
                    Tree.Expression expression = specifierExpression.getExpression();
                    Tree.Term expressionTerm = Decl.unwrapExpressionsUntilTerm(expression);
                    // we can optimise lambdas and static method calls
                    if(!CodegenUtil.canOptimiseMethodSpecifier(expressionTerm, m)){
                        // we need a field to save the callable value
                        String name = naming.getMethodSpecifierAttributeName(m);
                        JCExpression specifierType = makeJavaType(expression.getTypeModel());
                        JCExpression specifier = expressionGen().transformExpression(expression);
                        classBuilder.field(PRIVATE | FINAL, name, specifierType, specifier, false);
                    }
                }

                // copy from formal declaration
                for (ParameterList pl : m.getParameterLists()) {
                    Tree.ParameterList tpl = new Tree.ParameterList(null);
                    tpl.setModel(pl);
                    for (Parameter p : pl.getParameters()) {
                        Tree.Parameter tp = null;
                        if (p.getModel() instanceof Value) {
                            Tree.ValueParameterDeclaration tvpd = new Tree.ValueParameterDeclaration(null);
                            tvpd.setParameterModel(p);
                            tp = tvpd;
                        } else if (p.getModel() instanceof Method) {
                            Tree.FunctionalParameterDeclaration tfpd = new Tree.FunctionalParameterDeclaration(null);
                            tfpd.setParameterModel(p);
                            tp = tfpd;
                        }
                        tp.setScope(p.getDeclaration().getContainer());
                        //tp.setIdentifier(makeIdentifier(p.getName()));
                        tpl.addParameter(tp);
                    }
                    methDecl.addParameterList(tpl);
                }
                
                // Make sure the boxing information is set correctly
                BoxingDeclarationVisitor v = new CompilerBoxingDeclarationVisitor(this);
                v.visit(methDecl);
                
                // Generate the method
                classBuilder.method(methDecl);
            }
        } else {
            // Normal case, just generate the specifier statement
            result = result.append(expressionGen().transform(op));
        }
        
        Tree.Term term = op.getBaseMemberExpression();
        if (term instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = (Tree.BaseMemberExpression)term;
            DeferredSpecification ds = statementGen().getDeferredSpecification(bme.getDeclaration());
            if (ds != null && needsInnerSubstitution(term.getScope(), bme.getDeclaration())){
                result = result.append(ds.openInnerSubstitution());
            }
        }
        return result;
    }

    /**
     * We only need an inner substitution if we're within that substitution's scope
     */
    private boolean needsInnerSubstitution(Scope scope, Declaration declaration) {
        while(scope != null && scope instanceof Package == false){
            if(scope instanceof ControlBlock){
                Set<MethodOrValue> specifiedValues = ((ControlBlock) scope).getSpecifiedValues();
                if(specifiedValues != null && specifiedValues.contains(declaration))
                    return true;
            }
            scope = scope.getScope();
        }
        return false;
    }
    
    public void transform(AttributeDeclaration decl, ClassDefinitionBuilder classBuilder) {
        final Value model = decl.getDeclarationModel();
        boolean lazy = decl.getSpecifierOrInitializerExpression() instanceof LazySpecifierExpression;
        boolean useField = Strategy.useField(model) && !lazy;
        String attrName = decl.getIdentifier().getText();

        // Only a non-formal or a concrete-non-lazy attribute has a corresponding field
        // and if a captured class parameter exists with the same name we skip this part as well
        Parameter parameter = CodegenUtil.findParamForDecl(decl);
        boolean createField = Strategy.createField(parameter, model) && !lazy;
        boolean concrete = Decl.withinInterface(decl)
                && decl.getSpecifierOrInitializerExpression() != null;
        if (!lazy && 
                (concrete || 
                        (!Decl.isFormal(decl) 
                                && createField))) {
            ProducedTypedReference typedRef = getTypedReference(model);
            ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
            ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
            
            if (Decl.isIndirect(decl)) {
                attrName = naming.getAttrClassName(model, 0);
                nonWideningType = getGetterInterfaceType(model);
            }
            
            JCExpression initialValue = null;
            if (decl.getSpecifierOrInitializerExpression() != null) {
                Value declarationModel = model;
                initialValue = expressionGen().transformExpression(decl.getSpecifierOrInitializerExpression().getExpression(), 
                        CodegenUtil.getBoxingStrategy(declarationModel), 
                        nonWideningType);
            }

            int flags = 0;
            
            if (!CodegenUtil.isUnBoxed(nonWideningTypedRef.getDeclaration())) {
                flags |= JT_NO_PRIMITIVES;
            }
            JCExpression type = makeJavaType(nonWideningType, flags);
            if (Decl.isLate(decl)) {
                type = make().TypeArray(type);
            }

            int modifiers = (useField) ? transformAttributeFieldDeclFlags(decl) : transformLocalDeclFlags(decl);
            
            // If the attribute is really from a parameter then don't generate a field
            // (makeAttributeForValueParameter() or makeMethodForFunctionalParameter() 
            //  does it in those cases)
            if (parameter == null
                    || parameter.isHidden()) {
                if (concrete) {
                    classBuilder.getCompanionBuilder((TypeDeclaration)model.getContainer()).field(modifiers, attrName, type, initialValue, !useField);
                } else {
                    // fields should be ignored, they are accessed by the getters
                    classBuilder.field(modifiers, attrName, type, initialValue, !useField, makeAtIgnore());
                }        
            }
        }

        boolean withinInterface = Decl.withinInterface(decl);
        if (useField || withinInterface || lazy) {
            if (!withinInterface || model.isShared()) {
                // Generate getter in main class or interface (when shared)
                classBuilder.attribute(makeGetter(decl, false, lazy));
            }
            if (withinInterface && lazy) {
                // Generate getter in companion class
                classBuilder.getCompanionBuilder((Interface)decl.getDeclarationModel().getContainer()).attribute(makeGetter(decl, true, lazy));
            }
            if (Decl.isVariable(decl) || Decl.isLate(decl)) {
                if (!withinInterface || model.isShared()) {
                    // Generate setter in main class or interface (when shared)
                    classBuilder.attribute(makeSetter(decl, false, lazy));
                }
                if (withinInterface) {
                    // Generate setter in companion class
                    classBuilder.getCompanionBuilder((Interface)decl.getDeclarationModel().getContainer()).attribute(makeSetter(decl, true, lazy));
                }
            }
        }
    }
    
    private static int transformDeclarationSharedFlags(Declaration decl){
        return Decl.isShared(decl) && !Decl.isAncestorLocal(decl) ? PUBLIC : 0;
    }
    
    static int transformClassDeclFlags(ClassOrInterface cdecl) {
        int result = 0;

        result |= transformDeclarationSharedFlags(cdecl);
        // aliases cannot be abstract, especially since they're just placeholders
        result |= (cdecl instanceof Class) && (cdecl.isAbstract() || cdecl.isFormal()) && !cdecl.isAlias() ? ABSTRACT : 0;
        result |= (cdecl instanceof Interface) ? INTERFACE : 0;
        // aliases are always final placeholders, final classes are also final
        result |= (cdecl instanceof Class) && (cdecl.isAlias() || cdecl.isFinal())  ? FINAL : 0;

        return result;
    }

    private int transformTypeAliasDeclFlags(TypeAlias decl) {
        int result = 0;

        result |= transformDeclarationSharedFlags(decl);
        result |= FINAL;

        return result;
    }

    private int transformClassDeclFlags(Tree.ClassOrInterface cdecl) {
        return transformClassDeclFlags(cdecl.getDeclarationModel());
    }
    
    static int transformMethodDeclFlags(Method def) {
        int result = 0;

        if (def.isToplevel()) {
            result |= def.isShared() ? PUBLIC : 0;
            result |= STATIC;
        } else if (Decl.isLocalNotInitializer(def)) {
            result |= def.isShared() ? PUBLIC : 0;
        } else {
            result |= def.isShared() ? PUBLIC : PRIVATE;
            result |= def.isFormal() && !def.isDefault() ? ABSTRACT : 0;
            result |= !(def.isFormal() || def.isDefault() || def.getContainer() instanceof Interface) ? FINAL : 0;
        }

        return result;
    }
    
    private int transformAttributeFieldDeclFlags(Tree.AttributeDeclaration cdecl) {
        int result = 0;

        result |= Decl.isVariable(cdecl) || Decl.isLate(cdecl) ? 0 : FINAL;
        result |= PRIVATE;

        return result;
    }

    private int transformLocalDeclFlags(Tree.AttributeDeclaration cdecl) {
        int result = 0;

        result |= Decl.isVariable(cdecl) ? 0 : FINAL;

        return result;
    }

    /**
     * Returns the modifier flags to be used for the getter & setter for the 
     * given attribute-like declaration.  
     * @param tdecl attribute-like declaration (Value, Getter, Parameter etc)
     * @param forCompanion Whether the getter/setter is on a companion type
     * @return The modifier flags.
     */
    int transformAttributeGetSetDeclFlags(TypedDeclaration tdecl, boolean forCompanion) {
        if (tdecl instanceof Setter) {
            // Spec says: A setter may not be annotated shared, default or 
            // actual. The visibility and refinement modifiers of an attribute 
            // with a setter are specified by annotating the matching getter.
            tdecl = ((Setter)tdecl).getGetter();
        }
        
        int result = 0;

        result |= tdecl.isShared() ? PUBLIC : PRIVATE;
        result |= ((tdecl.isFormal() && !tdecl.isDefault()) && !forCompanion) ? ABSTRACT : 0;
        result |= !(tdecl.isFormal() || tdecl.isDefault() || Decl.withinInterface(tdecl)) || forCompanion ? FINAL : 0;

        return result;
    }

    private int transformObjectDeclFlags(Value cdecl) {
        int result = 0;

        result |= FINAL;
        result |= !Decl.isAncestorLocal(cdecl) && Decl.isShared(cdecl) ? PUBLIC : 0;

        return result;
    }

    private AttributeDefinitionBuilder makeGetterOrSetter(Tree.AttributeDeclaration decl, boolean forCompanion, boolean lazy, 
                                                          AttributeDefinitionBuilder builder, boolean isGetter) {
        at(decl);
        if (forCompanion || lazy) {
            if (decl.getSpecifierOrInitializerExpression() != null) {
                Value declarationModel = decl.getDeclarationModel();
                ProducedTypedReference typedRef = getTypedReference(declarationModel);
                ProducedTypedReference nonWideningTypedRef = nonWideningTypeDecl(typedRef);
                ProducedType nonWideningType = nonWideningType(typedRef, nonWideningTypedRef);
                
                JCExpression expr = expressionGen().transformExpression(decl.getSpecifierOrInitializerExpression().getExpression(), 
                        CodegenUtil.getBoxingStrategy(declarationModel), 
                        nonWideningType);
                expr = convertToIntIfHashAttribute(declarationModel, expr);
                builder.getterBlock(make().Block(0, List.<JCStatement>of(make().Return(expr))));
            } else {
                JCExpression accessor = naming.makeQualifiedName(
                        naming.makeQuotedThis(), 
                        decl.getDeclarationModel(), 
                        Naming.NA_MEMBER | (isGetter ? Naming.NA_GETTER : Naming.NA_SETTER));
                
                if (isGetter) {
                    builder.getterBlock(make().Block(0, List.<JCStatement>of(make().Return(
                            make().Apply(
                                    null, 
                                    accessor, 
                                    List.<JCExpression>nil())))));
                } else {
                    List<JCExpression> args = List.<JCExpression>of(naming.makeName(decl.getDeclarationModel(), Naming.NA_MEMBER | Naming.NA_IDENT));
                    builder.setterBlock(make().Block(0, List.<JCStatement>of(make().Exec(
                            make().Apply(
                                    null, 
                                    accessor, 
                                    args)))));
                }
                
            }
        }
        if(forCompanion)
            builder.notActual();
        return builder
            .modifiers(transformAttributeGetSetDeclFlags(decl.getDeclarationModel(), forCompanion))
            .isFormal((Decl.isFormal(decl) || Decl.withinInterface(decl)) && !forCompanion);
    }
    
    private AttributeDefinitionBuilder makeGetter(Tree.AttributeDeclaration decl, boolean forCompanion, boolean lazy) {
        at(decl);
        String attrName = decl.getIdentifier().getText();
        AttributeDefinitionBuilder getter = AttributeDefinitionBuilder
            .getter(this, attrName, decl.getDeclarationModel())
            .userAnnotations(expressionGen()
            .transform(decl.getAnnotationList()));
        
        if (Decl.isIndirect(decl)) {
            getter.getterBlock(generateIndirectGetterBlock(decl.getDeclarationModel()));
        }
        
        return makeGetterOrSetter(decl, forCompanion, lazy, getter, true);
    }

    private JCTree.JCBlock generateIndirectGetterBlock(Value v) {
        JCTree.JCExpression returnExpr;
        returnExpr = naming.makeQualIdent(naming.makeName(v, Naming.NA_WRAPPER), "get_");
        returnExpr = make().Apply(null, returnExpr, List.<JCExpression>nil());
        JCReturn returnValue = make().Return(returnExpr);
        List<JCStatement> stmts = List.<JCTree.JCStatement>of(returnValue);   
        JCTree.JCBlock block = make().Block(0L, stmts);
        return block;
    }

    private AttributeDefinitionBuilder makeSetter(Tree.AttributeDeclaration decl, boolean forCompanion, boolean lazy) {
        at(decl);
        String attrName = decl.getIdentifier().getText();
        AttributeDefinitionBuilder setter = AttributeDefinitionBuilder.setter(this, attrName, decl.getDeclarationModel());
        return makeGetterOrSetter(decl, forCompanion, lazy, setter, false);
    }

    public List<JCTree> transformWrappedMethod(Tree.AnyMethod def) {
        final Method model = def.getDeclarationModel();
        if (model.isParameter()) {
            return List.nil();
        }
        naming.clearSubstitutions(model);
        // Generate a wrapper class for the method
        String name = def.getIdentifier().getText();
        ClassDefinitionBuilder builder = ClassDefinitionBuilder.methodWrapper(this, name, Decl.isShared(def));
        
        if (Decl.isAnnotationConstructor(def)) {
            AnnotationInvocation ai = ((AnnotationInvocation)def.getDeclarationModel().getAnnotationConstructor());
            builder.annotations(List.of(makeAtAnnotationInstantiation(ai)));
            builder.annotations(makeExprAnnotations(def, ai));
        }
        
        builder.defs((model.isToplevel() ? toplevelFunctionTransformation : localFunctionTransformation).transform(def));
        
        // Toplevel method
        if (Strategy.generateMain(def)) {
            // Add a main() method
            builder.method(makeMainForFunction(model));
        }
        
        List<JCTree> result = builder.build();
        
        if (Decl.isLocal(def)) {
            // Inner method
            JCVariableDecl call = at(def).VarDef(
                    make().Modifiers(FINAL),
                    naming.getSyntheticInstanceName(model),
                    naming.makeSyntheticClassname(model),
                    makeSyntheticInstance(model));
            result = result.append(call);
        }
        
        //if (Decl.isAnnotationConstructor(def)) {
            //result = result.prependList(transformAnnotationConstructorType(def));
        //}
        return result;
    }

    /**
     * Make the {@code @*Exprs} annotations to hold the literal arguments 
     * to the invocation.
     */
    private List<JCAnnotation> makeExprAnnotations(Tree.AnyMethod def,
            AnnotationInvocation ai) {
        AnnotationInvocation ctor = (AnnotationInvocation)def.getDeclarationModel().getAnnotationConstructor();
        return ai.makeExprAnnotations(expressionGen(), ctor, List.<AnnotationFieldName>nil());
    }
    
    public JCAnnotation makeAtAnnotationInstantiation(AnnotationInvocation invocation) {
        return invocation.encode(this, ListBuffer.<JCExpression>lb());
    }

    private MethodDefinitionBuilder makeAnnotationMethod(Tree.Parameter parameter) {
        Parameter parameterModel = parameter.getParameterModel();
        JCExpression type = transformAnnotationMethodType(parameter);
        JCExpression defaultValue = parameterModel.isDefaulted() ? transformAnnotationParameterDefault(parameter) : null;
        MethodDefinitionBuilder mdb = MethodDefinitionBuilder.method(this, parameterModel.getModel(), Naming.NA_ANNOTATION_MEMBER);
        if (isMetamodelReference(parameterModel.getType())
                || 
                (typeFact().isIterableType(parameterModel.getType())
                && isMetamodelReference(typeFact().getIteratedType(parameterModel.getType())))) {
            mdb.modelAnnotations(List.of(make().Annotation(make().Type(syms().ceylonAtDeclarationReferenceType), 
                    List.<JCExpression>nil())));
        } else if (Decl.isEnumeratedTypeWithAnonCases(parameterModel.getType())
                || 
                (typeFact().isIterableType(parameterModel.getType())
                        && Decl.isEnumeratedTypeWithAnonCases(typeFact().getIteratedType(parameterModel.getType())))) {
            mdb.modelAnnotations(List.of(make().Annotation(make().Type(syms().ceylonAtEnumerationReferenceType), 
                    List.<JCExpression>nil())));
        }
        mdb.modifiers(PUBLIC | ABSTRACT);
        mdb.resultType(null, type);
        mdb.defaultValue(defaultValue);
        mdb.noBody();
        return mdb;
    }
    
    public List<JCTree> transform(Tree.AnyMethod def,
            ClassDefinitionBuilder classBuilder) {
        final Method model = def.getDeclarationModel();
        if (model.isParameter()) {
            // Functional parameters get transformed in transformClass()
            // using the classMethodFromFunctionalParameterTransformation
            return List.nil();
        }
        List<JCTree> result;
        if (model.isToplevel()) {
            result = toplevelFunctionTransformation.transform(def);
        } else if (Decl.withinClass(model)
                && !Decl.isLocalToInitializer(model)) {
            result = classMethodTransformation.transform(def);
        } else if (Decl.withinInterface(model)) {
            classBuilder.getCompanionBuilder((TypeDeclaration)model.getContainer())
                .defs(companionMethodTransformation.transform(def));
            result = interfaceMethodTransformation.transform(def);
        } else {
            // It must be a local function
            result = localFunctionTransformation.transform(def);
        }
        return result;
    }
    
    /**
     * Constructs all but the outer-most method of a {@code Method} with 
     * multiple parameter lists 
     * @param model The {@code Method} model
     * @param body The inner-most body
     */
    List<JCStatement> transformMplBody(java.util.List<Tree.ParameterList> parameterListsTree,
            Method model,
            List<JCStatement> body) {
        ProducedType resultType = model.getType();
        for (int index = model.getParameterLists().size() - 1; index >  0; index--) {
            resultType = gen().typeFact().getCallableType(resultType);
            CallableBuilder cb = CallableBuilder.mpl(gen(), resultType, model.getParameterLists().get(index), parameterListsTree.get(index), body);
            body = List.<JCStatement>of(make().Return(cb.build()));
        }
        return body;
    }
    
    private List<JCStatement> transformDeferredMethodBody(Tree.AnyMethod def) {
        final Method model = def.getDeclarationModel();
        // Uninitialized or deferred initialized method => Make a Callable field
        String fieldName = naming.selector(model);
        final Parameter initializingParameter = CodegenUtil.findParamForDecl(def);
        int mods = PRIVATE;
        JCExpression initialValue;
        if (initializingParameter != null) {
            mods |= FINAL;
            initialValue = makeUnquotedIdent(Naming.getAliasedParameterName(initializingParameter));
        } else {
            // The field isn't initialized by a parameter, but later in the block
            initialValue = makeNull();
        }
        ProducedType callableType = typeFact().getCallableType(model.getType());
        if (Decl.isLocal(def.getDeclarationModel())) {
            
        } else {
            current().field(mods, fieldName, makeJavaType(callableType), initialValue, false);
        }
        Invocation invocation = new CallableSpecifierInvocation(
                this,
                model,
                makeUnquotedIdent(fieldName),
                // we don't have to give a Term here because it's used for casting the Callable in case of callable erasure, 
                // but with deferred methods we can't define them so that they are erased so we're good
                null,
                def);
        invocation.handleBoxing(true);
        JCExpression call = expressionGen().transformInvocation(invocation);
        JCStatement stmt;
        if (!isVoid(def) || !Decl.isUnboxedVoid(model) || Strategy.useBoxedVoid(model)) {
            stmt = make().Return(call);
        } else {
            stmt = make().Exec(call);
        }
        
        JCStatement result;
        if (initializingParameter == null) {
            // If the field isn't initialized by a parameter we have to 
            // cope with the possibility that it's never initialized
            final JCBinary cond = make().Binary(JCTree.EQ, makeUnquotedIdent(fieldName), makeNull());
            final JCStatement throw_ = make().Throw(make().NewClass(null, null, 
                    makeIdent(syms().ceylonUninitializedMethodErrorType), 
                    List.<JCExpression>nil(), 
                    null));
            result = make().If(cond, throw_, stmt);
        } else {
            result = stmt;
        }
        return List.<JCStatement>of(result);
    }

    private List<JCStatement> transformMethodBlock(
            final Tree.MethodDefinition def) {
        final Method model = def.getDeclarationModel();
        final Tree.Block block = def.getBlock();
        List<JCStatement> body;
        boolean prevNoExpressionlessReturn = statementGen().noExpressionlessReturn;
        try {
            statementGen().noExpressionlessReturn = Decl.isMpl(model) || Strategy.useBoxedVoid(model);
            body = statementGen().transformBlock(block);
        } finally {
            statementGen().noExpressionlessReturn = prevNoExpressionlessReturn;
        }
        // We void methods need to have their Callables return null
        // so adjust here.
        if ((Decl.isMpl(model) || Strategy.useBoxedVoid(model))
                && !block.getDefinitelyReturns()) {
            if (Decl.isUnboxedVoid(model)) {
                body = body.append(make().Return(makeNull()));
            } else {
                body = body.append(make().Return(makeErroneous(block, "compiler bug: non-void method doesn't definitely return")));
            }
        }
        return body;
    }

    List<JCStatement> transformSpecifiedMethodBody(Tree.MethodDeclaration  def, SpecifierExpression specifierExpression) {
        if (specifierExpression == null) {
            return null;
        }
        return transformSpecifiedMethodBody(def.getDeclarationModel(), specifierExpression);
    }
    
    List<JCStatement> transformSpecifiedMethodBody(final Method model, SpecifierExpression specifierExpression) {
        List<JCStatement> body;
        boolean isLazy = specifierExpression instanceof Tree.LazySpecifierExpression;
        boolean returnNull = false;
        JCExpression bodyExpr;
        Tree.Term term = null;
        if (specifierExpression != null
                && specifierExpression.getExpression() != null) {
            term = Decl.unwrapExpressionsUntilTerm(specifierExpression.getExpression());
        }
        if (!isLazy && term instanceof Tree.FunctionArgument) {
            // Method specified with lambda: Don't bother generating a 
            // Callable, just transform the expr to use as the method body.
            Tree.FunctionArgument fa = (Tree.FunctionArgument)term;
            ProducedType resultType = model.getType();
            returnNull = isAnything(resultType) && fa.getExpression().getUnboxed();
            final java.util.List<com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter> lambdaParams = fa.getParameterLists().get(0).getParameters();
            final java.util.List<Parameter> defParams = model.getParameterLists().get(0).getParameters();
            List<Substitution> substitutions = List.nil();
            for (int ii = 0; ii < lambdaParams.size(); ii++) {
                substitutions = substitutions.append(naming.addVariableSubst(
                        lambdaParams.get(ii).getParameterModel().getModel(), 
                        defParams.get(ii).getName()));
            }
            bodyExpr = gen().expressionGen().transformExpression(fa.getExpression(), 
                            returnNull ? BoxingStrategy.INDIFFERENT : CodegenUtil.getBoxingStrategy(model), 
                            resultType);
            for (Substitution subs : substitutions) {
                subs.close();
            }
        } else if (!isLazy && typeFact().isCallableType(term.getTypeModel())) {
            returnNull = isAnything(term.getTypeModel()) && term.getUnboxed();
            Method method = model;
            boolean lazy = specifierExpression instanceof Tree.LazySpecifierExpression;
            boolean inlined = CodegenUtil.canOptimiseMethodSpecifier(term, method);
            Invocation invocation;
            if ((lazy || inlined)
                    && term instanceof Tree.MemberOrTypeExpression
                    && ((Tree.MemberOrTypeExpression)term).getDeclaration() instanceof Functional) {
                Declaration primaryDeclaration = ((Tree.MemberOrTypeExpression)term).getDeclaration();
                ProducedReference producedReference = ((Tree.MemberOrTypeExpression)term).getTarget();
                invocation = new MethodReferenceSpecifierInvocation(
                        this, 
                        (Tree.MemberOrTypeExpression)term, 
                        primaryDeclaration,
                        producedReference,
                        method,
                        specifierExpression);
            } else if (!lazy && !inlined) {
                // must be a callable we stored
                String name = naming.getMethodSpecifierAttributeName(method);
                invocation = new CallableSpecifierInvocation(
                        this, 
                        method, 
                        naming.makeUnquotedIdent(name),
                        term,
                        term);
            } else if (isCeylonCallableSubtype(term.getTypeModel())) {
                invocation = new CallableSpecifierInvocation(
                        this, 
                        method, 
                        expressionGen().transformExpression(term),
                        term,
                        term);
            } else {
                throw Assert.fail("Unhandled primary " + term);
            }
            invocation.handleBoxing(true);
            bodyExpr = expressionGen().transformInvocation(invocation);
        } else {
            bodyExpr = expressionGen().transformExpression(model, term);
            // The innermost of an MPL method declared void needs to return null
            returnNull = Decl.isUnboxedVoid(model) && Decl.isMpl(model);
        }
        if (!Decl.isUnboxedVoid(model)
                || Decl.isMpl(model)
                || Strategy.useBoxedVoid(model)) {
            if (returnNull) {
                body = List.<JCStatement>of(make().Exec(bodyExpr), make().Return(makeNull()));
            } else {
                body = List.<JCStatement>of(make().Return(bodyExpr));
            }
        } else {
            body = List.<JCStatement>of(make().Exec(bodyExpr));
        }
        return body;
    }

    private boolean isVoid(Tree.Declaration def) {
        if (def instanceof Tree.AnyMethod) {
            return gen().isAnything(((Tree.AnyMethod)def).getType().getTypeModel());
        } else if (def instanceof Tree.AnyClass) {
            // Consider classes void since ctors don't require a return statement
            return true;
        }
        throw new RuntimeException();
    }
    
    private boolean isVoid(Declaration def) {
        if (def instanceof Method) {
            return gen().isAnything(((Method)def).getType());
        } else if (def instanceof Class) {
            // Consider classes void since ctors don't require a return statement
            return true;
        }
        throw new RuntimeException();
    }

    /** 
     * Abstraction over possible transformations for the body of an overloaded
     * declaration which supplies a defaulted argument.
     * @see DefaultedArgumentOverload 
     */
    abstract class DaoBody<D extends Declaration&Functional> {
        
        protected List<JCExpression> makeTypeArguments(D model, DefaultedArgumentOverload<D> ol) {
            if (ol.defaultParameterMethodOnSelf(model) 
                    || ol.defaultParameterMethodOnOuter(model)) {
                return List.<JCExpression>nil();
            } else if (ol.defaultParameterMethodStatic(model)){
                return typeArguments(model);
            } else {
                return List.<JCExpression>nil();
            }
        }

        abstract void makeBody(D model, DefaultedArgumentOverload<D> overloaded,
                MethodDefinitionBuilder overloadBuilder,
                ParameterList parameterList,
                Parameter currentParameter,
                java.util.List<TypeParameter> typeParameterList);

        JCExpression makeMethodNameQualifier() {
            return null;
        }
        
    }
    
    /** 
     * a body-less (i.e. abstract) transformation.
     */
    private class DaoAbstract<D extends Declaration&Functional> extends DaoBody<D> {
        @Override
        public void makeBody(D model, DefaultedArgumentOverload<D> overloaded,
                MethodDefinitionBuilder overloadBuilder,
                ParameterList parameterList,
                Parameter currentParameter,
                java.util.List<TypeParameter> typeParameterList) {
            overloadBuilder.noBody();
        }
    }
    final DaoAbstract daoAbstract = new DaoAbstract();
    /**
     * a transformation for an overloaded 
     * default parameter body which delegates to the "canonical"
     * method/constructor using a {@code let} expresssion
     * to substitute defaulted arguments
     */
    private class DaoThis<D extends Declaration&Functional> extends DaoBody<D> {
        public void makeBody(D model, DefaultedArgumentOverload<D> overloaded,
                MethodDefinitionBuilder overloadBuilder,
                ParameterList parameterList,
                Parameter currentParameter,
                java.util.List<TypeParameter> typeParameterList) {
            ListBuffer<JCExpression> canonicalArgs = ListBuffer.<JCExpression>lb();
            ListBuffer<JCExpression> dpmArgs = ListBuffer.<JCExpression>lb();
            overloaded.appendDpmImplicitArguments(model, typeParameterList, overloadBuilder, dpmArgs);
            overloaded.appendCanonicalImplicitArguments(model, typeParameterList, overloadBuilder, canonicalArgs);
            
            ListBuffer<JCStatement> vars = ListBuffer.<JCStatement>lb();
            
            boolean initedVars = false;
            boolean useDefault = false;
            for (Parameter parameterModel : parameterList.getParameters()) {
                if (currentParameter != null && parameterModel == currentParameter) {
                    useDefault = true;
                }
                if (useDefault) {
                    JCExpression defaultArgument;
                    if (Strategy.hasDefaultParameterValueMethod(parameterModel)) {
                        if (!initedVars) {
                            // Only call init vars if we actually invoke a defaulted param method
                            overloaded.initVars(model, currentParameter, vars);
                        }
                        initedVars = true;
                        JCExpression defaultValueMethodName = overloaded.makeDefaultedParamMethod(model, parameterModel, this);
                        at(null);
                        defaultArgument = make().Apply(makeTypeArguments(model, overloaded), 
                                defaultValueMethodName, 
                                ListBuffer.<JCExpression>lb().appendList(dpmArgs).toList());
                    } else if (Strategy.hasEmptyDefaultArgument(parameterModel)) {
                        defaultArgument = makeEmptyAsSequential(true);
                    } else {
                        defaultArgument = makeErroneous(null, "compiler bug: parameter " + parameterModel.getName() + " has an unsupported default value");
                    }
                    Naming.SyntheticName varName = naming.temp(parameterModel.getName());
                    ProducedType paramType = overloaded.parameterType(parameterModel);
                    vars.append(makeVar(varName, 
                            makeJavaType(paramType, CodegenUtil.isUnBoxed(parameterModel.getModel()) ? 0 : JT_NO_PRIMITIVES), 
                            defaultArgument));
                    dpmArgs.add(varName.makeIdent());
                    canonicalArgs.add(varName.makeIdent());
                } else {
                    dpmArgs.add(naming.makeName(parameterModel.getModel(), Naming.NA_MEMBER | Naming.NA_ALIASED));
                    canonicalArgs.add(naming.makeName(parameterModel.getModel(), Naming.NA_MEMBER | Naming.NA_ALIASED));
                }
            }
            makeBody(model, overloaded, overloadBuilder, canonicalArgs, vars);
        }
        
        protected final void makeBody(D model, DefaultedArgumentOverload<D> overloaded, MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args, ListBuffer<JCStatement> vars) {
            JCExpression invocation = overloaded.makeInvocation(model, this, args);
            if (!isVoid(model)
                    // MPL overloads always return a Callable
                    || (model instanceof Functional && Decl.isMpl((Functional) model))
                    || (model instanceof Method && !(Decl.isUnboxedVoid(model)))
                    || (model instanceof Method && Strategy.useBoxedVoid((Method)model)) 
                    || Strategy.generateInstantiator(model) && overloaded instanceof DefaultedArgumentInstantiator) {
                if (!vars.isEmpty()) {
                    invocation = make().LetExpr(vars.toList(), invocation);
                }
                overloadBuilder.body(make().Return(invocation));
            } else {
                vars.append(make().Exec(invocation));
                invocation = make().LetExpr(vars.toList(), makeNull());
                overloadBuilder.body(make().Exec(invocation));
            }
        }
    }
    final DaoThis daoThis = new DaoThis();
    
    /**
     * specialises {@link DaoThis} for transforming declarations for companion classes
     */
    private class DaoCompanion<D extends Declaration&Functional>  extends DaoThis<D> {
        @Override
        protected final List<JCExpression> makeTypeArguments(D model, DefaultedArgumentOverload<D> ol) {
            return List.<JCExpression>nil();
        }
    }
    final DaoCompanion daoCompanion = new DaoCompanion();
    
    /**
     * a transformation for an overloaded 
     * default parameter method body which delegates
     * to the super class. This is used when we need to refine the return 
     * type of a DPM. 
     */
    private class DaoSuper<D extends Declaration&Functional> extends DaoBody<D> {

        JCExpression makeMethodNameQualifier() {
            return naming.makeSuper();
        }
        
        @Override
        void makeBody(
                D model,
                DefaultedArgumentOverload<D> overloaded,
                MethodDefinitionBuilder overloadBuilder,
                ParameterList parameterList,
                Parameter currentParameter,
                java.util.List<TypeParameter> typeParameterList) {
            
            ListBuffer<JCExpression> args = ListBuffer.<JCExpression>lb();
            for (Parameter parameter : parameterList.getParameters()) {
                if (currentParameter != null && parameter == currentParameter) {
                    break;
                }
                args.add(naming.makeUnquotedIdent(parameter.getName()));
            }
            JCExpression superCall = overloaded.makeInvocation(model, this, args);
            /*JCMethodInvocation superCall = make().Apply(null,
                    naming.makeQualIdent(naming.makeSuper(), ((Method)overloaded.getModel()).getName()),
                    args.toList());*/
            JCExpression refinedType = makeJavaType(((Method)model).getType(), JT_NO_PRIMITIVES);
            overloadBuilder.body(make().Return(make().TypeCast(refinedType, superCall)));
        }
    }
    final DaoSuper daoSuper = new DaoSuper();
    
    private java.util.List<TypeParameter> typeParameterListModel(Tree.TypeParameterList typeParameterList) {
        java.util.List<TypeParameter> tpList = null;
        if (typeParameterList != null) {
            tpList = new ArrayList<TypeParameter>();
            for (TypeParameterDeclaration tpd : typeParameterList.getTypeParameterDeclarations()) {
                tpList.add(tpd.getDeclarationModel());
            }
        }
        return tpList;
    }
    
    /**
     * A base class for transformations used for Ceylon declarations
     * which have defaulted parameters. We generate an overloaded 
     * method/constructor whose implementation which supplies the default
     * argument and delegates to the "canonical" method.
     * 
     * Subclasses specialise for the different kinds of declaration, and 
     * a separate set of classes handle the various transformations for the 
     * body of an overloaded declaration (see {@link DaoBody})
     */
    abstract class DefaultedArgumentOverload<D extends Declaration&Functional> {
        JCExpression makeDefaultedParamMethod(D model, Parameter parameterModel, DaoBody<D> body) {
            return naming.makeDefaultedParamMethod(makeDefaultArgumentValueMethodQualifier(model, body), parameterModel);
        }
        
        
        /**
         * Generates an overloaded method or constructor.
         */
        public MethodDefinitionBuilder makeOverload (
                D functional,
                ParameterList parameterList,// TODO Do I need this? Can't I get it from the functional
                Parameter currentParameter,// TODO Do I need this? Can't I get it from the functional
                java.util.List<TypeParameter> typeParameterList,// TODO Do I need this? Can't I get it from the functional
                DaoBody<D> daoBody) {
            MethodDefinitionBuilder overloadBuilder = makeOverloadBuilder(functional);
            try (SavedPosition pos = noPosition()) {
                
                // Make the declaration
                // need annotations for BC, but the method isn't really there
                overloadBuilder.ignoreModelAnnotations();
                overloadBuilder.modifiers(getModifiers(functional, daoBody));
                transformResultType(functional, overloadBuilder);
                transformTypeParameterList(functional, overloadBuilder);
                
                appendImplicitParameters(functional, typeParameterList, overloadBuilder);
                transformParameterList(functional, parameterList, currentParameter, overloadBuilder);
                
                // Make the body
                // TODO MPL
                // TODO Type args on method call
                
                daoBody.makeBody(functional, this, overloadBuilder,
                        parameterList,
                        currentParameter,
                        typeParameterList);
            }
            overloadBuilder.closeSubstitutions();
            return overloadBuilder;
        }
        
        protected abstract MethodDefinitionBuilder makeOverloadBuilder(D model);
        
        protected abstract long getModifiers(D model, DaoBody<D> daoBody);

        /** Returns the name of the method that the overload delegates to */
        protected abstract JCExpression makeMethodName(D model, DaoBody<D> daoBody);

        protected abstract void transformResultType(D model, MethodDefinitionBuilder overloadBuilder);

        protected abstract void transformTypeParameterList(D model, MethodDefinitionBuilder overloadBuilder);

        protected void transformParameterList(D functional, ParameterList parameterList, Parameter currentParameter, MethodDefinitionBuilder overloadBuilder) {
            for (Parameter parameter : parameterList.getParameters()) {
                if (currentParameter != null && parameter == currentParameter) {
                    break;
                }
                transformParameter(overloadBuilder, parameter);
            }
        }

        protected void transformParameter(
                MethodDefinitionBuilder overloadBuilder, Parameter parameter) {
            overloadBuilder.parameter(parameter, null, 0, false);
        }
        
        protected void appendImplicitParameters(D functional, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder) {
            if(typeParameterList != null){
                overloadBuilder.reifiedTypeParameters(typeParameterList);
            }
        }
        
        /** Called by the DaoBody: Add any implicit arguments to the given args */
        protected abstract void appendDpmImplicitArguments(D model, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args);
        protected abstract void appendCanonicalImplicitArguments(D model, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args);
        
        protected ProducedType parameterType(Parameter parameterModel) {
            ProducedType paramType = null;
            if (parameterModel.getModel() instanceof Method) {
                paramType = typeFact().getCallableType(parameterModel.getType());
            } else {
                paramType = parameterModel.getType();
            }
            return paramType;
        }
        
        protected abstract void initVars(D model, Parameter currentParameter, ListBuffer<JCStatement> vars);
        
        protected final boolean defaultParameterMethodOnSelf(D model) {
            return Strategy.defaultParameterMethodOnSelf(model);
        }
        
        protected final boolean defaultParameterMethodOnOuter(D model) {
            return Strategy.defaultParameterMethodOnOuter(model);
        }
        
        protected final boolean defaultParameterMethodStatic(D model) {
            return Strategy.defaultParameterMethodStatic(model);
        }
        
        protected JCExpression makeInvocation(D model, DaoBody<D> daoBody, ListBuffer<JCExpression> args) {
            final JCExpression methName = makeMethodName(model, daoBody);
            return make().Apply(List.<JCExpression>nil(),
                    methName, args.toList());
        }
        
        /** Returns the qualiifier to use when invoking the default parameter value method */
        protected abstract JCExpression makeDefaultArgumentValueMethodQualifier(D model, DaoBody<D> daoBody);
        
    }
    
    /**
     * A transformation for generating overloaded <em>methods</em> for 
     * defaulted arguments. 
     */
    abstract class DefaultedArgumentMethod extends DefaultedArgumentOverload<Method> {
        
        protected void appendImplicitParameters(Method functional, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder) {
            if(typeParameterList != null){
                overloadBuilder.reifiedTypeParameters(typeParameterList);
            }
        }
        
        @Override
        protected long getModifiers(Method method, DaoBody<Method> daoBody) {
            long mods = transformMethodDeclFlags(method);
            if (daoBody instanceof DaoAbstract == false) {
                mods &= ~ABSTRACT;
            }
            if (daoBody instanceof DaoCompanion) {
                mods |= FINAL;
            }
            return mods;
        }
        
        @Override
        protected JCExpression makeMethodName(Method method, DaoBody<Method> daoBody) {
            int flags = Naming.NA_MEMBER;
            if (Decl.withinClassOrInterface(method)
                    && method.isDefault()) {
                flags |= Naming.NA_CANONICAL_METHOD;
            }
            return naming.makeQualifiedName(daoBody.makeMethodNameQualifier(), method, flags);
        }
        
        @Override
        protected void transformResultType(Method method, MethodDefinitionBuilder overloadBuilder) {
            overloadBuilder.resultType(method, 0);
        }
        
        @Override
        protected void transformTypeParameterList(Method method, MethodDefinitionBuilder overloadBuilder) {
            copyTypeParameters(method, overloadBuilder);
        }
        
        @Override
        protected void appendDpmImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            if(typeParameterList != null){
                // we pass the reified type parameters along
                for(TypeParameter tp : typeParameterList){
                    args.append(makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
                }
            }
        }
        
        @Override
        protected void appendCanonicalImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            
            if(typeParameterList != null){
                // we pass the reified type parameters along
                for(TypeParameter tp : typeParameterList){
                    args.append(makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
                }
            }
        }
        
        @Override
        protected void initVars(Method method, Parameter currentParameter, ListBuffer<JCStatement> vars) {
        }
        
        @Override
        protected JCExpression makeDefaultArgumentValueMethodQualifier(Method method, DaoBody<Method> daoBody) {
            return null;
        }
        
        @Override
        protected MethodDefinitionBuilder makeOverloadBuilder(Method method) {
            MethodDefinitionBuilder overloadBuilder = MethodDefinitionBuilder.method(ClassTransformer.this, method);
            overloadBuilder.location(null);
            return overloadBuilder;
        }
    }
    
    /**
     * A transformation for generating overloaded <em>methods</em> for 
     * defaulted arguments. 
     */
    class MixinDefaultedArgumentMethod extends DefaultedArgumentMethod {
        private ProducedTypedReference typedMember;

        MixinDefaultedArgumentMethod(ProducedTypedReference typedMember) {
            this.typedMember = typedMember;
        }
        
        /*JCExpression makeDefaultedParamMethod(Method model, Parameter parameterModel, DaoBody<Method> body) {
            return naming.makeDefaultedParamMethod(null, parameterModel);
        }*/
        
        @Override
        JCExpression makeDefaultedParamMethod(Method method, Parameter parameterModel, DaoBody<Method> body) {
            return naming.makeUnquotedIdent(naming.getDefaultedParamMethodName(method, parameterModel));
        }
        
        /** Returns the name of the method that the overload delegates to */
        @Override
        protected JCExpression makeMethodName(Method method, DaoBody<Method> daoBody) {
            return naming.makeName(method, Naming.NA_MEMBER | Naming.NA_CANONICAL_METHOD);
        }
        
        @Override
        protected void appendCanonicalImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            //outerReifiedTypeArguments(ClassTransformer.this, method, args);
            //addCapturedReifiedTypeParametersInternal(method, method, typedMember, args, false);
            if(typeParameterList != null){
                // we pass the reified type parameters along
                for(TypeParameter tp : typeParameterList){
                    args.append(makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
                }
            }
        }

        @Override
        protected void appendImplicitParameters(Method functional, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder) {
            
            //capturedThisParameter(functional, overloadBuilder);
            //capturedLocalParameters(functional, overloadBuilder);
            //outerReifiedTypeParameters(functional, overloadBuilder);
            
            super.appendImplicitParameters(functional, typeParameterList, overloadBuilder);
        }
        @Override
        protected void appendDpmImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            //args.add(naming.makeQuotedThis());
            //addCapturedLocalArguments(args, method);
            super.appendDpmImplicitArguments(method, typeParameterList, overloadBuilder, args);
        }
        
        @Override
        protected void transformResultType(Method method, MethodDefinitionBuilder overloadBuilder) {
            if (!isAnything(method.getType())
                    || !Decl.isUnboxedVoid(method)
                    || Strategy.useBoxedVoid(method)) {
                ProducedTypedReference typedRef = typedMember;
                overloadBuilder.resultTypeNonWidening(typedMember.getQualifyingType(), typedRef, typedMember.getType(), 0);
            } else {
                super.transformResultType(method, overloadBuilder);
            }
        }
        
        @Override
        protected void transformParameter(
                MethodDefinitionBuilder overloadBuilder, Parameter param) {
            ProducedType type = paramType(param);
            overloadBuilder.parameter(param, type, FINAL, 0, true);
        }
        
        @Override
        protected ProducedType parameterType(Parameter parameter) {
            ProducedType paramType = paramType(parameter);
            if (parameter.getModel() instanceof Method) {
                paramType = typeFact().getCallableType(parameter.getType());
            }
            return paramType;
        }

        private ProducedType paramType(Parameter parameter) {
            final ProducedTypedReference typedParameter = typedMember.getTypedParameter(parameter);
            ProducedType paramType;
            // if the supertype method itself got erased to Object, we can't do better than this
            if (gen().willEraseToObject(parameter.getType()) && !gen().willEraseToBestBounds(parameter)) {
                paramType = typeFact().getObjectDeclaration().getType();
            } else {
                paramType = typedParameter.getType();
            }
            return paramType;
        }
        
        @Override
        protected MethodDefinitionBuilder makeOverloadBuilder(Method method) {
            return MethodDefinitionBuilder.method(ClassTransformer.this, method)
                .isOverride(true);
        }
    }
    
    /**
     * A transformation for generating the canonical <em>method</em> used by the
     * defaulted argument overload methods
     */
    class CanonicalMethod extends DefaultedArgumentMethod {
        private List<JCStatement> body;
        private boolean useBody;
        
        CanonicalMethod() {
            useBody = false;
        }
        
        CanonicalMethod(List<JCStatement> body) {
            this.body = body;
            useBody = true;
        }
        
        @Override
        protected void transformTypeParameterList(Method method, MethodDefinitionBuilder overloadBuilder) {
            if (method.isInterfaceMember()) {
                outerTypeParameters(method, overloadBuilder);
            }
            super.transformTypeParameterList(method, overloadBuilder);
        }
        
        @Override
        protected void appendCanonicalImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            if (method.isInterfaceMember()) {
                args.add(naming.makeQuotedThis());
                outerReifiedTypeArguments(ClassTransformer.this, method, args);
            }
            super.appendCanonicalImplicitArguments(method, typeParameterList, overloadBuilder, args);
        }
        
        @Override
        protected long getModifiers(Method method, DaoBody<Method> daoBody) {
            long mods = super.getModifiers(method, daoBody);
            if (useBody) {
                mods = mods & ~PUBLIC & ~FINAL | PRIVATE;
            }
            if (method.isInterfaceMember()) {
                mods |= STATIC;
            }
            return mods;
        }
        
        @Override
        protected void transformParameterList(Method method, 
                ParameterList parameterList, 
                Parameter currentParameter, 
                MethodDefinitionBuilder overloadBuilder) {
            if (method.isInterfaceMember()) {
                capturedThisParameter(method, overloadBuilder);
                capturedLocalParameters(method, overloadBuilder);
                outerReifiedTypeParameters(method, overloadBuilder);
            }
            super.transformParameterList(method, parameterList, currentParameter, overloadBuilder);
        }
        
        /**
         * Generates a canonical method
         */
         @Override
        public MethodDefinitionBuilder makeOverload (
                Method method,
                ParameterList parameterList,
                Parameter currentParameter,
                java.util.List<TypeParameter> typeParameterList,
                DaoBody<Method> daoBody) {
            MethodDefinitionBuilder canonicalBuilder = makeOverloadBuilder(method);
            if (!useBody) {
                daoBody.makeBody(method, this, canonicalBuilder,
                        parameterList,
                        currentParameter,
                        typeParameterList);
            } else {
                // Make the declaration
                // need annotations for BC, but the method isn't really there
                canonicalBuilder.ignoreModelAnnotations();
                canonicalBuilder.modifiers(getModifiers(method, daoBody));
                transformResultType(method, canonicalBuilder);
                transformTypeParameterList(method, canonicalBuilder);

                appendImplicitParameters(method, typeParameterList, canonicalBuilder);
                transformParameterList(method, parameterList, currentParameter, canonicalBuilder);
                
                if (body != null) {
                    // Construct the outermost method using the body we've built so far
                    canonicalBuilder.body(body);
                } else {
                    canonicalBuilder.noBody();
                }
            }
            
            return canonicalBuilder;
        }

        @Override
        protected MethodDefinitionBuilder makeOverloadBuilder(Method method) {
            return MethodDefinitionBuilder.method(ClassTransformer.this, method, Naming.NA_CANONICAL_METHOD);
        }
    }
    
    /**
     * A base class for transformations that generate overloaded declarations for 
     * defaulted arguments. 
     */
    abstract class DefaultedArgumentClass extends DefaultedArgumentOverload<Class> {
        
        protected Naming.SyntheticName companionInstanceName = null;
        
        @Override
        protected void initVars(Class klass, Parameter currentParameter, ListBuffer<JCStatement> vars) {
            if (!Strategy.defaultParameterMethodStatic(klass)
                    && !Strategy.defaultParameterMethodOnOuter(klass)
                    && currentParameter != null) {
                companionInstanceName = naming.temp("impl");
                vars.append(makeVar(companionInstanceName, 
                        makeJavaType(klass.getType(), AbstractTransformer.JT_COMPANION),
                        make().NewClass(null, 
                                null,
                                makeJavaType(klass.getType(), AbstractTransformer.JT_CLASS_NEW | AbstractTransformer.JT_COMPANION),
                                List.<JCExpression>nil(), null)));
            }
        }
        
        @Override
        protected JCIdent makeDefaultArgumentValueMethodQualifier(Class klass, DaoBody<Class> daoBody) {
            if (defaultParameterMethodOnSelf(klass) 
                    || defaultParameterMethodOnOuter(klass)
                    || daoBody instanceof DaoCompanion) {
                return null;
            } else if (defaultParameterMethodStatic(klass)){
                return null;
            } else {
                return companionInstanceName.makeIdent();
            }
        }
    }
    
    /**
     * A transformation for generating overloaded <em>constructors</em> for 
     * defaulted arguments. 
     */
    class DefaultedArgumentConstructor extends DefaultedArgumentClass {

        private final ClassDefinitionBuilder classBuilder;

        DefaultedArgumentConstructor(ClassDefinitionBuilder classBuilder) {
            this.classBuilder = classBuilder;
        }

        protected final void appendImplicitParameters(Class functional, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder) {
            capturedLocalParameters(functional, overloadBuilder);
            outerReifiedTypeParameters(functional, overloadBuilder);
            super.appendImplicitParameters(functional, typeParameterList, overloadBuilder);
        }
        
        @Override
        protected long getModifiers(Class klass, DaoBody<Class> daoBody) {
            return transformClassDeclFlags(klass) & (PUBLIC | PRIVATE | PROTECTED);
        }

        @Override
        protected JCExpression makeMethodName(Class klass, DaoBody<Class> daoBody) {
            return naming.makeQualifiedThis(daoBody.makeMethodNameQualifier());
        }

        @Override
        protected void transformResultType(Class klass, MethodDefinitionBuilder overloadBuilder) {
            // Constructor has no result type
        }

        @Override
        protected void transformTypeParameterList(Class klass, MethodDefinitionBuilder overloadBuilder) {
            // Constructor has type parameters
        }
        
       
        
        @Override
        protected void appendCanonicalImplicitArguments(Class klass, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            addCapturedLocalArguments(args, klass);
            addCapturedReifiedTypeParameters(args, klass);
            if(typeParameterList != null){
                // we pass the reified type parameters along
                for(TypeParameter tp : typeParameterList){
                    args.append(makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
                }
            }
        }
        
        @Override
        protected void appendDpmImplicitArguments(Class klass, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            addCapturedLocalArguments(args, klass);
            addCapturedReifiedTypeParameters(args, klass);
            if(typeParameterList != null){
                // we pass the reified type parameters along
                for(TypeParameter tp : typeParameterList){
                    args.append(makeUnquotedIdent(naming.getTypeArgumentDescriptorName(tp.getName())));
                }
            }
        }
        
        @Override
        protected MethodDefinitionBuilder makeOverloadBuilder(Class klass) {
            return classBuilder.addConstructor();
        }
    }
    
    protected void addCapturedLocalArguments(
            ListBuffer<JCExpression> result, 
            Declaration primaryDeclaration){
        // Add implementation argument for a deferred local function
        if (primaryDeclaration instanceof Method
                && ((Method)primaryDeclaration).isDeferred()
                // If the deferred local function is in a class initializer we don't need the implementation argument
                && (!(primaryDeclaration.getContainer() instanceof Class) || !primaryDeclaration.isCaptured())) {
            result.append(
                    naming.makeUnquotedIdent(naming.selector((Method)primaryDeclaration, Naming.NA_MEMBER))
                    );
        }
        if (primaryDeclaration.isClassMember()) { 
                ///&& Decl.isLocal((Class)primaryDeclaration.getScope())) {
            // The class constructor captures, and the member accesses fields.
            return;
        }
        
        java.util.List<Declaration> cl = Decl.getCapturedLocals(primaryDeclaration);
        if (cl != null) {
            for (Declaration decl : cl) {
                if (decl instanceof Method 
                        && Strategy.useStaticForFunction((Method)decl)
                        && !decl.isParameter()) {
                    continue;
                }
                if ((decl instanceof MethodOrValue)
                        && Decl.isLocal(decl)
                        && ((MethodOrValue)decl).isTransient()
                        && !((MethodOrValue)decl).isParameter()
                        && !((MethodOrValue)decl).isDeferred()) {
                    continue;
                }
                /*result.append(new ExpressionAndType(
                        gen.naming.makeUnquotedIdent(gen.naming.substitute(decl)),
                        gen.makeJavaType(((TypedDeclaration)decl).getType())));*/
                if (Decl.isGetter(decl)
                        && Decl.isLocal(decl)){
                    result.append(
                            ((Value)decl).isTransient() ? naming.makeQuotedIdent(naming.getAttrClassName((Value)decl, 0)) : naming.makeName((TypedDeclaration)decl, Naming.NA_Q_LOCAL_INSTANCE)
                            );
                } else if (decl instanceof Setter
                        && Decl.isLocal(decl)){
                    result.append(
                            naming.makeQuotedIdent(naming.getAttrClassName((Setter)decl, 0))
                            );
                } else if (decl instanceof TypedDeclaration) {
                    result.append(
                            naming.makeName((TypedDeclaration)decl, Naming.NA_IDENT)
                            );
                } else if (decl instanceof TypeDeclaration) {
                    result.append(naming.makeQualifiedThis(makeJavaType(((TypeDeclaration)decl).getType(), JT_RAW)));
                } else {
                    Assert.fail();
                }
            }
        }
        //
    }
    
    protected void addCapturedReifiedTypeParameters(
            ListBuffer<JCExpression> result, 
            Declaration primaryDeclaration){
        if (primaryDeclaration.isClassMember()) { 
            ///&& Decl.isLocal((Class)primaryDeclaration.getScope())) {
            // The class constructor captures, and the member accesses fields.
            return;
        }
        addCapturedReifiedTypeParametersInternal(primaryDeclaration, primaryDeclaration, null, result, false);
    }
    
    void addCapturedReifiedTypeParametersInternal(Declaration origin, Declaration declaration, 
            ProducedReference typedOrigin,
            ListBuffer result, boolean expressionAndType) {
        Declaration container = Decl.getDeclarationContainer(declaration);
        if (container != null) {
            if (Decl.isLocal(container) || origin.isInterfaceMember()) {
                addCapturedReifiedTypeParametersInternal(origin, container, typedOrigin, result, expressionAndType);
            }
            if (origin.isInterfaceMember() ||
                    (container instanceof Generic
                    && !(container instanceof Class))) {
                for (TypeParameter tp : ((Generic)container).getTypeParameters()) {
                    ProducedType reifiedType;
                    if (typedOrigin != null && typedOrigin.getTypeArguments().get(tp) != null) {
                        reifiedType = typedOrigin.getTypeArguments().get(tp);
                    } else {
                        reifiedType = tp.getType();
                    }
                    JCExpression reifiedTypeArg = makeReifiedTypeArgument(reifiedType);
                    if (expressionAndType) {
                        result.append(new ExpressionAndType(reifiedTypeArg, makeTypeDescriptorType()));
                    } else {
                        result.append(reifiedTypeArg);
                    }
                }
            }
        }
    }
    
    /**
     * A transformation for generating overloaded <em>instantiator methods</em> for 
     * defaulted arguments. 
     */
    class DefaultedArgumentInstantiator extends DefaultedArgumentClass {
        
        @Override
        protected long getModifiers(Class klass, DaoBody<Class> daoBody) {
            // remove the FINAL bit in case it gets set, because that is valid for a class decl, but
            // not for a method if in an interface
            return transformClassDeclFlags(klass) & ~FINAL;
        }
        
        @Override
        protected JCExpression makeMethodName(Class klass, DaoBody<Class> daoBody) {
            return naming.makeInstantiatorMethodName(daoBody.makeMethodNameQualifier(), klass);
        }
        
        @Override
        protected void transformResultType(Class klass, MethodDefinitionBuilder overloadBuilder) {
            /* Not actually part of the return type */
            overloadBuilder.ignoreModelAnnotations();
            if (!klass.isAlias() 
                    && Strategy.generateInstantiator(klass.getExtendedTypeDeclaration())
                    && klass.isActual()
                    && klass.getExtendedTypeDeclaration().getContainer() instanceof Class) {
                overloadBuilder.isOverride(true);
            }
            /**/
            
            JCExpression resultType;
            ProducedType type = klass.isAlias() ? klass.getExtendedType() : klass.getType();
            if (Strategy.isInstantiatorUntyped(klass)) {
                // We can't expose a local type name to a place it's not visible
                resultType = make().Type(syms().objectType);
            } else {
                resultType = makeJavaType(type);
            }
            overloadBuilder.resultType(null, resultType);
        }

        @Override
        protected void transformTypeParameterList(Class klass, MethodDefinitionBuilder overloadBuilder) {
            for (TypeParameter tp : typeParametersForInstantiator(klass)) {
                overloadBuilder.typeParameter(tp);
            }
        }
        
        @Override
        protected void appendCanonicalImplicitArguments(Class klass, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            ProducedType type = klass.isAlias() ? klass.getExtendedType() : klass.getType();
            type = type.resolveAliases();
            // fetch the type parameters from the klass we're instantiating itself if any
            for(ProducedType pt : type.getTypeArgumentList()){
                args.append(makeReifiedTypeArgument(pt));
            }
        }
        
        @Override
        protected void appendDpmImplicitArguments(Class klass, java.util.List<TypeParameter> typeParameterList,
                MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
            ProducedType type = klass.isAlias() ? klass.getExtendedType() : klass.getType();
            type = type.resolveAliases();
            // fetch the type parameters from the klass we're instantiating itself if any
            for(ProducedType pt : type.getTypeArgumentList()){
                args.append(makeReifiedTypeArgument(pt));
            }
        }

        @Override
        protected JCExpression makeInvocation(Class klass, DaoBody<Class> daoBody, ListBuffer<JCExpression> args) {
            ProducedType type = klass.isAlias() ? klass.getExtendedType() : klass.getType();
            return make().NewClass(null, 
                    null, 
                    makeJavaType(type, JT_CLASS_NEW),
                    args.toList(),
                    null);
        }

        @Override
        protected MethodDefinitionBuilder makeOverloadBuilder(Class klass) {
            return MethodDefinitionBuilder.systemMethod(ClassTransformer.this, naming.getInstantiatorMethodName(klass));
        }
    }

    /**
     * When generating an instantiator method if the inner class has a type 
     * parameter with the same name as a type parameter of an outer type, then the 
     * instantiator method shouldn't declare its own type parameter of that 
     * name -- it should use the captured one. This method filters out the
     * type parameters of the inner class which are the same as type parameters 
     * of the outer class so that they can be captured.
     */
    private java.util.List<TypeParameter> typeParametersForInstantiator(final Class model) {
        Assert.that(Strategy.generateInstantiator(model));
        java.util.List<TypeParameter> filtered = new ArrayList<TypeParameter>();
        java.util.List<TypeParameter> tps = model.getTypeParameters();
        if (tps != null) {
            for (TypeParameter tp : tps) {
                boolean omit = false;
                Scope s = model.getContainer();
                while (!(s instanceof Package)) {
                    if (s instanceof Generic) {
                        for (TypeParameter outerTp : ((Generic)s).getTypeParameters()) {
                            if (tp.getName().equals(outerTp.getName())) {
                                omit = true;
                            }
                        }
                    }
                    s = s.getContainer();
                }
                if (!omit) {
                    filtered.add(tp);
                }
            }
        }
        return filtered;
    }

    private java.util.List<TypeParameter> typeParametersOfAllContainers(final ClassOrInterface model, boolean includeModelTypeParameters) {
        java.util.List<java.util.List<TypeParameter>> r = new ArrayList<java.util.List<TypeParameter>>(1);
        Scope s = model.getContainer();
        while (!(s instanceof Package)) {
            if (s instanceof Generic) {
                r.add(0, ((Generic)s).getTypeParameters());
            }
            s = s.getContainer();
        }
        Set<String> names = new HashSet<String>();
        for(TypeParameter tp : model.getTypeParameters()){
            names.add(tp.getName());
        }
        java.util.List<TypeParameter> result = new ArrayList<TypeParameter>(1);
        for (java.util.List<TypeParameter> tps : r) {
            for(TypeParameter tp : tps){
                if(names.add(tp.getName())){
                    result.add(tp);
                }
            }
        }
        if(includeModelTypeParameters)
            result.addAll(model.getTypeParameters());
        return result;
    }
    
    abstract class DefaultParameterValueMethodTransformation<T extends Declaration> {
        public MethodDefinitionBuilder transform(T functional, ParameterList parameterList, Tree.Parameter parameter) {
            Parameter parameterModel = parameter.getParameterModel();
            MethodDefinitionBuilder methodBuilder = makeMethodBuilder(functional, parameterModel);
            transformModifiers(functional, parameterModel, methodBuilder);
            transformAnnotations(functional, parameterModel, methodBuilder);
            transformResultType(functional, parameterModel, methodBuilder);
            transformTypeParameterList(functional, methodBuilder);
            transformParameterList(functional, parameterList, parameterModel, methodBuilder);
            transformBody(functional, parameter, methodBuilder);
            return methodBuilder;
        }
        
        protected MethodDefinitionBuilder makeMethodBuilder(T functional, Parameter parameter) {
            return MethodDefinitionBuilder.systemMethod(ClassTransformer.this, naming.getDefaultedParamMethodName(functional, parameter));
        }
        
        protected void transformModifiers(T functional,
                Parameter parameter, MethodDefinitionBuilder methodBuilder) {
            methodBuilder.modifiers(getModifiers(functional));
        }
        
        protected int getModifiers(T functional) {
            int modifiers = 0;
            if (functional.isShared()) {
                modifiers |= PUBLIC;
            } else if (!functional.isToplevel()){
                modifiers |= PRIVATE;
            }
            if (Strategy.defaultParameterMethodStatic(functional)) {
                // static default parameter methods should be consistently public so that if non-shared class Top and
                // shared class Bottom which extends Top both have the same default param name, we don't get an error
                // if the Bottom class tries to "hide" a static public method with a private one
                modifiers &= ~ PRIVATE;
                modifiers |= STATIC | PUBLIC;
            }
            return modifiers;
        }

        protected void transformAnnotations(T functional,
                Parameter parameter, MethodDefinitionBuilder methodBuilder) {
            methodBuilder.ignoreModelAnnotations();
        }
        
        protected void transformResultType(T functional,
                Parameter parameter, MethodDefinitionBuilder methodBuilder) {
            methodBuilder.resultType(parameter.getModel(), parameter.getType(), 0);
        }
        
        protected abstract void transformTypeParameterList(T functional,
                MethodDefinitionBuilder methodBuilder);
        
        protected abstract void transformParameterList(T functional,
                ParameterList parameterList, Parameter parameter, MethodDefinitionBuilder methodBuilder);

        protected void transformBody(T functional,
                Tree.Parameter parameter, MethodDefinitionBuilder methodBuilder) {
            at(parameter);
            JCExpression expr = expressionGen().transform(parameter);
            at(parameter);
            JCBlock body = make().Block(0, List.<JCStatement> of(make().Return(expr)));
            methodBuilder.block(body);
        }
    }
    class MethodDpvmTransformation extends DefaultParameterValueMethodTransformation<Method> {
        @Override
        protected void transformAnnotations(Method method, Parameter parameter,
                    MethodDefinitionBuilder methodBuilder) {
            super.transformAnnotations(method, parameter, methodBuilder);
            if (Decl.isAnnotationConstructor(method)) {
                AnnotationInvocation ac = (AnnotationInvocation)method.getAnnotationConstructor();
                for (AnnotationConstructorParameter acp : ac.getConstructorParameters()) {
                    if (acp.getParameter().equals(parameter)
                            && acp.getDefaultArgument() != null) {
                        methodBuilder.userAnnotations(acp.getDefaultArgument().makeDpmAnnotations(expressionGen()));
                    }
                }
            }
        }
        @Override
        protected void transformTypeParameterList(Method method,
                MethodDefinitionBuilder methodBuilder) {
            copyTypeParameters(method, methodBuilder);
        }
        @Override
        protected void transformParameterList(Method method,
                ParameterList parameterList, Parameter parameter,
                MethodDefinitionBuilder methodBuilder)  {
            // make sure reified type parameters are accepted
            if(method.getTypeParameters() != null)
                methodBuilder.reifiedTypeParameters(method.getTypeParameters());
            
            // Add any of the preceding parameters as parameters to the method
            for (Parameter p : parameterList.getParameters()) {
                if (p == parameter) {
                    break;
                }
                methodBuilder.parameter(p, null, 0, false);
            }
        }
        @Override
        protected int getModifiers(Method method) {
            int result = FINAL | super.getModifiers(method);
            return result;
        }
    }
    class CompanionMethodDpvmTransformation extends MethodDpvmTransformation {
        
        @Override
        protected void transformTypeParameterList(Method method,
                MethodDefinitionBuilder methodBuilder) {
            outerTypeParameters(method, methodBuilder);
            super.transformTypeParameterList(method, methodBuilder);
        }
        
        @Override
        protected void transformParameterList(Method method,
                ParameterList parameterList, Parameter parameter,
                MethodDefinitionBuilder methodBuilder)  {
            capturedThisParameter(method, methodBuilder);
            capturedLocalParameters(method, methodBuilder);
            outerReifiedTypeParameters(method, methodBuilder);
            super.transformParameterList(method, parameterList, parameter, methodBuilder);
        }
        
        @Override
        protected int getModifiers(Method method) {
            int mods = STATIC | (transformClassDeclFlags((Interface)method.getContainer()) & ~INTERFACE);
            if (!method.isShared()) {
                mods |= PRIVATE;
            }
            return mods;
        }
    }
    
    
    MethodDpvmTransformation methodDpvmTransformation = new MethodDpvmTransformation();
    MethodDpvmTransformation abstractMethodDpvmTransformation = new MethodDpvmTransformation() {
        @Override
        protected void transformBody(Method method,
                    Tree.Parameter parameter, MethodDefinitionBuilder methodBuilder) {
            methodBuilder.noBody();
        }
        @Override
        protected int getModifiers(Method method) {
            int modifiers = ABSTRACT | (super.getModifiers(method) & ~FINAL);
            if (method.isInterfaceMember()) {
                modifiers = modifiers & ~STATIC;
            }
            return modifiers;
        }
    };
    
    class ClassDpvmTransformation extends DefaultParameterValueMethodTransformation<Class> {
        @Override
        protected void transformTypeParameterList(Class cls,
                    MethodDefinitionBuilder methodBuilder) {
            if (Decl.isToplevel(cls)) {
                copyTypeParameters(cls, methodBuilder);
            }
        }
        
        @Override
        protected void transformParameterList(Class cls,
                ParameterList parameterList, Parameter parameter,
                MethodDefinitionBuilder methodBuilder)  {
            // XXX less than ideally we copy all the stuff captured by the class, rather than just the stuff captured by the defaulted expression
            capturedLocalParameters(cls, methodBuilder);
            outerReifiedTypeParameters(cls, methodBuilder);
            // make sure reified type parameters are accepted
            if(cls.getTypeParameters() != null)
                methodBuilder.reifiedTypeParameters(cls.getTypeParameters());
            
            // Add any of the preceding parameters as parameters to the method
            for (Parameter p : parameterList.getParameters()) {
                if (p == parameter) {
                    break;
                }
                methodBuilder.parameter(p, null, 0, true);
            }
        }
        @Override
        protected int getModifiers(Class cls) {
            int mods =  super.getModifiers(cls);
            if (!Strategy.defaultParameterMethodStatic(cls)) {
                mods |= FINAL;
            }
            return mods;
        }
    }
    ClassDpvmTransformation classDpvmTransformation = new ClassDpvmTransformation();
    ClassDpvmTransformation localClassDpvmTransformation = new ClassDpvmTransformation();
    ClassDpvmTransformation abstractClassDpvmTransformation = new ClassDpvmTransformation(){
        @Override
        protected int getModifiers(Class functional) {
            return PUBLIC | ABSTRACT;
        }
    };
    class CallableDpvmTransformation<Nowt extends Declaration> extends DefaultParameterValueMethodTransformation<Nowt> {
        @Override
        protected void transformTypeParameterList(Nowt functional,
                    MethodDefinitionBuilder methodBuilder) {
            // There are no type parameters
        }
        
        @Override
        protected void transformParameterList(Nowt functional,
                ParameterList parameterList, Parameter parameter,
                MethodDefinitionBuilder methodBuilder)  {
            // Add any of the preceding parameters as parameters to the method
            for (Parameter p : parameterList.getParameters()) {
                if (p == parameter) {
                    break;
                }
                methodBuilder.parameter(p, null, 0, false);
            }
        }
        @Override
        protected int getModifiers(Nowt functional) {
            return PRIVATE | FINAL;
        }
    }
    CallableDpvmTransformation callableDpvmTransformation = new CallableDpvmTransformation();
    
    public List<JCTree> transformObjectDefinition(Tree.ObjectDefinition def, ClassDefinitionBuilder containingClassBuilder) {
        return transformObject(def, def.getDeclarationModel(), 
                def.getAnonymousClass(), containingClassBuilder, Decl.isLocalNotInitializer(def));
    }
    
    public List<JCTree> transformObjectArgument(Tree.ObjectArgument def) {
        return transformObject(def, def.getDeclarationModel(), 
                def.getAnonymousClass(), null, false);
    }
    
    private List<JCTree> transformObject(Tree.StatementOrArgument def, Value model, 
            Class klass,
            ClassDefinitionBuilder containingClassBuilder,
            boolean makeLocalInstance) {
        naming.clearSubstitutions(model);
        
        String name = model.getName();
        ClassDefinitionBuilder objectClassBuilder = ClassDefinitionBuilder.object(
                this, name);
        
        CeylonVisitor visitor = gen().visitor;
        final ListBuffer<JCTree> prevDefs = visitor.defs;
        final boolean prevInInitializer = visitor.inInitializer;
        final ClassDefinitionBuilder prevClassBuilder = visitor.classBuilder;
        List<JCStatement> childDefs;
        try {
            visitor.defs = new ListBuffer<JCTree>();
            visitor.inInitializer = true;
            visitor.classBuilder = objectClassBuilder;
            
            def.visitChildren(visitor);
            childDefs = (List<JCStatement>)visitor.getResult().toList();
        } finally {
            visitor.classBuilder = prevClassBuilder;
            visitor.inInitializer = prevInInitializer;
            visitor.defs = prevDefs;
        }

        satisfaction(klass, objectClassBuilder);
        
        TypeDeclaration decl = model.getType().getDeclaration();

        if (Decl.isToplevel(model)
                && def instanceof Tree.ObjectDefinition) {
            // generate a field and getter
            AttributeDefinitionBuilder builder = AttributeDefinitionBuilder
                    // TODO attr build take a JCExpression className
                    .wrapped(this, null, model.getName(), model, true)
                    .userAnnotations(makeAtIgnore())
                    .userAnnotationsSetter(makeAtIgnore())
                    .immutable()
                    .initialValue(makeNewClass(naming.makeName(model, Naming.NA_FQ | Naming.NA_WRAPPER)))
                    .is(PUBLIC, Decl.isShared(decl))
                    .is(STATIC, true);
            if (def instanceof Tree.ObjectDefinition) {
                builder.userAnnotations(expressionGen().transform(((Tree.ObjectDefinition) def).getAnnotationList()));
            }            
            objectClassBuilder.defs(builder.build());
        }

        // Make sure top types satisfy reified type
        addReifiedTypeInterface(objectClassBuilder, klass);

        List<JCTree> result = objectClassBuilder
            .annotations(makeAtObject())
            .modelAnnotations(model.getAnnotations())
            .modifiers(transformObjectDeclFlags(model))
            .constructorModifiers(PRIVATE)
            .satisfies(decl.getSatisfiedTypes())
            .init(childDefs)
            .addGetTypeMethod(model.getType())
            .build();
        
        if (makeLocalInstance) {
            if(model.isSelfCaptured()){
                // if it's captured we need to box it and define the var before the class, so it can access it
                JCNewClass newInstance = makeNewClass(objectClassBuilder.getClassName(), false, null);
                JCFieldAccess setter = naming.makeSelect(name, Naming.getSetterName(model));
                JCStatement assign = make().Exec(make().Assign(setter, newInstance));
                result = result.prepend(assign);

                JCVariableDecl localDecl = makeVariableBoxDecl(null, model);
                result = result.prepend(localDecl);
            }else{
                // not captured, we can define the var after the class
                JCVariableDecl localDecl = makeLocalIdentityInstance(name, objectClassBuilder.getClassName(), false);
                result = result.append(localDecl);
            }
            
        } else if (Decl.withinClassOrInterface(model)) {
            boolean visible = Decl.isCaptured(model);
            int modifiers = FINAL | ((visible) ? PRIVATE : 0);
            JCExpression type = makeJavaType(klass.getType());
            JCExpression initialValue = makeNewClass(makeJavaType(klass.getType()), null);
            containingClassBuilder.field(modifiers, name, type, initialValue, !visible);
            
            if (visible) {
                AttributeDefinitionBuilder getter = AttributeDefinitionBuilder
                .getter(this, name, model)
                .modifiers(transformAttributeGetSetDeclFlags(model, false));
                if (def instanceof Tree.ObjectDefinition) {
                    getter.userAnnotations(expressionGen().transform(((Tree.ObjectDefinition)def).getAnnotationList()));
                }
                
                result = result.appendList(getter.build());
            }
        }
        
        return result;
    }
    
    /**
     * Makes a {@code main()} method which calls the given top-level method
     * @param def
     */
    MethodDefinitionBuilder makeMainForClass(ClassOrInterface model) {
        at(null);
        if(model.isAlias())
            model = model.getExtendedTypeDeclaration();
        JCExpression nameId = makeJavaType(model.getType(), JT_RAW);
        List<JCExpression> arguments = makeBottomReifiedTypeParameters(model.getTypeParameters());
        JCNewClass expr = make().NewClass(null, null, nameId, arguments, null);
        return makeMainMethod(model, expr);
    }
    
    /**
     * Makes a {@code main()} method which calls the given top-level method
     * @param method
     */
    private MethodDefinitionBuilder makeMainForFunction(Method method) {
        at(null);
        JCExpression qualifiedName = naming.makeName(method, Naming.NA_FQ | Naming.NA_WRAPPER | Naming.NA_MEMBER);
        List<JCExpression> arguments = makeBottomReifiedTypeParameters(method.getTypeParameters());
        MethodDefinitionBuilder mainMethod = makeMainMethod(method, make().Apply(null, qualifiedName, arguments));
        return mainMethod;
    }
    
    private List<JCExpression> makeBottomReifiedTypeParameters(
            java.util.List<TypeParameter> typeParameters) {
        List<JCExpression> arguments = List.nil();
        for(int i=typeParameters.size()-1;i>=0;i--){
            arguments = arguments.prepend(gen().makeNothingTypeDescriptor());
        }
        return arguments;
    }

    /** 
     * Makes a {@code main()} method which calls the given callee 
     * (a no-args method or class)
     * @param decl
     * @param callee
     */
    private MethodDefinitionBuilder makeMainMethod(Declaration decl, JCExpression callee) {
        // Add a main() method
        MethodDefinitionBuilder methbuilder = MethodDefinitionBuilder
                .main(this)
                .ignoreModelAnnotations();
        // Add call to process.setupArguments
        JCExpression argsId = makeUnquotedIdent("args");
        JCMethodInvocation processExpr = make().Apply(null, naming.makeLanguageValue("process"), List.<JCTree.JCExpression>nil());
        methbuilder.body(make().Exec(make().Apply(null, makeSelect(processExpr, "setupArguments"), List.<JCTree.JCExpression>of(argsId))));
        // Add call to toplevel method
        methbuilder.body(make().Exec(callee));
        return methbuilder;
    }
    
    static <B extends ParameterizedBuilder<B>> void copyTypeParameters(Generic def, B methodBuilder) {
        if (def.getTypeParameters() != null) {
            for (TypeParameter t : def.getTypeParameters()) {
                methodBuilder.typeParameter(t);
            }
        }
    }

    public List<JCTree> transform(final Tree.TypeAliasDeclaration def) {
        final TypeAlias model = def.getDeclarationModel();
        
        // we only create types for aliases so they can be imported with the model loader
        // and since we can't import local declarations let's just not create those types
        // in that case
        if(Decl.isAncestorLocal(def))
            return List.nil();
        
        naming.clearSubstitutions(model);
        String ceylonClassName = def.getIdentifier().getText();
        final String javaClassName = Naming.quoteClassName(def.getIdentifier().getText());

        ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder
                .klass(this, javaClassName, ceylonClassName);

        // class alias
        classBuilder.constructorModifiers(PRIVATE);
        classBuilder.annotations(makeAtTypeAlias(model.getExtendedType()));
        classBuilder.annotations(expressionGen().transform(def.getAnnotationList()));
        classBuilder.isAlias(true);

        if (def.getTypeParameterList() != null) {
            for (Tree.TypeParameterDeclaration param : def.getTypeParameterList().getTypeParameterDeclarations()) {
                TypeDeclaration container = (TypeDeclaration)param.getDeclarationModel().getContainer();
                classBuilder.typeParameter(param);
                // TODO We shouldn't be dicking with companion builders here
                classBuilder.getCompanionBuilder(container).typeParameter(param);
            }
        }
        
        // make sure we set the container in case we move it out
        addAtContainer(classBuilder, model);

        visitClassOrInterfaceDefinition(def, classBuilder);

        return classBuilder
            .modelAnnotations(model.getAnnotations())
            .modifiers(transformTypeAliasDeclFlags(model))
            .satisfies(model.getSatisfiedTypes())
            .build();
    }
    
    /////////////////////////////////////////////////////////////////////
    
    
    /** 
     * <p>Baseclass for transformations of things with a {@link Functional}
     * model (i.e. classes, functions and methods).</p>
     * 
     * <p>This class uses the "template method" pattern to generate a 
     * family of methods or constructors when transforming a given functional:</p>
     * <dl>
     * <dt>ultimate</dt>
     * <dd>The "ultimate" method or constructor is the one whose signature 
     *     corresponds to the full parameter list of the Ceylon method, 
     *     ignoring defaulted parameters.</dd>
     * <dt></dt>
     * <dd>peripheral</dd> 
     * <dd>"Peripheral" methods and constructors include overloads of the 
     *     ultimate method or constructor as well as helper methods needed by
     *     the ultimate and/or other peripherals</dd>
     * </dl>
     */
    abstract class FunctionalTransformation<T extends Tree.Declaration> {
        public List<JCTree> transform(T functional) {
            ListBuffer<JCTree> lb = ListBuffer.<JCTree>lb();
            transformPeripheral(functional, lb);
            transformUltimate(functional, lb);
            return lb.toList();
        }
        
        /** Gets the (multiple) parameter lists of this functional */
        protected abstract java.util.List<Tree.ParameterList> getParameterLists(T functional);
        
        protected abstract Tree.ParameterList getFirstParameterList(T functional);
        
        /** 
         * Transforms the given declaration to its ultimate <em>result</em>.
         * For example, a single Ceylon method with defaulted parameters
         * has as its ultimate result the Java method with a 
         * complete parameter list and the implementation code.
         * @param hasOverloads 
         */
        protected abstract void transformUltimate(T functional, ListBuffer<JCTree> lb);
        
        /** 
         * Transforms the given declaration to 0 or more <em>peripheral results</em>.
         * For example a single Ceylon method with defaulted parameters
         * has two peripheral methods for each defaulted parameter:
         * <ol>
         * <li>a method to evaluate the default parameter value, and</li>
         * <li>an overload of the ultimate method, which invokes the 
         *     default parameter value method and invokes the ultimate method</li>
         * </ol>
         * 
         * @see #transformDefaultedParameterValueMethod(com.redhat.ceylon.compiler.typechecker.tree.Tree.Declaration, com.redhat.ceylon.compiler.typechecker.tree.Tree.ParameterList, com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter, ListBuffer)
         * @see #transformOverloadMethod(com.redhat.ceylon.compiler.typechecker.tree.Tree.Declaration, com.redhat.ceylon.compiler.typechecker.tree.Tree.ParameterList, com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter, ListBuffer)
         */
        protected void transformPeripheral(T functional, ListBuffer<JCTree> lb) {
            Declaration functionalDeclaration = functional.getDeclarationModel();
            Declaration refinedDeclaration = functionalDeclaration.getRefinedDeclaration();
            
            Tree.ParameterList parameterList = getFirstParameterList(functional);
            for (final Tree.Parameter parameter : parameterList.getParameters()) {
                Parameter parameterModel = parameter.getParameterModel();
                if (Strategy.hasDefaultParameterValueMethod(parameterModel)
                        || Strategy.hasDefaultParameterOverload(parameterModel)) {
                    if (refinedDeclaration == functionalDeclaration
                            || (!Decl.withinInterface(functional))) {
                        transformOverloadMethod(functional, parameterList, parameter, lb);
                        if (refinedDeclaration == functionalDeclaration
                                && Strategy.hasDefaultParameterValueMethod(parameterModel)) {
                            transformDefaultedParameterValueMethod(functional, parameterList, parameter, lb);
                        }
                    }
                }
            }
        }
        
        /**
         * Generates a method or constructor which overloads the ultimate 
         * method or constructor. Subclasses usually implement this by 
         * generating a method or constructor which invokes a 
         * defaulted parameter value method and delegates to the ultimate 
         * method.
         */
        protected abstract void transformOverloadMethod(
                T functional,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb);
        
        /**
         * Generates a method which encodes the expression of a 
         * defaulted parameter in this functional's parameter list.
         */
        protected abstract void transformDefaultedParameterValueMethod(
                T functional,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb);
    }
    
    /** 
     * <p>Abstract baseclass for transformations of things with a {@link Method} model, 
     * that is methods and functions.</p>
     */
    abstract class MethodOrFunctionTransformation extends FunctionalTransformation<Tree.AnyMethod> {
        @Override
        protected java.util.List<Tree.ParameterList> getParameterLists(Tree.AnyMethod methodOrFunction) {
            return methodOrFunction.getParameterLists();
        }
        
        @Override
        protected Tree.ParameterList getFirstParameterList(Tree.AnyMethod methodOrFunction) {
            return methodOrFunction.getParameterLists().get(0);
        }
        
        @Override
        protected void transformUltimate(Tree.AnyMethod methodOrFunction, ListBuffer<JCTree> lb) {
            Method model = methodOrFunction.getDeclarationModel();
            MethodDefinitionBuilder builder = MethodDefinitionBuilder.method(ClassTransformer.this, model);
            
            transformUltimateHeader(methodOrFunction, builder);
            
            boolean prevSyntheticClassBody = expressionGen().withinSyntheticClassBody(Decl.isMpl(methodOrFunction.getDeclarationModel()) || expressionGen().isWithinSyntheticClassBody());
            transformUltimateBody(methodOrFunction, builder);
            expressionGen().withinSyntheticClassBody(prevSyntheticClassBody);
            
            lb.append(builder.build());
            
            
        }
        
        protected void transformUltimateHeader(Tree.AnyMethod methodOrFunction,
                MethodDefinitionBuilder builder) {
            Method model = methodOrFunction.getDeclarationModel();
            transformUltimateUserAnnotations(methodOrFunction.getAnnotationList(), builder);
            transformUltimateModelAnnotations(model, builder);
            transformUltimateModifiers(model, builder);
            transformUltimateTypeParameterList(model, builder);
            transformUltimateResultType(model, builder);
            transformUltimateParameterList(methodOrFunction, builder);// can't this be a parameter list?
        }
        protected void transformUltimateParameterList(Tree.AnyMethod methodOrFunction, MethodDefinitionBuilder builder) {
            // Add parameters for the reified types
            Method model = methodOrFunction.getDeclarationModel();
            java.util.List<TypeParameter> typeParameterList = model.getTypeParameters();
            if (typeParameterList != null && gen().supportsReified(model)) {
                builder.reifiedTypeParameters(typeParameterList);
            }
            // Add parameters for the explicit parameters
            Tree.ParameterList parameterList = getFirstParameterList(methodOrFunction);
            for (final Tree.Parameter parameter : parameterList.getParameters()) {
                builder.parameter(parameter.getParameterModel(), transformParameterAnnotations(parameter), 0, true);
            }
        }
        
        protected List<JCAnnotation> transformParameterAnnotations(
                final Tree.Parameter parameter) {
            List<JCAnnotation> annotations = null;
            if (parameter instanceof Tree.ParameterDeclaration
                    && ((Tree.ParameterDeclaration)parameter).getTypedDeclaration() != null) {
                annotations = expressionGen().transform(((Tree.ParameterDeclaration)parameter).getTypedDeclaration().getAnnotationList());
            }
            return annotations;
        }
        
        protected void transformUltimateResultType(Method methodOrFunction, MethodDefinitionBuilder builder) {
            builder.resultType(methodOrFunction, 0);
        }
        
        protected void transformUltimateTypeParameterList(Method methodOrFunction, MethodDefinitionBuilder builder) {
            copyTypeParameters(methodOrFunction, builder);
        }
        
        protected void transformUltimateModifiers(Method methodOrFunction, MethodDefinitionBuilder builder) {
            builder.modifiers(transformMethodDeclFlags(methodOrFunction));
        }
        
        protected void transformUltimateModelAnnotations(Method methodOrFunction, MethodDefinitionBuilder builder) {
            builder.modelAnnotations(methodOrFunction.getAnnotations());
        }
        
        protected void transformUltimateUserAnnotations(
                Tree.AnnotationList annotationList, MethodDefinitionBuilder builder) {
            builder.userAnnotations(expressionGen().transform(annotationList));
        }
        
        protected void transformUltimateBody(Tree.AnyMethod methodOrFunction,
                MethodDefinitionBuilder builder) {
            builder.body(transformBody(methodOrFunction));
        }
        protected final List<JCStatement> transformBody(Tree.AnyMethod methodOrFunction) {
            List<JCStatement> body;
            if (methodOrFunction.getDeclarationModel().isDeferred()) {
                body = transformDeferredMethodBody(methodOrFunction);
            } else if (methodOrFunction instanceof Tree.MethodDefinition) {
                body = transformMethodBlock((Tree.MethodDefinition)methodOrFunction);
            } else if (methodOrFunction instanceof Tree.MethodDeclaration) {
                body = transformSpecifiedMethodBody((Tree.MethodDeclaration)methodOrFunction, ((Tree.MethodDeclaration) methodOrFunction).getSpecifierExpression());
            } else {
                throw new RuntimeException();
            }
            return transformMplBody(methodOrFunction.getParameterLists(), methodOrFunction.getDeclarationModel(), body);
        }
        @Override
        protected void transformOverloadMethod(
                Tree.AnyMethod methodOrFunction,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb){
            MethodDefinitionBuilder overloadedMethod = getDefaultedArgumentMethod(
                    methodOrFunction.getDeclarationModel(), 
                    parameter.getParameterModel())
                .makeOverload(methodOrFunction.getDeclarationModel(),
                    parameterList.getModel(),
                    parameter.getParameterModel(),
                    methodOrFunction.getDeclarationModel().getTypeParameters(),
                    getDefaultArgumentOverload(methodOrFunction.getDeclarationModel()));
            lb.append(overloadedMethod.build());
        }
        
        /**
         * Gets the transformation to use to construct the defaulted argument overload
         * @param parameter 
         * @param method 
         */
        protected DefaultedArgumentOverload<Method> getDefaultedArgumentMethod(
                Method method, 
                Parameter parameter) {
            return new DefaultedArgumentMethod() {
                @Override
                protected void appendCanonicalImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
                    super.appendCanonicalImplicitArguments(method, typeParameterList, overloadBuilder, args);
                }
            };
        }
        
        protected DaoBody<Method> getDefaultArgumentOverload(Method model) {
            return daoThis;
        }
        
        @Override
        protected void transformDefaultedParameterValueMethod(
                Tree.AnyMethod methodOrFunction,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb){
            Method model = methodOrFunction.getDeclarationModel();
            Declaration refinedDeclaration = model.getRefinedDeclaration();
            Parameter parameterModel = parameter.getParameterModel();
            DefaultParameterValueMethodTransformation<Method> dpmTransformation 
                = getDefaultParameterValueMethodTransformation(model, refinedDeclaration, parameterModel);
            lb.append(dpmTransformation.transform(methodOrFunction.getDeclarationModel(),
                    parameterList.getModel(),
                    parameter).build());
        }
        
        /**
         * Gets the transformation to use to create the default parameter value method
         */
        protected DefaultParameterValueMethodTransformation<Method> getDefaultParameterValueMethodTransformation(
                Method model, Declaration refinedDeclaration,
                Parameter parameterModel) {
            DefaultParameterValueMethodTransformation<Method> dpmTransformation = abstractMethodDpvmTransformation;
            if (Strategy.hasDefaultParameterValueMethod(parameterModel)
                    || Strategy.hasDefaultParameterOverload(parameterModel)) {
                if (refinedDeclaration == model
                        || !Decl.withinInterface(model)) {
                    if (refinedDeclaration == model
                            && Strategy.hasDefaultParameterValueMethod(parameterModel)) {
                        dpmTransformation = methodDpvmTransformation;
                    }
                }
            }
            return dpmTransformation;
        }
    }
    /** Transformation of a toplevel function */
    class ToplevelFunctionTransformation extends MethodOrFunctionTransformation {
        
    }
    private ToplevelFunctionTransformation toplevelFunctionTransformation = new ToplevelFunctionTransformation();
    
    static <B extends ParameterizedBuilder<B>> void capturedThisParameter(Declaration memberOrLocal, B builder) {
        
        Declaration member;
        if (memberOrLocal.isInterfaceMember()) {
            member = memberOrLocal;
        } else if (!memberOrLocal.isToplevel()) {
            member = Decl.getDeclarationContainer(memberOrLocal);
            Assert.that(member.isInterfaceMember());
        } else {
            throw Assert.fail();
        }
        Interface iface = (Interface)member.getContainer();
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.implicitParameter(builder.gen, "$this");
        pdb.type(builder.gen.makeJavaType(iface.getType()), null);
        pdb.modifiers(FINAL);
        builder.parameter(pdb);
    }
    
    static <B extends ParameterizedBuilder<B>> void outerTypeParameters(Declaration declaration, B builder) {
        outerTypeParametersInternal(declaration, declaration, builder);
    }
    
    static <B extends ParameterizedBuilder<B>> void outerTypeParametersInternal(Declaration origin, Declaration declaration, B builder) {
        Declaration container = Decl.getDeclarationContainer(declaration);
        if (container != null) {
            if (Decl.isLocal(container) || origin.isInterfaceMember()) {
                outerTypeParametersInternal(origin, container, builder);
            }
            if (origin.isInterfaceMember() || 
                    (container instanceof Generic
                    && (!(container instanceof Class) || declaration instanceof Interface))) {
                copyTypeParameters((Generic)container, builder);
            }
        }
    }
    
    static <B extends ParameterizedBuilder<B>> void outerReifiedTypeParameters(Declaration declaration, B builder) {
        outerReifiedTypeParametersInternal(declaration, declaration, builder);
    }
    static <B extends ParameterizedBuilder<B>> void outerReifiedTypeParametersInternal(Declaration origin, Declaration declaration, B builder) {
        Declaration container = Decl.getDeclarationContainer(declaration);
        if (container != null) {
            if (Decl.isLocal(container) || origin.isInterfaceMember()) {
                outerReifiedTypeParametersInternal(origin, container, builder);
            }
            if (origin.isInterfaceMember() ||
                    (container instanceof Generic
                            && !(container instanceof Class))) {
                builder.reifiedTypeParameters(((Generic)container).getTypeParameters());
            }
        }
    }
    
    static void outerReifiedTypeArguments(AbstractTransformer gen, Declaration declaration, ListBuffer<JCExpression> result) {
        outerReifiedTypeArgumentsInternal(gen, declaration, declaration, result);
    }
    
    static void outerReifiedTypeArgumentsInternal(AbstractTransformer gen, Declaration origin, Declaration declaration, ListBuffer<JCExpression> result) {
        Declaration container = Decl.getDeclarationContainer(declaration);
        if (container != null) {
            if (Decl.isLocal(container) || origin.isInterfaceMember()) {
                outerReifiedTypeArgumentsInternal(gen, origin, container, result);
            }
            if (origin.isInterfaceMember() || 
                    (container instanceof Generic
                            && !(container instanceof Class))) {
                for (TypeParameter tp : ((Generic)container).getTypeParameters()) {
                    JCExpression reifiedTypeArg = gen.makeReifiedTypeArgument(tp.getType());
                    result.append(reifiedTypeArg);
                }
            }
        }
    }
    
    /** Appends implicit parameters for the captured locals */
    static <B extends ParameterizedBuilder<B>> void capturedLocalParameters(Declaration decl, B builder) {
        for (Declaration captured : Decl.getCapturedLocals(decl)) {
            if (captured instanceof TypedDeclaration) {
                if ((captured instanceof MethodOrValue)
                        && Decl.isLocal(captured)
                        && ((MethodOrValue)captured).isTransient()
                        && !((MethodOrValue)captured).isParameter()
                        && !((MethodOrValue)captured).isDeferred()) {
                    continue;
                }
                builder.capturedLocalParameter((TypedDeclaration)captured);
            } else if (decl.isInterfaceMember()) {
                builder.capturedLocalParameter((TypeDeclaration)captured);
            }
        }
    }
    
    
    /** 
     * When a function's specification is deferred, we need an 
     * implicit parameter for the specified callable 
     */
    static void deferredSpecificationParameter(AbstractTransformer gen, MethodOrValue function, JCExpression type, MethodDefinitionBuilder builder) {
        if (function.isDeferred() && !function.isParameter()) {
            ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.implicitParameter(gen, gen.naming.selector(function, Naming.NA_MEMBER));
            pdb.modifiers(FINAL);
            pdb.ignored();
            pdb.type(type, null);
            builder.parameter(pdb);
        }
    }
    
    static void deferredSpecificationArgument(AbstractTransformer gen, MethodOrValue function, ListBuffer<JCExpression> args) {
        if (function.isDeferred() && !function.isParameter()) {
            args.add(gen.naming.makeUnquotedIdent(gen.naming.selector(function, Naming.NA_MEMBER)));
        }
    }
    
    /** 
     * Transformation of a local function: We transform to a static 
     * method declared on the same class as contains the method/getter
     * that contains the local function. 
     */
    class LocalFunctionTransformation extends MethodOrFunctionTransformation {
        
        private void capturedLocalArguments(Method function, ListBuffer<JCExpression> args) {
            for (Declaration captured : Decl.getCapturedLocals(function)) {
                if (captured instanceof TypedDeclaration) {
                    if ((captured instanceof MethodOrValue)
                            && Decl.isLocal(captured)
                            && ((MethodOrValue)captured).isTransient()
                            && !((MethodOrValue)captured).isParameter()
                            && !((MethodOrValue)captured).isDeferred()) {
                        continue;
                    }
                    args.add(naming.makeName((TypedDeclaration)captured, Naming.NA_IDENT));
                }
            }
        }
        
        /**
         * The overload methods need:
         * <ul>
         * <li> to be {@code static}.
         * <li> to have type parameters from the containing declaration, 
                in addition to the type parameters of the method.
         * <li> TODO to have additional parameters depending on what's captured
         * </ul> 
         */
        protected DefaultedArgumentOverload<Method> getDefaultedArgumentMethod(
                Method method, 
                Parameter parameter) {
            class LocalDefaultedArgumentFunction extends DefaultedArgumentMethod {
                @Override
                protected long getModifiers(Method method, DaoBody<Method> daoBody) {
                    return PRIVATE | FINAL | useStatic(method) | (super.getModifiers(method, daoBody) & ~(PUBLIC | PROTECTED));
                }
                @Override
                protected void transformTypeParameterList(Method method, MethodDefinitionBuilder overloadBuilder) {
                    outerTypeParameters(method, overloadBuilder);
                    super.transformTypeParameterList(method, overloadBuilder);
                }
                @Override
                protected final void transformParameterList(Method method, ParameterList parameterList, Parameter currentParameter, MethodDefinitionBuilder overloadBuilder) {
                    deferredSpecificationParameter(ClassTransformer.this, method, makeJavaType(method.getType().getFullType()), overloadBuilder);
                    capturedLocalParameters(method, overloadBuilder);
                    outerReifiedTypeParameters(method, overloadBuilder);
                    super.transformParameterList(method, parameterList, currentParameter, overloadBuilder);
                }
                @Override
                protected void appendCanonicalImplicitArguments(Method method, 
                        java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
                    deferredSpecificationArgument(ClassTransformer.this, method, args);
                    capturedLocalArguments(method, args);
                    outerReifiedTypeArguments(ClassTransformer.this, method, args);
                    super.appendCanonicalImplicitArguments(method, typeParameterList, overloadBuilder, args);
                }
                @Override
                protected void appendDpmImplicitArguments(Method method, 
                        java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
                    // don't include the $impl$ for a deferred method: It cannot affect the result
                    capturedLocalArguments(method, args);
                    outerReifiedTypeArguments(ClassTransformer.this, method, args);
                    super.appendDpmImplicitArguments(method, typeParameterList, overloadBuilder, args);
                }
                /** Returns the name of the method that the overload delegates to */
                @Override
                protected JCExpression makeMethodName(Method method, DaoBody<Method> daoBody) {
                    int flags = Naming.NA_IDENT;
                    /*if (Decl.withinClassOrInterface(method)
                            && method.isDefault()) {
                        flags |= Naming.NA_CANONICAL_METHOD;
                    }*/
                    return naming.makeUnquotedIdent(naming.selector(method));//makeName(method, flags);
                }
            }
            return new LocalDefaultedArgumentFunction();
        }
        
        /**
         * The default parameter value methods need:
         * <ul>
         * <li> to be {@code static}.
         * <li> to have type parameters from the containing declaration, 
                in addition to the type parameters of the method.
         * <li> TODO to be named according to the (local) name of the local function
         * <li> TODO to have additional parameters depending on what's captured
         * </ul> 
         */
        @Override
        protected DefaultParameterValueMethodTransformation<Method> getDefaultParameterValueMethodTransformation(
                Method model, 
                Declaration refinedDeclaration,
                Parameter parameterModel) {
            
            return new MethodDpvmTransformation() {
                @Override
                protected int getModifiers(Method method) {
                    return PRIVATE | useStatic(method) | (super.getModifiers(method) & ~(PUBLIC | PROTECTED));
                }
                @Override
                protected void transformTypeParameterList(Method method,
                        MethodDefinitionBuilder methodBuilder) {
                    outerTypeParameters(method, methodBuilder);
                    super.transformTypeParameterList(method, methodBuilder);
                }
                @Override
                protected void transformParameterList(Method method,
                        ParameterList parameterList, Parameter parameter,
                        MethodDefinitionBuilder methodBuilder)  {
                    capturedLocalParameters(method, methodBuilder);
                    outerReifiedTypeParameters(method, methodBuilder);
                    super.transformParameterList(method, parameterList, parameter, methodBuilder);
                }
            };
        }
        
        protected void transformUltimateTypeParameterList(Method methodOrFunction, MethodDefinitionBuilder builder) {
            outerTypeParameters(methodOrFunction, builder);
            copyTypeParameters(methodOrFunction, builder);
        }
        
        protected void transformUltimateParameterList(Tree.AnyMethod methodOrFunction, MethodDefinitionBuilder builder) {
            Method model = methodOrFunction.getDeclarationModel();
            deferredSpecificationParameter(ClassTransformer.this, model, makeJavaType(model.getType().getFullType()), builder);
            capturedLocalParameters(model, builder);
            outerReifiedTypeParameters(model, builder);
            super.transformUltimateParameterList(methodOrFunction, builder);
        }
    
        protected void transformUltimateModifiers(Method methodOrFunction, MethodDefinitionBuilder builder) {
            int mods = PRIVATE | FINAL | transformMethodDeclFlags(methodOrFunction);
            mods = mods | useStatic(methodOrFunction);
            // TODO when container is interface
            builder.modifiers(mods);
        }

        private int useStatic(Method methodOrFunction) {
            return localStatic(methodOrFunction) ? STATIC : 0;
        }
    }
    
    static boolean localStatic(Declaration value) {
        // TODO Refactor: Essentially the same logic exists for localFunctionTransformation
        Declaration container = Decl.getDeclarationContainer(value, false);
        Declaration nonLocalContainer = Decl.getNonLocalDeclarationContainer(value);
        if (container.isInterfaceMember()) {
            return true;
        }
        if (container instanceof Method) {
            if ((transformMethodDeclFlags((Method)container) & STATIC) != 0
                || nonLocalContainer instanceof TypedDeclaration && nonLocalContainer.isToplevel()) {
                return true;
            }
        } else if (container instanceof Value) {
            if (container.isToplevel()
                || nonLocalContainer instanceof TypedDeclaration && nonLocalContainer.isToplevel()) {
                return true;
            }
        } else if (container instanceof Setter) {
            if (container.isToplevel()
                || nonLocalContainer instanceof TypedDeclaration && nonLocalContainer.isToplevel()) {
                return true;
            }
        } else if (container instanceof Class) {
            if ((transformClassDeclFlags((Class)container) & STATIC) != 0) {
                return true;
            }
        }
        return false;
    }
    
    LocalFunctionTransformation localFunctionTransformation = new LocalFunctionTransformation();
    
    /** 
     * <p>Transformation of a method. We need to worry about:</p>
     * <ul>
     * <li>Refined return types</li>
     * <li>Generating a "canonical method" if the ceylon method is {@code default} (#1203). 
     *     We do this by having the ultimate method delegate to an extra peripheral method
     *     called the canonical method, which contains the method code.</li>
     * <ul>
     */
    abstract class MethodTransformation extends MethodOrFunctionTransformation {
        protected final boolean overloadsUltimate(Tree.AnyMethod method) {
            for (Tree.Parameter parameter : getFirstParameterList(method).getParameters()) {
                if (Strategy.hasDefaultParameterOverload(parameter.getParameterModel())) {
                    return true;
                }
            }
            return false;
        }
        /**
         * Determine whether we need to generate a canonical method.
         */
        protected boolean generateCanonical(Tree.AnyMethod method) {
            return overloadsUltimate(method) && method.getDeclarationModel().isDefault();
        }
        /**
         * If necessary we generate an extra 
         * peripheral method -- the canonical method -- which holds the 
         * transformed code and which the ultimate method will delegate to
         */
        protected void transformPeripheral(Tree.AnyMethod method, ListBuffer<JCTree> lb) {
            super.transformPeripheral(method, lb);
            
            Method model = method.getDeclarationModel();
            if (generateCanonical(method)) {
                // Generate a canonical method
                boolean refinedResultType = !model.getType().isExactly(
                        ((TypedDeclaration)model.getRefinedDeclaration()).getType());
                DaoBody<Method> daoTransformation = refinedResultType 
                        && !Decl.withinInterface(model.getRefinedDeclaration())? daoSuper : daoThis;
                MethodDefinitionBuilder canonicalMethod = new CanonicalMethod(transformBody(method))
                    .makeOverload(model,
                        getFirstParameterList(method).getModel(),
                        null,
                        model.getTypeParameters(),
                        daoTransformation);
                lb.append(canonicalMethod.build());
            }
        }
        
        @Override
        protected DaoBody getDefaultArgumentOverload(Method model) {
            return model.isFormal() ? daoAbstract : daoThis;
        }
        
        /** 
         * If we're using canonical methods: generate an ultimate method 
         * that deletegates the canonical method. 
         * If we're not using canoncial methods: generate an ultimate method
         * that contains the transformed code.
         */
        @Override
        protected void transformUltimateBody(Tree.AnyMethod method,
                MethodDefinitionBuilder builder) {
            if (generateCanonical(method)) {
                Method model = method.getDeclarationModel();
                // ultimate body will just delegate to the canonical method
                CanonicalMethod canonical = new CanonicalMethod();
                daoThis.makeBody(model, canonical, builder, getFirstParameterList(method).getModel(),
                        null, model.getTypeParameters());
            } else {
                super.transformUltimateBody(method, builder);
            }
        }
    }
    
    /** Transformation of a method that's a member of a class. */
    class ClassMethodTransformation extends MethodTransformation {
        /** For class methods we need add {@code @Override} iff it's {@code actual}. */
        @Override
        protected void transformUltimateModifiers(Method method, MethodDefinitionBuilder builder) {
            super.transformUltimateModifiers(method, builder);
            builder.isOverride(method.isActual());
        }
    }
    /** The instance of {@link ClassMethodTransformation} */
    private ClassMethodTransformation classMethodTransformation = new ClassMethodTransformation();
    
    /** Transformation of a method that's a member of a class, but declared as a functional parameter*/
    class ClassMethodFromFunctionalParameterTransformation extends ClassMethodTransformation {
        @Override
        protected void transformUltimateBody(Tree.AnyMethod method,
                MethodDefinitionBuilder builder) {
            Method model = method.getDeclarationModel();
            java.util.List<Parameter> parameters = model.getParameterLists().get(0).getParameters();
            CallBuilder callBuilder = CallBuilder.instance(ClassTransformer.this).invoke(
                    naming.makeQualIdent(naming.makeName(model, Naming.NA_IDENT), 
                            Naming.getCallableMethodName(model)));
            for (Parameter parameter : parameters) {
                JCExpression parameterExpr = naming.makeName(parameter.getModel(), Naming.NA_IDENT);
                parameterExpr = expressionGen().applyErasureAndBoxing(parameterExpr, parameter.getType(), 
                        !CodegenUtil.isUnBoxed(parameter.getModel()), BoxingStrategy.BOXED, 
                        parameter.getType());
                callBuilder.argument(parameterExpr);
            }
            JCExpression expr = callBuilder.build();
            JCStatement body;
            if (isVoid(method) && Decl.isUnboxedVoid(model) && !Strategy.useBoxedVoid(model)) {
                body = make().Exec(expr);
            } else {
                expr = expressionGen().applyErasureAndBoxing(expr, model.getInitializerParameter().getType()/*paramModel.getType()*/, true, CodegenUtil.getBoxingStrategy(model), model.getInitializerParameter().getType());
                body = make().Return(expr);
            }
            builder.body(body);
        }
    }
    /** The instance of {@link ClassMethodFromFunctionalParameterTransformation} */
    private ClassMethodFromFunctionalParameterTransformation classMethodFromFunctionalParameterTransformation = new ClassMethodFromFunctionalParameterTransformation();
    
    /** Transformation of a <em>concrete</em> method that's a member of an interface */
    class CompanionMethodTransformation extends MethodTransformation {
        
        /**
         * Gets the transformation to use to construct the defaulted argument overload
         * @param parameter 
         * @param method 
         */
        @Override
        protected DefaultedArgumentOverload<Method> getDefaultedArgumentMethod(
                Method method, 
                Parameter parameter) {
            class CompanionDefaultedArgumentMethod extends DefaultedArgumentMethod {
                
                @Override
                protected void transformTypeParameterList(Method method, MethodDefinitionBuilder overloadBuilder) {
                    outerTypeParameters(method, overloadBuilder);
                    super.transformTypeParameterList(method, overloadBuilder);
                }
                
                JCExpression makeDefaultedParamMethod(Method model, Parameter parameterModel, DaoBody<Method> body) {
                    String methodName = naming.getDefaultedParamMethodName(model, parameterModel);
                    return naming.makeQualIdent(makeJavaType(((Interface)model.getContainer()).getType(), JT_COMPANION), methodName);
                }
                @Override
                protected final JCExpression makeDefaultArgumentValueMethodQualifier(Method method, DaoBody<Method> daoBody) {
                    return makeJavaType(((Interface)method.getContainer()).getType(), JT_COMPANION);//naming.getDefaultedParamMethodName(method, param);
                }
                @Override
                protected void appendImplicitParameters(Method functional, java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder) {
                    
                    capturedThisParameter(functional, overloadBuilder);
                    capturedLocalParameters(functional, overloadBuilder);
                    outerReifiedTypeParameters(functional, overloadBuilder);
                    
                    super.appendImplicitParameters(functional, typeParameterList, overloadBuilder);
                }
                @Override
                protected long getModifiers(Method method, DaoBody<Method> daoBody) {
                    long mods = !method.isShared() ? PRIVATE : 
                        ((Interface)method.getContainer()).isShared() ? PUBLIC : 0;
                    mods |= STATIC;
                    return mods;
                }
                @Override
                protected void appendDpmImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
                    args.add(naming.makeQuotedThis());
                    addCapturedLocalArguments(args, method);
                    addCapturedReifiedTypeParameters(args, method);
                    //outerReifiedTypeArguments(ClassTransformer.this, method, args);
                    super.appendDpmImplicitArguments(method, typeParameterList, overloadBuilder, args);
                }
                @Override
                protected void appendCanonicalImplicitArguments(Method method, java.util.List<TypeParameter> typeParameterList,
                        MethodDefinitionBuilder overloadBuilder, ListBuffer<JCExpression> args) {
                    args.add(naming.makeQuotedThis());
                    outerReifiedTypeArguments(ClassTransformer.this, method, args);
                    super.appendCanonicalImplicitArguments(method, typeParameterList, overloadBuilder, args);
                }
            }
            return new CompanionDefaultedArgumentMethod();
        }
        
        @Override
        protected DefaultParameterValueMethodTransformation<Method> getDefaultParameterValueMethodTransformation(
                Method model, 
                Declaration refinedDeclaration,
                Parameter parameterModel) {
            return new CompanionMethodDpvmTransformation();
        }
        @Override
        public List<JCTree> transform(Tree.AnyMethod method) {
            Interface prev = expressionGen().withinCompanion((Interface)method.getDeclarationModel().getContainer());
            Method m = method.getDeclarationModel();
            // We only transform if there's code
            ListBuffer<JCTree> lb = ListBuffer.<JCTree>lb();
            transformPeripheral(method, lb);
            if (!m.isFormal()) {
                transformUltimate(method, lb);
            }
            expressionGen().withinCompanion(prev);
            return lb.toList();
        }
        
        @Override
        protected void transformOverloadMethod(
                Tree.AnyMethod method,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb){
            if (!method.getDeclarationModel().isShared()) {
                super.transformOverloadMethod(method, parameterList, parameter, lb);
            }
        }
        @Override
        protected void transformUltimateModelAnnotations(Method methodOrFunction, MethodDefinitionBuilder builder) {
            if (methodOrFunction.isShared()) {
                builder.noAnnotations();
            }
        }
        @Override
        protected void transformUltimateUserAnnotations(
                Tree.AnnotationList annotationList, MethodDefinitionBuilder builder) {
            // no annotations
        }
        @Override
        protected void transformUltimateTypeParameterList(Method method, MethodDefinitionBuilder builder) {
            // TPs from outer
            outerTypeParameters(method, builder);
            // TPs from method itself
            super.transformUltimateTypeParameterList(method, builder);
        }
        
        @Override
        protected void transformUltimateParameterList(Tree.AnyMethod methodOrFunction, MethodDefinitionBuilder builder) {
            Method model = methodOrFunction.getDeclarationModel();
            // The $this parameter
            capturedThisParameter(model, builder);
            // parameters for captured local environment
            capturedLocalParameters(model, builder);
            // parameters for reified type arguments
            outerReifiedTypeParameters(model, builder);
            // the explicit parameter list
            super.transformUltimateParameterList(methodOrFunction, builder);
        }
        
        @Override
        protected void transformUltimateModifiers(Method method, MethodDefinitionBuilder builder) {
            long mods = !method.isShared() ? PRIVATE : 
                ((Interface)method.getContainer()).isShared() ? PUBLIC : 0;
            mods |= STATIC;
            builder.modifiers(mods);
        }
    }
    private CompanionMethodTransformation companionMethodTransformation = new CompanionMethodTransformation();
    
    /** Transformation of a method that's a member of an interface */
    class InterfaceMethodTransformation extends MethodTransformation {
        /** 
         * canonical methods never appear on interfaces, since they're 
         * public and by definition are not abstract
         */
        protected boolean generateCanonical(Tree.AnyMethod method) {
            return false;
        }
        
        /** 
         * A Ceylon interface method only gets transformed to the Java interface 
         * if it's {@code shared} 
         */
        @Override
        public List<JCTree> transform(Tree.AnyMethod method) {
            if (method.getDeclarationModel().isShared()) {
                return super.transform(method);
            } else {
                return List.<JCTree>nil();
            }
        }
        /** 
         * For interface methods we need add {@code @Override} iff it's {@code actual}. 
         */
        @Override
        protected void transformUltimateModifiers(Method method, MethodDefinitionBuilder builder) {
            super.transformUltimateModifiers(method, builder);
            builder.isOverride(method.isActual());
        }
        /** The defaulted parameter value method will always be abstract */
        @Override
        protected void transformDefaultedParameterValueMethod(
                Tree.AnyMethod methodOrFunction,
                Tree.ParameterList parameterList,
                Tree.Parameter parameter,
                ListBuffer<JCTree> lb){
            // use abstract transformation, because we're transforming to an interface
            lb.append(abstractMethodDpvmTransformation.transform(methodOrFunction.getDeclarationModel(),
                    parameterList.getModel(),
                    parameter).build());
        }
        /** The ultimate method will always be abstract */
        @Override
        protected void transformUltimateBody(Tree.AnyMethod method,
                MethodDefinitionBuilder builder) {
            builder.noBody();
        }
        /** The overload will always be abstract */
        @Override
        protected DaoBody getDefaultArgumentOverload(Method model) {
            return daoAbstract;
        }
    }
    private InterfaceMethodTransformation interfaceMethodTransformation = new InterfaceMethodTransformation();

 }
