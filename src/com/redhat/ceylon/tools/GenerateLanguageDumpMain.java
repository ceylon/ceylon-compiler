/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.tools;

import java.io.File;
import java.io.IOException;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;

public class GenerateLanguageDumpMain {
    private static final String CEYLON_VERSION = "0.3.1 'V2000'";
    private static final int SC_OK = 0;
    private static final int SC_ARGS = 1;
    private static final int SC_ERROR = 2;

    public static void main(String[] args) throws IOException {
        String destFile = null;
        String languageSrc = null;
        boolean verbose = false;
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            int argsLeft = args.length - 1 - i;
            if ("-h".equals(arg)
                    || "-help".equals(arg)
                    || "--help".equals(arg)) {
                printUsage(SC_OK);
            } else if ("-v".equals(arg)
                        || "-version".equals(arg)
                        || "--version".equals(arg)) {
                printVersion();
            } else if ("-debug".equals(arg)) {
                verbose = true;
            } else if ("-out".equals(arg)) {
                if (argsLeft <= 0) {
                    optionMissingArgument(arg);
                }
                destFile = args[++i];
            } else if ("-src".equals(arg)) {
                if (argsLeft <= 0) {
                    optionMissingArgument(arg);
                }
                languageSrc = args[++i];
            } else if (arg.startsWith("-")) {
                System.err.println(GenerateLanguageDumpMessages.msg("error.optionUnknown", arg));
                exit(SC_ARGS);
            } else {
                System.err.println(GenerateLanguageDumpMessages.msg("error.tooManyArguments", arg));
                exit(SC_ARGS);
            }
            
        }
        
        File file;
        
        if (destFile == null) {
            file = File.createTempFile("ceylon.language-"+TypeChecker.LANGUAGE_MODULE_VERSION, ".dump");
        }else{
            file = new File(destFile);
        }
        
        if(languageSrc == null){
            languageSrc = "../ceylon.language/src";
        }
        File languageDir = new File(languageSrc);

        try{
            GenerateLanguageDump tool = new GenerateLanguageDump(file, languageDir, verbose);
            tool.dump();
        }catch(GenerateLanguageDumpException x){
            System.err.println(GenerateLanguageDumpMessages.msg("error", x.getLocalizedMessage()));
            // no need to print the stack trace
            exit(SC_ERROR);
        }catch(Exception x){
            System.err.println(GenerateLanguageDumpMessages.msg("error", x.getLocalizedMessage()));
            x.printStackTrace();
            exit(SC_ERROR);
        }
    }

    private static void exit(int statusCode) {
        System.exit(statusCode);
    }
    
    private static void optionMissingArgument(String arg) {
        System.err.println(GenerateLanguageDumpMessages.msg("error.optionMissing", arg));
        exit(SC_ARGS);
    }

    private static void printVersion() {
        System.out.println(GenerateLanguageDumpMessages.msg("info.version", CEYLON_VERSION));
        exit(SC_OK);
    }

    private static void printUsage(int statusCode) {
        System.err.print(GenerateLanguageDumpMessages.msg("info.usage"));
        exit(statusCode);
    }

}