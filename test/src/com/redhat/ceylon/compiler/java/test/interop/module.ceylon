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
native("jvm")
module com.redhat.ceylon.compiler.java.test.interop "1" {
    import java.base "7";
    import javax.xml "7";
    import java.desktop "7";
    import oracle.jdk.httpserver "7";
    import javax.annotation "7";
    import java.management "7";
    import ceylon.interop.java "1.2.0";
    import javax.inject "1";
    import "javax.validation:validation-api" "1.1.0.Final";
}