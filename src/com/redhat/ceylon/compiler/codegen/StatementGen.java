package com.redhat.ceylon.compiler.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

public class StatementGen extends GenPart {

	// Used to hold the name of the variable associated with the fail-block if the innermost for-loop
	// Is null if we're currently in a while-loop or not in any loop at all
    private Name currentForFailVariable = null;

	public StatementGen(Gen2 gen) {
        super(gen);
    }

    class StatementVisitor extends Visitor implements NaturalVisitor {
        final ListBuffer<JCStatement> stmts;
        final Tree.ClassOrInterface cdecl;

        StatementVisitor(Tree.ClassOrInterface cdecl, ListBuffer<JCStatement> stmts) {
            this.stmts = stmts;
            this.cdecl = cdecl;
        }

        public ListBuffer<JCStatement> stmts() {
            return stmts;
        }

        public void visit(Tree.InvocationExpression expr) {
            append(at(expr).Exec(gen.expressionGen.convert(expr)));
        }

        public void visit(Tree.Return ret) {
            append(convert(ret));
        }

        public void visit(Tree.IfStatement stat) {
            append(convert(cdecl, stat));
        }

        public void visit(Tree.WhileStatement stat) {
            append(convert(cdecl, stat));
        }

        public void visit(Tree.DoWhileStatement stat) {
            append(convert(cdecl, stat));
        }

        public void visit(Tree.ForStatement stat) {
            append(convert(cdecl, stat));
        }

        public void visit(Tree.Break stat) {
            append(convert(cdecl, stat));
        }

        public void visit(Tree.AttributeDeclaration decl) {
        	append(gen.classGen.convert(cdecl, decl));
        }

        public void visit(Tree.SpecifierStatement op) {
            append(convert(op));
        }

        // FIXME: not sure why we don't have just an entry for Tree.Term here...
        public void visit(Tree.OperatorExpression op) {
            append(at(op).Exec(gen.expressionGen.convertExpression(op)));
        }

        public void visit(Tree.Expression tree) {
            append(at(tree).Exec(gen.expressionGen.convertExpression(tree)));
        }

        public void visit(Tree.MethodDefinition decl) {
            final ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
            gen.classGen.methodClass(cdecl, decl, defs, false);
            for (JCTree def : defs.toList()) {
                JCClassDecl innerDecl = (JCClassDecl) def;
                stmts.append(innerDecl);
                JCExpression id = make().Ident(innerDecl.name);
                stmts.append(at(decl).VarDef(make().Modifiers(FINAL), names().fromString(decl.getIdentifier().getText()), id, at(decl).NewClass(null, null, id, List.<JCExpression> nil(), null)));
            }
        }

        // FIXME: I think those should just go in convertExpression no?
        public void visit(Tree.PostfixOperatorExpression expr) {
            append(at(expr).Exec(gen.expressionGen.convert(expr)));
        }

        public void visit(Tree.PrefixOperatorExpression expr) {
            append(at(expr).Exec(gen.expressionGen.convert(expr)));
        }

        public void visit(Tree.ExpressionStatement tree) {
            append(at(tree).Exec(gen.expressionGen.convertExpression(tree.getExpression())));
        }
        
        private void append(JCStatement stmt) {
        	stmts.append(stmt);
        }
        
        private void append(List<JCStatement> list) {
            for (JCStatement stmt : list) {
                stmts.append(stmt);
            }
        }
    }

    public JCBlock convert(Tree.ClassOrInterface cdecl, Tree.Block block) {
        return block == null ? null : at(block).Block(0, convertStmts(cdecl, block.getStatements()));
    }

    private List<JCStatement> convertStmts(Tree.ClassOrInterface cdecl, java.util.List<Tree.Statement> list) {
        final ListBuffer<JCStatement> buf = new ListBuffer<JCStatement>();

        StatementVisitor v = new StatementVisitor(cdecl, buf);

        for (Tree.Statement stmt : list)
            stmt.visit(v);

        return buf.toList();
    }

    private List<JCStatement> convert(Tree.ClassOrInterface cdecl, Tree.IfStatement stmt) {
        JCBlock thenPart = convert(cdecl, stmt.getIfClause().getBlock());
        JCBlock elsePart = stmt.getElseClause() != null ? convert(cdecl, stmt.getElseClause().getBlock()) : null;
        return convertCondition(stmt.getIfClause().getCondition(), JCTree.IF, thenPart, elsePart);
    }

    private List<JCStatement> convert(Tree.ClassOrInterface cdecl, Tree.WhileStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        currentForFailVariable = null;
        
        JCBlock thenPart = convert(cdecl, stmt.getWhileClause().getBlock());
        List<JCStatement> res = convertCondition(stmt.getWhileClause().getCondition(), JCTree.WHILELOOP, thenPart, null);
        
        currentForFailVariable = tempForFailVariable;
        
        return res;
    }

    private List<JCStatement> convert(Tree.ClassOrInterface cdecl, Tree.DoWhileStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        currentForFailVariable = null;
        
        JCBlock thenPart = convert(cdecl, stmt.getDoClause().getBlock());
        List<JCStatement> res = convertCondition(stmt.getDoClause().getCondition(), JCTree.DOLOOP, thenPart, null);
        
        currentForFailVariable = tempForFailVariable;
        
        return res;
    }

    private List<JCStatement> convertCondition(Tree.Condition cond, int tag, JCBlock thenPart, JCBlock elsePart) {

        if (cond instanceof Tree.ExistsCondition) {
            Tree.ExistsCondition exists = (Tree.ExistsCondition) cond;
            Tree.Identifier name = exists.getVariable().getIdentifier();

            // We're going to give this variable an initializer in order to be
            // able to determine its type, but the initializer will be deleted
            // in LowerCeylon. Do not change the string "Deleted".
            Name tmp = names().fromString(tempName("DeletedExists"));
            Name tmp2 = names().fromString(name.getText());

            JCExpression type;
            if (exists.getVariable().getType() == null) {
                type = makeIdent(syms().ceylonAnyType);
            } else {
                type = gen.variableType(exists.getVariable().getType(), null);
            }

            JCExpression expr;
            if (exists.getExpression() == null) {
                expr = at(cond).Ident(tmp2);
            } else {
                expr = gen.expressionGen.convertExpression(exists.getExpression());
            }

            expr = at(cond).Apply(null, at(cond).Select(expr, names().fromString("$internalErasedExists")), List.<JCExpression> nil());

            // This temp variable really should be SYNTHETIC, but then javac
            // won't let you use it...
            JCVariableDecl decl = at(cond).VarDef(make().Modifiers(0), tmp, type, exists.getVariable().getType() == null ? expr : null);
            JCVariableDecl decl2 = at(cond).VarDef(make().Modifiers(FINAL), tmp2, type, at(cond).Ident(tmp));
            thenPart = at(cond).Block(0, List.<JCStatement> of(decl2, thenPart));

            JCExpression assignment = at(cond).Assign(make().Ident(decl.name), expr);

            JCTree.JCBinary test = at(cond).Binary(JCTree.NE, assignment, make().Literal(TypeTags.BOT, null));

            JCStatement cond1;
            switch (tag) {
            case JCTree.IF:
                cond1 = at(cond).If(test, thenPart, elsePart);
                break;
            case JCTree.WHILELOOP:
                assert elsePart == null;
                cond1 = at(cond).WhileLoop(test, thenPart);
                break;
            case JCTree.DOLOOP:
                assert elsePart == null;
                cond1 = at(cond).DoLoop(thenPart, test);
                break;
            default:
                throw new RuntimeException();
            }
            return List.<JCStatement> of(decl, cond1);
        } else if (cond instanceof Tree.IsCondition) {
            // FIXME: This code has a lot in common with the ExistsExpression
            // above, but it has a niggling few things that are different.
            // It needs to be refactored.

            Tree.IsCondition isExpr = (Tree.IsCondition) cond;
            Tree.Identifier name = isExpr.getVariable().getIdentifier();
            JCExpression type = gen.variableType(isExpr.getType(), null);

            // We're going to give this variable an initializer in order to be
            // able to determine its type, but the initializer will be deleted
            // in LowerCeylon. Do not change the string "Deleted".
            Name tmp = names().fromString(tempName("DeletedIs"));
            Name tmp2 = names().fromString(name.getText());

            JCExpression expr;
            if (isExpr.getExpression() == null) {
                expr = convert(name);
            } else {
                expr = gen.expressionGen.convertExpression(isExpr.getExpression());
            }

            // This temp variable really should be SYNTHETIC, but then javac
            // won't let you use it...
            JCVariableDecl decl = at(cond).VarDef(make().Modifiers(0), tmp, makeIdent(syms().ceylonAnyType), expr);
            JCVariableDecl decl2 = at(cond).VarDef(make().Modifiers(FINAL), tmp2, type, at(cond).TypeCast(type, at(cond).Ident(tmp)));
            thenPart = at(cond).Block(0, List.<JCStatement> of(decl2, thenPart));

            JCExpression assignment = at(cond).Assign(make().Ident(decl.name), expr);

            JCExpression test = at(cond).TypeTest(assignment, type);

            JCStatement cond1;
            switch (tag) {
            case JCTree.IF:
                cond1 = at(cond).If(test, thenPart, elsePart);
                break;
            case JCTree.WHILELOOP:
                assert elsePart == null;
                cond1 = at(cond).WhileLoop(test, thenPart);
                break;
            case JCTree.DOLOOP:
                assert elsePart == null;
                cond1 = at(cond).DoLoop(thenPart, test);
                break;
            default:
                throw new RuntimeException();
            }
            return List.<JCStatement> of(decl, cond1);
        } else if (cond instanceof Tree.BooleanCondition) {
            Tree.BooleanCondition booleanCondition = (Tree.BooleanCondition) cond;
            JCExpression test = gen.expressionGen.convertExpression(booleanCondition.getExpression());
            JCExpression trueValue = at(cond).Apply(List.<JCTree.JCExpression>nil(), 
                    makeIdent("ceylon", "language", "$true", "getTrue"), List.<JCTree.JCExpression>nil());
            test = at(cond).Binary(JCTree.EQ, test, trueValue);
            
            JCStatement result;
            switch (tag) {
            case JCTree.IF:
                result = at(cond).If(test, thenPart, elsePart);
                break;
            case JCTree.WHILELOOP:
                assert elsePart == null;
                result = at(cond).WhileLoop(test, thenPart);
                break;
            case JCTree.DOLOOP:
                assert elsePart == null;
                result = at(cond).DoLoop(thenPart, test);
                break;
            default:
                throw new RuntimeException();
            }

            return List.<JCStatement> of(result);
        } else {
            throw new RuntimeException("Not implemented: " + cond.getNodeType());
        }
    }

    private JCStatement convert(Tree.ClassOrInterface cdecl, Tree.ForStatement stmt) {
        class ForVisitor extends Visitor {
            Tree.Variable variable = null;

            public void visit(Tree.ValueIterator valueIterator) {
                assert variable == null;
                variable = valueIterator.getVariable();
            }

            public void visit(Tree.KeyValueIterator keyValueIterator) {
                assert variable == null;
                // FIXME: implement this
                throw new RuntimeException("Not implemented: " + keyValueIterator.getNodeType());
            }
        }

        Name tempForFailVariable = currentForFailVariable;
        
        List<JCStatement> outer = List.<JCStatement> nil();
        if (stmt.getFailClause() != null) {
        	// boolean $ceylontmpX = true;
            JCVariableDecl failtest_decl = at(stmt).VarDef(make().Modifiers(0), names().fromString(tempName()), makeIdent("boolean"), makeIdent("true"));
            outer = outer.append(failtest_decl);
            
        	currentForFailVariable = failtest_decl.getName();
        } else {
        	currentForFailVariable = null;
        }

        ForVisitor visitor = new ForVisitor();
        stmt.getForClause().getForIterator().visit(visitor);
        JCExpression item_type = gen.variableType(visitor.variable.getType(), null);

        // ceylon.language.Iterator<T> $ceylontmpX = ITERABLE.iterator();
        JCExpression containment = gen.expressionGen.convertExpression(stmt.getForClause().getForIterator().getSpecifierExpression().getExpression());
        JCVariableDecl iter_decl = at(stmt).VarDef(make().Modifiers(0), names().fromString(tempName()), gen.iteratorType(item_type), at(stmt).Apply(null, at(stmt).Select(containment, names().fromString("iterator")), List.<JCExpression> nil()));
        outer = outer.append(iter_decl);
        JCIdent iter_id = at(stmt).Ident(iter_decl.getName());

        // ceylon.language.Optional<T> $ceylontmpY = $ceylontmpX.head();
        JCVariableDecl optional_item_decl = at(stmt).VarDef(make().Modifiers(FINAL), names().fromString(tempName()), gen.optionalType(item_type), at(stmt).Apply(null, at(stmt).Select(iter_id, names().fromString("head")), List.<JCExpression> nil()));
        List<JCStatement> while_loop = List.<JCStatement> of(optional_item_decl);
        JCIdent optional_item_id = at(stmt).Ident(optional_item_decl.getName());

        // T n = $ceylontmpY.t;
        JCVariableDecl item_decl = at(stmt).VarDef(make().Modifiers(0), names().fromString(visitor.variable.getIdentifier().getText()), item_type, at(stmt).Apply(null, at(stmt).Select(optional_item_id, names().fromString("$internalErasedExists")), List.<JCExpression> nil()));
        List<JCStatement> inner = List.<JCStatement> of(item_decl);

        // The user-supplied contents of the loop
        inner = inner.appendList(convertStmts(cdecl, stmt.getForClause().getBlock().getStatements()));

        // if ($ceylontmpY != null) ... else break;
        JCStatement test = at(stmt).If(at(stmt).Binary(JCTree.NE, optional_item_id, make().Literal(TypeTags.BOT, null)), at(stmt).Block(0, inner), at(stmt).Block(0, List.<JCStatement> of(at(stmt).Break(null))));
        while_loop = while_loop.append(test);

        // $ceylontmpX = $ceylontmpX.tail();
        JCExpression next = at(stmt).Assign(iter_id, at(stmt).Apply(null, at(stmt).Select(iter_id, names().fromString("tail")), List.<JCExpression> nil()));
        while_loop = while_loop.append(at(stmt).Exec(next));

        // while (True)...
        outer = outer.append(at(stmt).WhileLoop(at(stmt).Literal(TypeTags.BOOLEAN, 1), at(stmt).Block(0, while_loop)));

        if (stmt.getFailClause() != null) {
            // The user-supplied contents of fail block
        	List<JCStatement> failblock = convertStmts(cdecl, stmt.getFailClause().getBlock().getStatements());
        	
        	// if ($ceylontmpX) ...
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            outer = outer.append(at(stmt).If(failtest_id, at(stmt).Block(0, failblock), null));
        }
        currentForFailVariable = tempForFailVariable;

        return at(stmt).Block(0, outer);
    }

    private List<JCStatement> convert(Tree.ClassOrInterface cdecl, Tree.Break stmt) {
    	// break;
    	JCStatement brk = at(stmt).Break(null);
    	
    	if (currentForFailVariable != null) {
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            List<JCStatement> list = List.<JCStatement> of(at(stmt).Exec(at(stmt).Assign(failtest_id, makeIdent("false"))));
    		list = list.append(brk);
            return list;
    	} else {
    		return List.<JCStatement> of(brk);
    	}
    }

    private JCStatement convert(Tree.Return ret) {
        Tree.Expression expr = ret.getExpression();
        JCExpression returnExpr = expr != null ? gen.expressionGen.convertExpression(expr) : null;
        return at(ret).Return(returnExpr);
    }

    private JCIdent convert(Tree.Identifier identifier) {
        return at(identifier).Ident(names().fromString(identifier.getText()));
    }

    private JCStatement convert(Tree.SpecifierStatement op) {
        JCExpression rhs = gen.expressionGen.convertExpression(op.getSpecifierExpression().getExpression());
        return at(op).Exec(make().Assign(gen.expressionGen.convertExpression(op.getBaseMemberExpression()), rhs));
    }

}
