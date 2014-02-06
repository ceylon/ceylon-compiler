@noanno
shared interface ToplevelInterface<T> {
    shared void m() {}
}
@noanno
shared class ToplevelClass<T>() {
    shared void m() {}
}
@noanno
shared interface OuterInterface<T> {
    shared void m() {}
    shared class MemberClass<U>() {
        shared void m() {}
    }
    shared interface MemberInterface<U> {
        shared void m() {}
    }
}
@noanno
shared class OuterClass<T>() {
    shared void m() {}
    shared class MemberClass<U>() {
        shared void m() {}
    }
    shared interface MemberInterface<U> {
        shared void m() {}
    }
}
