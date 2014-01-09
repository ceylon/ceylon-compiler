@noanno
void classLocalToToplevelFunction<T>(T t) 
        given T satisfies Object {
    Integer i = 0;
    class ClassLocalToToplevelFunction() {
        shared Integer capture() {
            return t.hash ^ i;
        }
        /*TODO shared Integer transitiveCapture() {
            return capture();
        }*/
    }
    ClassLocalToToplevelFunction v => ClassLocalToToplevelFunction();
    print(v.capture());
    // TODO object o extends ClassLocalToToplevelFunction(){}
}