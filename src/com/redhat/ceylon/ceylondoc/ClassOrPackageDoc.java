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

package com.redhat.ceylon.ceylondoc;

import static com.redhat.ceylon.ceylondoc.Util.getDoc;
import static com.redhat.ceylon.ceylondoc.Util.getDocFirstLine;
import static com.redhat.ceylon.ceylondoc.Util.getModifiers;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.Value;

public abstract class ClassOrPackageDoc extends CeylonDoc {

	public ClassOrPackageDoc(Module module, CeylonDocTool tool, Writer writer) {
		super(module, tool, writer);
	}

    protected void writeSee(Declaration decl) throws IOException {
        Annotation see = Util.getAnnotation(decl, "see");
        if(see == null)
            return;
        
        boolean first = true;
        open("div class='see'");
        write("See also: ");
        for (String target : see.getPositionalArguments()) {
            // try to resolve in containing scopes
            
            Scope declScope = resolveScope(decl);
            Declaration targetDecl = resolveDeclaration(declScope, target);
            if(targetDecl != null){
                if (!first) {
                    write(", ");
                } else {
                    first = false;
                }
                if (targetDecl instanceof TypeDeclaration) {
                    link(((TypeDeclaration) targetDecl).getType());
                } else if (targetDecl.isMember()) {
                    linkToMember(targetDecl);
                }
            }
        }
        close("div");
    }

    private Scope resolveScope(Declaration decl) {
        if (decl == null) {
            return null;
        } else if (decl instanceof Scope) {
            return (Scope) decl;
        } else {
            return decl.getContainer();
        }
    }

    private Declaration resolveDeclaration(Scope decl, String target) {
        if(decl == null)
            return null;
        Declaration member = decl.getMember(target, null);
        if (member != null)
            return member;
        return resolveDeclaration(decl.getContainer(), target);
    }

    protected void doc(Method m) throws IOException {
        open("tr class='TableRowColor' id='" + m.getName() + "'");
        open("td", "code");
        writeIcon(m);
        around("span class='modifiers'", getModifiers(m));
        write(" ");
        link(m.getType());
        close("code", "td");
        open("td");
        linkSource(m);
        writeTagged(m);
        open("code");
        write(m.getName());
        List<TypeParameter> typeParameters = m.getTypeParameters();
        if (!typeParameters.isEmpty()) {
            write("&lt;");
            boolean first = true;
            for (TypeParameter type : typeParameters) {
                if (first)
                    first = false;
                else
                    write(", ");
                write(type.getName());
            }
            write("&gt;");
        }
        writeParameterList(m.getParameterLists());
        close("code");
        
        startPrintingLongDoc(m);
        writeThrows(m);
        writeSee(m);
        endLongDocAndPrintShortDoc(m);
        close("td");
        close("tr");
    }

    private void linkSource(MethodOrValue m) throws IOException {
        if (!tool.isIncludeSourceCode()) {
            return;
        }
        String srcUrl;
        if (m.isToplevel()) {
            srcUrl = getSrcUrl(m);
        } else {
            srcUrl = getSrcUrl(m.getContainer());
        }
        int[] lines = tool.getDeclarationSrcLocation(m);
        if(lines != null){
            open("div class='source-code member'");
            around("a href='" + srcUrl + "#" + lines[0] + "," + lines[1] + "'", "Source Code");
            close("div");
        }
    }

    protected void doc(MethodOrValue f) throws IOException {
        if (f instanceof Value) {
            f = (Value) f;
        }
        open("tr class='TableRowColor' id='" + f.getName() + "'");
        open("td", "code");
        writeIcon(f);
        around("span class='modifiers'", getModifiers(f));
        write(" ");
        link(f.getType());
        close("code", "td");
        open("td");
        linkSource(f);
        writeTagged(f);
        open("code");
        write(f.getName());
        close("code");
        startPrintingLongDoc(f);
        writeThrows(f);
        writeSee(f);
        endLongDocAndPrintShortDoc(f);
        close("td");
        close("tr");
    }

    protected void writeParameterList(List<ParameterList> parameterLists) throws IOException {
        for (ParameterList lists : parameterLists) {
            write("(");
            boolean first = true;
            for (Parameter param : lists.getParameters()) {
                if (!first) {
                    write(", ");
                } else {
                    first = false;
                }
                link(param.getType());
                write(" ", param.getName());
            }
            write(")");
        }
    }

    protected void endLongDocAndPrintShortDoc(Declaration d) throws IOException {
        close("div");
        open("div class='short'");
        writeDeprecated(d);
        around("div class='doc'", getDocFirstLine(d));
        close("div");
    }

    protected void startPrintingLongDoc(Declaration d) throws IOException {
        open("div class='long'");
        writeDeprecated(d);
        around("div class='doc'", getDoc(d));
    }

    protected abstract void subMenu() throws IOException;
    
    protected void printSubMenuItem(String id, String title) throws IOException {
        open("div");
        around("a href='#"+id+"'", title);
        close("div");
    }
    
    protected void writeThrows(Declaration decl) throws IOException {
        boolean first = true;
        for (Annotation annotation : decl.getAnnotations()) {
            if (annotation.getName().equals("throws")) {

                String excType = annotation.getPositionalArguments().get(0);
                String excDesc = annotation.getPositionalArguments().size() == 2 ? annotation.getPositionalArguments().get(1) : null;
                
                if (first) {
                    first = false;
                    open("div class='throws'");
                    write("Throws: ");
                    open("ul");
                }

                open("li");

                Scope declScope = resolveScope(decl);
                Declaration excTypeDecl = resolveDeclaration(declScope, excType);
                if (excTypeDecl instanceof TypeDeclaration) {
                    link(((TypeDeclaration)excTypeDecl).getType());
                } else {
                    write(excType);
                }

                if (excDesc != null) {
                    write(Util.wikiToHTML(Util.unquote(excDesc)));
                }

                close("li");
            }
        }
        if (!first) {
            close("ul");
            close("div");
        }
    }
    
    protected void writeDeprecated(Declaration decl) throws IOException {
        Annotation deprecated = Util.getAnnotation(decl, "deprecated");
        if (deprecated != null) {
            open("div class='deprecated'");
            String text = "__Deprecated:__ ";
            if (!deprecated.getPositionalArguments().isEmpty()) {
                String reason = deprecated.getPositionalArguments().get(0);
                if (reason != null) {
                    text += Util.unquote(reason);
                }
            }
            write(Util.wikiToHTML(text));
            close("div");
        }
    }
    
    protected void writeTagged(Declaration decl) throws IOException {
        List<String> tags = Util.getTags(decl);
        if (!tags.isEmpty()) {
            open("div class='tags'");
            write("<span class='tagCaption'>Tags: </span>");
            Iterator<String> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                String tag = tagIterator.next();
                write("<a class='tagLabel' name='" + tag + "' href='search.html?q=" + tag + "'>" + tag + "</a>");
            }
            close("div");
        }
    }

}