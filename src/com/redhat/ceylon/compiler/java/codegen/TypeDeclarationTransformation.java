package com.redhat.ceylon.compiler.java.codegen;

import static com.redhat.ceylon.compiler.java.codegen.Naming.DeclNameFlag.QUALIFIED;

import com.redhat.ceylon.compiler.typechecker.model.ClassAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypeParameterList;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

class ClassOrInterfaceTransformer extends AbstractTransformer {

    public ClassOrInterfaceTransformer(Context context) {
        super(context);
    }

    abstract class ClassOrInterfaceTransformation<T extends Tree.ClassOrInterface, D extends ClassOrInterface> {
        public List<JCTree> transform(T tree, D model) {
            ClassDefinitionBuilder builder = makeBuilder(tree, model);
            
            return builder.build();
        }
        protected final ClassDefinitionBuilder makeBuilder(T tree, D model) {
            String ceylonClassName = tree.getIdentifier().getText();
            ClassDefinitionBuilder classBuilder = ClassDefinitionBuilder
                    .klass(ClassOrInterfaceTransformer.this, javaClassName(tree), ceylonClassName)
                    .forDefinition(tree);
            return classBuilder;
        }
        protected String javaClassName(Tree.ClassOrInterface def) {
            return Naming.quoteClassName(def.getIdentifier().getText());
        }
        protected abstract void transformTypeParameterList(TypeParameterList tps, ClassDefinitionBuilder builder);
        protected abstract void transformUserAnnotations(T tree, ClassDefinitionBuilder builder);
        protected abstract void transformModelAnnotations(D model, ClassDefinitionBuilder builder);
        protected abstract void transformSatisfiedTypes(D model, ClassDefinitionBuilder builder);
        protected abstract void transformCaseTypes(D model, ClassDefinitionBuilder builder);
        protected final void transformMembers(T tree, ClassDefinitionBuilder builder) {
            transformExplicitMembers(tree, builder);
            transformImplicitMembers(tree, builder);
        }
        protected abstract void transformExplicitMembers(T tree, ClassDefinitionBuilder builder);
        protected abstract void transformImplicitMembers(T tree, ClassDefinitionBuilder builder);
    }
    
    abstract class ClassTransformation extends ClassOrInterfaceTransformation<Tree.AnyClass, Class> {
        protected abstract List<JCMethodDecl> transformInitializer();
        protected abstract void transformMixins();
        @Override
        protected void transformImplicitMembers(T tree, ClassDefinitionBuilder builder) {
            transformMixins();
            // TODO add a main if needed
            // TODO add a getType method
        }
    }
    
    class ToplevelClassTransformation extends ClassTransformation {
    
    }
    
    class MemberClassTransformation extends ClassTransformation {
    
    }
    
    class LocalClassTransformation extends ClassTransformation {
    
    }
    
    class InitializerTransformation extends FunctionalTransformation {
    
    }
    
    class InstantiatorTransformation extends FunctionalTransformation {
    
    }
    
    class ClassAliasTransformation extends ClassOrInterfaceTransformation<Tree.AnyClass, ClassAlias> {
    
    }
    
    abstract class InterfaceTransformation 
            extends ClassOrInterfaceTransformation<Tree.AnyInterface, Interface> {
        protected String javaClassName(Tree.AnyInterface def) {
            return naming.declName(def.getDeclarationModel(), QUALIFIED).replaceFirst(".*\\.", "");
        }
    }
    
    abstract class CompanionTransformation extends ClassOrInterfaceTransformation<Tree.AnyInterface, Interface> {
    
    }
    
    class ObjectTransformation {
    // This is just a class transformation and a value transformation
    }
}