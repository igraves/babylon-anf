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

import org.oracle.anf.util.Traverse;

import java.lang.reflect.code.Block;
import java.lang.reflect.code.Body;
import java.lang.reflect.code.Op;
import java.lang.reflect.code.op.CoreOp;
import java.util.*;

import static org.oracle.anf.ANF.*;

public class Transform {

    public ANF.LetRec transform(CoreOp.FuncOp f) {
        ANF.LetRec outerLetRec = transformOuterBody(f.body());

        HashMap<ANF.Var,ANF.Expression> paramSubs = new HashMap<>();
        for (int i =0; i < f.parameters().size(); i++) {
            paramSubs.put(variable(f.parameters().get(i)), makeParamCall(i));
        }

        /*
        var res = Traverse.traverse((expr) -> {
            if (expr instanceof ANF.Var v) {
               return paramSubs.getOrDefault(v,v);
            } else {
                return expr;
            }
        }, outerLetRec);
         */

        return outerLetRec;
    }

    private static ANF.FunApply makeParamCall(int index) {
        return funApply(variable("getParam"),List.of(constant(index)),
                FunKind.PRIMITIVE);
    }

    //Outer body corresponds to outermost letrec
    //F_p
    public ANF.LetRec transformOuterBody(Body b) {
        var entry = b.entryBlock();
        ANF.Function entry_f = transformBlock(entry);
        var params = b.entryBlock().parameters().stream().map(ANF::variable).toList();
        entry_f = function(entry_f.name(), params, entry_f.expBody());

        var funmap = letRecConstruction(b);
        var childfuns = funmap.keySet().stream().filter((block) -> block.immediateDominator() == b.entryBlock()).map(funmap::get).toList();

        ArrayList<ANF.Function> afunctions = new ArrayList<>(childfuns);
        afunctions.addFirst(entry_f);
        return letRec(afunctions, funApply(entry_f.name(),List.of(),FunKind.FC));
    }

    public ANF.Function transformBlock(Block b) {
        List<ANF.Var> params = b.parameters().stream().map(ANF::variable).toList();
        ANF.Expression fbody = transformOps(b.ops().iterator());
        return function(variable(b),params,fbody);
    }

    public ANF.Expression transformOps(Iterator<Op> ops) {
        if (ops.hasNext()) {
            var op = ops.next();
            if (op instanceof Op.Terminating t) {
                switch (t) {
                    case CoreOp.ReturnOp rop -> {
                        if (rop.operands().isEmpty()) {
                            return constant(0);
                        }
                        return variable(rop.operands().getFirst());
                    }
                    case CoreOp.ConditionalBranchOp c -> {
                        var tbranch_args = c.trueBranch().arguments().stream().map(ANF::variable).map(ANF.Term.class::cast).toList();
                        var fbranch_args = c.falseBranch().arguments().stream().map(ANF::variable).map(ANF.Term.class::cast).toList();
                        return ifThen(variable(c.predicate()),
                                funApply(variable(c.trueBranch().targetBlock()),tbranch_args, FunKind.FC),
                                funApply(variable(c.falseBranch().targetBlock()),fbranch_args, FunKind.FC));
                    }
                    case CoreOp.BranchOp br -> {
                        var tblock = br.branch().targetBlock();
                        var args = br.branch().arguments().stream().map(ANF::variable).map(ANF.Term.class::cast).toList();
                        return funApply(variable(tblock),args, FunKind.FC);
                    }
                    default -> throw new UnsupportedOperationException("Don't support terminating op type of " + op.opName());
                }

            } else {
               return let(variable(op.result()), transformOp(op), transformOps(ops));
            }
        } else {
            throw new UnsupportedOperationException("Encountered prematurely empty op iterator");
        }
    }

    public ANF.Term transformOp(Op o) {
        switch (o) {
            case CoreOp.ArithmeticOperation op -> {
                List<ANF.Term> args = transformOperands(op);
                return funApply(variable(op.opName()),args,FunKind.PRIMITIVE);
            }
            case CoreOp.TestOperation op -> {
                List<ANF.Term> args = transformOperands(op);
                return funApply(variable(op.opName()),args,FunKind.PRIMITIVE);
            }
            case CoreOp.ConstantOp op ->  {
                return constant(op.value());
            }
            default -> throw new UnsupportedOperationException("Operator type " + o.opName() + " not supported");

        }
    }

    private static List<ANF.Term> transformOperands(Op op) {
        return op.operands().stream().map(ANF::variable).map(ANF.Term.class::cast).toList();
    }

    private Map<Block, ANF.Function> letRecConstruction(Body b) {
        var processedFunctions = leafFunctions(b);
        List<Block> workQueue = new LinkedList<>(processedFunctions.keySet().stream().map(Block::immediateDominator).toList());
        Set<Block> processed = new HashSet<>(processedFunctions.keySet());
        processed.add(b.entryBlock());

        while (!workQueue.isEmpty()) {
            Block workBlock = workQueue.removeFirst();

            if (workBlock == null || processed.contains(workBlock)) {
                continue;
            }

            //Ugly slow. Blocks dominated by this one.
            var domBlocks = b.blocks().stream().filter((block) -> block.immediateDominator() != null && block.immediateDominator().equals(workBlock)).toList();

            var unProcessedDomBlocks = domBlocks.stream().filter((block) -> !processedFunctions.containsKey(block)).toList();

            //If all dependencies aren't processed, queue them in front, requeue, and continue
            if (!unProcessedDomBlocks.isEmpty()) {
                unProcessedDomBlocks.forEach(workQueue::addLast);
                workQueue.addLast(workBlock);
                continue;
            }

            var domFuns = domBlocks.stream().map(processedFunctions::get).toList();


            var bodyExpr = transformOps(workBlock.ops().iterator());
            var lr = letRec(domFuns, bodyExpr);

            var params = workBlock.parameters().stream().map(ANF::variable).toList();
            var fun = function(variable(workBlock),params,lr);

            processedFunctions.put(workBlock,fun);
        }

        return processedFunctions;
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
        HashSet<Block> leafBlocks = new HashSet<>(b.blocks());
        leafBlocks.remove(b.entryBlock());
        b.blocks().forEach((block) -> {
            var dom = idoms.get(block);
            //Remove all blocks that dominate other blocks.
            if (dom != null) {
                leafBlocks.remove(dom);
            }
        });
        //Return blocks that dominate nothing. These are leaves.
        return leafBlocks.stream().toList();
    }

    //public static ANF.Expression transform
}
