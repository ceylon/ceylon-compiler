Integer functionLocalToToplevelValue {
    if (1+1==2) {
        void local(){}
        local();
    } else {
        void local(){}
        local();
    }
    
    Integer x = 0;
    function localCapture() => x+1;
    localCapture();
    
    // local variable capture
    variable Integer y = 0;
    function localVariableCapture() {
        y=1;
        y++;
        y+=1;
        return y;
    }
    localVariableCapture();
    return 0;
}
assign functionLocalToToplevelValue {
    if (1+1==2) {
        void local(){}
        local();
    } else {
        void local(){}
        local();
    }
    
    Integer x = 0;
    function localCapture() => x+1;
    localCapture();
    
    // local variable capture
    variable Integer y = 0;
    function localVariableCapture() {
        y=1;
        y++;
        y+=1;
        return y;
    }
    localVariableCapture();
}