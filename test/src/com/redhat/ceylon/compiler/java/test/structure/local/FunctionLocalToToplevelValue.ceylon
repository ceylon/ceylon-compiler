@noanno
Integer functionLocalToToplevelValue {
    Integer x = 0;
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
    
    function localCapture() => x+1;
    localCapture();
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
            return x + v.hash + w.hash;
        }
        return nested(1);
    }
    nesting(1);
    ref = nesting<String>;
    
    // defaulted parameters
    function defaultedParameters(Integer i, Integer j = i) {
        return i+j;
    }
    // invocation with defaulted arguments
    defaultedParameters(0);
    defaultedParameters(0, 1);
    defaultedParameters{
        i=0;
    };
    defaultedParameters{
        j=1;
        i=0;
    };
    ref = defaultedParameters;
    
    return 0;
}
assign functionLocalToToplevelValue {
    Integer x = 0;
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
    
    function localCapture() => x+1;
    localCapture();
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
            return x + v.hash + w.hash;
        }
        return nested(1);
    }
    nesting(1);
    ref = nesting<String>;
    
    // defaulted parameters
    function defaultedParameters(Integer i, Integer j = i) {
        return i+j;
    }
    // invocation with defaulted arguments
    defaultedParameters(0);
    defaultedParameters(0, 1);
    defaultedParameters{
        i=0;
    };
    defaultedParameters{
        j=1;
        i=0;
    };
    ref = defaultedParameters;
    
    Anything deferred(Integer i=x);
    if (1+1==2) {
        deferred = function(Integer i){ return i; };
        class X() {
            deferred(x);
            deferred();
            deferred{
                i=x;
            };
            //ref = deferred;
        }
    } else {
        deferred = function(Integer i){ return i; };
    }
    deferred(1);
    deferred();
    deferred{
        i=1;
    };
    ref = deferred;
}