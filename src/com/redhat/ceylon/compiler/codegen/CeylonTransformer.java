package com.redhat.ceylon.compiler.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.tools.JavaFileObject;

import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;

import com.redhat.ceylon.compiler.loader.CeylonModelLoader;
import com.redhat.ceylon.compiler.loader.ModelLoader.DeclarationType;
import com.redhat.ceylon.compiler.typechecker.model.BottomType;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeGetterDefinition;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilerAnnotation;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LocalModifier;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypedDeclaration;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.parser.Keywords;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Position.LineMap;

/**
 * Main transformer that delegates all transforming of ceylon to java to auxiliary classes.
 */
public class CeylonTransformer {
    private TreeMaker make;
    Name.Table names;
    private LineMap map;
    Symtab syms;
    private Keywords keywords;
    private CeylonModelLoader modelLoader;
    private Map<String, String> varNameSubst = new HashMap<String, String>();
    
    ExpressionTransformer expressionGen;
    StatementTransformer statementGen;
    ClassTransformer classGen;
    GlobalTransformer globalGen;

    public boolean disableModelAnnotations = false;
    
    public static CeylonTransformer getInstance(Context context) throws Exception {
        CeylonTransformer gen2 = context.get(CeylonTransformer.class);
        if (gen2 == null) {
            gen2 = new CeylonTransformer(context);
            context.put(CeylonTransformer.class, gen2);
        }
        return gen2;
    }

    public CeylonTransformer(Context context) {
        setup(context);
        expressionGen = new ExpressionTransformer(this);
        statementGen = new StatementTransformer(this);
        classGen = new ClassTransformer(this);
        globalGen = new GlobalTransformer(this);
    }

    private void setup(Context context) {
        Options options = Options.instance(context);
        // It's a bit weird to see "invokedynamic" set here,
        // but it has to be done before Resolve.instance().
        options.put("invokedynamic", "invokedynamic");
        make = TreeMaker.instance(context);

        names = Name.Table.instance(context);
        syms = Symtab.instance(context);
        keywords = Keywords.instance(context);
        modelLoader = CeylonModelLoader.instance(context);
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

    JCTree.Factory at(Node t) {
        CommonTree antlrTreeNode = t.getAntlrTreeNode();
        Token token = antlrTreeNode.getToken();
        if (token != null) {
            make.at(getMap().getStartPosition(token.getLine()) + token.getCharPositionInLine());
        }
        return make;
    }

    TreeMaker make() {
        return make;
    }

    public GlobalTransformer globalGen() {
        return globalGen;
    }

    GlobalTransformer globalGenAt(Node t) {
        at(t);
        return globalGen;
    }

	private static final class ImportListVisitor extends AbstractVisitor<JCTree> {
		
		private ImportListVisitor(CeylonTransformer ceylonTransformer) {
			super(ceylonTransformer);
		}

		// FIXME: handle the rest of the cases here
		public void visit(Tree.ImportPath that) {
		    JCImport stmt = at(that).Import(gen.makeIdentFromIdentifiers(that.getIdentifiers()), false);
		    append(stmt);
		}
	}

    JCFieldAccess makeSelect(JCExpression s1, String s2) {
        return make().Select(s1, names.fromString(s2));
    }

    JCFieldAccess makeSelect(String s1, String s2) {
        return makeSelect(make().Ident(names.fromString(s1)), s2);
    }

    JCFieldAccess makeSelect(String s1, String s2, String... rest) {
        return makeSelect(makeSelect(s1, s2), rest);
    }

    JCFieldAccess makeSelect(JCFieldAccess s1, String[] rest) {
        JCFieldAccess acc = s1;

        for (String s : rest)
            acc = makeSelect(acc, s);

        return acc;
    }

    // Make a name from a list of strings, using only the first component.
    Name makeName(Iterable<String> components) {
        Iterator<String> iterator = components.iterator();
        String s = iterator.next();
        assert (!iterator.hasNext());
        return names.fromString(s);
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

    JCExpression makeIdentFromIdentifiers(Iterable<Tree.Identifier> components) {

        JCExpression type = null;
        for (Tree.Identifier component : components) {
            if (type == null)
                type = make().Ident(names.fromString(component.getText()));
            else
                type = makeSelect(type, component.getText());
        }

        return type;
    }

    JCExpression makeIdent(Iterable<String> components) {

        JCExpression type = null;
        for (String component : components) {
            if (type == null)
                type = make().Ident(names.fromString(component));
            else
                type = makeSelect(type, component);
        }

        return type;
    }

    JCExpression makeIdent(String... components) {

        JCExpression type = null;
        for (String component : components) {
            if (type == null)
                type = make().Ident(names.fromString(component));
            else
                type = makeSelect(type, component);
        }

        return type;
    }

    JCExpression makeIdent(String nameAsString) {
        return makeIdent(nameAsString.split("\\."));
    }

    JCExpression makeIdent(com.sun.tools.javac.code.Type type) {
        return make.QualIdent(type.tsym);
    }

    JCExpression makeInteger(long i) {
        // FIXME Using Integer only to make hashCode() work!!
        // We should introduce "small"!!
        return make.Literal(Integer.valueOf((int)i));
    }
    
    JCExpression makeBoolean(boolean b) {
        JCExpression expr;
        if (b) {
            expr = make.Literal(TypeTags.BOOLEAN, Integer.valueOf(1));
        } else {
            expr = make.Literal(TypeTags.BOOLEAN, Integer.valueOf(0));
        }
        return expr;
    }

    JCExpression makeBooleanTest(JCExpression expr, boolean val) {
        return make().Binary(JCTree.EQ, expr, makeBoolean(val));
    }
    
    boolean isBooleanTrue(Declaration decl) {
        return decl == modelLoader.getDeclaration("ceylon.language.$true", DeclarationType.VALUE);
    }
    
    boolean isBooleanFalse(Declaration decl) {
        return decl == modelLoader.getDeclaration("ceylon.language.$false", DeclarationType.VALUE);
    }
    
    // FIXME: port handleOverloadedToplevelClasses when I figure out what it
    // does

    private JCExpression getPackage(String fullname) {
        String shortName = Convert.shortName(fullname);
        String packagePart = Convert.packagePart(fullname);
        if (packagePart == null || packagePart.length() == 0)
            return make.Ident(names.fromString(shortName));
        else
            return make.Select(getPackage(packagePart), names.fromString(shortName));
    }

    public List<JCTree> transform(Tree.ImportList importList) {
        
        ImportListVisitor visitor = new ImportListVisitor(this);
        importList.visit(visitor);
        return (List<JCTree>) visitor.getResult().toList();
    }

    public JCTree transform(AttributeDeclaration decl) {
        AttributeDefinitionBuilder builder = globalGenAt(decl)
            .defineGlobal(
                    makeJavaType(actualType(decl)),
                    decl.getIdentifier().getText());

        // Add @Attribute (@Ceylon gets added by default)
        builder.classAnnotations(makeAtAttribute());

        builder.valueAnnotations(makeJavaTypeAnnotations(decl.getDeclarationModel(), actualType(decl)));
        
        builder.classIsFinal(true).classIsPublic(decl.getDeclarationModel().isShared());
        builder.getterIsStatic(true).getterIsPublic(decl.getDeclarationModel().isShared());
        builder.setterIsStatic(true).setterIsPublic(decl.getDeclarationModel().isShared());

        if (!decl.getDeclarationModel().isVariable()) {
            builder.immutable();
        }

        if (decl.getSpecifierOrInitializerExpression() != null) {
            builder.initialValue(expressionGen.transformExpression(
                    decl.getSpecifierOrInitializerExpression().getExpression()));
        }

        return builder.build();
    }

    public List<JCTree> transform(AttributeGetterDefinition decl) {
        AttributeDefinitionBuilder builder = globalGenAt(decl)
            .defineGlobal(
                    makeJavaType(actualType(decl)),
                    decl.getIdentifier().getText());

        // Add @Attribute (@Ceylon gets added by default)
        builder.classAnnotations(makeAtAttribute());

        builder.valueAnnotations(makeJavaTypeAnnotations(decl.getDeclarationModel(), actualType(decl)));

        boolean isMethodLocal = decl.getDeclarationModel().getContainer() instanceof com.redhat.ceylon.compiler.typechecker.model.Method;
        builder.classIsFinal(true).classIsPublic(decl.getDeclarationModel().isShared());
        builder.getterIsPublic(decl.getDeclarationModel().isShared()).getterIsStatic(!isMethodLocal);
        builder.setterIsPublic(decl.getDeclarationModel().isShared()).setterIsStatic(!isMethodLocal);

        if (!decl.getDeclarationModel().isVariable()) {
            builder.immutable();
        }

        JCBlock block = make().Block(0, statementGen.transformStmts(decl.getBlock().getStatements()));
        builder.getterBlock(block);

        if (isMethodLocal) {
            // Add a "foo foo = new foo();" at the decl site
            JCTree.JCIdent name = make().Ident(names.fromString(decl.getIdentifier().getText()));
            
            JCExpression initValue = at(decl).NewClass(null, null, name, List.<JCTree.JCExpression>nil(), null);
            List<JCAnnotation> annots2 = List.<JCAnnotation>nil();
    
            int modifiers = decl.getDeclarationModel().isShared() ? 0 : FINAL;
            JCTree.JCVariableDecl var = at(decl).VarDef(at(decl)
                    .Modifiers(modifiers, annots2), 
                    names.fromString(decl.getIdentifier().getText()), 
                    name, 
                    initValue);
            
            return List.of(builder.build(), var);
        } else {
            return List.of(builder.build());
        }
    }

    // FIXME: figure out what CeylonTree.ReflectedLiteral maps to

    JCExpression iteratorType(JCExpression type) {
        return make().TypeApply(makeIdent(syms.ceylonIteratorType), List.<JCExpression> of(type));
    }

    long counter = 0;

    String tempName() {
        String result = "$ceylontmp" + counter;
        counter++;
        return result;
    }

    String tempName(String s) {
        String result = "$ceylontmp" + s + counter;
        counter++;
        return result;
    }

    String aliasName(String s) {
        String result = "$" + s + "$" + counter;
        counter++;
        return result;
    }
    
    // A type is optional when it is a union of Nothing|Type...
    boolean isOptional(ProducedType type) {
        return (type.getDeclaration() instanceof UnionType && type.getDeclaration().getCaseTypes().size() > 1 && toPType(syms.ceylonNothingType).isSubtypeOf(type));
    }

    // FIXME: this is ugly and probably wrong
    boolean isSameType(Tree.Identifier ident, com.sun.tools.javac.code.Type type) {
        return ident.getText().equals(type.tsym.getQualifiedName());
    }

    public void setMap(LineMap map) {
        this.map = map;
    }

    public LineMap getMap() {
        return map;
    }

    String addVariableSubst(String origVarName, String substVarName) {
        return varNameSubst.put(origVarName, substVarName);
    }

    void removeVariableSubst(String origVarName, String prevSubst) {
        if (prevSubst != null) {
            varNameSubst.put(origVarName, prevSubst);
        } else {
            varNameSubst.remove(origVarName);
        }
    }
    
    /*
     * Checks a global map of variable name substitutions and returns
     * either the original name if none was found or the substitute.
     */
    String substitute(String varName) {
        if (varNameSubst.containsKey(varName)) {
            return varNameSubst.get(varName);            
        } else {
            return varName;
        }
    }

    public ProducedType toPType(com.sun.tools.javac.code.Type t) {
        return modelLoader.getType(t.tsym.getQualifiedName().toString(), null);
    }
    
    public boolean sameType(Type t1, ProducedType t2) {
        return toPType(t1).isExactly(t2);
    }
    
    private boolean isUnion(ProducedType type) {
        TypeDeclaration tdecl = type.getDeclaration();
        return (tdecl instanceof UnionType && tdecl.getCaseTypes().size() > 1);
    }
    
    // Determines if a type will be erased to java.lang.Object once converted to Java
    boolean willEraseToObject(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms.ceylonVoidType, type) || sameType(syms.ceylonObjectType, type)
                || sameType(syms.ceylonNothingType, type) || sameType(syms.ceylonEqualityType, type)
                || sameType(syms.ceylonIdentifiableObjectType, type)
                || type.getDeclaration() instanceof BottomType
                || isUnion(type));
    }
    
    // Determine if the type is a Ceylon String (which will be erased to a Java String)
    boolean willEraseToString(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms.ceylonStringType, type));
    }
    
    // Determine if the type is a Ceylon Boolean (which will be erased to a Java Boolean/boolean)
    boolean willEraseToBoolean(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms.ceylonBooleanType, type));
    }
    
    // Determine if the type is a Ceylon Integer (which will be erased to a Java Integer/int)
    boolean willEraseToInteger(ProducedType type) {
        type = simplifyType(type);
        return (sameType(syms.ceylonIntegerType, type));
    }

    static final int SATISFIES = 1 << 0;
    static final int EXTENDS = 1 << 1;
    static final int TYPE_PARAM = 1 << 2;
    static final int WANT_RAW_TYPE = 1 << 3;

    JCExpression makeJavaType(ProducedType producedType) {
        return makeJavaType(producedType, 0);
    }

    JCExpression makeJavaType(ProducedType type, int flags) {
        int satisfiesOrExtends = flags & (SATISFIES | EXTENDS | TYPE_PARAM);
        
        // ERASURE
        if (willEraseToObject(type)) {
            // For an erased type:
            // - Any of the Ceylon types Void, Object, Nothing, Equality,
            //   IdentifiableObject, and Bottom result in the Java type Object
            // For any other union type U|V (U nor V is Optional):
            // - The Ceylon type U|V results in the Java type Object
            if ((satisfiesOrExtends & SATISFIES) != 0) {
                return null;
            } else {
                return make.Type(syms.objectType);
            }
        } else if (willEraseToString(type)) {
            return make.Type(syms.stringType);
        } else if (willEraseToBoolean(type)) {
            if (satisfiesOrExtends != 0 || isOptional(type)) {
                return make.Type(syms.booleanObjectType);
            } else {
                return make.TypeIdent(TypeTags.BOOLEAN);
            }
        } else if (willEraseToInteger(type)) {
            if (satisfiesOrExtends != 0 || isOptional(type)) {
                return make.Type(syms.integerObjectType);
            } else {
                return make.TypeIdent(TypeTags.INT);
            }
        }
        
        JCExpression jt;
        type = simplifyType(type);
        TypeDeclaration tdecl = type.getDeclaration();
        java.util.List<ProducedType> tal = type.getTypeArgumentList();

        if (((flags & WANT_RAW_TYPE) == 0) && tal != null && !tal.isEmpty()) {
            // GENERIC TYPES

            ListBuffer<JCExpression> typeArgs = new ListBuffer<JCExpression>();

            int idx = 0;
            for (ProducedType ta : tal) {
                if (isUnion(ta)) {
                    // For any other union type U|V (U nor V is Optional):
                    // - The Ceylon type Foo<U|V> results in the raw Java type Foo.
                    // A bit ugly, but we need to escape from the loop and create a raw type, no generics
                    typeArgs = null;
                    break;
                }
                JCExpression jta;
                if (sameType(syms.ceylonVoidType, ta)) {
                    // For the root type Void:
                    if (satisfiesOrExtends != 0) {
                        // - The Ceylon type Foo<Void> appearing in an extends or satisfies
                        //   clause results in the Java raw type Foo<Object>
                        jta = make.Type(syms.objectType);
                    } else {
                        // - The Ceylon type Foo<Void> appearing anywhere else results in the Java type
                        // - Foo<Object> if Foo<T> is invariant in T
                        // - Foo<?> if Foo<T> is covariant in T, or
                        // - Foo<Object> if Foo<T> is contravariant in T
                        TypeParameter tp = tdecl.getTypeParameters().get(idx);
                        if (tp.isContravariant()) {
                            jta = make.Type(syms.objectType);
                        } else if (tp.isCovariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.UNBOUND), makeJavaType(ta));
                        } else {
                            jta = make.Type(syms.objectType);
                        }
                    }
                } else if (ta.getDeclaration() instanceof BottomType) {
                    // For the bottom type Bottom:
                    if (satisfiesOrExtends != 0) {
                        // - The Ceylon type Foo<Bottom> appearing in an extends or satisfies
                        //   clause results in the Java raw type Foo
                        // A bit ugly, but we need to escape from the loop and create a raw type, no generics
                        typeArgs = null;
                        break;
                    } else {
                        // - The Ceylon type Foo<Bottom> appearing anywhere else results in the Java type
                        // - raw Foo if Foo<T> is invariant in T,
                        // - raw Foo if Foo<T> is covariant in T, or
                        // - Foo<?> if Foo<T> is contravariant in T
                        TypeParameter tp = tdecl.getTypeParameters().get(idx);
                        if (tp.isContravariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.UNBOUND), makeJavaType(ta));
                        } else {
                            // A bit ugly, but we need to escape from the loop and create a raw type, no generics
                            typeArgs = null;
                            break;
                        }
                    }
                } else {
                    // For an ordinary class or interface type T:
                    if (satisfiesOrExtends != 0) {
                        // - The Ceylon type Foo<T> appearing in an extends or satisfies clause
                        //   results in the Java type Foo<T>
                        jta = makeJavaType(ta, satisfiesOrExtends);
                    } else {
                        // - The Ceylon type Foo<T> appearing anywhere else results in the Java type
                        // - Foo<T> if Foo is invariant in T,
                        // - Foo<? extends T> if Foo is covariant in T, or
                        // - Foo<? super T> if Foo is contravariant in T
                        TypeParameter tp = tdecl.getTypeParameters().get(idx);
                        if (tp.isContravariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.SUPER), makeJavaType(ta, TYPE_PARAM));
                        } else if (tp.isCovariant()) {
                            jta = make().Wildcard(make().TypeBoundKind(BoundKind.EXTENDS), makeJavaType(ta, TYPE_PARAM));
                        } else {
                            jta = makeJavaType(ta, TYPE_PARAM);
                        }
                    }
                }
                typeArgs.add(jta);
                idx++;
            }

            if (typeArgs != null && typeArgs.size() > 0) {
                jt = make().TypeApply(makeIdent(tdecl.getQualifiedNameString()), typeArgs.toList());
            } else {
                jt = makeIdent(tdecl.getQualifiedNameString());
            }
        } else {
            // For an ordinary class or interface type T:
            // - The Ceylon type T results in the Java type T
            jt = makeIdent(tdecl.getQualifiedNameString());
        }
        
        return jt;
    }

    List<JCTree.JCAnnotation> makeJavaTypeAnnotations(Value decl, ProducedType type) {
        return makeJavaTypeAnnotations(type, decl.isToplevel() || (decl.isClassOrInterfaceMember() && decl.isShared()));
    }

    List<JCTree.JCAnnotation> makeJavaTypeAnnotations(Getter decl, ProducedType type) {
        return makeJavaTypeAnnotations(type, decl.isToplevel() || (decl.isClassOrInterfaceMember() && decl.isShared()));
    }

    List<JCTree.JCAnnotation> makeJavaTypeAnnotations(ProducedType type, boolean required) {
        if (!required)
            return List.nil();
        // Add the original type to the annotations
        return makeAtType(type.getProducedTypeQualifiedName());
    }
    
    private ProducedType simplifyType(ProducedType type) {
        if (isOptional(type)) {
            // For an optional type T?:
            //  - The Ceylon type T? results in the Java type T
            // Nasty cast because we just so happen to know that nothingType is a Class
            type = type.minus((ClassOrInterface)(toPType(syms.ceylonNothingType).getDeclaration()));
        }
        
        TypeDeclaration tdecl = type.getDeclaration();
        if (tdecl instanceof UnionType && tdecl.getCaseTypes().size() == 1) {
            // Special case when the Union contains only a single CaseType
            // FIXME This is not correct! We might lose information about type arguments!
            type = tdecl.getCaseTypes().get(0);
        }
        
        return type;
    }
    
    ProducedType actualType(TypedDeclaration decl) {
        ProducedType t = decl.getType().getTypeModel();
        if (decl.getType() instanceof LocalModifier) {
            LocalModifier m = (LocalModifier)(decl.getType());
            t = m.getTypeModel();
        }
        return t;
    }

    List<JCAnnotation> makeAtOverride() {
        return List.<JCAnnotation> of(make().Annotation(makeIdent(syms.overrideType), List.<JCExpression> nil()));
    }

    private List<JCAnnotation> makeModelAnnotation(Type annotationType, List<JCExpression> annotationArgs) {
        if (disableModelAnnotations)
            return List.nil();
        return List.of(make().Annotation(makeIdent(annotationType), annotationArgs));
    }

    private List<JCAnnotation> makeModelAnnotation(Type annotationType) {
        return makeModelAnnotation(annotationType, List.<JCExpression>nil());
    }

    List<JCAnnotation> makeAtCeylon() {
        return makeModelAnnotation(syms.ceylonAtCeylonType);
    }

    List<JCAnnotation> makeAtName(String name) {
        return makeModelAnnotation(syms.ceylonAtNameType, List.<JCExpression>of(make().Literal(name)));
    }

    List<JCAnnotation> makeAtType(String name) {
        return makeModelAnnotation(syms.ceylonAtTypeInfoType, List.<JCExpression>of(make().Literal(name)));
    }

    List<JCAnnotation> makeAtAttribute() {
        return makeModelAnnotation(syms.ceylonAtAttributeType);
    }

    List<JCAnnotation> makeAtMethod() {
        return makeModelAnnotation(syms.ceylonAtMethodType);
    }

    List<JCAnnotation> makeAtObject() {
        return makeModelAnnotation(syms.ceylonAtObjectType);
    }

    boolean isInner(Declaration decl) {
        return decl.getContainer() instanceof Method;
    }

    private boolean isJavaKeyword(Name name) {
        return keywords.key(name) != com.sun.tools.javac.parser.Token.IDENTIFIER;
    }

    protected Name quoteName(String text) {
        Name name = names.fromString(text);

        if (isJavaKeyword(name)) {
            return names.fromString('$' + text);
        }

        return name;
    }
    
    protected boolean hasCompilerAnnotation(Tree.Declaration decl, String name){
        for(CompilerAnnotation annotation : decl.getCompilerAnnotations()){
            if(annotation.getIdentifier().getText().equals(name))
                return true;
        }
        return false;
    }
}
