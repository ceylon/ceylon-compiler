


final class Mixin {

    boolean k() {
        return true;
    }
    
    class InnerClass {
        Mixin $outer() {
            return Mixin.this;
        }
        static interface InnerInnerInterface {
            public abstract boolean concrete();
            
        }
        static abstract class InnerInnerCompanion {
            private InnerInnerCompanion() {}
            static boolean concrete(InnerClass $this) {
                return $this.$outer().k();
            }
        }
        class InnerInner implements InnerInnerInterface {
            @Override
            public boolean concrete() {
                return InnerInnerCompanion.concrete(InnerClass.this);
            }
        }
        
        public boolean n(final boolean b) {
            class Foo implements InnerInnerInterface {
                @Override
                public boolean concrete() {
                    return InnerInnerCompanion.concrete(InnerClass.this);
                }
            }
            return new Foo().concrete();
        }
    }
    
    public boolean m(final boolean b) {
        /*static -- parser doesn't like this, but it's be accepted by rest */
        interface LocalInterface {
            public abstract boolean concrete();
        }
        abstract static class LocalInterfaceCompanion {
            static boolean concrete() {
                return b; // need to fix attr to give sensible error about this
                //return true;
            }
        }
        class LocalClass implements LocalInterface {
            @Override
            public boolean concrete() {
                return LocalInterfaceCompanion.concrete();
            }
        }
        return new LocalClass().concrete();
    }
    
    public static void main(String[] args) {
        System.out.println(new Mixin().m(false));
        System.out.println((new Mixin()).new InnerClass().n(false));
    }
}

