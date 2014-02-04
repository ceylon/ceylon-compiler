@noanno
interface ClassLocalToToplevelInterfaceMethod<T> 
        given T satisfies Object {
    T t => nothing;
    shared Integer attr => 0;
    shared Integer meth() => 0;
    
    shared void method<U>(U u) 
            given U satisfies Object {
        Integer i = 0;
        variable Integer result;
        variable Anything ref = null;
        variable Anything staticRef = null;
        
        class Capture(Integer k) {
            shared default Integer capture() {
                return t.hash ^ i + k;
            }
            shared Integer transitiveCapture() {
                return capture();
            }
        }
        
        result = Capture(0).capture();
        result = Capture{
            k=1;
        }.transitiveCapture();
        ref = Capture;
        staticRef = Capture.capture;
        staticRef = Capture.transitiveCapture;
        
        if (i == 0) {
            class LocalClass() {
                shared Integer num => 0;
            }
            result = LocalClass().num;
            ref = LocalClass;
        } else {
            class LocalClass() {
                shared Integer num => 1;
            }
            result = LocalClass().num;
            ref = LocalClass;
        }
        
        class Nesting() {
            class NestedLocalClass() {
                shared Integer m() {
                    return i + t.hash;
                }
            }
            value nlc = NestedLocalClass();
            value h = nlc.m();
            shared class NestedMemberClass() {
                shared Integer m() {
                    return i + t.hash;
                }
            }
            shared Integer k = NestedMemberClass().m();
        }
        result = Nesting().k;
        ref = Nesting;
        
        class GenericMethod() {
            shared U m<U>(U u) => u;
        }
        value x = GenericMethod();
        result = x.m(result);
        result = GenericMethod().m{
            u=result;
        };
        ref = GenericMethod;
        
        class TpCapture() {
            shared T t1 => t;
        }
        result = TpCapture().t1.hash;
        ref = TpCapture;
        
        class VariableCapture() {
            result = 0;
            result++;
            result+=1;
            shared void mutate() {
                result = 0;
                result++;
                result+=1;
            }
        }
        VariableCapture().mutate();
        ref = VariableCapture;
        
        class DefaultedParameter(Integer a, Integer x = i+5) {
            shared Integer m(Integer b, Integer y = result) {
                return x+y;
            }
        }
        /*
        result = DefaultedParameter(1).m(2);
        result = DefaultedParameter{
            a=1;
        }.m{
            b=2;
        };
        ref = DefaultedParameter;
         */
        class SuperclassCapture() extends Capture(1) {
            // TODO We should only generate fields for captured stuff if we 
            // capture it directly ouselves
        }
        result = SuperclassCapture().hash;
        ref = SuperclassCapture;
        
        interface Interface {
            shared Integer x() => i;
            shared Integer y => i;
        }
        
        class SuperinterfaceCapture() satisfies Interface {
            
        }
        result = SuperinterfaceCapture().x() + SuperinterfaceCapture().y;
        
        class SelfRef() extends Capture(1) {
            shared actual Integer capture() {
                return super.capture() + this.capture();
            }
            shared Integer outerCapture() {
                return outer.attr + outer.meth();
            }
        }
        result = SelfRef().capture();
        
        /* TODO
         // issues todo with locality within the initializer
         
         // transitive type parameter capture: Nothing to test here ATM because we always
         copy down all the reified TPs that are in scope. We should fix that XXX
         // object declarations
         // static references
         // TODO object o extends ClassLocalToToplevelFunction(){}
         */
         
    }
}