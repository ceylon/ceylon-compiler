@noanno
interface ValueLocalToToplevelInterfaceMethodTop<T> {
    shared default T diamond => nothing;
}
@noanno
interface ValueLocalToToplevelInterfaceMethodLeft<U>
        satisfies ValueLocalToToplevelInterfaceMethodTop<U> {
    shared actual default U diamond => nothing;
}
@noanno
interface ValueLocalToToplevelInterfaceMethodRight 
        satisfies ValueLocalToToplevelInterfaceMethodTop<String> {
    shared actual default String diamond => "";
}
@noanno
interface ValueLocalToToplevelInterfaceMethod
        satisfies ValueLocalToToplevelInterfaceMethodLeft<String>
        &ValueLocalToToplevelInterfaceMethodRight {
    shared actual default String diamond => "a";
    shared actual String string => "";
    shared void naming() {
        if (1+1==2) {
            value local{ return 1; }
            assign local {
            }
            Integer k = local;
            local = k;
        } else {
            value local{ return 2; }
            assign local {
            }
            Integer k = local;
            local = k;
        }
    }
    shared void method<U>(U u) 
            given U satisfies Object {
        Integer x = 0;
        variable Integer result = 0;
        // TODO Test with a => getter
        // TODO Retest variations of this onces gavin's fixed #887
        Integer localCapture { 
            return  x+1; 
        }
        assign localCapture {
            result+=localCapture;
        }
        result = localCapture;
        localCapture = result;
        
        // local variable capture
        variable Integer y = 0;
        value localVariableCapture {
            y=1;
            y++;
            y+=1;
            return y;
        }
        assign localVariableCapture {
            y=1;
            y++;
            y+=1;
        }
        result = localVariableCapture;
        localVariableCapture = result;
        
        // transitive capture
        value transitiveCapture {
            return localVariableCapture + localCapture;
        }
        assign transitiveCapture {
            localVariableCapture = transitiveCapture;
            localCapture = transitiveCapture;
        }
        result = transitiveCapture;
        transitiveCapture = result;
        
        // tpCapture
        U tpCapture {
            return nothing;
        }
        assign tpCapture {
            
        }
        result = tpCapture.hash;
        tpCapture = nothing;
        
        Integer thisAccess {
            return this.string.hash;
        }
        result = thisAccess;
        
        value superAccess {
            return (super of ValueLocalToToplevelInterfaceMethodRight).diamond.hash +
                    (super of ValueLocalToToplevelInterfaceMethodLeft<String>).diamond.hash +
                    super.string.hash;
        }
        result = superAccess;
        
        value reified {
            return x is U;
        }
        result = reified.hash;
        
        // nesting
        Integer nesting {
            value nested {
                return x;
            }
            assign nested {
                
            }
            return nested++;
        }
        assign nesting {
            value nested {
                return x;
            }
            assign nested {
                
            }
            nested++;
        }
        result = nesting;
        nesting = result;
        // TODO Retest variations of this onces gavin's fixed #885
        Integer deferred;
        deferred => nesting;
        result = deferred;
        
        Integer localCapture2 => x+1;
        result = localCapture2;
        assign localCapture2 => print(result+=x);
        localCapture2 = result;
        
        Integer localCapture3;
        localCapture3 => x+1;
        result = localCapture3;
        
        value transitiveCapture2 => localVariableCapture + localCapture2;
        result = transitiveCapture2;
        assign transitiveCapture2 => print(result+=x);
    }
}


/*
@noanno
interface ValueLocalToToplevelInterfaceMethod<T> 
        given T satisfies Object {
    shared actual String string => "";
    shared void naming() {
        if (1+1==2) {
            value local{ return 1; }
            assign local {
            }
            Integer k = local;
            local = k;
        } else {
            value local{ return 2; }
            assign local {
            }
            Integer k = local;
            local = k;
        }
    }
    shared void method<U>(U u) 
            given U satisfies Object {
        Integer x = 0;
        variable Integer result = 0;
        // TODO Test with a => getter
        // TODO Retest variations of this onces gavin's fixed #887
        Integer localCapture { 
            return  x+1; 
        }
        assign localCapture {
            result+=localCapture;
        }
        result = localCapture;
        localCapture = result;
        
        // local variable capture
        variable Integer y = 0;
        value localVariableCapture {
            y=1;
            y++;
            y+=1;
            return y;
        }
        assign localVariableCapture {
            y=1;
            y++;
            y+=1;
        }
        result = localVariableCapture;
        localVariableCapture = result;
        
        // transitive capture
        value transitiveCapture {
            return localVariableCapture + localCapture;
        }
        assign transitiveCapture {
            localVariableCapture = transitiveCapture;
            localCapture = transitiveCapture;
        }
        result = transitiveCapture;
        transitiveCapture = result;
        
        // tpCapture
        T tpCapture {
            return nothing;
        }
        assign tpCapture {
            
        }
        result = tpCapture.hash;
        tpCapture = nothing;
        
        Integer thisAccess {
            return this.string.hash;
        }
        result = thisAccess;
        
        value reified {
            return x is T;
        }
        result = reified.hash;
        
        // nesting
        Integer nesting {
            value nested {
                return x;
            }
            assign nested {
                
            }
            return nested++;
        }
        assign nesting {
            value nested {
                return x;
            }
            assign nested {
                
            }
            nested++;
        }
        result = nesting;
        nesting = result;
        // TODO Retest variations of this onces gavin's fixed #885
        Integer deferred;
        deferred => nesting;
        result = deferred;
    }
}*/