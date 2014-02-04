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
@noanno
shared interface MultipleInheritance<T> {
    shared default Integer a {
        return 1;
    } assign a {
        
    }
    shared default void m() {
    }
    void x(Object t) {
        if (is T t) {
        } 
    }
}
@noanno
class MultipleInheritanceImpl<A>() satisfies MultipleInheritance<A> {
}
@noanno
interface MultipleInheritanceSub<B> satisfies MultipleInheritance<B> {
}
@noanno
class MultipleInheritanceSubImpl<C>() satisfies MultipleInheritanceSub<C> {
}
@noanno
class MultipleInheritanceImplSub<D>() extends MultipleInheritanceImpl<D>() satisfies MultipleInheritanceSub<D> {
    shared actual variable Integer a = 1;
    shared actual void m() {
        (super of MultipleInheritanceSub<D>).m();
        value x = (super of MultipleInheritanceSub<D>).a;
        (super of MultipleInheritanceSub<D>).a=0;
        (super of MultipleInheritanceSub<D>).a+=a;
        (super of MultipleInheritanceSub<D>).a++;
    }
}
@noanno
class MultipleInheritanceSubImplSub<E>() extends MultipleInheritanceSubImpl<E>() satisfies MultipleInheritance<E> {
    shared actual variable Integer a = 1;
    shared actual void m() {
        (super of MultipleInheritance<E>).m();
        value x = (super of MultipleInheritanceSub<E>).a;
        (super of MultipleInheritance<E>).a=0;
        (super of MultipleInheritance<E>).a+=a;
        (super of MultipleInheritance<E>).a++;
    }    
}
