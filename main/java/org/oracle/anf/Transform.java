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

package org.oracle.anf;

import org.oracle.anf.util.Labeller;
import org.oracle.anf.util.Mapper;
import org.oracle.anf.util.Traverse;

import java.lang.reflect.code.Block;
import java.lang.reflect.code.Body;
import java.lang.reflect.code.Op;
import java.lang.reflect.code.Value;
import java.lang.reflect.code.op.CoreOp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Transform {

    Labeller labeller = new Labeller();
    Mapper mapper = new Mapper();

    public Transform() {

    }

    public ANF.LetRec transform(CoreOp.FuncOp f) {
        //List<ANF.Var> outerparams = f.parameters().stream().map(ANF.Var::new).toList();
        //return new ANF.LetRec(List.of(ANF.Function(new ANF.Var(f.funcName()),outerparams,))

        ANF.LetRec outerLetRec = transformOuterBody(f.body());

        HashMap<ANF.Var,ANF.Expression> paramSubs = new HashMap<>();
        for (int i =0; i < f.parameters().size(); i++) {
            paramSubs.put(new ANF.Var(f.parameters().get(i)), makeParamCall(i));
        }

        var res = Traverse.traverse((expr) -> {
           return switch (expr) {
               case ANF.Var v -> paramSubs.getOrDefault(v,v);
               default -> expr;
           };
        }, outerLetRec);

        return (ANF.LetRec) res;
    }

    private static ANF.FunApply makeParamCall(int index) {
        return new ANF.FunApply(new ANF.Var("getParam"),List.of(new ANF.Const(index)),
                new FunKind.Primitive("getParam"));
    }

    //Outer body corresponds to outermost letrec
    //F_p
    public ANF.LetRec transformOuterBody(Body b) {
        var entry = b.entryBlock();
        ANF.Function entry_f = transformBlock(entry);
        entry_f = new ANF.Function(entry_f.name(), List.of(), entry_f.expBody());
        var functions = List.of(entry_f);

        //var entryExp = new ANF.FunApply(
        //        functions.get(0).name(),
        //)
        return new ANF.LetRec(List.of(entry_f),new ANF.FunApply(entry_f.name(),List.of(),new FunKind.FC()));
    }

    public ANF.Function transformBlock(Block b) {
        List<ANF.Var> params = b.parameters().stream().map(ANF.Var::new).toList();
        ANF.Expression fbody = transformOps(b.ops().iterator());
        ANF.Function f = new ANF.Function(new ANF.Var(labeller.nextFunction()),params,fbody);
        return f;
    }

    public ANF.Expression transformOps(Iterator<Op> ops) {
        if (ops.hasNext()) {
            var op = ops.next();
            if (op instanceof Op.Terminating t) {
                switch (t) {
                    case CoreOp.ReturnOp rop -> {
                        return new ANF.Var(rop.operands().get(0));
                    }
                    default -> throw new UnsupportedOperationException("Don't support terminating op type of " + op.opName());
                }

            } else {
               return new ANF.Let(new ANF.Var(op.result()), transformOp(op), transformOps(ops));
            }
        } else {
            throw new UnsupportedOperationException("Encountered prematurely empty op iterator");
        }
    }

    public ANF.Term transformOp(Op o) {
        switch (o) {
            case CoreOp.ArithmeticOperation op -> {
                List<ANF.Term> args = transformOperands(op);
                return new ANF.FunApply(new ANF.Var(op.opName()),args,new FunKind.Primitive(op.opName()));
            }
            case CoreOp.TestOperation op -> {
                List<ANF.Term> args = transformOperands(op);
                return new ANF.FunApply(new ANF.Var(op.opName()),args,new FunKind.Primitive(op.opName()));
            }
            case CoreOp.ConstantOp op ->  {
                return new ANF.Const(op.value());
            }
            default -> throw new UnsupportedOperationException("Operator type " + o.opName() + " not supported");

        }
    }

    private static List<ANF.Term> transformOperands(Op op) {
        return op.operands().stream().map(ANF.Var::new).map(ANF.Term.class::cast).toList();
    }

    private Map<Block, ANF.Function> leafFunctions(Body b) {
        List<Block> leafBlocks = leafBlocks(b);
        HashMap<Block, ANF.Function> functions = new HashMap<>();

        for (Block leafBlock : leafBlocks) {
            functions.put(leafBlock, transformBlock(leafBlock));
        }

        return functions;
    }

    private static List<Block> leafBlocks(Body b) {
        var idoms = b.immediateDominators();
        return b.blocks().stream().filter((Block block) -> idoms.get(block) == null).toList();
    }

    //public static ANF.Expression transform
}
