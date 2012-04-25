/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors as
 * indicated by the authors tag. All rights reserved.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in
 * the LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License, along with
 * this distribution; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.test.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.loader.CeylonModelLoader;
import com.redhat.ceylon.compiler.java.test.CompilerTest;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.compiler.java.tools.LanguageCompiler;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.loader.ModelLoader.DeclarationType;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.util.Context;

public class ModelLoaderTest extends CompilerTest
{

	private final Map<Declaration, Set<Declaration>>	alreadyCompared	=
																				new HashMap<Declaration, Set<Declaration>>();

	private boolean alreadyCompared(final Declaration validDeclaration,
			final Declaration modelDeclaration)
	{
		Set<Declaration> comparedDeclarations =
				alreadyCompared.get(modelDeclaration);
		if (comparedDeclarations == null)
		{
			comparedDeclarations = new HashSet<Declaration>();
			alreadyCompared.put(modelDeclaration, comparedDeclarations);
		}
		return !comparedDeclarations.add(validDeclaration);
	}

	private void compareAnnotation(final String element,
			final Annotation validAnnotation,
			final Annotation modelAnnotation)
	{
		Assert.assertEquals(element + " [annotation name]",
				validAnnotation.getName(), modelAnnotation.getName());
		final String name = element + "@" + validAnnotation.getName();
		final List<String> validPositionalArguments =
				validAnnotation.getPositionalArguments();
		final List<String> modelPositionalArguments =
				modelAnnotation.getPositionalArguments();
		Assert.assertEquals(name + " [annotation argument size]",
				validPositionalArguments.size(),
				modelPositionalArguments.size());
		for (int i = 0; i < validPositionalArguments.size(); i++)
		{
			Assert.assertEquals(name + " [annotation argument " + i + "]",
					validPositionalArguments.get(i),
					modelPositionalArguments.get(i));
		}
		final Map<String, String> validNamedArguments =
				validAnnotation.getNamedArguments();
		final Map<String, String> modelNamedArguments =
				modelAnnotation.getNamedArguments();
		Assert.assertEquals(name + " [annotation named argument size]",
				validNamedArguments.size(), modelNamedArguments.size());
		for (final Entry<String, String> validEntry : validNamedArguments
				.entrySet())
		{
			final String modelValue =
					modelNamedArguments.get(validEntry.getKey());
			Assert.assertEquals(name + " [annotation named argument "
					+ validEntry.getKey() + "]", validEntry.getValue(),
					modelValue);
		}
	}

	private void compareAnnotations(final Declaration validDeclaration,
			final Declaration modelDeclaration)
	{
		// let's not compare setter annotations
		if (validDeclaration instanceof Setter)
		{
			return;
		}
		final String name = validDeclaration.getQualifiedNameString();
		final List<Annotation> validAnnotations =
				validDeclaration.getAnnotations();
		final List<Annotation> modelAnnotations =
				modelDeclaration.getAnnotations();
		Assert.assertEquals(name + " [annotation count]",
				validAnnotations.size(), modelAnnotations.size());
		for (int i = 0; i < validAnnotations.size(); i++)
		{
			compareAnnotation(name, validAnnotations.get(i),
					modelAnnotations.get(i));
		}
	}

	private void
			compareAttributeDeclarations(final MethodOrValue validDeclaration,
					final Value modelDeclaration)
	{
		// let's not check Setters since their corresponding Getter is checked
		// already
		if (validDeclaration instanceof Setter)
		{
			return;
		}
		// make sure the flags are the same
		Assert.assertEquals(validDeclaration.getQualifiedNameString()
				+ " [variable]", validDeclaration.isVariable(),
				modelDeclaration.isVariable());
		Assert.assertEquals(validDeclaration.getQualifiedNameString()
				+ " [formal]", validDeclaration.isFormal(),
				modelDeclaration.isFormal());
		Assert.assertEquals(validDeclaration.getQualifiedNameString()
				+ " [actual]", validDeclaration.isActual(),
				modelDeclaration.isActual());
		Assert.assertEquals(validDeclaration.getQualifiedNameString()
				+ " [default]", validDeclaration.isDefault(),
				modelDeclaration.isDefault());
		// compare the types
		compareDeclarations(validDeclaration.getType().getDeclaration(),
				modelDeclaration.getType().getDeclaration());
	}

	private void compareClassDeclarations(final Class validDeclaration,
			final Class modelDeclaration)
	{
		final String name = validDeclaration.getQualifiedNameString();
		Assert.assertEquals(name + " [abstract]",
				validDeclaration.isAbstract(), modelDeclaration.isAbstract());
		// extended type
		if (validDeclaration.getExtendedTypeDeclaration() == null)
		{
			Assert.assertTrue(name + " [null supertype]",
					modelDeclaration.getExtendedTypeDeclaration() == null);
		}
		else
		{
			compareDeclarations(validDeclaration.getExtendedTypeDeclaration(),
					modelDeclaration.getExtendedTypeDeclaration());
		}
		// satisfied types!
		compareSatisfiedTypes(name,
				validDeclaration.getSatisfiedTypeDeclarations(),
				modelDeclaration.getSatisfiedTypeDeclarations());
		// parameters
		compareParameterLists(validDeclaration.getQualifiedNameString(),
				validDeclaration.getParameterLists(),
				modelDeclaration.getParameterLists());
		// make sure it has every member required
		for (final Declaration validMember : validDeclaration.getMembers())
		{
			// skip non-shared members
			if (!validMember.isShared())
			{
				continue;
			}
			final Declaration modelMember =
					lookupMember(modelDeclaration, validMember);
			Assert.assertNotNull(validMember.getQualifiedNameString()
					+ " [member] not found in loaded model", modelMember);
			compareDeclarations(validMember, modelMember);
		}
		// and not more
		for (final Declaration modelMember : modelDeclaration.getMembers())
		{
			// skip non-shared members
			if (!modelMember.isShared())
			{
				continue;
			}
			final Declaration validMember =
					lookupMember(validDeclaration, modelMember);
			Assert.assertNotNull(modelMember.getQualifiedNameString()
					+ " [extra member] encountered in loaded model",
					validMember);
		}
	}

	private void compareDeclarations(final Declaration validDeclaration,
			final Declaration modelDeclaration)
	{
		if (alreadyCompared(validDeclaration, modelDeclaration))
		{
			return;
		}
		final String name = validDeclaration.getQualifiedNameString();
		Assert.assertNotNull("Missing model declararion for: " + name,
				modelDeclaration);
		// check that we have a unit
		Assert.assertNotNull(
				"Missing Unit: " + modelDeclaration.getQualifiedNameString(),
				modelDeclaration.getUnit());
		Assert.assertNotNull("Invalid Unit", modelDeclaration.getUnit()
				.getPackage());
		// let's not check java stuff for now, due to missing types in the jdk's
		// private methods
		if (name.startsWith("java."))
		{
			return;
		}
		// only compare parameter names for public methods
		if (!(validDeclaration instanceof Parameter)
				|| validDeclaration.isShared())
		{
			Assert.assertEquals(name + " [name]",
					validDeclaration.getQualifiedNameString(),
					modelDeclaration.getQualifiedNameString());
		}
		Assert.assertEquals(name + " [shared]", validDeclaration.isShared(),
				modelDeclaration.isShared());
		// if they're not shared, stop at making sure they are the same type of
		// object
		if (!validDeclaration.isShared())
		{
			boolean sameType =
					validDeclaration.getClass().isAssignableFrom(
							modelDeclaration.getClass());
			// we may replace Getter or Setter with Value, no harm done
			sameType |=
					validDeclaration instanceof Getter
							&& modelDeclaration instanceof Value;
			sameType |=
					validDeclaration instanceof Setter
							&& modelDeclaration instanceof Value;
			Assert.assertTrue(name + " [type]", sameType);
			return;
		}
		compareAnnotations(validDeclaration, modelDeclaration);
		// full check
		if (validDeclaration instanceof Class)
		{
			Assert.assertTrue(name + " [Class]",
					modelDeclaration instanceof Class);
			compareClassDeclarations((Class) validDeclaration,
					(Class) modelDeclaration);
		}
		else if (validDeclaration instanceof Method)
		{
			Assert.assertTrue(name + " [Method]",
					modelDeclaration instanceof Method);
			compareMethodDeclarations((Method) validDeclaration,
					(Method) modelDeclaration);
		}
		else if (validDeclaration instanceof Value
				|| validDeclaration instanceof Getter
				|| validDeclaration instanceof Setter)
		{
			Assert.assertTrue(name + " [Attribute]",
					modelDeclaration instanceof Value);
			compareAttributeDeclarations((MethodOrValue) validDeclaration,
					(Value) modelDeclaration);
		}
		else if (validDeclaration instanceof TypeParameter)
		{
			Assert.assertTrue(name + " [TypeParameter]",
					modelDeclaration instanceof TypeParameter);
			compareTypeParameters((TypeParameter) validDeclaration,
					(TypeParameter) modelDeclaration);
		}
	}

	private void compareMethodDeclarations(final Method validDeclaration,
			final Method modelDeclaration)
	{
		final String name = validDeclaration.getQualifiedNameString();
		Assert.assertEquals(name + " [formal]", validDeclaration.isFormal(),
				modelDeclaration.isFormal());
		Assert.assertEquals(name + " [actual]", validDeclaration.isActual(),
				modelDeclaration.isActual());
		Assert.assertEquals(name + " [default]", validDeclaration.isDefault(),
				modelDeclaration.isDefault());
		// make sure it has every parameter list required
		final List<ParameterList> validParameterLists =
				validDeclaration.getParameterLists();
		final List<ParameterList> modelParameterLists =
				modelDeclaration.getParameterLists();
		compareParameterLists(name, validParameterLists, modelParameterLists);
		// now same for return type
		compareDeclarations(validDeclaration.getType().getDeclaration(),
				modelDeclaration.getType().getDeclaration());
		// work on type parameters
		compareTypeParameters(name, validDeclaration.getTypeParameters(),
				modelDeclaration.getTypeParameters());
	}

	private void compareParameterLists(final String name,
			final List<ParameterList> validParameterLists,
			final List<ParameterList> modelParameterLists)
	{
		Assert.assertEquals(name + " [param lists count]",
				validParameterLists.size(), modelParameterLists.size());
		for (int i = 0; i < validParameterLists.size(); i++)
		{
			final List<Parameter> validParameterList =
					validParameterLists.get(i).getParameters();
			final List<Parameter> modelParameterList =
					modelParameterLists.get(i).getParameters();
			Assert.assertEquals(name + " [param lists " + i + " count]",
					validParameterList.size(), modelParameterList.size());
			for (int p = 0; p < validParameterList.size(); p++)
			{
				final Parameter validParameter = validParameterList.get(i);
				final Parameter modelParameter = modelParameterList.get(i);
				Assert.assertEquals(
						name + " [param " + validParameter.getName()
								+ " sequenced]", validParameter.isSequenced(),
						modelParameter.isSequenced());
				Assert.assertEquals(
						name + " [param " + validParameter.getName()
								+ " defaulted]", validParameter.isDefaulted(),
						modelParameter.isDefaulted());
				// make sure they have the same name and type
				compareDeclarations(validParameter, modelParameter);
			}
		}
	}

	private void compareSatisfiedTypes(final String name,
			final List<TypeDeclaration> validTypeDeclarations,
			final List<TypeDeclaration> modelTypeDeclarations)
	{
		Assert.assertEquals(name + " [Satisfied types count]",
				validTypeDeclarations.size(), modelTypeDeclarations.size());
		for (int i = 0; i < validTypeDeclarations.size(); i++)
		{
			final TypeDeclaration validTypeDeclaration =
					validTypeDeclarations.get(i);
			final TypeDeclaration modelTypeDeclaration =
					modelTypeDeclarations.get(i);
			compareDeclarations(validTypeDeclaration, modelTypeDeclaration);
		}
	}

	private void compareTypeParameters(final String name,
			final List<TypeParameter> validTypeParameters,
			final List<TypeParameter> modelTypeParameters)
	{
		Assert.assertEquals(name + " [type parameter count]",
				validTypeParameters.size(), modelTypeParameters.size());
		for (int i = 0; i < validTypeParameters.size(); i++)
		{
			final TypeParameter validTypeParameter = validTypeParameters.get(i);
			final TypeParameter modelTypeParameter = modelTypeParameters.get(i);
			compareDeclarations(validTypeParameter, modelTypeParameter);
		}
	}

	private void compareTypeParameters(final TypeParameter validDeclaration,
			final TypeParameter modelDeclaration)
	{
		final String name =
				validDeclaration.getContainer().toString() + "<"
						+ validDeclaration.getName() + ">";
		Assert.assertEquals("[Contravariant]",
				validDeclaration.isContravariant(),
				modelDeclaration.isContravariant());
		Assert.assertEquals("[Covariant]", validDeclaration.isCovariant(),
				modelDeclaration.isCovariant());
		Assert.assertEquals("[SelfType]", validDeclaration.isSelfType(),
				modelDeclaration.isSelfType());
		compareSatisfiedTypes(name,
				validDeclaration.getSatisfiedTypeDeclarations(),
				modelDeclaration.getSatisfiedTypeDeclarations());
	}

	private Declaration lookupMember(final Class container,
			final Declaration referenceMember)
	{
		final String name = referenceMember.getName();
		for (final Declaration member : container.getMembers())
		{
			if (member.getName() != null && member.getName().equals(name))
			{
				// we have a special case if we're asking for a Value and we
				// find a Class, it means it's an "object"'s
				// class with the same name so we ignore it
				if (referenceMember instanceof Value && member instanceof Class)
				{
					continue;
				}
				// the opposite is also true
				if (referenceMember instanceof Class && member instanceof Value)
				{
					continue;
				}
				// otherwise we found it
				return member;
			}
		}
		// not found
		return null;
	}

	protected void verifyClassLoading(final String ceylon)
	{
		// now compile the ceylon decl file
		final CeyloncTaskImpl task = getCompilerTask(ceylon);
		// get the context to grab the phased units
		final Context context = task.getContext();

		Boolean success = task.call();

		Assert.assertTrue(success);

		final PhasedUnits phasedUnits =
				LanguageCompiler.getPhasedUnitsInstance(context);

		// find out what was in that file
		Assert.assertEquals(1, phasedUnits.getPhasedUnits().size());
		final PhasedUnit phasedUnit = phasedUnits.getPhasedUnits().get(0);
		final Map<String, Declaration> decls =
				new HashMap<String, Declaration>();
		for (final Declaration decl : phasedUnit.getUnit().getDeclarations())
		{
			if (decl.isToplevel())
			{
				decls.put(Util.getQualifiedPrefixedName(decl), decl);
			}
		}

		// now compile the ceylon usage file
		// remove the extension, make lowercase and add "test"
		final String testfile =
				ceylon.substring(0, ceylon.length() - 7).toLowerCase()
						+ "test.ceylon";
		final JavacTaskImpl task2 = getCompilerTask(testfile);
		// get the context to grab the declarations
		final Context context2 = task2.getContext();

		// check the declarations after Enter but before the compilation is done
		// otherwise we can'tload lazy
		// declarations from the jar anymore because we've overridden the jar
		// and the javac jar index is corrupted
		class Listener implements TaskListener
		{
			@Override
			public void finished(final TaskEvent e)
			{
				if (e.getKind() == Kind.ENTER)
				{
					final CeylonModelLoader modelLoader =
							CeylonModelLoader.instance(context2);
					// now see if we can find our declarations
					for (final Entry<String, Declaration> entry : decls
							.entrySet())
					{
						final String quotedQualifiedName =
								Util.quoteJavaKeywords(entry.getKey()
										.substring(1));
						final Declaration modelDeclaration =
								modelLoader
										.getDeclaration(
												quotedQualifiedName,
												entry.getValue() instanceof Value ? DeclarationType.VALUE
														: DeclarationType.TYPE);
						Assert.assertNotNull(modelDeclaration);
						// make sure we loaded them exactly the same
						compareDeclarations(entry.getValue(), modelDeclaration);
					}
				}
			}

			@Override
			public void started(final TaskEvent e)
			{}
		}
		final Listener listener = new Listener();
		task2.setTaskListener(listener);

		success = task2.call();
		Assert.assertTrue("Compilation failed", success);
	}

	@Test
	public void loadClass()
	{
		verifyClassLoading("Klass.ceylon");
	}

	@Test
	public void loadClassWithAttributeAndConflictingMethods()
	{
		verifyClassLoading("KlassWithAttributeAndConflictingMethods.ceylon");
	}

	@Test
	public void loadClassWithAttributes()
	{
		verifyClassLoading("KlassWithAttributes.ceylon");
	}

	@Test
	public void loadClassWithMethods()
	{
		verifyClassLoading("KlassWithMethods.ceylon");
	}

	@Test
	public void loadDefaultValues()
	{
		verifyClassLoading("DefaultValues.ceylon");
	}

	@Test
	public void loadDocAnnotations()
	{
		verifyClassLoading("DocAnnotations.ceylon");
	}

	@Test
	public void loadErasedTypes()
	{
		verifyClassLoading("ErasedTypes.ceylon");
	}

	@Test
	public void loadGettersWithUnderscores()
	{
		verifyClassLoading("GettersWithUnderscores.ceylon");
	}

	@Test
	public void loadInnerClass()
	{
		verifyClassLoading("InnerClass.ceylon");
	}

	@Test
	public void loadJavaKeywords()
	{
		verifyClassLoading("JavaKeywords.ceylon");
	}

	@Test
	public void loadLocalDeclarations()
	{
		verifyClassLoading("LocalDeclarations.ceylon");
	}

	@Test
	public void loadToplevelAttributes()
	{
		verifyClassLoading("ToplevelAttributes.ceylon");
	}

	@Test
	public void loadToplevelMethods()
	{
		verifyClassLoading("ToplevelMethods.ceylon");
	}

	@Test
	public void loadToplevelObjects()
	{
		verifyClassLoading("ToplevelObjects.ceylon");
	}

	@Test
	public void loadTypeParameterResolving()
	{
		verifyClassLoading("TypeParameterResolving.ceylon");
	}

	@Test
	public void loadTypeParameters()
	{
		verifyClassLoading("TypeParameters.ceylon");
	}
}
