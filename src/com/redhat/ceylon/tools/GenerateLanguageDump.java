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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import com.redhat.ceylon.compiler.java.codegen.BoxingDeclarationVisitor;
import com.redhat.ceylon.compiler.java.codegen.BoxingVisitor;
import com.redhat.ceylon.compiler.java.util.Timer;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.TypeCheckerBuilder;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnyAttribute;

public class GenerateLanguageDump {

    private boolean verbose;
    private File file;
    private File languageSrc;

    public GenerateLanguageDump(File file, File languageSrc, boolean verbose) {
        this.file = file;
        this.languageSrc = languageSrc;
        this.verbose = verbose;
        if(!languageSrc.exists())
            throw new GenerateLanguageDumpException("error.languageSrc.doesNotExist");
        if(!languageSrc.isDirectory())
            throw new GenerateLanguageDumpException("error.languageSrc.isNotDirectory");
    }

    public void dump() {
        TypeCheckerBuilder builder = new TypeCheckerBuilder();
        builder.addSrcDirectory(languageSrc);
        TypeChecker typeChecker = builder.getTypeChecker();
        typeChecker.process();
        if(typeChecker.getErrors() > 0){
            throw new GenerateLanguageDumpException("error.typechecker", typeChecker.getErrors());
        }
        Module languageModule = typeChecker.getContext().getModules().getLanguageModule();
        // run some extra phases
        BoxingDeclarationVisitor boxingDeclarationVisitor = new BoxingDeclarationVisitor(){
            @Override
            public void visit(AnyAttribute that) {
                super.visit(that);
                TypedDeclaration model = that.getDeclarationModel();
                if(model == null)
                    return;
                if(model.getName().equals("hash")
                        && model.getContainer() instanceof ClassOrInterface)
                    model.getType().setUnderlyingType("int");
            }
            
            @Override
            protected boolean isCeylonBasicType(ProducedType type) {
                TypeDeclaration declaration = type.getDeclaration();
                if(declaration == null)
                    return false;
                Unit unit = declaration.getUnit();
                return declaration == unit.getStringDeclaration()
                        || declaration == unit.getBooleanDeclaration()
                        || declaration == unit.getIntegerDeclaration()
                        || declaration == unit.getFloatDeclaration()
                        || declaration == unit.getCharacterDeclaration();
            }
        };
        BoxingVisitor boxingVisitor = new BoxingVisitor(){

            @Override
            protected boolean isBooleanTrue(Declaration decl) {
                return decl == decl.getUnit().getLanguageModuleDeclaration("true");
            }

            @Override
            protected boolean isBooleanFalse(Declaration decl) {
                return decl == decl.getUnit().getLanguageModuleDeclaration("true");
            }
        };
        // Extra phases for the compiler
        List<PhasedUnit> phasedUnits = typeChecker.getPhasedUnits().getPhasedUnits();
        for (PhasedUnit pu : phasedUnits) {
            pu.getCompilationUnit().visit(boxingDeclarationVisitor);
        }
        for (PhasedUnit pu : phasedUnits) {
            pu.getCompilationUnit().visit(boxingVisitor);
        }

        if(verbose){
            System.err.println("Language module has "+languageModule.getPackages().size()+" packages");
        }

        ObjectOutputStream os;
        try {
            os = new ObjectOutputStream(new FileOutputStream(file));
            try{
                os.writeObject(languageModule);
                os.flush();
            }finally{
                os.close();
            }
        } catch (IOException e) {
            throw new GenerateLanguageDumpException("error.output", file.getAbsolutePath(), e.getLocalizedMessage());
        }
        System.err.println("Written to "+file.getAbsolutePath()+": "+file.length()+" bytes");
    }
}
