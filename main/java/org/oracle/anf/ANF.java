package org.oracle.anf;

import java.util.List;

public class ANF {

    public record Const(Object value) implements Term, Expression {}

    public record Var(Object varId) implements Term, Expression {}

    public sealed interface Term permits Const, Var, FunApply {}

    public sealed interface Expression permits Const, Var, FunApply, Let, LetRec, IfThen {}

    public record FunApply(Term name, List<Term> arguments, FunKind fc) implements Expression, Term {}

    public record Let(Var name, Term term, Expression expBody) implements Expression {}

    public record Function(Var name, List<Var> parameters, Expression expBody) {}

    public record LetRec(List<Function> funs, Expression combinedBody) implements Expression {}

    public record IfThen(Term cond, Expression trueExp, Expression falseExp) implements Expression {}

}
