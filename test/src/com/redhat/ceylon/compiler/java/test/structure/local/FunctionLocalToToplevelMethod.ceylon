@noanno
void functionLocalToToplevelMethod<U>(U u) {
    if (1+1==2) {
        void local(){}
        local();
    } else {
        void local(){}
        local();
    }
    void tpCapture(U u) {}
    tpCapture(u);
    
    Integer x = 0;
    function localCapture() => x+1;
    localCapture();
    /*
    // local variable capture
    variable Integer y = 0;
    function localVariableCapture() {
        y=1;
        y++;
        y+=1;
        return y;
    }
    localVariableCapture();
    */
    
    // TODO Transitive capture
    
    // TODO Transitive capture naming
}