import com.redhat.ceylon.compiler.test.structure.import_.pkg{...}

@nomodel
class ImportAlias() {
    void m() {
        C1();
        C2();
        variable boolean b := f1;
        b := f2;
        m1();
        m2();
    }
}