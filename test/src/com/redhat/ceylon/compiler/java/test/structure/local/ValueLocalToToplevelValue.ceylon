@noanno
Integer valueLocalToToplevelValue {
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
    
    return 0;
}
// Setter
assign valueLocalToToplevelValue {
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