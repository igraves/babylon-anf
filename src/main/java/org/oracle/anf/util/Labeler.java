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
import org.oracle.anf.FunKind;

import java.util.HashMap;

import static org.oracle.anf.ANF.*;

public class Labeler {

    static final String fPrefix = "f_";
    static final String pPrefix = "p_";
    static final String vPrefix = "v_";

    int fCount = 0;
    int pCount = 0;
    int vCount = 0;

    final HashMap<Object, String> map = new HashMap<>();

    private String remapVar(ANF.Var name) {
        return remap(name, vPrefix);
    }

    private String remapFun(ANF.Var name) {
        return remap(name, fPrefix);
    }

    private String remapParam(ANF.Var name) {
        return remap(name, pPrefix);
    }

    private String remap(ANF.Var name, String pref) {
        if (map.get(name.varId()) == null) {
            String n;
            if (pref.equals(fPrefix)) {
                n = this.nextFunction();
            } else if (pref.equals(pPrefix)) {
                n = this.nextParameter();
            } else {
                n = this.nextVariable();
            }
            map.put(name.varId(), n);
            return n;
        }
        return map.get(name.varId());
    }

    public ANF.Expression label(ANF.Expression expr) {
            switch (expr) {
                case ANF.Let(var name, var term, var expbody) -> {
                    return let(variable(remapVar(name)), remapTerm(term), label(expbody));
                }
                case ANF.Const c -> {
                    return c;
                }
                case ANF.Var v -> {
                    var rn = remapVar(v);
                    return variable(rn);
                }
                case ANF.FunApply fa -> {
                    return (ANF.FunApply) remapTerm(fa);
                }
                case ANF.LetRec(var funs, var exprBody) -> {
                    var fs = funs.stream().map(this::labelFunction).toList();
                    var exp = label(exprBody);
                    return letRec(fs, exp);
                }
                case ANF.IfThen(var cond, var trueExp, var falseExp) -> {
                    var new_cond = remapTerm(cond);
                    return ifThen(new_cond, label(trueExp), label(falseExp));
                }
            }
    }

    private ANF.Function labelFunction(ANF.Function f) {
       var n = variable(remapVar(f.name()));
       var ps = f.parameters().stream().map(this::remapParam).map(ANF::variable).toList();
       var exp = label(f.expBody());
       return function(n, ps, exp);
    }

    private ANF.Term remapTerm(ANF.Term term) {
       switch (term) {
           case ANF.Var v -> {
               var rm = remapVar(v);
               return variable(rm);
           }
           case ANF.FunApply fa -> {
               var fun_name = fa.name();
               if (!(fa.fc().equals(FunKind.PRIMITIVE))) {
                    fun_name = remapTerm(fa.name());
               }
               return funApply(fun_name,
                       fa.arguments().stream().map(this::remapTerm).toList(),
                       fa.fc());
           }
           case ANF.Const c -> {
               return c;
           }
       }
    }

    public String nextFunction(){
        var res = fPrefix + pCount;
        fCount++;
        return res;
    }
    public String nextParameter(){
        var res = pPrefix + pCount;
        pCount++;
        return res;
    }
    public String nextVariable(){
        var res = vPrefix + vCount;
        vCount++;
        return res;
    }
}
