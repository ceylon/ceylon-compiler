package com.redhat.ceylon.compiler.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.PROTECTED;
import static com.sun.tools.javac.code.Flags.PUBLIC;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.util.Util;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * Builder for Java Classes. The specific properties of the "framework" of the
 * class like its name, superclass, interfaces etc can be set directly.
 * There are also three freely definable "zones" where any code can be inserted:
 * the "defs" that go at the top of the class body, the "body" that goes at
 * the bottom and the "init" the goes inside the constructor in the middle.
 * (the reason for these 3 zones is mostly historical, 2 would do just as well)
 * 
 * @author Tako Schotanus
 */
public class ClassDefinitionBuilder {
    private final AbstractTransformer gen;
    
    private final String name;
    
    private long modifiers;
    private long constructorModifiers = -1;
    
    private JCExpression extending;
    private final ListBuffer<JCExpression> satisfies = ListBuffer.lb();
    private final ListBuffer<JCTypeParameter> typeParams = ListBuffer.lb();
    
    private final ListBuffer<JCAnnotation> annotations = ListBuffer.lb();
    
    private final ListBuffer<JCVariableDecl> params = ListBuffer.lb();
    
    private final ListBuffer<JCTree> defs = ListBuffer.lb();
    private final ListBuffer<JCTree> concreteInterfaceMemberDefs = ListBuffer.lb();
    private final ListBuffer<JCTree> body = ListBuffer.lb();
    private final ListBuffer<JCStatement> init = ListBuffer.lb();
    
    public static ClassDefinitionBuilder klass(AbstractTransformer gen, String name) {
        return new ClassDefinitionBuilder(gen, name);
    }
    
    private ClassDefinitionBuilder(AbstractTransformer gen, String name) {
        this.gen = gen;
        this.name = name;
        
        extending = getSuperclass(null);
        annotations(gen.makeAtCeylon());
    }

    public boolean existsParam(String name) {
        for (JCTree decl : params) {
            if (decl instanceof JCVariableDecl) {
                JCVariableDecl var = (JCVariableDecl)decl;
                if (var.name.toString().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<JCTree> build() {
        ListBuffer<JCTree> defs = ListBuffer.lb();
        appendDefinitionsTo(defs);
        JCTree.JCClassDecl klass = gen.make().ClassDef(
                gen.make().Modifiers(modifiers, annotations.toList()),
                gen.names().fromString(Util.quoteIfJavaKeyword(name)),
                typeParams.toList(),
                extending,
                satisfies.toList(),
                defs.toList());
        if(concreteInterfaceMemberDefs.isEmpty())
            return List.<JCTree>of(klass);
        JCTree.JCClassDecl concreteInterfaceKlass = gen.make().ClassDef(
                gen.make().Modifiers(PUBLIC | FINAL),
                gen.names().fromString(Util.getConcreteMemberInterfaceImplementationName(name)),
                // FIXME: type params probably have to get added to the method type params
                typeParams.toList(),
                (JCTree)null,
                List.<JCTree.JCExpression>nil(),
                concreteInterfaceMemberDefs.toList());
        return List.<JCTree>of(klass, concreteInterfaceKlass);
    }

    private void appendDefinitionsTo(ListBuffer<JCTree> defs) {
        defs.appendList(this.defs);
        if ((modifiers & INTERFACE) == 0) {
            defs.append(createConstructor());
        }
        defs.appendList(body);
    }

    private List<JCTree> appendConcreteInterfaceMembers(java.util.List<ProducedType> satisfies) {
        ListBuffer<JCTree> members = new ListBuffer<JCTree>();
        // FIXME: recurse in parent interfaces
        // FIXME: do not produce method if we override it
        for(ProducedType type : satisfies){
            TypeDeclaration decl = type.getDeclaration();
            for(Declaration member : decl.getMembers()){
                if(member instanceof Method && !member.isFormal()){
                    // this member has a body so we need to add a definition for it
                    MethodDefinitionBuilder methodBuilder = MethodDefinitionBuilder.method(gen, member.getName());
                    Method method = (Method) member;
                    ListBuffer<JCTree.JCExpression> params = ListBuffer.lb();
                    params.append(gen.makeIdent("this"));
                    for(Parameter param : method.getParameterLists().get(0).getParameters()){
                        methodBuilder.parameter(param);
                        params.append(gen.makeIdent(param.getName()));
                    }
                    
                    boolean isVoid = method.getType().getProducedTypeQualifiedName().equals("ceylon.language.Void");
                    JCMethodInvocation expr = gen.make().Apply(/*FIXME*/List.<JCTree.JCExpression>nil(), 
                            gen.makeIdent(Util.getConcreteMemberInterfaceImplementationName(decl.getName())+"."+method.getName()), 
                            params.toList());
                    JCTree.JCStatement body;
                    if (!isVoid) {
                        methodBuilder.resultType(method);
                        body = gen.make().Return(expr);
                    }else{
                        body = gen.make().Exec(expr);
                    }
                    methodBuilder.body(body);
                    methodBuilder.modifiers(PUBLIC);
                    members.add(methodBuilder.build());
                }
            }
        }
        return members.toList();
    }

    private JCExpression getSuperclass(ProducedType extendedType) {
        JCExpression superclass;
        if (extendedType != null) {
            superclass = gen.makeJavaType(extendedType, CeylonTransformer.EXTENDS);
            // simplify if we can
// FIXME superclass.sym can be null
//            if (superclass instanceof JCTree.JCFieldAccess 
//            && ((JCTree.JCFieldAccess)superclass).sym.type == gen.syms.objectType) {
//                superclass = null;
//            }
        } else {
            if ((modifiers & INTERFACE) != 0) {
                // The VM insists that interfaces have java.lang.Object as their superclass
                superclass = gen.makeIdent(gen.syms().objectType);
            } else {
                superclass = null;
            }
        }
        return superclass;
    }

    private List<JCExpression> transformSatisfiedTypes(java.util.List<ProducedType> list) {
        if (list == null) {
            return List.nil();
        }

        ListBuffer<JCExpression> satisfies = new ListBuffer<JCExpression>();
        for (ProducedType t : list) {
            JCExpression jt = gen.makeJavaType(t, CeylonTransformer.SATISFIES);
            if (jt != null) {
                satisfies.append(jt);
            }
        }
        return satisfies.toList();
    }

    private JCMethodDecl createConstructor() {
        long mods = constructorModifiers;
        if (mods == -1) {
            // The modifiers were never explicitly set
            // so we try to come up with some good defaults
            mods = modifiers & (PUBLIC | PRIVATE | PROTECTED);
        }
        return MethodDefinitionBuilder
            .constructor(gen)
            .modifiers(mods)
            .parameters(params.toList())
            .body(init.toList())
            .build();
    }
    
    /*
     * Builder methods - they transform the inner state before doing the final construction
     */
    
    public ClassDefinitionBuilder modifiers(long... modifiers) {
        long mods = 0;
        for (long mod : modifiers) {
            mods |= mod;
        }
        this.modifiers = mods;
        return this;
    }

    public ClassDefinitionBuilder constructorModifiers(long... constructorModifiers) {
        long mods = 0;
        for (long mod : constructorModifiers) {
            mods |= mod;
        }
        this.constructorModifiers = mods;
        return this;
    }

    public ClassDefinitionBuilder typeParameter(String name, java.util.List<ProducedType> types) {
        ListBuffer<JCExpression> bounds = new ListBuffer<JCExpression>();
        for (ProducedType t : types) {
            if (!gen.willEraseToObject(t)) {
                bounds.append(gen.makeJavaType(t));
            }
        }
        typeParams.append(gen.make().TypeParameter(gen.names().fromString(name), bounds.toList()));
        return this;
    }

    public ClassDefinitionBuilder typeParameter(Tree.TypeParameterDeclaration param) {
        gen.at(param);
        String name = param.getIdentifier().getText();
        return typeParameter(name, param.getDeclarationModel().getSatisfiedTypes());
    }

    public ClassDefinitionBuilder extending(ProducedType extendingType) {
        this.extending = getSuperclass(extendingType);
        return this;
    }

    public ClassDefinitionBuilder extending(Tree.ExtendedType extendedType) {
        if (extendedType.getInvocationExpression().getPositionalArgumentList() != null) {
            List<JCExpression> args = List.<JCExpression> nil();

            int index = 0;
            for (Tree.PositionalArgument arg : extendedType.getInvocationExpression().getPositionalArgumentList().getPositionalArguments()) {
                if (index == 0 
                        && extendedType.getInvocationExpression().getTypeModel().isExactly(gen.typeFact().getExceptionType())) {
                    // unbox message argument to super for direct subclasses of Exception
                    args = args.append(gen.unboxType(gen.expressionGen().transformArg(arg), gen.typeFact().getStringType()));
                } else {
                    args = args.append(gen.expressionGen().transformArg(arg));
                }
                index += 1;
            }

            init(gen.at(extendedType).Exec(gen.make().Apply(List.<JCExpression> nil(), gen.make().Ident(gen.names()._super), args)));
        }
        return extending(extendedType.getType().getTypeModel());
    }
    
    public ClassDefinitionBuilder satisfies(java.util.List<ProducedType> satisfies) {
        this.satisfies.addAll(transformSatisfiedTypes(satisfies));
        this.defs.addAll(appendConcreteInterfaceMembers(satisfies));
        return this;
    }

    public ClassDefinitionBuilder annotations(List<JCTree.JCAnnotation> annotations) {
        this.annotations.appendList(annotations);
        return this;
    }

    private ClassDefinitionBuilder parameter(String name, ProducedType paramType, Parameter parameter) {
        // Create a parameter for the constructor
        JCExpression type = gen.makeJavaType(paramType, gen.isGenericsImplementation(parameter) ? AbstractTransformer.NO_ERASURE_TO_PRIMITIVE : 0);
        List<JCAnnotation> annots = gen.makeAtName(name);
        annots = annots.appendList(gen.makeJavaTypeAnnotations(paramType, true));
        JCVariableDecl var = gen.make().VarDef(gen.make().Modifiers(0, annots), gen.names().fromString(name), type, null);
        params.append(var);
        
        // Check if the parameter is used outside of the initializer
        if (parameter.isCaptured()) {
            // If so we create a field for it initializing it with the parameter's value
            JCVariableDecl localVar = gen.make().VarDef(gen.make().Modifiers(FINAL | PRIVATE), gen.names().fromString(name), type , null);
            defs.append(localVar);
            init.append(gen.make().Exec(gen.make().Assign(gen.makeSelect("this", localVar.getName().toString()), gen.make().Ident(var.getName()))));
        }
        
        return this;
    }
    
    public ClassDefinitionBuilder parameter(Tree.Parameter param) {
        gen.at(param);
        String name = param.getIdentifier().getText();
        return parameter(name, param.getType().getTypeModel(), param.getDeclarationModel());
    }
    
    public ClassDefinitionBuilder defs(JCTree statement) {
        this.defs.append(statement);
        return this;
    }
    
    public ClassDefinitionBuilder defs(List<JCTree> defs) {
        this.defs.appendList(defs);
        return this;
    }
    
    public ClassDefinitionBuilder body(JCTree statement) {
        this.body.append(statement);
        return this;
    }
    
    public ClassDefinitionBuilder body(List<JCTree> body) {
        this.body.appendList(body);
        return this;
    }
    
    public ClassDefinitionBuilder init(JCStatement statement) {
        this.init.append(statement);
        return this;
    }
    
    public ClassDefinitionBuilder init(List<JCStatement> init) {
        this.init.appendList(init);
        return this;
    }

    public ClassDefinitionBuilder concreteInterfaceMemberDefs(
            JCMethodDecl concreteInterfaceMember) {
        this.concreteInterfaceMemberDefs.append(concreteInterfaceMember);
        return this;
    }

}
