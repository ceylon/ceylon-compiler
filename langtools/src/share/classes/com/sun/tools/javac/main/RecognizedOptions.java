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

import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.config.DefaultToolOptions;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.JavacOption.HiddenOption;
import com.sun.tools.javac.main.JavacOption.Option;
import com.sun.tools.javac.main.JavacOption.COption;
import com.sun.tools.javac.main.JavacOption.XOption;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.lang.model.SourceVersion;

import static com.sun.tools.javac.main.OptionName.*;

/**
 * TODO: describe com.sun.tools.javac.main.RecognizedOptions
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class RecognizedOptions {

    private RecognizedOptions() {}

    public interface OptionHelper {

        void setOut(PrintWriter out);

        void error(String key, Object... args);

        void printVersion();

        void printFullVersion();

        void printHelp();

        void printXhelp();

        void addFile(File f);

        void addClassName(String s);

    }

    public static class GrumpyHelper implements OptionHelper {

        public void setOut(PrintWriter out) {
            throw new IllegalArgumentException();
        }

        public void error(String key, Object... args) {
            throw new IllegalArgumentException(Main.getLocalizedString(key, args));
        }

        public void printVersion() {
            throw new IllegalArgumentException();
        }

        public void printFullVersion() {
            throw new IllegalArgumentException();
        }

        public void printHelp() {
            throw new IllegalArgumentException();
        }

        public void printXhelp() {
            throw new IllegalArgumentException();
        }

        public void addFile(File f) {
            throw new IllegalArgumentException(f.getPath());
        }

        public void addClassName(String s) {
            throw new IllegalArgumentException(s);
        }

    }

    static Set<OptionName> javacOptions = EnumSet.of(
        G,
        G_NONE,
        G_CUSTOM,
        XLINT,
        XLINT_CUSTOM,
        NOWARN,
        VERBOSE,
        VERBOSE_CUSTOM,
        DEPRECATION,
        CLASSPATH,
        CP,
        CEYLONCWD,
        CEYLONREPO,
        CEYLONSYSTEMREPO,
        CEYLONCACHEREPO,
        CEYLONNODEFREPOS,
        CEYLONUSER,
        CEYLONPASS,
        CEYLONNOOSGI,
        CEYLONOSGIPROVIDEDBUNDLES,
        CEYLONNOPOM,
        CEYLONPACK200,
        SOURCEPATH,
        CEYLONSOURCEPATH,
        CEYLONRESOURCEPATH,
        CEYLONRESOURCEROOT,
        BOOTCLASSPATH,
        XBOOTCLASSPATH_PREPEND,
        XBOOTCLASSPATH_APPEND,
        XBOOTCLASSPATH,
        EXTDIRS,
        DJAVA_EXT_DIRS,
        ENDORSEDDIRS,
        DJAVA_ENDORSED_DIRS,
        PROC,
        PROCESSOR,
        PROCESSORPATH,
        D,
        CEYLONOUT,
        CEYLONOFFLINE,
        CEYLONTIMEOUT,
        CEYLONCONTINUE,
        CEYLONPROGRESS,
        CEYLONFLATCLASSPATH,
        CEYLONAUTOEXPORTMAVENDEPENDENCIES,
        CEYLONMAVENOVERRIDES,
        CEYLONOVERRIDES,
        S,
        IMPLICIT,
        ENCODING,
        SOURCE,
        TARGET,
        VERSION,
        FULLVERSION,
        DIAGS,
        HELP,
        A,
        X,
        J,
        MOREINFO,
        WERROR,
        // COMPLEXINFERENCE,
        PROMPT,
        DOE,
        PRINTSOURCE,
        WARNUNCHECKED,
        XMAXERRS,
        XMAXWARNS,
        XSTDOUT,
        XPKGINFO,
        XPRINT,
        XPRINTROUNDS,
        XPRINTPROCESSORINFO,
        XPREFER,
        O,
        XJCOV,
        XD,
        AT,
        SOURCEFILE,
        SRC,
        BOOTSTRAPCEYLON,
        CEYLONDISABLEOPT,
        CEYLONDISABLEOPT_CUSTOM,
        CEYLONSUPPRESSWARNINGS);

    static Set<OptionName> javacFileManagerOptions = EnumSet.of(
        CLASSPATH,
        CP,
        CEYLONCWD,
        CEYLONREPO,
        CEYLONSYSTEMREPO,
        CEYLONCACHEREPO,
        CEYLONNODEFREPOS,
        CEYLONUSER,
        CEYLONPASS,
        CEYLONNOOSGI,
        CEYLONOSGIPROVIDEDBUNDLES,
        CEYLONNOPOM,
        CEYLONPACK200,
        SOURCEPATH,
        CEYLONSOURCEPATH,
        CEYLONRESOURCEPATH,
        CEYLONRESOURCEROOT,
        BOOTCLASSPATH,
        XBOOTCLASSPATH_PREPEND,
        XBOOTCLASSPATH_APPEND,
        XBOOTCLASSPATH,
        EXTDIRS,
        DJAVA_EXT_DIRS,
        ENDORSEDDIRS,
        DJAVA_ENDORSED_DIRS,
        PROCESSORPATH,
        D,
        CEYLONOUT,
        CEYLONOFFLINE,
        CEYLONTIMEOUT,
        CEYLONCONTINUE,
        CEYLONPROGRESS,
        CEYLONFLATCLASSPATH,
        CEYLONAUTOEXPORTMAVENDEPENDENCIES,
        CEYLONMAVENOVERRIDES,
        CEYLONOVERRIDES,
        S,
        ENCODING,
        SOURCE,
        SRC);

    static Set<OptionName> javacToolOptions = EnumSet.of(
        G,
        G_NONE,
        G_CUSTOM,
        XLINT,
        XLINT_CUSTOM,
        NOWARN,
        VERBOSE,
        VERBOSE_CUSTOM,
        DEPRECATION,
        PROC,
        PROCESSOR,
        IMPLICIT,
        SOURCE,
        TARGET,
        // VERSION,
        // FULLVERSION,
        // HELP,
        A,
        // X,
        // J,
        MOREINFO,
        WERROR,
        // COMPLEXINFERENCE,
        PROMPT,
        DOE,
        PRINTSOURCE,
        WARNUNCHECKED,
        XMAXERRS,
        XMAXWARNS,
        // XSTDOUT,
        XPKGINFO,
        XPRINT,
        XPRINTROUNDS,
        XPRINTPROCESSORINFO,
        XPREFER,
        O,
        XJCOV,
        XD,
        BOOTSTRAPCEYLON,
        CEYLONDISABLEOPT,
        CEYLONDISABLEOPT_CUSTOM,
        CEYLONSUPPRESSWARNINGS);

    public static Option[] getJavaCompilerOptions(OptionHelper helper) {
        return getOptions(helper, javacOptions);
    }

    public static Option[] getJavacFileManagerOptions(OptionHelper helper) {
        return getOptions(helper, javacFileManagerOptions);
    }

    public static Option[] getJavacToolOptions(OptionHelper helper) {
        return getOptions(helper, javacToolOptions);
    }

    static Option[] getOptions(OptionHelper helper, Set<OptionName> desired) {
        ListBuffer<Option> options = new ListBuffer<Option>();
        for (Option option : getAll(helper))
            if (desired.contains(option.getName()))
                options.append(option);
        return options.toArray(new Option[options.length()]);
    }

    /**
     * Get all the recognized options.
     * @param helper an {@code OptionHelper} to help when processing options
     * @return an array of options
     */
    public static Option[] getAll(final OptionHelper helper) {
        return new Option[] {
        new Option(G,                                           "opt.g"),
        new Option(G_NONE,                                      "opt.g.none") {
            @Override
            public boolean process(Options options, String option) {
                options.put("-g:", "none");
                return false;
            }
        },

        new Option(G_CUSTOM,                                    "opt.g.lines.vars.source",
                Option.ChoiceKind.ANYOF, "lines", "vars", "source"),

        new XOption(XLINT,                                      "opt.Xlint"),
        new XOption(XLINT_CUSTOM,                               "opt.Xlint.suboptlist",
                Option.ChoiceKind.ANYOF, getXLintChoices()),

        // -nowarn is retained for command-line backward compatibility
        new Option(NOWARN,                                      "opt.nowarn") {
            @Override
            public boolean process(Options options, String option) {
                options.put("-Xlint:none", option);
                return false;
            }
        },

        new Option(VERBOSE,                                     "opt.verbose"),
        new Option(VERBOSE_CUSTOM,                              "opt.verbose.suboptlist") {
            public boolean matches(String s) {
                return s.startsWith("-verbose:");
            }
            public boolean process(Options options, String option) {
                String suboptions = option.substring(9);
                options.put("-verbose:", suboptions);
                // enter all the -verbose suboptions as "-verbose:suboption"
                for (StringTokenizer t = new StringTokenizer(suboptions, ","); t.hasMoreTokens(); ) {
                    String tok = t.nextToken();
                    // make sure all is an alias for --verbose
                    if(tok.equals("all"))
                        options.put(VERBOSE, "true");
                    String opt = "-verbose:" + tok;
                    options.put(opt, opt);
                }
                return false;
            }
        },

        // -deprecation is retained for command-line backward compatibility
        new Option(DEPRECATION,                                 "opt.deprecation") {
            @Override
            public boolean process(Options options, String option) {
                options.put("-Xlint:deprecation", option);
                return false;
            }
        },

        new Option(CLASSPATH,              "opt.arg.path",      "opt.classpath"),
        new Option(CP,                     "opt.arg.path",      "opt.classpath") {
            @Override
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-classpath", arg);
            }
        },
        new COption(CEYLONCWD,              "opt.arg.path",     "opt.ceyloncwd"),
        new COption(CEYLONREPO,             "opt.arg.url",      "opt.ceylonrepo"){
            @Override
            public boolean process(Options options, String option, String arg) {
                if(options != null)
                    options.addMulti(CEYLONREPO, arg);
                return false;
            }
        },
        new COption(CEYLONSYSTEMREPO,       "opt.arg.url",       "opt.ceylonsystemrepo"),
        new COption(CEYLONCACHEREPO,        "opt.arg.url",       "opt.ceyloncacherepo"),
        new COption(CEYLONNODEFREPOS,                            "opt.ceylonnodefrepos"),
        new COption(CEYLONUSER,             "opt.arg.value",     "opt.ceylonuser"),
        new COption(CEYLONPASS,             "opt.arg.value",     "opt.ceylonpass"),
        new COption(CEYLONNOOSGI,                                "opt.ceylonnoosgi"),
        new COption(CEYLONOSGIPROVIDEDBUNDLES,"opt.arg.value", "opt.ceylonosgiprovidedbundles"),
        new COption(CEYLONNOPOM,                                 "opt.ceylonnopom"),
        new COption(CEYLONPACK200,                               "opt.ceylonpack200"),
        new COption(CEYLONRESOURCEROOT,     "opt.arg.path",      "opt.ceylonresourceroot"),
        new COption(CEYLONDISABLEOPT,                            "opt.ceylondisableopt"),
        new COption(CEYLONDISABLEOPT_CUSTOM,                     "opt.ceylondisableopt.suboptlist"),
        new COption(CEYLONSUPPRESSWARNINGS, "opt.arg.value",     "opt.ceylonsuppresswarnings"),
        new Option(SOURCEPATH,              "opt.arg.path",      "opt.sourcepath"){
            @Override
            public boolean process(Options options, String option, String arg) {
                if(options != null)
                    options.addMulti(SOURCEPATH, arg);
                return false;
            }
        },
        new COption(CEYLONSOURCEPATH,       "opt.arg.directory", "opt.ceylonsourcepath"){
            @Override
            public boolean process(Options options, String option, String arg) {
                if(options != null)
                    options.addMulti(SOURCEPATH, arg);
                return false;
            }
        },
        new COption(CEYLONRESOURCEPATH,     "opt.arg.url",      "opt.ceylonresourcepath"){
            @Override
            public boolean process(Options options, String option, String arg) {
                if(options != null)
                    options.addMulti(CEYLONRESOURCEPATH, arg);
                return false;
            }
        },
        new Option(BOOTCLASSPATH,           "opt.arg.path",      "opt.bootclasspath") {
            @Override
            public boolean process(Options options, String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process(options, option, arg);
            }
        },
        new XOption(XBOOTCLASSPATH_PREPEND,"opt.arg.path", "opt.Xbootclasspath.p"),
        new XOption(XBOOTCLASSPATH_APPEND, "opt.arg.path", "opt.Xbootclasspath.a"),
        new XOption(XBOOTCLASSPATH,        "opt.arg.path", "opt.bootclasspath") {
            @Override
            public boolean process(Options options, String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process(options, "-bootclasspath", arg);
            }
        },
        new Option(EXTDIRS,                "opt.arg.dirs",      "opt.extdirs"),
        new XOption(DJAVA_EXT_DIRS,        "opt.arg.dirs",      "opt.extdirs") {
            @Override
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-extdirs", arg);
            }
        },
        new Option(ENDORSEDDIRS,            "opt.arg.dirs",     "opt.endorseddirs"),
        new XOption(DJAVA_ENDORSED_DIRS,    "opt.arg.dirs",     "opt.endorseddirs") {
            @Override
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-endorseddirs", arg);
            }
        },
        new Option(PROC,                                 "opt.proc.none.only",
                Option.ChoiceKind.ONEOF, "none", "only"),
        new Option(PROCESSOR,           "opt.arg.class.list",   "opt.processor"),
        new Option(PROCESSORPATH,       "opt.arg.path",         "opt.processorpath"),
        new Option(D,                   "opt.arg.directory",    "opt.d"),
        new COption(CEYLONOUT,           "opt.arg.url",         "opt.ceylonout"){
            @Override
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-d", arg);
            } 
        },
        new COption(CEYLONOFFLINE,      "opt.ceylonoffline"),
        new COption(CEYLONTIMEOUT,      "opt.arg.number",       "opt.ceylontimeout"),
        new COption(CEYLONCONTINUE,     "opt.ceyloncontinue"),
        new COption(CEYLONPROGRESS,     "opt.ceylonprogress"),
        new COption(CEYLONAUTOEXPORTMAVENDEPENDENCIES,          "opt.ceylonautoexportmavendependencies"),
        new COption(CEYLONFLATCLASSPATH,  "opt.ceylonflatclasspath"),
        new COption(CEYLONOVERRIDES,      "opt.arg.url",        "opt.ceylonoverrides"),
        // backwards-compat
        new COption(CEYLONMAVENOVERRIDES, "opt.arg.url",        "opt.ceylonoverrides"){
            @Override
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-overrides", arg);
            } 
        },
        new Option(S,                   "opt.arg.directory",    "opt.sourceDest"),
        new Option(IMPLICIT,                                    "opt.implicit",
                Option.ChoiceKind.ONEOF, "none", "class"),
        new Option(ENCODING,            "opt.arg.encoding",     "opt.encoding") {
            @Override
            public boolean process(Options options, String option, String operand) {
                try {
                    Charset.forName(operand);
                    options.put(option, operand);
                    return false;
                } catch (UnsupportedCharsetException e) {
                    helper.error("err.unsupported.encoding", operand);
                    return true;
                } catch (IllegalCharsetNameException e) {
                    helper.error("err.unsupported.encoding", operand);
                    return true;
                }
            }
        },
        new Option(SOURCE,              "opt.arg.release",      "opt.source") {
            @Override
            public boolean process(Options options, String option, String operand) {
                Source source = Source.lookup(operand);
                if (source == null) {
                    helper.error("err.invalid.source", operand);
                    return true;
                }
                return super.process(options, option, operand);
            }
        },
        new Option(TARGET,              "opt.arg.release",      "opt.target") {
            @Override
            public boolean process(Options options, String option, String operand) {
                Target target = Target.lookup(operand);
                if (target == null) {
                    helper.error("err.invalid.target", operand);
                    return true;
                }
                return super.process(options, option, operand);
            }
        },
        new COption(VERSION,                                     "opt.version") {
            @Override
            public boolean process(Options options, String option) {
                helper.printVersion();
                return super.process(options, option);
            }
        },
        new HiddenOption(FULLVERSION) {
            @Override
            public boolean process(Options options, String option) {
                helper.printFullVersion();
                return super.process(options, option);
            }
        },
        new HiddenOption(DIAGS) {
            @Override
            public boolean process(Options options, String option) {
                Option xd = getOptions(helper, EnumSet.of(XD))[0];
                option = option.substring(option.indexOf('=') + 1);
                String diagsOption = option.contains("%") ?
                    "-XDdiagsFormat=" :
                    "-XDdiags=";
                diagsOption += option;
                if (xd.matches(diagsOption))
                    return xd.process(options, diagsOption);
                else
                    return false;
            }
        },
        new COption(HELP,                                        "opt.help") {
            @Override
            public boolean process(Options options, String option) {
                helper.printHelp();
                return super.process(options, option);
            }
        },
        new Option(A,                "opt.arg.key.equals.value","opt.A") {
            @Override
            String helpSynopsis() {
                hasSuffix = true;
                return super.helpSynopsis();
            }

            @Override
            public boolean matches(String arg) {
                return arg.startsWith("-A");
            }

            @Override
            public boolean hasArg() {
                return false;
            }
            // Mapping for processor options created in
            // JavacProcessingEnvironment
            @Override
            public boolean process(Options options, String option) {
                int argLength = option.length();
                if (argLength == 2) {
                    helper.error("err.empty.A.argument");
                    return true;
                }
                int sepIndex = option.indexOf('=');
                String key = option.substring(2, (sepIndex != -1 ? sepIndex : argLength) );
                if (!JavacProcessingEnvironment.isValidOptionName(key)) {
                    helper.error("err.invalid.A.key", option);
                    return true;
                }
                return process(options, option, option);
            }
        },
        new Option(X,                                           "opt.X") {
            @Override
            public boolean process(Options options, String option) {
                helper.printXhelp();
                return super.process(options, option);
            }
        },

        // This option exists only for the purpose of documenting itself.
        // It's actually implemented by the launcher.
        new Option(J,                   "opt.arg.flag",         "opt.J") {
            @Override
            String helpSynopsis() {
                hasSuffix = true;
                return super.helpSynopsis();
            }
            @Override
            public boolean process(Options options, String option) {
                throw new AssertionError
                    ("the -J flag should be caught by the launcher.");
            }
        },

        // stop after parsing and attributing.
        // new HiddenOption("-attrparseonly"),

        // new Option("-moreinfo",                                      "opt.moreinfo") {
        new HiddenOption(MOREINFO) {
            @Override
            public boolean process(Options options, String option) {
                Type.moreInfo = true;
                return super.process(options, option);
            }
        },

        // treat warnings as errors
        new Option(WERROR,                                      "opt.Werror"),


        new Option(SRC,                     "opt.arg.src",      "opt.src") {
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-src", arg);
            }
        },

        // use complex inference from context in the position of a method call argument
        new HiddenOption(COMPLEXINFERENCE),

        // generare source stubs
        // new HiddenOption("-stubs"),

        // relax some constraints to allow compiling from stubs
        // new HiddenOption("-relax"),

        // output source after translating away inner classes
        // new Option("-printflat",                             "opt.printflat"),
        // new HiddenOption("-printflat"),

        // display scope search details
        // new Option("-printsearch",                           "opt.printsearch"),
        // new HiddenOption("-printsearch"),

        // prompt after each error
        // new Option("-prompt",                                        "opt.prompt"),
        new HiddenOption(PROMPT),

        // dump stack on error
        new HiddenOption(DOE),

        // output source after type erasure
        // new Option("-s",                                     "opt.s"),
        new HiddenOption(PRINTSOURCE),

        // allow us to compile ceylon.language
        new HiddenOption(BOOTSTRAPCEYLON),

        // output shrouded class files
        // new Option("-scramble",                              "opt.scramble"),
        // new Option("-scrambleall",                           "opt.scrambleall"),

        // display warnings for generic unchecked operations
        new HiddenOption(WARNUNCHECKED) {
            @Override
            public boolean process(Options options, String option) {
                options.put("-Xlint:unchecked", option);
                return false;
            }
        },

        new XOption(XMAXERRS,           "opt.arg.number",       "opt.maxerrs"),
        new XOption(XMAXWARNS,          "opt.arg.number",       "opt.maxwarns"),
        new XOption(XSTDOUT,            "opt.arg.file",         "opt.Xstdout") {
            @Override
            public boolean process(Options options, String option, String arg) {
                try {
                    helper.setOut(new PrintWriter(new FileWriter(arg), true));
                } catch (java.io.IOException e) {
                    helper.error("err.error.writing.file", arg, e);
                    return true;
                }
                return super.process(options, option, arg);
            }
        },

        new XOption(XPRINT,                                     "opt.print"),

        new XOption(XPRINTROUNDS,                               "opt.printRounds"),

        new XOption(XPRINTPROCESSORINFO,                        "opt.printProcessorInfo"),

        new XOption(XPREFER,                                    "opt.prefer",
                Option.ChoiceKind.ONEOF, "source", "newer"),

        new XOption(XPKGINFO,                                   "opt.pkginfo",
                Option.ChoiceKind.ONEOF, "always", "legacy", "nonempty"),

        /* -O is a no-op, accepted for backward compatibility. */
        new HiddenOption(O),

        /* -Xjcov produces tables to support the code coverage tool jcov. */
        new HiddenOption(XJCOV),

        /* This is a back door to the compiler's option table.
         * -XDx=y sets the option x to the value y.
         * -XDx sets the option x to the value x.
         */
        new HiddenOption(XD) {
            String s;
            @Override
            public boolean matches(String s) {
                this.s = s;
                return s.startsWith(name.optionName);
            }
            @Override
            public boolean process(Options options, String option) {
                s = s.substring(name.optionName.length());
                int eq = s.indexOf('=');
                String key = (eq < 0) ? s : s.substring(0, eq);
                String value = (eq < 0) ? s : s.substring(eq+1);
                options.put(key, value);
                return false;
            }
        },

        // This option exists only for the purpose of documenting itself.
        // It's actually implemented by the CommandLine class.
        new Option(AT,                   "opt.arg.file",         "opt.AT") {
            @Override
            String helpSynopsis() {
                hasSuffix = true;
                return super.helpSynopsis();
            }
            @Override
            public boolean process(Options options, String option) {
                throw new AssertionError
                    ("the @ flag should be caught by CommandLine.");
            }
        },

        /*
         * TODO: With apt, the matches method accepts anything if
         * -XclassAsDecls is used; code elsewhere does the lookup to
         * see if the class name is both legal and found.
         *
         * In apt, the process method adds the candidate class file
         * name to a separate list.
         */
        new HiddenOption(SOURCEFILE) {
            String s;
            @Override
            public boolean matches(String s) {
                this.s = s;
                return s.endsWith(".java")  // Java source file
                        || s.endsWith(".ceylon") // FIXME: Should be a FileManager query
                        || "default".equals(s) // FIX for ceylon because default is not a valid name for Java
                        || isCeylonName(s)   // Legal type name for Ceylon
                        || (new File(s)).isFile();   // Possibly a resource file
            }
            @Override
            public boolean process(Options options, String option) {
                File f = new File(s);
                if (s.endsWith(".java")
                        || s.endsWith(".ceylon") // FIXME: Should be a FileManager query
                ) {
                    // Most likely a source file
                    if (!f.isFile()) {
                        // -sourcepath not -src because the COption for 
                        // CEYLONSOURCEPATH puts it in the options map as -sourcepath
                        List<String> sourcePaths = options.getMulti("-sourcepath");
                        if(sourcePaths.isEmpty())
                            sourcePaths = FileUtil.filesToPathList(DefaultToolOptions.getCompilerSourceDirs());
                        if (checkIfModule(sourcePaths, s)) {
                            // A Ceylon module name that ends with .ceylon or .java
                            helper.addClassName(s);
                            return false;
                        }

                        if (f.exists()) {
                            helper.error("err.file.not.file", f);
                            return true;
                        }
                    }
                    if (!f.exists()) {
                        helper.error("err.file.not.found", f);
                        return true;
                    }
                    helper.addFile(f);
                } 
                if (f.isFile()) {
                    // Most likely a resource file
                    helper.addFile(f);
                } else {
                    // Should be a module name
                    // the default module is always allowed, it doesn't need to have any folder
                    if(s.equals(Module.DEFAULT_MODULE_NAME)){
                        helper.addClassName(s);
                        return false;
                    }
                    // find a corresponding physical module in the source path
                    List<String> sourcePaths = options.getMulti("-sourcepath");
                    if(sourcePaths.isEmpty())
                        sourcePaths = FileUtil.filesToPathList(DefaultToolOptions.getCompilerSourceDirs());
                    if (checkIfModule(sourcePaths, s)) {
                        helper.addClassName(s);
                        return false;
                    }

                    String paths = sourcePaths.toString();
                    helper.error("err.module.not.found", s, paths.substring(1,  paths.length()-1));
                    return true;
                }
                return false;
            }
            
            private boolean checkIfModule(List<String> paths, String moduleName) {
                String moduleDirName = moduleName.replace(".", File.separator);
                // walk every path arg
                for(String path : paths){
                    // split the path
                    for(String part : path.split("\\"+File.pathSeparator)){
                        // try to see if it's a module folder
                        File moduleFolder = new File(part, moduleDirName);
                        if (moduleFolder.isDirectory()) {
                            return true;
                        }
                    }
                }
                return false;
            }
        },
        };
    }

    /**
     * Version of isName for Ceylon which allows keywords since we do allow them as part of module names 
     * @param name
     * @return
     */
    public static boolean isCeylonName(CharSequence name) {
        String id = name.toString();

        for(String s : id.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }

    public enum PkgInfo {
        ALWAYS, LEGACY, NONEMPTY;
        public static PkgInfo get(Options options) {
            String v = options.get(XPKGINFO);
            return (v == null
                    ? PkgInfo.LEGACY
                    : PkgInfo.valueOf(v.toUpperCase()));
        }
    }

    private static Map<String,Boolean> getXLintChoices() {
        Map<String,Boolean> choices = new LinkedHashMap<String,Boolean>();
        choices.put("all", false);
        for (Lint.LintCategory c : Lint.LintCategory.values())
            choices.put(c.option, c.hidden);
        for (Lint.LintCategory c : Lint.LintCategory.values())
            choices.put("-" + c.option, c.hidden);
        choices.put("none", false);
        return choices;
    }

}
