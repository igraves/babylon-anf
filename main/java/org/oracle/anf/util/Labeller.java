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

public class Labeller {

    static final String fPrefix = "f_";
    static final String pPrefix = "p_";
    static final String vPrefix = "v_";

    int fCount = 0;
    int pCount = 0;
    int vCount = 0;

    public Labeller(){

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
