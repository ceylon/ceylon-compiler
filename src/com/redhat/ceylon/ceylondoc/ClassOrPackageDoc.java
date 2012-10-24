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
import static com.redhat.ceylon.ceylondoc.Util.getModifiers;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.FunctionalParameter;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;

public abstract class ClassOrPackageDoc extends CeylonDoc {

	public ClassOrPackageDoc(Module module, CeylonDocTool tool, Writer writer) {
		super(module, tool, writer);
	}

    protected final void doc(ClassOrInterface d) throws IOException {
        open("tr");
        
        open("td id='" + d.getName() + "' nowrap");
        writeIcon(d);
        around("a class='link-discreet' href='"+ linkRenderer().to(d).getUrl() +"'", d.getName());
        close("td");
        
        open("td");
        writeLinkSourceCode(d);
        writeTagged(d);
        open("div class='signature'");
        around("span class='modifiers'", getModifiers(d));
        write(" ");
        linkRenderer().to(d.getType()).write();
        close("div");
        writeDescription(d);
        close("td");
        
        close("tr");
    }

    protected final void doc(MethodOrValue d) throws IOException {
        // put the id on the td because IE8 doesn't support id attributes on tr (yeah right)
        open("tr");
        
        open("td id='" + d.getName() + "' nowrap");
        writeIcon(d);
        write(d.getName());
        close("td");
        
        open("td");
        writeLinkSource(d);
        writeTagged(d);
        
        open("div class='signature'");
        around("span class='modifiers'", getModifiers(d));
        write(" ");
        linkRenderer().to(d.getType()).write();
        write(" ");
        write(d.getName());
        if( d instanceof Method ) {
            Method m = (Method) d;
            writeTypeParameters(m);
            writeParameterList(m);
        }
        close("div");
        writeDescription(d);
        close("td");
        close("tr");
    }
    
    private void writeDescription(Declaration d) throws IOException {
        open("div class='description'");
        writeDeprecated(d);
        around("div class='doc section'", getDoc(d, linkRenderer()));
        if( d instanceof MethodOrValue ) {
        	writeParameters(d);
            writeThrows(d);        
            writeBy(d);
            writeSee(d);
        }
        close("div"); // description
    }

    private void writeLinkSource(MethodOrValue m) throws IOException {
        if (!tool.isIncludeSourceCode()) {
            return;
        }
        String srcUrl;
        if (m.isToplevel()) {
            srcUrl = linkRenderer().getSrcUrl(m);
        } else {
            srcUrl = linkRenderer().getSrcUrl(m.getContainer());
        }
        int[] lines = tool.getDeclarationSrcLocation(m);
        if(lines != null){
            open("a class='link-source-code member' href='" + srcUrl + "#" + lines[0] + "," + lines[1] + "'");
            write("<i class='icon-source-code'></i>");
            write("Source Code");
            close("a");
        }
    }

    private void writeTypeParameters(Method m) throws IOException {
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
    }

    protected final void writeParameterList(Functional f) throws IOException {
        for (ParameterList lists : f.getParameterLists()) {
            write("(");
            boolean first = true;
            for (Parameter param : lists.getParameters()) {
                if (!first) {
                    write(", ");
                } else {
                    first = false;
                }
                
                if (param instanceof FunctionalParameter) {
                    writeFunctionalParameter((FunctionalParameter) param);
                } else if (param.isSequenced()) {
                    writeSequencedParameter(param);
                } else {
                    linkRenderer().to(param.getType()).write();
                    write(" ", param.getName());
                }
            }
            write(")");
        }
    }

    private void writeFunctionalParameter(FunctionalParameter functionParam) throws IOException {
        linkRenderer().to(functionParam.getType()).write();
        write(" ");
        write(functionParam.getName());
        writeParameterList(functionParam);
    }

    private void writeSequencedParameter(Parameter param) throws IOException {
        ProducedType sequencedParamType = param.getUnit().getIteratedType(param.getType());
        linkRenderer().to(sequencedParamType).write();
        write("...");
        write(" ", param.getName());
    }

    protected final void writeParameters(Declaration decl) throws IOException {
    	if( decl instanceof Functional ) {
    		boolean first = true;
    		List<ParameterList> parameterLists = ((Functional)decl).getParameterLists();
    		for (ParameterList parameterList : parameterLists) {
    			for (Parameter parameter : parameterList.getParameters()) {
    				String doc = getDoc(parameter, linkRenderer());
    				if( !doc.isEmpty() ) {
    					if( first ) {
    						first = false;
    						open("div class='parameters section'");
    						around("span class='title'", "Parameters: ");
    						open("ul");
    					}
    					open("li");
    					write(parameter.getName());
    					write(doc);
    					close("li");
    				}
    			}
    		}    			
    		if (!first) {
    			close("ul");
    			close("div");
    		}
    	}
    }

	private void writeThrows(Declaration decl) throws IOException {
        boolean first = true;
        for (Annotation annotation : decl.getAnnotations()) {
            if (annotation.getName().equals("throws")) {

                String excType = annotation.getPositionalArguments().get(0);
                String excDesc = annotation.getPositionalArguments().size() == 2 ? annotation.getPositionalArguments().get(1) : null;
                
                if (first) {
                    first = false;
                    open("div class='throws section'");
                    around("span class='title'", "Throws: ");
                    open("ul");
                }

                open("li");
                
                linkRenderer().to(excType).useScope(decl).write();

                if (excDesc != null) {
                    write(Util.wikiToHTML(Util.unquote(excDesc), linkRenderer().useScope(decl)));
                }

                close("li");
            }
        }
        if (!first) {
            close("ul");
            close("div");
        }
    }
    
    private void writeDeprecated(Declaration decl) throws IOException {
        Annotation deprecated = Util.findAnnotation(decl, "deprecated");
        if (deprecated != null) {
            open("div class='deprecated section'");
            String text = "__Deprecated:__ ";
            if (!deprecated.getPositionalArguments().isEmpty()) {
                String reason = deprecated.getPositionalArguments().get(0);
                if (reason != null) {
                    text += Util.unquote(reason);
                }
            }
            write(Util.wikiToHTML(text, linkRenderer().useScope(decl)));
            close("div");
        }
    }
    
    protected final void writeSee(Declaration decl) throws IOException {
        Annotation see = Util.getAnnotation(decl, "see");
        if(see == null)
            return;

        open("div class='see section'");
        around("span class='title'", "See also: ");
        
        open("span class='value'");
        boolean first = true;
        for (String target : see.getPositionalArguments()) {
            if (!first) {
                write(", ");
            } else {
                first = false;
            }
            linkRenderer().to(target).useScope(decl).write();
        }
        close("span");
        
        close("div");
    }

    protected final void writeTagged(Declaration decl) throws IOException {
        List<String> tags = Util.getTags(decl);
        if (!tags.isEmpty()) {
            open("div class='tags section'");
            Iterator<String> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                String tag = tagIterator.next();
                write("<a class='tag label' name='" + tag + "' href='search.html?q=" + tag + "'>" + tag + "</a>");
            }
            close("div");
        }
    }    

}