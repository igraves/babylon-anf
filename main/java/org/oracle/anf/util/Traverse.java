/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.oracle.anf.util;

import org.oracle.anf.ANF;

import java.util.List;
import java.util.function.Function;

public class Traverse {


    public static ANF.Expression traverse(Function<ANF.Expression, ANF.Expression> f, ANF.Expression expr) {

        return switch (expr) {
            case ANF.Let(var name, var term, var expbody) -> {
               var res = new ANF.Let(name, traverseTerm(f, term), traverse(f, expbody));
               yield f.apply(res);
            }
            case ANF.Const c -> f.apply(c);
            case ANF.Var v -> f.apply(v);
            case ANF.FunApply(var name, var args, var fc) -> {
                List<ANF.Term> args_ = args.stream()
                        .map(a -> switch (a) {
                            case ANF.Var v -> f.apply(v);
                            default -> a;
                        })
                        .map(ANF.Term.class::cast)
                        .toList();
                var res = new ANF.FunApply(name, args_, fc);
                yield res;
            }
            case ANF.LetRec(var funs, var exprBody) -> {
                var body_expr = traverse(f, exprBody);
                var funs2 = funs.stream().map(anf_fun -> traverseFunction(f, anf_fun)).toList();
                yield new ANF.LetRec(funs2, body_expr);
            }
            case ANF.IfThen(var cond, var trueExp, var falseExp) -> {
                var trueExp_ = traverse(f, trueExp);
                var falseExp_ = traverse(f, falseExp);
                yield new ANF.IfThen(cond, trueExp_, falseExp_);
            }
        };
    }

    private static ANF.Function traverseFunction(Function<ANF.Expression, ANF.Expression> f, ANF.Function anf_f) {
        switch (anf_f) {
            case ANF.Function(var name, var params, var exprBody) -> {
                return new ANF.Function(name, params, traverse(f, exprBody));
            }
        }
    }

    private static ANF.Term traverseTerm(Function<ANF.Expression, ANF.Expression> f, ANF.Term anf_t) {
        return switch (anf_t) {
            case ANF.Var v -> (ANF.Term) f.apply(v);
            case ANF.FunApply fa -> (ANF.Term) traverse(f, fa);
            default -> anf_t;
        };
    }
}
