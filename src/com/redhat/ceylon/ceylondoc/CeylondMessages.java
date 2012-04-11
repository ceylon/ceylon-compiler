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

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class CeylondMessages {

    private static final CeylondMessages INSTANCE = new CeylondMessages();

    private final ResourceBundle resourceBundle;

    private CeylondMessages() {
        resourceBundle = ResourceBundle.getBundle("com.redhat.ceylon.ceylondoc.resources.messages");
    }

    public static CeylondMessages get() {
        return INSTANCE;
    }

    public String error(String message) {
        return msg("error", message);
    }

    public String errorCantFindModule(String moduleName, String moduleVersion) {
        return msg("error.cantFindModule", moduleName, moduleVersion);
    }

    public String errorCouldNotCreateDirectory(Object file) {
        return msg("error.couldNotCreateDirectory", file);
    }

    public String errorExpectedUriToBeAbsolute(Object uri) {
        return msg("error.expectedUriToBeAbsolute", uri);
    }

    public String errorFailedDeleteFile(Object file) {
        return msg("error.failedDeleteFile", file);
    }

    public String errorFailedParsing(int errorCount) {
        return msg("error.failedParsing", errorCount);
    }

    public String errorFailedUriRelativize(Object uri, Object uri2, Object result) {
        return msg("error.failedUriRelativize", uri, uri2, result);
    }

    public String errorFailedRemoveArtifact(Object context, String exceptionMsg) {
        return msg("error.failedRemoveArtifact", context, exceptionMsg);
    }

    public String errorFailedWriteArtifact(Object context, String exceptionMsg) {
        return msg("error.failedWriteArtifact", context, exceptionMsg);
    }

    public String errorNoModulesSpecified() {
        return msg("error.noModulesSpecified");
    }

    public String errorNoSuchSourceDirectory(Object dir) {
        return msg("error.noSuchSourceDirectory", dir);
    }

    public String errorNoPage(Object obj) {
        return msg("error.noPage", obj);
    }

    public String errorOptionDnotSupported() {
        return msg("error.optionDnotSupported");
    }

    public String errorOptionMissing(String arg) {
        return msg("error.optionMissing", arg);
    }

    public String errorOptionUnknown(String arg) {
        return msg("error.optionUnknown", arg);
    }

    public String errorUnexpected(Object arg) {
        return msg("error.unexpected", arg);
    }

    public String errorUnexpectedAdditionalResource(String resourceName) {
        return msg("error.unexpectedAdditionalResource", resourceName);
    }

    public String warnCouldNotFindAnyDeclaration() {
        return msg("warn.couldNotFindAnyDeclaration");
    }

    public String warnModuleHasNoDeclaration(String moduleName) {
        return msg("warn.moduleHasNoDeclaration", moduleName);
    }

    public String infoUsage1() {
        return msg("info.usage1");
    }

    public String infoUsage2() {
        return msg("info.usage2");
    }

    public String infoVersion(String version) {
        return msg("info.version", version);
    }

    private String msg(String key, Object... args) {
        String msg = resourceBundle.getString(key);
        if (args != null) {
            msg = MessageFormat.format(msg, args);
        }
        return msg;
    }

}