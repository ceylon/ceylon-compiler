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
package com.redhat.ceylon.ceylondoc.test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.redhat.ceylon.ceylondoc.CeylondMessages;

public class CeylondMessagesTest {

    private Map<Class<?>, Object> defaultMsgArgs;

    @Before
    public void init() {
        defaultMsgArgs = new HashMap<Class<?>, Object>();
        defaultMsgArgs.put(Boolean.TYPE, Boolean.FALSE);
        defaultMsgArgs.put(Byte.TYPE, new Byte((byte) 0));
        defaultMsgArgs.put(Short.TYPE, new Short((short) 0));
        defaultMsgArgs.put(Character.TYPE, new Character('\0'));
        defaultMsgArgs.put(Integer.TYPE, new Integer(0));
        defaultMsgArgs.put(Long.TYPE, new Long(0L));
        defaultMsgArgs.put(Float.TYPE, new Float(0.0F));
        defaultMsgArgs.put(Double.TYPE, new Double(0.0D));
        defaultMsgArgs.put(String.class, "aaa");
        defaultMsgArgs.put(Object.class, "bbb");
    }

    @Test
    public void testMessages() {
        Method[] methods = CeylondMessages.class.getDeclaredMethods();
        for (Method method : methods) {
            if (isMsgMethod(method)) {
                String msg = invokeMsgMethod(method);
                assertNotNull("Message " + method.getName() + " is null!", msg);
                assertFalse("Message " + method.getName() + " is empty!", msg.isEmpty());

                System.out.println(method.getName() + " = " + msg);
            }
        }
    }

    private boolean isMsgMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && method.getReturnType().equals(String.class);
    }

    private String invokeMsgMethod(Method method) {
        try {
            Class<?>[] argsTypes = method.getParameterTypes();
            Object[] args = new Object[argsTypes.length];
            for (int i = 0; i < argsTypes.length; i++) {
                args[i] = defaultMsgArgs.get(argsTypes[i]);
            }
            return (String) method.invoke(CeylondMessages.get(), args);
        } 
        catch (Exception e) {
            throw new RuntimeException("Failed get message: " + method.getName(), e);
        }
    }

}