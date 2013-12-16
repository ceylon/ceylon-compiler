@noanno
class LocalFunctionSuperCaptureClass<X,Y>() {
    shared default X getXC => nothing;
    assign getXC {}
    shared default Y getYC => nothing;
    assign getYC {}
    shared default void takesXC(X x){}
    shared default void takesYC(Y y){}
    shared default X returnsXC() => nothing;
    shared default Y returnsYC() => nothing;
}
@noanno
interface LocalFunctionSuperCaptureInterface<X,Y> {
    shared default X getXI => nothing;
    assign getXI {}
    shared default Y getYI => nothing;
    assign getYI {}
    shared default void takesXI(X x){}
    shared default void takesYI(Y y){}
    shared default X returnsXI() => nothing;
    shared default Y returnsYI() => nothing;
}
@noanno
class LocalFunctionSuperCapture<T>() 
        extends LocalFunctionSuperCaptureClass<Integer, T>()
        satisfies LocalFunctionSuperCaptureInterface<Integer,T> {
    
    shared actual default Integer getXC => nothing;
    assign getXC {}
    shared actual default T getYC => nothing;
    assign getYC {}
    shared actual default void takesXC(Integer x){}
    shared actual default void takesYC(T y){}
    shared actual default Integer returnsXC() => nothing;
    shared actual default T returnsYC() => nothing;
    shared actual default Integer getXI => nothing;
    assign getXI {}
    shared actual default T getYI => nothing;
    assign getYI {}
    shared actual default void takesXI(Integer x){}
    shared actual default void takesYI(T y){}
    shared actual default Integer returnsXI() => nothing;
    shared actual default T returnsYI() => nothing;
    
    void m() {
        void local() {
            variable Integer i;
            variable T t;
            
            i = super.getXC;
            //TODO super.getXC = i;
            // TODO i = super.getXI;
            //TODO super.getXI = i;
            
            t = super.getYC;
            //TODO super.getYC = t;
            //t = super.getYI;
            //super.getYI = t;
            
            super.takesXC(super.returnsXC());
            //super.takesXI(super.returnsXI());
            super.takesYC(super.returnsYC());
            //super.takesYI(super.returnsYI());
            
            /*super.takesXI(super.returnsXC());
            super.takesYI(super.returnsYC());
            super.takesXC(super.returnsXI());
            super.takesYC(super.returnsYI());*/
        }
        local();
    }
}
/*
@noanno
class LocalFunctionSuperCaptureSub<T>() 
        extends LocalFunctionSuperCapture<Float>() {
    
    shared actual Integer getXC => nothing;
    assign getXC {}
    shared actual Float getYC => nothing;
    assign getYC {}
    shared actual void takesXC(Integer x){}
    shared actual void takesYC(Float y){}
    shared actual Integer returnsXC() => nothing;
    shared actual Float returnsYC() => nothing;
    shared actual Integer getXI => nothing;
    assign getXI {}
    shared actual Float getYI => nothing;
    assign getYI {}
    shared actual void takesXI(Integer x){}
    shared actual void takesYI(Float y){}
    shared actual Integer returnsXI() => nothing;
    shared actual Float returnsYI() => nothing;

    void m() {
        void local() {
            variable Integer i;
            variable T t;
            
            i = super.getXC;
        }
        local();
    }
}*/