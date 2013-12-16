@noanno
class LocalFunctionInvocation() {
    class Inner() {
        shared Integer simple = 0;
        shared Integer attribute {
            return 0;
        }
        assign attribute {
            
        }
        shared Integer method(Integer y) {
            return y;
        }
        
        shared Integer memberCapture() {
            variable value z = 0;
            function captureSimpleMember(Integer x) {
                return simple + x;
            }
            z = captureSimpleMember(z);
            function captureGetterMember(Integer x) {
                return attribute + x;
            }
            z = captureGetterMember(z);
            function captureSetterMember(Integer x) {
                attribute = x;
                attribute++;
                attribute+=x;
                return x;
            }
            z = captureSetterMember(z);
            function captureMethod(Integer x) {
                return method(x);
            }
            z = captureMethod(z);
            return z;
        }
        
        shared Integer selfRefCapture() {
            variable value z = 0;
            function captureThis() {
                return this;
            }
            z = captureThis().hash;
            function captureSuper() {
                return super.hash;
            }
            z = captureSuper().hash;
            function captureOuter() {
                return outer;
            }
            z = captureOuter().hash;
            return z;
        }
        
        shared Integer localCapture() {
            variable value z = 0;
            Integer localValue = 0;
            
            function captureLocalValue(Integer y) {
                return y+localValue;
            }
            z = captureLocalValue(z);
            
            Integer localValueDeferred;
            localValueDeferred = 0;
            
            function captureLocalValueDeferred(Integer y) {
                return y+localValueDeferred;
            }
            z = captureLocalValueDeferred(z);
            
            Integer localGetterDeferred;
            localGetterDeferred => 0;
            
            function captureLocalDeferredGetter(Integer y) {
                return y+localGetterDeferred;
            }
            z = captureLocalDeferredGetter(z);
            
            Integer localGetterSetter {
                return 0;
            }
            assign localGetterSetter {
                
            }
            
            function captureLocalGetterSetter(Integer y) {
                localGetterSetter = y;
                return localGetterSetter;
            }
            z = captureLocalGetterSetter(z);
            
            function captureLocalFunction(Integer y) {
                return captureLocalValue(y);
            }
            z = captureLocalFunction(z);
            return z;
        }
    }
}
@noanno
interface Foo {

    shared void m(Integer functional()) {
        function local() {
            return functional();
        }
        local();
    }
}