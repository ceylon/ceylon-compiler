package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.ArrayList;

import com.redhat.ceylon.compiler.java.codegen.Naming.Substitution;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;

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
    public final B capturedLocalParameter(TypedDeclaration declaration) {
        // TODO This is a copy of what's in MDB: We need to refactor it
        if (declaration instanceof Method 
                && Strategy.useStaticForFunction((Method)declaration)
                && !declaration.isParameter()) {
            return (B)this;
        }
        String parameterName;
        Scope scope = declaration.getScope();
        /*if (Decl.isGetter(declaration)) {// TODO This is just wrong
            parameterName = gen.naming.getLocalInstanceName(declaration, 0);
        } else if (declaration instanceof Setter) {
            parameterName = gen.naming.getAttrClassName(declaration, 0);
        } else*/ {
            parameterName = gen.naming.aliasName(declaration.getName()).toString();
            if (subsToClose == null) {
                subsToClose = new ArrayList<Naming.Substitution>(5);
            }
            subsToClose.add(gen.naming.addVariableSubst(declaration, parameterName));
        } /* else {
            parameterName = gen.naming.substitute(declaration);
        //}
        if (Decl.isGetter(declaration)) {// TODO This is just wrong
            parameterName = gen.naming.getLocalInstanceName(declaration, 0);
        } else if (declaration instanceof Setter) {
            parameterName = Naming.getAttrClassName(declaration, 0);
        }*/
        
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.implicitParameter(gen, 
                parameterName);
        pdb.ignored();
        pdb.modifiers(FINAL/*| Flags.SYNTHETIC*/);
        // TODO This futzing around how we use the getter needs better encapsulation
        if (Decl.isGetter(declaration)) {
            pdb.type(gen.makeJavaType(gen.getGetterInterfaceType((TypedDeclaration)declaration)), null);
        } else if (declaration instanceof Setter) {
            pdb.type(gen.naming.makeQuotedIdent(gen.naming.getAttrClassName(declaration, 0)), null);
        } else if (declaration.isVariable()) {
            pdb.type(gen.makeVariableBoxType(declaration), null);
        } else if (declaration instanceof Value
                && Decl.isLocal(declaration)
                && ((Value)declaration).isTransient()) {
            pdb.type(gen.naming.makeName((Value)declaration, Naming.NA_WRAPPER | Naming.NA_GETTER), null);
        } else if (declaration instanceof Method 
                && !Strategy.useStaticForFunction((Method)declaration)) {
            pdb.type(gen.naming.makeName((Method)declaration, Naming.NA_WRAPPER), null);
        } else if (declaration instanceof Method) {
            pdb.type(gen.makeJavaType(declaration.getType().getFullType()), null);
        } else {
            pdb.type(gen.makeJavaType(declaration.getType()), null);
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
