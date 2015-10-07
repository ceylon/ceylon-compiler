/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.main;

import java.util.Arrays;

import com.redhat.ceylon.compiler.java.codegen.Optimization;


/**
 * TODO: describe com.sun.tools.javac.main.OptionName
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public enum OptionName {
    G("-g"),
    G_NONE("-g:none"),
    G_CUSTOM("-g:"),
    XLINT("-Xlint"),
    XLINT_CUSTOM("-Xlint:"),
    DIAGS("-XDdiags="),
    NOWARN("-nowarn"),
    VERBOSE("-verbose"),
    VERBOSE_CUSTOM("-verbose:{all,loader,ast,code,cmr,benchmark}"),
    DEPRECATION("-deprecation"),
    CLASSPATH("-classpath"),
    CP("-cp"),
    CEYLONCWD("-cwd"),
    CEYLONREPO("-rep"),
    CEYLONSYSTEMREPO("-sysrep"),
    CEYLONCACHEREPO("-cacherep"),
    CEYLONNODEFREPOS("-nodefreps"),
    CEYLONUSER("-user"),
    CEYLONPASS("-pass"),
    CEYLONNOOSGI("-noosgi"),
    CEYLONOSGIPROVIDEDBUNDLES("-osgi-provided-bundles"),
    CEYLONNOPOM("-nopom"),
    CEYLONPACK200("-pack200"),
    SOURCEPATH("-sourcepath"),
    CEYLONSOURCEPATH("-src"),
    CEYLONRESOURCEPATH("-res"),
    CEYLONRESOURCEROOT("-resroot"),
    CEYLONDISABLEOPT("-disableOptimization"),
    CEYLONDISABLEOPT_CUSTOM("-disableOptimization:{"+optimizations()+"}"),
    CEYLONSUPPRESSWARNINGS("-suppress-warnings"),
    BOOTCLASSPATH("-bootclasspath"),
    XBOOTCLASSPATH_PREPEND("-Xbootclasspath/p:"),
    XBOOTCLASSPATH_APPEND("-Xbootclasspath/a:"),
    XBOOTCLASSPATH("-Xbootclasspath:"),
    EXTDIRS("-extdirs"),
    DJAVA_EXT_DIRS("-Djava.ext.dirs="),
    ENDORSEDDIRS("-endorseddirs"),
    DJAVA_ENDORSED_DIRS("-Djava.endorsed.dirs="),
    PROC("-proc:"),
    PROCESSOR("-processor"),
    PROCESSORPATH("-processorpath"),
    D("-d"),
    CEYLONOUT("-out"),
    CEYLONOFFLINE("-offline"),
    CEYLONTIMEOUT("-timeout"),
    CEYLONCONTINUE("-continue"),
    CEYLONPROGRESS("-progress"),
    // Backwards-compat
    CEYLONMAVENOVERRIDES("-maven-overrides"),
    CEYLONOVERRIDES("-overrides"),
    CEYLONFLATCLASSPATH("-flat-classpath"),
    CEYLONAUTOEXPORTMAVENDEPENDENCIES("-auto-export-maven-dependencies"),
    S("-s"),
    IMPLICIT("-implicit:"),
    ENCODING("-encoding"),
    SOURCE("-source"),
    TARGET("-target"),
    VERSION("-version"),
    FULLVERSION("-fullversion"),
    HELP("-help"),
    A("-A"),
    X("-X"),
    J("-J"),
    MOREINFO("-moreinfo"),
    WERROR("-Werror"),
    COMPLEXINFERENCE("-complexinference"),
    PROMPT("-prompt"),
    DOE("-doe"),
    PRINTSOURCE("-printsource"),
    WARNUNCHECKED("-warnunchecked"),
    XMAXERRS("-Xmaxerrs"),
    XMAXWARNS("-Xmaxwarns"),
    XSTDOUT("-Xstdout"),
    XPKGINFO("-Xpkginfo:"),
    XPRINT("-Xprint"),
    XPRINTROUNDS("-XprintRounds"),
    XPRINTPROCESSORINFO("-XprintProcessorInfo"),
    XPREFER("-Xprefer:"),
    O("-O"),
    XJCOV("-Xjcov"),
    XD("-XD"),
    AT("@"),
    SOURCEFILE("sourcefile"),
    SRC("-src"),
    BOOTSTRAPCEYLON("-Xbootstrapceylon");

    public final String optionName;

    OptionName(String optionName) {
        this.optionName = optionName;
    }

    @Override
    public String toString() {
        return optionName;
    }
    
    public static String optimizations() {
        return Arrays.toString(Optimization.values()).replace("[", "").replace("]", "").replace(",  ", ",");
    }

}
