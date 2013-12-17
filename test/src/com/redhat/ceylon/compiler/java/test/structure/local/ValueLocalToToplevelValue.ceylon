@noanno
Integer valueLocalToToplevelValue {
    Integer x = 0;
    variable Integer result = 0;
    if (1+1==2) {
        value local{ return 0; }
        assign local {
        }
        Integer k = local;
    } else {
        value local{ return 0; }
        assign local {
        }
        Integer k = local;
    }
    /*
    value localCapture => x+1;
    assign localCapture {
    }
    result = localCapture;
    
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
    
    // transitive capture
    value transitiveCapture {
        return localVariableCapture + localCapture;
    }
    assign transitiveCapture {
        localVariableCapture = transitiveCapture;
        localCapture = transitiveCapture;
    }
    result = transitiveCapture;
    
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
     
    variable Integer deferred;
    deferred => 0;
    assign deferred => result++;
    */
    return 0;
}
// Setter
assign valueLocalToToplevelValue {
    Integer x = 0;
    variable Integer result = 0;
    if (1+1==2) {
        value local{ return 0; }
        assign local {
        }
        Integer k = local;
    } else {
        value local{ return 0; }
        assign local {
        }
        Integer k = local;
    }
    /*
    value localCapture => x+1;
    assign localCapture {
    }
    result = localCapture;
    
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
    
    // transitive capture
    value transitiveCapture {
        return localVariableCapture + localCapture;
    }
    assign transitiveCapture {
        localVariableCapture = transitiveCapture;
        localCapture = transitiveCapture;
    }
    result = transitiveCapture;
    
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
    variable Integer deferred;
    deferred => 0;
    assign deferred => result++;
    */
}