package com.redhat.ceylon.compiler.java.codegen;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.sun.tools.javac.tree.JCTree.JCExpression;

abstract class JavacNameVisitor {
    abstract void visit(JavacUnit unit);
    abstract void visit(JavacClass unit);
}

class JavacUnit {
    
    private final String packageName;
    
    JavacUnit(String packageName) {
        this.packageName = packageName;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public String toString() {
        return getPackageName();
    }

    public void accept(JavacNameVisitor v) {
        v.visit(this);
    }
    
    @Override
    public int hashCode() {
        return packageName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JavacUnit other = (JavacUnit) obj;
        if (packageName == null) {
            if (other.packageName != null)
                return false;
        } else if (!packageName.equals(other.packageName))
            return false;
        return true;
    }
}

public class JavacClass {
    private final String name;
    private final Object outer;
    private boolean isCompanion;
    private JavacClass peer;
    
    private JavacClass(Object outer, String name) {
        this.name = name;
        this.outer = outer;
    }
    
    public JavacClass(JavacUnit unit, String name) {
        this((Object)unit, name);
    }
    
    public JavacClass(JavacClass outer, String name) {
        this((Object)outer, name);
    }
    public void accept(JavacNameVisitor v) {
        if (isToplevel()) {
            getUnit().accept(v);
        } else {
            getOuter().accept(v);
        }
        v.visit(this);
    }
    
    /** The unit containing this class */
    JavacUnit getUnit() {
        if (this.outer instanceof JavacUnit) {
            return (JavacUnit)outer;
        }
        return getOuter().getUnit();
    }
    
    public boolean isToplevel() {
        return outer instanceof JavacUnit;
    }
    
    /** The outer class containing this clas, or null if this class is toplevel */
    JavacClass getOuter() {
        if (this.outer instanceof JavacClass) {
            return (JavacClass)outer;
        }
        return null;
    }
    
    /** 
     * Returns the companion of the class, or null if this class is 
     * itself a companion 
     */
    JavacClass getCompanion() {
        if (!isCompanion) {
            if (peer == null) {
                // XXX this supposes that classes and their companions share the same outer.
                this.peer = new JavacClass(outer, name+"$impl");
                this.peer.peer = this;
                this.peer.isCompanion = true;
            }
            return peer;
        }
        return null;
    }
    /** 
     * Returns the class this class is the companion of, or null if this 
     * class is not a companion
     */
    JavacClass getCompanionOf() {
        if (isCompanion) {
            return peer;
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isCompanion ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((outer == null) ? 0 : outer.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JavacClass other = (JavacClass) obj;
        if (isCompanion != other.isCompanion)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (outer == null) {
            if (other.outer != null)
                return false;
        } else if (!outer.equals(other.outer))
            return false;
        return true;
    }
    
    private void fqName(StringBuffer sb) {
        if (isToplevel()) {
            sb.append(".");
            sb.append(getUnit());
            sb.append(".");
        } else {
            getOuter().fqName(sb);
        }
        sb.append(getName()).append(".");
    }
    
    public String getFqName() {
        StringBuffer sb = new StringBuffer();
        fqName(sb);
        sb.setLength(sb.length()-1);// remove last dot
        return sb.toString();
    }
    
    public String toString() {
        return getFqName();
    }
    
    public String getName() {
        return name;
    }
    public static void main(String[] args) {
        JavacUnit unit = new JavacUnit("org.example");
        JavacClass t = new JavacClass(unit, "Toplevel");
        System.out.println(t);
        System.out.println(t.getCompanion());
        System.out.println(t.getCompanion().getCompanionOf());
        JavacClass m = new JavacClass(t, "Member");
        System.out.println(m);
        System.out.println(m.getCompanion());
        System.out.println(m.getCompanion().getCompanionOf());
    }
}
/*
class JavacMethod {
    private final JavacClass class_;
    private final String name;
    private final boolean static_;
}

class JavacField {
    private final JavacClass class_;
    private final String name;
    private final boolean static_;
}*/

class FooNaming {
    static String name(JavacClass c) {
        class TypeNamingVisitor extends JavacNameVisitor {
            
            private final StringBuffer sb = new StringBuffer();
            
            @Override
            void visit(JavacUnit unit) {
                //if (options.includePackage) {
                    sb.append(unit.getPackageName()).append(".");
                //}
            }
            
            @Override
            void visit(JavacClass unit) {
                sb.append(unit.getName()).append(".");
            }
        }
        TypeNamingVisitor n = new TypeNamingVisitor();
        c.accept(n);
        return n.sb.toString();
    }
    
    static JCExpression typeName(JavacClass c, final Naming naming, int options) {
        class TypeNamingVisitor extends JavacNameVisitor {
            
            private JCExpression expr;
            
            @Override
            void visit(JavacUnit unit) {
                //if (options.includePackage) {
                    expr = naming.makeQuotedQualIdentFromString(unit.getPackageName());
                //}
            }
            
            @Override
            void visit(JavacClass unit) {
                if (expr == null) {
                    expr = naming.makeQuotedIdent(unit.getName());
                } else {
                    expr = naming.makeSelect(expr, unit.getName());
                }
            }
        }
        TypeNamingVisitor n = new TypeNamingVisitor();
        c.accept(n);
        return n.expr;
    }
    
    /*static JavacClass name(JavacUnit unit, TypeDeclaration decl) {
        if (decl.isToplevel()) { 
            // a toplevel class or interface => top level class
            if (decl instanceof Class || decl instanceof Interface) {
                return new JavacClass(unit, decl.getName());
            }
            // TODO What about type aliases?
        }
        if (decl.isMember()) {
            if (((Declaration)decl.getContainer()).isToplevel()) {
                // member type of toplevel
                return new JavacClass(unit, ((Declaration)decl.getContainer()).getName() + "$" + decl.getName());
            } else if (((Declaration)decl.getContainer()).isMember()) {
                // member type of member
               return (hoist((TypeDeclaration)decl.getContainer()) & ~(Hoist.WRAPPER)) | Hoist.MEMBER;
            } else {// member type of a local
                return hoist(Decl.getClassOrInterfaceContainer(decl));
            }
        }
        // otherwise it must be local
        ClassOrInterface c = Decl.getClassOrInterfaceContainer(decl);
        if (c == null) {
            // local to toplevel
            return 
        } else {
            hoist(c);
        }
    }*/


}

class NomVisitor extends Visitor {
    
    private JavacUnit unit;
    private JavacClass outer;
    public void visit(Tree.CompilationUnit that) {
        unit = new JavacUnit(that.getUnit().getPackage().getNameAsString());
        super.visit(that);
    }
    
    public void visit(Tree.ClassOrInterface that) {
        that.getDeclarationModel().setJvmName(nameDeclaration(that, that.getDeclarationModel()));
    }
    
    public void visit(Tree.ObjectDefinition that) {
        that.getDeclarationModel().setJvmName(nameDeclaration(that, that.getDeclarationModel()));
    }
    
    public void visit(Tree.ObjectArgument that) {
        that.getDeclarationModel().setJvmName(nameDeclaration(that, that.getDeclarationModel()));
    }
    
    public void visit(Tree.TypeAliasDeclaration that) {
        that.getDeclarationModel().setJvmName(nameDeclaration(that, that.getDeclarationModel()));
    }

    private JavacClass nameDeclaration(Tree.StatementOrArgument that, Declaration model) {
        String name = model.getName();
        JavacClass prev = outer;
        JavacClass current;
        if (outer == null) {
            current = new JavacClass(unit, name);
        } else if (model.isMember()){
            current = new JavacClass(outer.getOuter(), outer.getName()+"$"+name);
        } else {
            current = new JavacClass(outer, name);
        }
        System.out.println(current);
        outer = current;
        super.visit(that);
        outer = prev;
        return current;
    }
}