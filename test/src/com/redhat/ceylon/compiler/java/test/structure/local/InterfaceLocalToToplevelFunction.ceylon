@noanno
void interfaceLocalToToplevelFunction<T>(T t) 
        given T satisfies Object {
    Integer i = 0;
    variable value result = 0;
    /*
    interface Capture{
        shared formal Integer k;
        shared default Integer capture() {
            return t.hash ^ i + k;
        }
        shared Integer transitiveCapture() {
            return capture();
        }
    }
    class CaptureClass(shared actual Integer k) satisfies Capture {
    }
    result = CaptureClass(1).capture();
    result = CaptureClass{
        k=1;
    }.transitiveCapture();
    
    if (i == 0) {
        interface LocalInterface {
            shared Integer num => 0;
        }
        class LocalClass() satisfies LocalInterface {
            
        }
        result = LocalClass().num;
    } else {
        interface LocalInterface {
            shared Integer num => 1;
        }
        class LocalClass() satisfies LocalInterface {
            
        }
        result = LocalClass().num;
    }
    
    /* Member interface of a local interface is broken because the
     outer interface becomes a member, which means the inner loses its capture.
    interface Nesting {
        interface NestedMemberInterface {
            shared Integer m() {
                return i + t.hash;
            }
        }
         
        /*shared interface NestedMemberInterface {
            shared Integer m() {
                class NestedLocalClass() satisfies NestedLocalInterface{}
                value nlc = NestedLocalClass();
                value h = nlc.m();
                return i + t.hash;
            }
        }
        shared class NestedMemberClass() satisfies NestedMemberInterface{}*/
    }
     */
    
    //class NestingClass() satisfies Nesting {}
    //result = NestingClass().NestedMemberClass().m();
    // TODO result = Nesting().k;
    // TODO ref = Nesting;
    
    interface GenericMethod {
        shared U m<U>(U u) => u;
    }
    class GenericMethodClass() satisfies GenericMethod {}
    value x = GenericMethodClass();
    result = x.m(result);
    result = x.m{
        u=result;
    };
    
    interface TpCapture {
        shared T t1 { return t; }
    }
    class TpCaptureClass() satisfies TpCapture {}
    result = TpCaptureClass().t1.hash;
    
    interface VariableCapture {
        shared void mutate() {
            result = 0;
            result++;
            result+=1;
        }
    }
    class VariableCaptureClass() satisfies VariableCapture {}
    VariableCaptureClass().mutate();
    */
    interface DefaultedParameter {
        shared default Integer z {
            return result;
        }
        /*Integer nonSharedMethod(Integer b, Integer y = result) {
            return b + y + z;
        }*/
        //shared Integer sharedMethod(Integer b, Integer y = result) {
        //    return b + y + z;
        //}
        //shared formal Integer formalMethod(Integer b, Integer y = result);
        shared default Integer defaultMethod(Integer b, Integer y = result) {
            return b + y + z;
        }
    }
    /*
    class DefaultedParameterClass() satisfies DefaultedParameter {
    }
    
    result = DefaultedParameterClass().m(2);
    result = DefaultedParameterClass{}.m{
        b=2;
    };
    
    
    interface SuperclassCapture satisfies Capture {
        // TODO We should only generate fields for captured stuff if we 
        // capture it directly ouselves
    }
    result = SuperclassCapture().hash;
    ref = SuperclassCapture;
    
    /*interface Interface { TODO
        shared Integer x => i;
    }
    class SuperinterfaceCapture() satisfies Interface {
        
    }
    result = SuperinterfaceCapture().x;*/
    
    interface SelfRef satisfies Capture {
        shared actual Integer capture() {
            return super.capture() + this.capture();
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
     */
}