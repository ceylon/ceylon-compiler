interface AttributeCapByLocalFunctionIface {
    shared formal Integer capturedOuter1;
    shared Integer capturedOuter2 => 0;
}

abstract class AttributeCapByLocalFunctionSuper() {
    shared formal Integer capturedOuter3;
    shared formal Integer capturedOuter4;
}

abstract class AttributeCapByLocalFunction() 
        extends AttributeCapByLocalFunctionSuper()
        satisfies AttributeCapByLocalFunctionIface{
    shared actual Integer capturedOuter4 = 0;
    Integer capturedOuter5 = 0;
    shared Integer capturedOuter6 = 0;
    shared default Integer capturedOuter7 = 0;
    
    void m() {
        @captures:"AttributeCapByLocalFunctionIface.capturedOuter1,
                   AttributeCapByLocalFunctionIface.capturedOuter2,
                   AttributeCapByLocalFunctionSuper.capturedOuter3,
                   AttributeCapByLocalFunction.capturedOuter4,
                   AttributeCapByLocalFunction.capturedOuter5,
                   AttributeCapByLocalFunction.capturedOuter6,
                   AttributeCapByLocalFunction.capturedOuter7"
        function captures() => capturedOuter1 + capturedOuter2 + capturedOuter3
            + capturedOuter4 + capturedOuter5 + capturedOuter6 + capturedOuter7;
    }
}