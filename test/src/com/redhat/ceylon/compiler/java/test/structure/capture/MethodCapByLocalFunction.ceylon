class MethodCapByLocalFunction() {
    shared Integer getter {
        @captures:"MethodCapByLocalFunction.sharedMethod,
                   MethodCapByLocalFunction.nonsharedMethod"
        function captures() => sharedMethod() + nonsharedMethod();
        return captures();
    }
    shared Integer sharedMethod() => 0;
    Integer nonsharedMethod() => 0;
}