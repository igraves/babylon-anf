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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;


public class ANFPrinter {

    public void print(ANF.Expression expr) {
        print(expr, System.out);
    }

    public void print(ANF.Expression expr, OutputStream os) {
        IndentWriter w = new IndentWriter(new PrintWriter(os));
        print(expr, w);
        try {
            w.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void print(ANF.Expression expr, IndentWriter w) {
        try {
            switch (expr) {
                case ANF.Let(var name, var term, var expbody) -> {
                    w.write("let ");
                    w.write(name.toString());
                    w.write(" = ");
                    w.write(term.toString());
                    w.write(" in \n");
                    w.write("{\n");
                    w.in();
                    print(expbody, w);
                    w.out();
                    w.write("}\n");
                }
                case ANF.Const c -> w.write(c.toString());
                case ANF.Var v -> w.write(v + "\n");
                case ANF.FunApply fa -> printFunApply(fa, w);
                case ANF.LetRec(var funs, var exprBody) -> {
                    w.write("letrec \n");
                    w.in();
                    printFunctions(funs, w);
                    w.out();
                    w.write("in\n{\n");
                    w.in();
                    print(exprBody, w);
                    w.out();
                    w.write("}\n");
                }
                case ANF.IfThen(var cond, var trueExp, var falseExp) -> {
                    w.write("if ");
                    printTerm(cond, w);
                    w.write("then {\n");
                    w.in();
                    print(trueExp, w);
                    w.out();
                    w.write("}\n");
                    w.write("else {\n");
                    w.in();
                    print(falseExp, w);
                    w.out();
                    w.write("}\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void printFunctions(List<? extends ANF.Function> funs, IndentWriter w) throws IOException {
        if (funs.size() == 1) {
           printFunction(funs.getFirst(),w);
           w.write(";\n");
        } else if (funs.size() > 1){
            for (ANF.Function fun : funs) {
                printFunction(fun, w);
                w.write(";\n");
            }
        }
    }

    private void printFunction(ANF.Function fun, IndentWriter w) throws IOException {
        w.write(fun.name().toString());
        printArgs(fun.parameters(),w);
        w.write(" = ");
        w.write("{\n");
        w.in();
        print(fun.expBody(), w);
        w.out();
        w.write("}\n");
    }

    private void printTerm(ANF.Term term, IndentWriter w) throws IOException {
       switch (term) {
           case ANF.Const(var value) -> w.write(value.toString());
           case ANF.Var (var name) -> w.write(name.toString());
           case ANF.FunApply fa -> printFunApply(fa, w);
       }
    }

    private void printFunApply(ANF.FunApply fa, IndentWriter w) throws IOException {
        w.write(fa.name().toString());
        printArgs(fa.arguments(),w);
        w.write("\n");

    }

    private void printArgs(List<? extends ANF.Term> args, IndentWriter w) throws IOException {
       if (args.isEmpty()) {
           w.write("()");
       } else {
           w.write("(");
           printIntersperse(args, ", ", w);
           w.write(")");
       }
    }

    private void printIntersperse(List<? extends ANF.Term> params, String delimiter, IndentWriter w) throws IOException {
        if (!params.isEmpty()) {
            Iterator<? extends ANF.Term> iter = params.iterator();
            printTerm(iter.next(), w);
            while(iter.hasNext()) {
                w.write(delimiter);
                printTerm(iter.next(), w);
            }
        }
    }

    static final class IndentWriter extends Writer {
        static final int INDENT = 2;
        final Writer w;
        int indent;
        boolean writeIndent;

        IndentWriter(Writer w) {
            this(w, 0);
        }

        IndentWriter(Writer w, int indent) {
            this.writeIndent = true;
            this.w = w;
            this.indent = indent;
        }

        public void write(char[] cbuf, int off, int len) throws IOException {
            if (this.writeIndent) {
                this.w.write(" ".repeat(this.indent));
                this.writeIndent = false;
            }

            this.w.write(cbuf, off, len);
            if (len > 0 && cbuf[off + len - 1] == '\n') {
                this.writeIndent = true;
            }

        }

        public void flush() throws IOException {
            this.w.flush();
        }

        public void close() throws IOException {
            this.w.close();
        }

        void in() {
            this.in(INDENT);
        }

        void in(int i) {
            this.indent += i;
        }

        void out() {
            this.out(INDENT);
        }

        void out(int i) {
            this.indent -= i;
        }
    }
}
