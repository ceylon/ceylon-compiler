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
"Value named arguments
 
     Type name {
         // ...
         return result;
     }
 
 can be compiled to a `let` expression instead of
 an anonymous class instantiation.
 
 In this `let` expression, the original getter's block
 is wrapped in
 
 ~~~java
 final Type $return$value;
 $return$label: do {
     // original block goes here
 } while (false);
 returning $return$value;
 ~~~
 
 and every `return x;` statement is replaced by
 
 ~~~java
 $return$value = x;
 break $return$label;
 ~~~"
void optimizedValueArguments() {
    // very easy case
    print {
        value val {
            return "Hello, World!";
        }
    };
    
    // slightly tougher case
    print {
        value val {
            if (1 > 0) {
                return "Hello, World!";
            } else {
                return "WARNING: Unsound system!";
            }
        }
    };
    
    // exceptions
    print {
        value val {
            if (1 > 0) {
                if (2 > 1) {
                    return "Hello, World!";
                } else {
                    return "WARNING: Partially unsound system!";
                }
            } else {
                throw AssertionError("Unsound system");
            }
        }
    };
    
    // early return: without the break, this is not allowed
    print {
        value val {
            if (2 > 1) {
                return "Hello, World!";
            }
            return "WARNING: Unsound system!";
        }
    };
    
    // mad case
    print {
        value val {
            if (2 > 1) {
                if (3 < 4) {
                    return "Hi";
                } else {
                    for (i in 1..10) {
                        if (i > 5) {
                            return i.string;
                        }
                    }
                }
                if (3 > 4) {
                    return "wat";
                }
            }
            while (1 < 2) {
                if (9 < 10) {
                    return "Hello";
                }
            }
            return "foo";
        }
    };
}