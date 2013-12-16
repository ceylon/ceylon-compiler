class ValueCapByInitFunction(Integer notCaptured1) {
    Integer notCaptured2;
    notCaptured2 = 0;
    @captures
    function captures() => notCaptured1 + notCaptured2;
}