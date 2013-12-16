@noanno
interface FunctionLocalToToplevelInterfaceMethodTop {
    shared default void diamond() {}
}
@noanno
interface FunctionLocalToToplevelInterfaceMethodLeft 
        satisfies FunctionLocalToToplevelInterfaceMethodTop {
    shared actual default void diamond() {}
}
@noanno
interface FunctionLocalToToplevelInterfaceMethodRight 
        satisfies FunctionLocalToToplevelInterfaceMethodTop {
    shared actual default void diamond() {}
}
@noanno
interface FunctionLocalToToplevelInterfaceMethod<T> 
        satisfies FunctionLocalToToplevelInterfaceMethodLeft
            &FunctionLocalToToplevelInterfaceMethodRight 
        given T satisfies Object {
    shared actual default void diamond() {}
    shared actual String string => "";
    shared void naming() {
        variable Anything ref;
        if (1+1==2) {
            void local(){}
            local();
            ref = local;
        } else {
            void local(){}
            local();
            ref = local;
        }
    }
    shared void method<U>(U u) 
            given U satisfies Object {
        Integer x = 0;
        variable Anything ref;
        
        void tpCapture(U u) {}
        tpCapture(u);
        ref = tpCapture;
        
        function localCapture() => x+1;
        localCapture();
        localCapture{};
        ref = localCapture;
        
        // local variable capture
        
        variable Integer y = 0;
        function localVariableCapture() {
            y=1;
            y++;
            y+=1;
            return y;
        }
        localVariableCapture();
        ref = localVariableCapture;
        
        // transitive capture
        function transitiveCapture() {
            return localVariableCapture() + localCapture();
        }
        transitiveCapture();
        ref = transitiveCapture;
        
        // nesting
        Integer nesting<V>(V v) 
                given V satisfies Object {
            function nested<W>(W w) 
                    given W satisfies Object {
                return x + u.hash + v.hash + w.hash + this.hash;
            }
            return nested(1);
        }
        nesting(1);
        ref = nesting<String>;
        
        // defaulted parameters
        function defaultedParameters(Integer i, Integer j = i, U k = u) {
            return i+j;
        }
        // invocation with defaulted arguments
        defaultedParameters(0);
        defaultedParameters(0, 1);
        defaultedParameters(0, 1, u);
        defaultedParameters{
            i=0;
        };
        defaultedParameters{
            j=1;
            i=0;
        };
        defaultedParameters{
            k=u;
            j=1;
            i=0;
        };
        ref = defaultedParameters;
        
        function superAccess() {
            (super of FunctionLocalToToplevelInterfaceMethodRight).diamond();
            (super of FunctionLocalToToplevelInterfaceMethodLeft).diamond();
            return super.string;
        }
        superAccess();
        ref = superAccess;
        
        function thisAccess() {
            return this.string;
        }
        thisAccess();
        ref = thisAccess;
        
        function reified() {
            return x is U || x is T;
        }
        reified();
        ref = reified;
        
        function mpl(U first=u, T second = nothing)(Integer third) {
            return transitiveCapture() + first.hash + second.hash + third;
        }
        mpl(u)(1);
        ref = mpl;
        
        Anything deferred(U i=u);
        if (1+1==2) {
            deferred = function(U i){ return i; };
            class X() {
                deferred(u);
                deferred();
                deferred{
                    i=u;
                };
                //ref = deferred;
            }
        } else {
            deferred = function(U i){ return i; };
        }
        deferred(u);
        deferred();
        deferred{
            i=u;
        };
        ref = deferred;
    }
}