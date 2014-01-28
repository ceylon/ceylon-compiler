@noanno
Integer valueLocalToToplevelFunction<T>() 
        given T satisfies Object {
    Integer x = 0;
    variable Integer result = 0;
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
    
    return 0;
}