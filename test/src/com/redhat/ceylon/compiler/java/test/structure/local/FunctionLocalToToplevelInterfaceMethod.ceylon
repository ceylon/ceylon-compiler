interface FunctionLocalToToplevelInterfaceMethod<T> {
    shared actual String string => "";
    shared void naming() {
        if (1+1==2) {
            void local(){}
            local();
        } else {
            void local(){}
            local();
        }
    }
    shared void method<U>(U u) {
        void tpCapture(U u) {}
        tpCapture(u);
        
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
        
        function superAccess() {
            return super.string;
        }
        superAccess();
        
        function thisAccess() {
            return this.string;
        }
        thisAccess();
    }
}