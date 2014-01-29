@noanno
void interfaceLocalToToplevelFunction<T>(T t) 
        given T satisfies Object {
    Integer i = 0;
    variable value result = 0;

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

    interface DefaultedParameter {
        shared default Integer z {
            return result;
        }
        Integer nonSharedMethod(Integer b, Integer y = result) {
            return b + y + z;
        }
        shared Integer sharedMethod(Integer b, Integer y = result) {
            return b + y + z;
        }
        shared formal Integer formalMethod(Integer b, Integer y = result);
        shared default Integer defaultMethod(Integer b, Integer y = result) {
            return b + y + z;
        }
    }
    
    class DefaultedParameterClass() satisfies DefaultedParameter {
        shared actual Integer formalMethod(Integer b, Integer y) => b+y;
    }
    
    result = DefaultedParameterClass().sharedMethod(2);
    result = DefaultedParameterClass().formalMethod(2);
    result = DefaultedParameterClass().defaultMethod(2);
    result = DefaultedParameterClass{}.sharedMethod{
        b=2;
    };
    result = DefaultedParameterClass{}.formalMethod{
        b=2;
    };
    result = DefaultedParameterClass{}.defaultMethod{
        b=2;
    };
    
    interface Top {
        shared Integer top => 1;
        
        shared variable formal Integer formalAttribute;
        
        shared default Integer defaultAttribute => this.top;
        assign defaultAttribute {}
        
        
        shared formal Integer formalMethod();
        shared default Integer defaultMethod() => top;
    }
    interface Left satisfies Top {
        shared default Integer left => 2;
        
        shared actual default Integer formalAttribute 
                => this.left + this.top + super.top;
        assign formalAttribute { 
            super.defaultAttribute++;
            this.defaultAttribute+=1;
            this.formalAttribute=1;
        }
        shared actual default Integer defaultAttribute 
                => super.defaultAttribute + this.formalAttribute;
        assign defaultAttribute { }
        
        shared actual default Integer formalMethod()
                => this.left + this.top + super.top;
        
        shared actual default Integer defaultMethod()
                => super.defaultAttribute + this.formalAttribute;
        
        void ref() {
            value x = super.defaultMethod;
            Integer y = x();
        }
    }
    /*interface Right satisfies Top {
        shared default Integer right => 4;
        shared actual default Integer defaultAttribute 
                => this.right + this.top + super.top;
        
    }
    class TopClass() satisfies Top {
        shared default Integer topClass => 8;
        shared actual default Integer formalAttribute 
                => this.topClass + this.top + super.top;
    }
    class SelfRef() extends TopClass() satisfies Left & Right {
        shared formal Integer formalAttribute 
                => super.left + super.right + super.top 
                    + (super of Left).formalAttribute 
                    + (super of Right).formalAttribute 
                    + (super of TopClass).formalAttribute;
    }
    class SelfRefClass() satisfies SelfRef {
        
    }
    result = SelfRef().capture();
    */
    /* TODO
    // issues todo with locality within the initializer
    
    // transitive type parameter capture: Nothing to test here ATM because we always
         copy down all the reified TPs that are in scope. We should fix that XXX
    // object declarations
    // static references
    // TODO object o extends ClassLocalToToplevelFunction(){}
    */
}