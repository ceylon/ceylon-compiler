class MethodCapBySiblingMethod() {
    void notCaptured() {
    }
    @captures
    function captures() => notCaptured();
}