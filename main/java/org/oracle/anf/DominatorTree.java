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

import java.lang.reflect.code.Block;
import java.lang.reflect.code.Body;
import java.util.*;

public class DominatorTree {

    private final Body body;

    //What a block dominates
    private final Map<Block, Set<Block>> dominates = new HashMap<>();

    //What a block immediately dominates
    private final Map<Block, Set<Block>> immediateDominates = new HashMap<>();

    //What a block is dominated by
    private final Map<Block, Set<Block>> dominators = new HashMap<>();

    //What block immediately dominates
    private final Map<Block, Block> immediateDominator = new HashMap<>();

    public DominatorTree(Body b) {
        body = b;
        buildTree(b);
    }

    //Naive O(N^x) approach
    private void buildTree(Body b) {
        for (Block block : b.blocks()) {
            dominates.putIfAbsent(block, new HashSet<>());
            dominators.putIfAbsent(block, new HashSet<>());
        }

        for (Block block : b.blocks()) {
            for (Block block2 : b.blocks()) {
                if (block.isDominatedBy(block2)) {
                    //block2 dominates block
                    dominates.compute(block2, (newb, bset) -> {
                        bset.add(newb);
                        return bset;
                    });

                    //block is dominated by block2
                    dominators.compute(block, (newb, bset) -> {
                        bset.add(newb);
                        return bset;
                    });
                }
            }
            var idom = block.immediateDominator();
            immediateDominates.compute(idom, (domkey, domset) -> {
                if (domset == null) {
                    var hs = new HashSet<Block>();
                    hs.add(block);
                    return hs;
                }
                domset.add(block);
                return domset;
            });
            immediateDominator.put(block, idom);
        }
    }

    public Set<Block> dominates(Block block) {
        return dominates.get(block);
    }

    public Block immediateDominator(Block block) {
        return immediateDominator.get(block);
    }

    public Set<Block> immediateDominates(Block block) {
        return immediateDominates.get(block);
    }

    public Set<Block> dominators(Block block) {
        return dominators.get(block);
    }

    public List<Block> bfs() {
        var entry = body.entryBlock();
        //TODO:
        return List.of();
    }
}
