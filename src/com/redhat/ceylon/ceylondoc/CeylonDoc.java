package com.redhat.ceylon.ceylondoc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;

public abstract class CeylonDoc {

	protected Writer writer;
    protected String destDir;

	public CeylonDoc(final String destDir) {
		this.destDir = destDir;
	}

	protected void setupWriter() throws IOException {
	    this.writer = new FileWriter(getOutputFile());
	}

	protected abstract File getOutputFile();

    protected void write(final String... text) throws IOException {
		for (String s : text) {
			writer.append(s);
		}
	}

	protected void tag(final String... tags) throws IOException {
		for (String tag : tags) {
			writer.append("<").append(tag).append("/>\n");
		}
	}

	protected String getPathToBase(final ClassOrInterface klass) {
		return getPathToBase(getPackage(klass));
	}
	protected String getPathToBase(final Package pkg) {
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = pkg.getName().size() - 1; i >= 0; i--) {
			stringBuilder.append("..");
			if (i > 0) {
				stringBuilder.append("/");
			}
		}
		return stringBuilder.toString();
	}

	protected void link(final ProducedType type) throws IOException {
		final TypeDeclaration decl = type.getDeclaration();
		link(decl, false);
	}

	protected void link(final TypeDeclaration decl, final boolean qualified) throws IOException {
		if (decl instanceof UnionType) {
			boolean first = true;
			for (TypeDeclaration ud : ((UnionType)decl).getCaseTypeDeclarations()) {
				if (first) {
					first = false;
				} else {
					write("|");
				}
				link(ud, qualified);
			}
		} else if (decl instanceof ClassOrInterface) {
			link((ClassOrInterface) decl, qualified);
        } else if (decl instanceof TypeParameter) {
            around("span class='type-parameter'", decl.getName());
		} else {
			write(decl.toString());
		}
	}

	protected void link(final ClassOrInterface decl, final boolean qualified) throws IOException {
		final String path = getPathToBase() + "/" + join("/", getPackage(decl).getName()) + "/" + getFileName(decl);
		around("a href='" + path + "'", qualified ? decl.getQualifiedNameString() : decl.getName());
	}

	protected abstract String getPathToBase();

	protected String getFileName(final Scope scope) {
		Scope klass = scope;
		final List<String> name = new LinkedList<String>();
		while (klass instanceof Declaration) {
			name.add(0, ((Declaration) klass).getName());
			klass = klass.getContainer();
		}
		return join(".", name) + ".html";
	}

	protected File getFolder(final Package pkg) {
		final File dir = new File(destDir, join("/", pkg.getName()));
		dir.mkdirs();
		return dir;
	}

	protected File getFolder(ClassOrInterface klass) {
		return getFolder(getPackage(klass));
	}

	protected static Package getPackage(final Scope scope) {
		Scope decl = scope;
		while (!(decl instanceof Package)) {
			decl = decl.getContainer();
		}
		return (Package) decl;
	}

	protected void around(final String tag, final String... text) throws IOException {		
		open(tag);
		for (String s : text) {
			writer.append(s);
		}
		String result = tag;
		final int space = result.indexOf(' ');
		if (space > -1) {
			result = tag.substring(0, space);
		}
		close(result);
	}

	protected void close(final String... tags) throws IOException {
		for (String tag : tags) {
			writer.append("</").append(tag).append(">\n");
		}
	}

	protected void open(final String... tags) throws IOException {
		for (String tag : tags) {
			writer.append("<").append(tag).append(">\n");
		}
	}

	protected static String join(final String str, final List<String> parts) {
		final StringBuilder stringBuilder = new StringBuilder();
		final Iterator<String> iterator = parts.iterator();
		while (iterator.hasNext()) {
			stringBuilder.append(iterator.next());
			if (iterator.hasNext()) {
				stringBuilder.append(str);
			}
		}
		return stringBuilder.toString();
	}

	protected void openTable(final String title) throws IOException {
	    open("table");
	    open("tr class='TableHeadingColor'");
	    around("th", title);
	    close("tr");
	}

	protected void openTable(final String title, final String firstColumnTitle, final String secondColumnTitle) throws IOException {
	    open("table");
	    open("tr class='TableHeadingColor'");
	    around("th colspan='2'", title);
	    close("tr");
	    open("tr class='TableSubHeadingColor'");
	    around("th", firstColumnTitle);
	    around("th", secondColumnTitle);
	    close("tr");
	}

   protected String unquote(final String string) {
        return string.substring(1, string.length() - 1);
   }

}
