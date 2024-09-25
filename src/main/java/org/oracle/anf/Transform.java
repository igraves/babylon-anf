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

import java.lang.reflect.code.*;
import java.lang.reflect.code.op.CoreOp;
import java.lang.reflect.code.type.FunctionType;
import java.lang.reflect.code.op.AnfDialect;
import java.util.*;
import java.util.function.Function;

public class Transform {

    public AnfDialect.AnfLetRecOp transform(CoreOp.FuncOp f) {
        var outerBody = f.body();
        var cc = CopyContext.create();
        Body.Builder builder = outerBody.copy(cc);

        Block entryBlockToTransform  = outerBody.entryBlock();
        List<Block> blocksToTransform = outerBody.blocks();

        // Map entry block
        // Rebind this block builder to the created context and transformer
        Block.Builder startingBlock = rebind(cc, ot);
        cc.mapBlock(entryBlockToTransform, startingBlock);
        cc.mapValues(entryBlockToTransform.parameters(), args);

        return outerLetRec;
    }
/*
    private static ANF.FunApply makeParamCall(int index) {
        return funApply(variable("getParam"),List.of(constant(index)),
                FunKind.PRIMITIVE);
    }

 */

    //Outer body corresponds to outermost letrec
    //F_p
    public AnfDialect.AnfLetRecOp transformOuterBody(Body b) {
        var entry = b.entryBlock();
        CoreOp.FuncOp entry_f = transformBlock(entry);

        var funmap = letRecConstruction(b);
        var childfuns = funmap.keySet().stream().filter((block) -> block.immediateDominator() == b.entryBlock()).map(funmap::get).toList();

        ArrayList<CoreOp.FuncOp> afunctions = new ArrayList<>(childfuns);
        afunctions.addFirst(entry_f);
        return AnfDialect.letRec(afunctions, CoreOp.funApp(entry, List.of(), getBlockReturnType(entry)));
        //return letRec(afunctions, funApply(entry_f.name(),List.of(),FunKind.FC));
    }

    public AnfDialect.AnfFuncOp.Builder transformBlock(Block b) {
        List<? extends Value> params = b.parameters().stream().toList();
        Op fbody = transformOps(b);
        var blockRtype = getBlockReturnType(b);
        var paramTypes = params.stream().map(Value::type).toList();
        var fbuilder = CoreOp.func("anonymous", FunctionType.functionType(blockRtype, paramTypes));
        return fbuilder.body(c -> c.op(fbody));
    }

    private TypeElement getBlockReturnType(Block b) {
        var ops = b.ops().iterator();
        while(ops.hasNext()) {
            var op = ops.next();
            if (op instanceof Op.Terminating) {
                return op.resultType();
            }
        }
        throw new RuntimeException("Encountered Block with no terminator");
    }

    private Block.Builder transformEndOp(Block.Builder b, Op op) {
        if (op instanceof Op.Terminating t) {
            switch (t) {
                case CoreOp.ConditionalBranchOp c -> {
                    var tbranch_args = c.trueBranch().arguments().stream().toList();
                    var fbranch_args = c.falseBranch().arguments();

                    AnfDialect.if_(b.parentBody().ancestorBody(),
                                   c.trueBranch().targetBlock().terminatingOp().resultType(),c.predicate())
                            .if_((builder) -> builder.context(). AnfDialect.apply())

                    return AnfDialect.if_(c.predicate(),
                            AnfDialect.apply(c.trueBranch().targetBlock()., tbranch_args, c.resultType()),
                            AnfDialect.apply(c.falseBranch().targetBlock(), fbranch_args, c.resultType())); // TODO: Result Type
                }
                case CoreOp.BranchOp br -> {
                    var tblock = br.branch().targetBlock();
                    var args = br.branch().arguments().stream().toList();
                    //return CoreOp.funApp(tblock, args, br.resultType()); //TODO: Result Type
                    return b;
                }
                default -> {
                    throw new UnsupportedOperationException("Unsupported terminating op encountered.");
                }
            }
        } else {
            //return op;
            b.op(op);
            return b;
        }
    }

    public AnfDialect.AnfLetOp transformOps(Block.Builder b) {
        Body.Builder ancestorBody = b.parentBody().ancestorBody();
        return AnfDialect.let(ancestorBody,transformEndOp(b.ops().getLast()));
    }
/*
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

 */

    private Map<Block, AnfDialect.AnfFuncOp> letRecConstruction(Body b) {
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


            var bodyExpr = transformOps(workBlock);
            var lr = AnfDialect.letrec();

            var paramTys = workBlock.parameters().stream().map(Block.Parameter::type).toList();
            var funBuilder = CoreOp.func(workBlock.toString(), FunctionType.functionType(lr.resultType(),paramTys));
            var fun = funBuilder.body(c -> c.op(lr));

            processedFunctions.put(workBlock,fun);
        }

        return processedFunctions;
    }

    private Map<Block, AnfDialect.AnfFuncOp.Builder> leafFunctions(Body b) {
        List<Block> leafBlocks = leafBlocks(b);
        HashMap<Block, AnfDialect.AnfFuncOp.Builder> functions = new HashMap<>();

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
}
