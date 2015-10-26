package com.redhat.ceylon.compiler.java.codegen;

import static com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil.isNeverSatisfied;
import static com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil.isAlwaysSatisfied;
import static com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil.isAtLeastOne;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.name;

import java.util.List;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class ThrowVisitor extends Visitor {

    public boolean getDefinitelyReturnsViaThrow() {
        return (definitelyReturns & 2) != 0;
    }
    
    void definitelyReturns() {
        definitelyReturns |= 1;
    }
    
    void definitelyThrows() {
        definitelyReturns |= 2;
    }
    
    private int definitelyReturns = 0;
    private boolean definitelyBreaksOrContinues = false;
    private boolean canReturn = false;
    private boolean canExecute = true;
    private Boolean possiblyBreaks = null;
    private boolean unreachabilityReported = false;
    
    int beginDefiniteReturnScope() {
        int dr = definitelyReturns;
        definitelyReturns = 0;
        return dr;
    }
        
    int beginIndefiniteReturnScope() {
        return definitelyReturns;
    }
    
    void endDefiniteReturnScope(int dr) {
        definitelyReturns = dr;
        unreachabilityReported = false;
    }
    
    boolean beginReturnScope(boolean cr) {
        boolean ocr = canReturn;
        canReturn = cr;
        return ocr;
    }
    
    void endReturnScope(boolean cr) {
        canReturn = cr;
    }
    
    boolean beginStatementScope(boolean ce) {
        boolean oce = canExecute;
        canExecute = ce;
        return oce;
    }
    
    void endStatementScope(boolean ce) {
        canExecute = ce;
    }
    
    Boolean beginLoop() {
        Boolean efl = possiblyBreaks;
        possiblyBreaks = false;
        return efl;
    }
    
    void endLoop(Boolean efl) {
        possiblyBreaks = efl;
    }
    
    boolean beginLoopScope() {
        boolean obc = definitelyBreaksOrContinues;
        definitelyBreaksOrContinues = false;
        return obc;
    }
    
    void exitLoopScope() {
        definitelyBreaksOrContinues = true;
    }
    
    void endLoopScope(boolean bc) {
        definitelyBreaksOrContinues = bc;
    }
    
    void exitLoop() {
        possiblyBreaks = true;
    }
    
    boolean inLoop() {
        return possiblyBreaks!=null;
    }
    
    Boolean pauseLoop() {
        Boolean efl = possiblyBreaks;
        possiblyBreaks = null;
        return efl;
    }
    
    void unpauseLoop(Boolean efl) {
        possiblyBreaks = efl;
    }
        
    boolean pauseLoopScope() {
        Boolean bc = definitelyBreaksOrContinues;
        definitelyBreaksOrContinues = false;
        return bc;
    }
    
    void unpauseLoopScope(boolean bc) {
        definitelyBreaksOrContinues = bc;
    }
        
    @Override
    public void visit(Tree.AttributeGetterDefinition that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        checkDefiniteReturn(that, name(that.getIdentifier()));
        endDefiniteReturnScope(d);
        endReturnScope(c);
    }

    @Override
    public void visit(Tree.AttributeArgument that) {
        if (that.getSpecifierExpression()==null) {
            boolean c = beginReturnScope(true);
            int d = beginDefiniteReturnScope();
            super.visit(that);
            checkDefiniteReturn(that, name(that.getIdentifier()));
            endDefiniteReturnScope(d);
            endReturnScope(c);
        }
        else {
            super.visit(that);
        }
    }

    private void checkDefiniteReturn(Node that, String name) {
        if (definitelyReturns != 0) {
            if (name==null) {
                name = "anonymous function";
            }
            else {
                name = "'" + name + "'";
            }
            that.addError("does not definitely return: " + 
                    name + " has branches which do not end in a return statement");
        }
    }

//    @Override
//    public void visit(Tree.MethodDeclaration that) {
//        if (that.getSpecifierExpression()!=null) {
//            checkExecutableStatementAllowed(that.getSpecifierExpression());
//            super.visit(that);
//        }
//    }
//    
    @Override
    public void visit(Tree.MethodDefinition that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        if (!that.getDeclarationModel().isDeclaredVoid()) {
            checkDefiniteReturn(that, name(that.getIdentifier()));
        }
        endDefiniteReturnScope(d);
        endReturnScope(c);
    }
    
    @Override
    public void visit(Tree.MethodArgument that) {
        if (that.getSpecifierExpression()==null) {
            boolean c = beginReturnScope(true);
            int d = beginDefiniteReturnScope();
            super.visit(that);
            if (!that.getDeclarationModel().isDeclaredVoid()) {
                checkDefiniteReturn(that, name(that.getIdentifier()));
            }
            endDefiniteReturnScope(d);
            endReturnScope(c);
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.FunctionArgument that) {
        if (that.getExpression()==null) {
            Boolean efl = pauseLoop();
            boolean bc = pauseLoopScope();
            boolean c = beginReturnScope(true);
            int d = beginDefiniteReturnScope();
            super.visit(that);
            if (!(that.getDeclarationModel().isDeclaredVoid())) {
                checkDefiniteReturn(that, null);
            }
            endDefiniteReturnScope(d);
            endReturnScope(c);
            unpauseLoop(efl);
            unpauseLoopScope(bc);
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.AttributeDeclaration that) {
        if (!that.getDeclarationModel().isParameter() &&
            that.getSpecifierOrInitializerExpression()!=null &&
                !(that.getSpecifierOrInitializerExpression() instanceof Tree.LazySpecifierExpression)) {
            checkExecutableStatementAllowed(that.getSpecifierOrInitializerExpression());
            super.visit(that);
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.AttributeSetterDefinition that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.Constructor that) {
        checkReachable(that);
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.Enumerated that) {
        checkReachable(that);
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.ClassDefinition that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.InterfaceDefinition that) {
        boolean c = beginReturnScope(false);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.ObjectDefinition that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.ObjectArgument that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.ObjectExpression that) {
        boolean c = beginReturnScope(true);
        int d = beginDefiniteReturnScope();
        super.visit(that);
        endReturnScope(c);
        endDefiniteReturnScope(d);
    }
    
    @Override
    public void visit(Tree.Body that) {
        boolean e = beginStatementScope(!(that instanceof Tree.InterfaceBody));
        super.visit(that);
        endStatementScope(e);
    }
    
    @Override
    public void visit(Tree.LazySpecifierExpression that) {
        boolean e = beginStatementScope(true);
        super.visit(that);
        endStatementScope(e);
    }
    
    @Override
    public void visit(Tree.Block that) {
        super.visit(that);
    }
    
    @Override
    public void visit(Tree.Declaration that) {
    }
    
    @Override
    public void visit(Tree.TypedArgument that) {
        Boolean efl = pauseLoop();
        boolean bc = pauseLoopScope();
        super.visit(that);
        unpauseLoop(efl);
        unpauseLoopScope(bc);
    }
    
    @Override
    public void visit(Tree.ExecutableStatement that) {
        boolean executable = true;
        //shortcut refinement statements with => aren't really "executable"
        if (that instanceof Tree.SpecifierStatement) {
            Tree.SpecifierStatement s = (Tree.SpecifierStatement) that;
            executable = !(s.getSpecifierExpression() instanceof Tree.LazySpecifierExpression) ||
                        !s.getRefinement();
        }
        if (executable) {
            checkExecutableStatementAllowed(that);
        }
        super.visit(that);
    }

    private void checkExecutableStatementAllowed(Node that) {
        if (!canExecute) {
            that.addError("statement or initializer may not occur directly in interface body");
        }
    }
    
    @Override
    public void visit(Tree.Return that) {
        if (!canReturn) {
            that.addError("nothing to return from");
        }
        super.visit(that);
        definitelyReturns();
        exitLoopScope();
    }

    @Override
    public void visit(Tree.Throw that) {
        super.visit(that);
        definitelyThrows();
        exitLoopScope();
    }
    
    @Override
    public void visit(Tree.Break that) {
        if (!inLoop()) {
            that.addError("no surrounding loop to break");
        }
        super.visit(that);
        exitLoop();
        exitLoopScope();
    }

    @Override
    public void visit(Tree.Continue that) {
        if (!inLoop()) {
            that.addError("no surrounding loop to continue");
        }
        super.visit(that);
        exitLoopScope();
    }
    
    @Override
    public void visit(Tree.Statement that) {
        if (!(that instanceof Tree.Variable)) {
            checkReachable(that);
        }
        super.visit(that);
    }

    private void checkReachable(Tree.Statement that) {
        if (definitelyReturns != 0 || definitelyBreaksOrContinues) {
            if (!unreachabilityReported) {
                that.addError("unreachable code");
                unreachabilityReported = true;
            }
        }
    }
    
    @Override
    public void visit(Tree.WhileStatement that) {
        checkExecutableStatementAllowed(that);
        checkReachable(that);
        int d = beginIndefiniteReturnScope();
        Boolean b = beginLoop();
        boolean bc = beginLoopScope();
        that.getWhileClause().visit(this);
        boolean definitelyDoesNotBreakFromWhile = !possiblyBreaks;
        int definitelyReturnsFromWhile = definitelyReturns;
        endDefiniteReturnScope(d);
        endLoop(b);
        endLoopScope(bc);
        if (isAlwaysSatisfied(that.getWhileClause().getConditionList())) {
            if (definitelyDoesNotBreakFromWhile 
                    || definitelyReturnsFromWhile != 0) { //superfluous?
                definitelyReturns = definitelyReturnsFromWhile;
            }
        }
    }
    
    @Override
    public void visit(Tree.ForStatement that) {
        checkExecutableStatementAllowed(that);
        checkReachable(that);
        int d = beginIndefiniteReturnScope();
        
        Boolean b = beginLoop();
        boolean bc = beginLoopScope();
        boolean atLeastOneIteration = false;
        Tree.ForClause forClause = that.getForClause();
        if (forClause!=null) {
            forClause.visit(this);
            atLeastOneIteration = isAtLeastOne(forClause);
        }
        boolean definitelyDoesNotBreakFromFor = !possiblyBreaks;
        int definitelyReturnsFromFor =  
                atLeastOneIteration && definitelyDoesNotBreakFromFor ? definitelyReturns : 0;
        that.setExits(possiblyBreaks);
        endLoop(b);
        endLoopScope(bc);
        
        definitelyReturns = d | definitelyReturnsFromFor;
        
        int definitelyReturnsFromElse;
        Tree.ElseClause elseClause = that.getElseClause();
        if (elseClause!=null) {
            elseClause.visit(this);
            definitelyReturnsFromElse =  
                    definitelyDoesNotBreakFromFor ? definitelyReturns : 0;
        }
        else {
            definitelyReturnsFromElse = 0;
        }
        endLoopScope(bc);
        
        definitelyReturns = d | 
                definitelyReturnsFromFor | 
                definitelyReturnsFromElse;
    }

    @Override
    public void visit(Tree.IfStatement that) {
        checkExecutableStatementAllowed(that);
        checkReachable(that);
        int d = beginIndefiniteReturnScope();
        Boolean e = possiblyBreaks;
        boolean bc = definitelyBreaksOrContinues;
        
        Tree.IfClause ifClause = that.getIfClause();
        if (ifClause!=null) {
            ifClause.visit(this);
        }
        int definitelyReturnsFromIf = definitelyReturns;
        Boolean possiblyBreaksFromIf = possiblyBreaks;
        boolean breaksOrContinuesFromIf = definitelyBreaksOrContinues;
        possiblyBreaks = e;
        endDefiniteReturnScope(d);
        endLoopScope(bc);
        
        int definitelyReturnsFromElse;
        boolean breaksOrContinuesFromElse;
        Tree.ElseClause elseClause = that.getElseClause();
        if (elseClause!=null) {
            elseClause.visit(this);
            definitelyReturnsFromElse = definitelyReturns;
            breaksOrContinuesFromElse = definitelyBreaksOrContinues;
        }
        else {
            definitelyReturnsFromElse = 0;
            breaksOrContinuesFromElse = false;
        }
        Boolean possiblyBreaksFromElse = possiblyBreaks;
        possiblyBreaks = e;
        endLoopScope(bc);
        
        Tree.ConditionList cl = ifClause==null ? null : ifClause.getConditionList();
        if (isAlwaysSatisfied(cl)) {
            definitelyReturns = d | definitelyReturnsFromIf;
            possiblyBreaks = possiblyBreaksFromIf;
            definitelyBreaksOrContinues = bc || breaksOrContinuesFromIf;
        } 
        else if (isNeverSatisfied(cl)) {
            definitelyReturns = d | definitelyReturnsFromElse;
            possiblyBreaks = possiblyBreaksFromElse;
            definitelyBreaksOrContinues = bc || breaksOrContinuesFromElse;
        }
        else {
            definitelyReturns = d | (definitelyReturnsFromIf & definitelyReturnsFromElse);
            possiblyBreaks = e==null ? null : possiblyBreaksFromIf || possiblyBreaksFromElse;
            definitelyBreaksOrContinues = bc || breaksOrContinuesFromIf && breaksOrContinuesFromElse;

        }
    }

    @Override
    public void visit(Tree.SwitchStatement that) {
        checkExecutableStatementAllowed(that);
        checkReachable(that);
        int d = beginIndefiniteReturnScope();
        boolean bc = definitelyBreaksOrContinues;
        
        that.getSwitchClause().visit(this);
        
        int definitelyReturnsFromEveryCase = 3;
        boolean definitelyBreaksOrContinuesFromEveryCase = true;
        List<Tree.CaseClause> caseClauses = 
                that.getSwitchCaseList().getCaseClauses();
        for (Tree.CaseClause cc: caseClauses) {
            cc.visit(this);
            definitelyReturnsFromEveryCase = definitelyReturnsFromEveryCase & definitelyReturns;
            definitelyBreaksOrContinuesFromEveryCase = definitelyBreaksOrContinuesFromEveryCase 
                    && definitelyBreaksOrContinues;
            endDefiniteReturnScope(d);
            endLoopScope(bc);
        }
        
        Tree.ElseClause elseClause = 
                that.getSwitchCaseList().getElseClause();
        if (elseClause!=null) {
            elseClause.visit(this);
            definitelyReturnsFromEveryCase = definitelyReturnsFromEveryCase & definitelyReturns;
            definitelyBreaksOrContinuesFromEveryCase = definitelyBreaksOrContinuesFromEveryCase 
                    && definitelyBreaksOrContinues;
            endDefiniteReturnScope(d);
            endLoopScope(bc);
        }
        
        definitelyReturns = d | definitelyReturnsFromEveryCase;
        definitelyBreaksOrContinues = bc || definitelyBreaksOrContinuesFromEveryCase;
    }

    @Override
    public void visit(Tree.TryCatchStatement that) {
        checkExecutableStatementAllowed(that);
        checkReachable(that);
        int d = beginIndefiniteReturnScope();
        boolean bc = definitelyBreaksOrContinues;
        
        Tree.TryClause tryClause = that.getTryClause();
        if (tryClause!=null) {
            tryClause.visit(this);
        }
        int definitelyReturnsFromTry = definitelyReturns;
        boolean definitelyBreaksOrContinuesFromTry = definitelyBreaksOrContinues;
        endDefiniteReturnScope(d);
        endLoopScope(bc);
        
        int definitelyReturnsFromEveryCatch = 3;
        boolean definitelyBreaksOrContinuesFromEveryCatch = true;
        for (Tree.CatchClause cc: that.getCatchClauses()) {
            cc.visit(this);
            definitelyReturnsFromEveryCatch = definitelyReturnsFromEveryCatch & definitelyReturns;
            definitelyBreaksOrContinuesFromEveryCatch = definitelyBreaksOrContinuesFromEveryCatch 
                    && definitelyBreaksOrContinues;
            endDefiniteReturnScope(d);
            endLoopScope(bc);
        }
        
        int definitelyReturnsFromFinally;
        boolean definitelyBreaksOrContinuesFromFinally;
        Tree.FinallyClause finallyClause = that.getFinallyClause();
        if (finallyClause!=null) {
            finallyClause.visit(this);
            definitelyReturnsFromFinally = definitelyReturns;
            definitelyBreaksOrContinuesFromFinally = definitelyBreaksOrContinues;
        }
        else {
            definitelyReturnsFromFinally = 0;
            definitelyBreaksOrContinuesFromFinally = false;
        }
        
        definitelyReturns = d | (definitelyReturnsFromTry & definitelyReturnsFromEveryCatch) 
                | definitelyReturnsFromFinally;
        definitelyBreaksOrContinues = bc || (definitelyBreaksOrContinuesFromTry && definitelyBreaksOrContinuesFromEveryCatch
                || definitelyBreaksOrContinuesFromFinally);
    }
    
    @Override
    public void visit(Tree.ExpressionStatement that) {
        super.visit(that);
        Tree.Expression expr = that.getExpression();
        if (expr!=null) {
            Tree.Term t = expr.getTerm();
            if (t==null) {
                expr.addError("malformed expression statement");
            }
            else {
                if (!(t instanceof Tree.InvocationExpression
                        || t instanceof Tree.PostfixOperatorExpression
                        || t instanceof Tree.PrefixOperatorExpression
                        || t instanceof Tree.AssignmentOp)) {
                    expr.addError("not a legal statement (not an invocation, assignment, or increment/decrement)", 
                            3000);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.Assertion that) {
        super.visit(that);
        if (isNeverSatisfied(that.getConditionList())) {
            definitelyThrows();
        }
    }
    
}

