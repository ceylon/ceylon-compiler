class FunctionLocalToToplevelClassMethod<T>() {
    shared void method<U>() {
        // HERE
        void local(){}
        local();
    }
}