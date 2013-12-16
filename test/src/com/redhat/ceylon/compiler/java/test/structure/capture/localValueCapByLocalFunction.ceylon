void localValueCapByLocalFunction(Integer capturedLocal1, Integer() capturedLocal2) {
    Integer capturedLocal3 = 0;
    @captures:"localValueCapByLocalFunction.capturedLocal1,
               localValueCapByLocalFunction.capturedLocal2,
               localValueCapByLocalFunction.capturedLocal3"
    function captures() => capturedLocal1 + capturedLocal2() + capturedLocal3;
    
}