package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.ArrayList;

import com.redhat.ceylon.compiler.java.codegen.Naming.Substitution;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * A baseclass for builders of methods and classes, which both deal with 
 * parameters and type parameters.
 */
abstract class ParameterizedBuilder<B extends ParameterizedBuilder<B>> {

    protected final AbstractTransformer gen;
    private java.util.List<Naming.Substitution> subsToClose = null;
    
    protected ParameterizedBuilder(AbstractTransformer gen) {
        this.gen = gen;
    }
    
    public abstract B parameter(ParameterDefinitionBuilder pdb);
    
    /**
     * Adds a parameter for the given captured declaration, introducing
     * a substitution to avoid multiple parameters with the same name 
     * due to capture of distinct declarations with the same name 
     * (from different scopes).
     * 
     * @see #closeSubstitutions()
     */
    public final B capturedLocalParameter(Declaration declaration) {
        // TODO This is a copy of what's in MDB: We need to refactor it
        if (declaration instanceof Method 
                && Strategy.useStaticForFunction((Method)declaration)
                && !declaration.isParameter()) {
            return (B)this;
        }
        String parameterName;
        
        if (declaration instanceof TypedDeclaration) {
            parameterName = gen.naming.aliasName(declaration.getName()).toString();
            if (subsToClose == null) {
                subsToClose = new ArrayList<Naming.Substitution>(5);
            }
            subsToClose.add(gen.naming.addVariableSubst((TypedDeclaration)declaration, parameterName));
        } else if (declaration instanceof TypeDeclaration) {
            parameterName = gen.naming.getOuterParameterName((TypeDeclaration)declaration);
        } else {
            throw Assert.fail();
        }
        
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.implicitParameter(gen, 
                parameterName);
        pdb.ignored();
        pdb.modifiers(FINAL/*| Flags.SYNTHETIC*/);
        // TODO This futzing around how we use the getter needs better encapsulation
        if (declaration instanceof TypedDeclaration) {
            TypedDeclaration typedDeclaration = (TypedDeclaration)declaration;
            if (Decl.isGetter(typedDeclaration)) {
                pdb.type(gen.makeJavaType(gen.getGetterInterfaceType(typedDeclaration)), null);
            } else if (typedDeclaration instanceof Setter) {
                pdb.type(gen.naming.makeQuotedIdent(gen.naming.getAttrClassName(typedDeclaration, 0)), null);
            } else if (typedDeclaration.isVariable()) {
                pdb.type(gen.makeVariableBoxType(typedDeclaration), null);
            } else if (typedDeclaration instanceof Value
                    && Decl.isLocal(typedDeclaration)
                    && ((Value)typedDeclaration).isTransient()) {
                pdb.type(gen.naming.makeName((Value)typedDeclaration, Naming.NA_WRAPPER | Naming.NA_GETTER), null);
            } else if (declaration instanceof Method 
                    && !Strategy.useStaticForFunction((Method)typedDeclaration)) {
                pdb.type(gen.naming.makeName((Method)typedDeclaration, Naming.NA_WRAPPER), null);
            } else if (declaration instanceof Method) {
                pdb.type(gen.makeJavaType(typedDeclaration.getType().getFullType()), null);
            } else {
                pdb.type(gen.makeJavaType(typedDeclaration.getType()), null);
            }
        } else if (declaration instanceof TypeDeclaration) {
            pdb.type(gen.makeJavaType(((TypeDeclaration)declaration).getType()), null);
        } else {
            Assert.fail();
        }
        return parameter(pdb);
    }
    
    /**
     * Closes any substitutions that were introduced in 
     * {@link #capturedLocalParameter(TypedDeclaration)} 
     */
    public final void closeSubstitutions() {
        if (subsToClose != null) {
            for (Substitution subs : subsToClose) {
                subs.close();
            }
            subsToClose = null;
        }
    }
    public abstract B reifiedTypeParameters(java.util.List<TypeParameter> typeParams);
    public abstract B typeParameter(TypeParameter param);
    

}
