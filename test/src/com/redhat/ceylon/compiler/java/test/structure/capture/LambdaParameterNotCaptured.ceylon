void lambdaParameterNotCaptured() {
    @captures
    String? local() {
        return [""].find((String notCaptured) => "".startsWith(notCaptured));
    }
    local();
}