package com.redhat.ceylon.compiler.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.Iterator;

import javax.tools.JavaFileObject;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnyAttribute;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeGetterDefinition;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;

/**
 * Main transformer that delegates all transforming of ceylon to java to auxiliary classes.
 */
public class CeylonTransformer extends AbstractTransformer {
    
    public static CeylonTransformer getInstance(Context context) {
        CeylonTransformer trans = context.get(CeylonTransformer.class);
        if (trans == null) {
            trans = new CeylonTransformer(context);
            context.put(CeylonTransformer.class, trans);
        }
        return trans;
    }

    public CeylonTransformer(Context context) {
        super(context);
        setup(context);
    }

    private void setup(Context context) {
        Options options = Options.instance(context);
        // It's a bit weird to see "invokedynamic" set here,
        // but it has to be done before Resolve.instance().
        options.put("invokedynamic", "invokedynamic");
    }

    /**
     * In this pass we only make an empty placeholder which we'll fill in the
     * EnterCeylon phase later on
     */
    public JCCompilationUnit makeJCCompilationUnitPlaceholder(Tree.CompilationUnit t, JavaFileObject file, String pkgName) {
        System.err.println(t);
        JCExpression pkg = pkgName != null ? getPackage(pkgName) : null;
        at(t);
        JCCompilationUnit topLev = new CeylonCompilationUnit(List.<JCTree.JCAnnotation> nil(), pkg, List.<JCTree> nil(), null, null, null, null, t);

        topLev.lineMap = getMap();
        topLev.sourcefile = file;
        topLev.isCeylonProgram = true;

        return topLev;
    }

    /**
     * This runs after _some_ typechecking has been done
     */
    public ListBuffer<JCTree> transformAfterTypeChecking(Tree.CompilationUnit t) {
        disableModelAnnotations = false;
        CeylonVisitor visitor = new CeylonVisitor(this);
        t.visitChildren(visitor);
        return (ListBuffer<JCTree>) visitor.getResult();
    }

    // Make a name from a list of strings, using only the first component.
    Name makeName(Iterable<String> components) {
        Iterator<String> iterator = components.iterator();
        String s = iterator.next();
        assert (!iterator.hasNext());
        return names().fromString(s);
    }

    String toFlatName(Iterable<String> components) {
        StringBuffer buf = new StringBuffer();
        Iterator<String> iterator;

        for (iterator = components.iterator(); iterator.hasNext();) {
            buf.append(iterator.next());
            if (iterator.hasNext())
                buf.append('.');
        }

        return buf.toString();
    }

    private JCExpression makeIdentFromIdentifiers(Iterable<Tree.Identifier> components) {

        JCExpression type = null;
        for (Tree.Identifier component : components) {
            if (type == null)
                type = make().Ident(names().fromString(component.getText()));
            else
                type = makeSelect(type, component.getText());
        }

        return type;
    }

    // FIXME: port handleOverloadedToplevelClasses when I figure out what it
    // does

    private JCExpression getPackage(String fullname) {
        String shortName = Convert.shortName(fullname);
        String packagePart = Convert.packagePart(fullname);
        if (packagePart == null || packagePart.length() == 0)
            return make().Ident(names().fromString(shortName));
        else
            return make().Select(getPackage(packagePart), names().fromString(shortName));
    }

    public JCImport transform(Tree.ImportPath that) {
        return at(that).Import(makeIdentFromIdentifiers(that.getIdentifiers()), false);
    }
    
    public List<JCTree> transform(AnyAttribute decl) {
        at(decl);
        AttributeDefinitionBuilder builder = globalGen()
            .defineGlobal(makeJavaType(actualType(decl)), decl.getIdentifier().getText())
            .classAnnotations(makeAtAttribute())
            .valueAnnotations(makeJavaTypeAnnotations(decl.getDeclarationModel(), actualType(decl)))
            .classIsFinal(true);

        if (decl.getDeclarationModel().isShared()) {
            builder
                .classIsPublic(true)
                .getterIsPublic(true)
                .setterIsPublic(true);
        }
        
        if (!decl.getDeclarationModel().isVariable()) {
            builder.immutable();
        }

        if (decl instanceof AttributeDeclaration) {
            AttributeDeclaration adecl = (AttributeDeclaration)decl;
            if (adecl.getSpecifierOrInitializerExpression() != null) {
                builder.initialValue(expressionGen().transformExpression(
                        adecl.getSpecifierOrInitializerExpression().getExpression()));
            }
        } else {
            AttributeGetterDefinition gdef = (AttributeGetterDefinition)decl;
            JCBlock block = make().Block(0, statementGen().transformStmts(gdef.getBlock().getStatements()));
            builder.getterBlock(block);
        }

        boolean isMethodLocal = decl.getDeclarationModel().getContainer() instanceof com.redhat.ceylon.compiler.typechecker.model.Method;
        if (isMethodLocal) {
            // Add a "foo foo = new foo();" at the decl site
            JCTree.JCIdent name = make().Ident(names().fromString(decl.getIdentifier().getText()));
            
            JCExpression initValue = at(decl).NewClass(null, null, name, List.<JCTree.JCExpression>nil(), null);
            List<JCAnnotation> annots2 = List.<JCAnnotation>nil();
    
            int modifiers = decl.getDeclarationModel().isShared() ? 0 : FINAL;
            JCTree.JCVariableDecl var = at(decl).VarDef(at(decl)
                    .Modifiers(modifiers, annots2), 
                    names().fromString(decl.getIdentifier().getText()), 
                    name, 
                    initValue);
            
            return List.of(builder.build(), var);
        } else {
            builder
                .getterIsStatic(true)
                .setterIsStatic(true);
            return List.of(builder.build());
        }
    }

    // FIXME: figure out what CeylonTree.ReflectedLiteral maps to

}
