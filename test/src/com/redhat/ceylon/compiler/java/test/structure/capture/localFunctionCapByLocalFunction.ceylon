void localFunctionCapByLocalFunction() {
    Integer capturedLocal1() => 0;
    void local() {
        @captures:"localFunctionCapByLocalFunction.capturedLocal1"
        function captures() => capturedLocal1();
    }
}
