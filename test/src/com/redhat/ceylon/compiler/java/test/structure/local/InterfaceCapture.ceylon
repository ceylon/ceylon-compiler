@noanno
abstract class InterfaceCapture() {
    Integer nonsharedValue = 0;
    Integer nonsharedGetter => 0;
    shared Integer sharedValue = 0;
    shared Integer sharedGetter => 0;
    shared default Integer defaultValue = 0;
    shared default Integer defaultGetter => 0;
    shared formal Integer formalValue;
    
    variable Integer nonsharedVariable = 0;
    Integer nonsharedGetterSetter => 0;
    assign nonsharedGetterSetter{}
    shared variable Integer sharedVariable = 0;
    shared Integer sharedGetterSetter => 0;
    assign sharedGetterSetter{}
    shared variable default Integer defaultVariable = 0;
    shared default Integer defaultGetterSetter => 0;
    assign defaultGetterSetter{}
    shared variable formal Integer formalVariable;
    
    Integer nonsharedMethod() => 0;
    shared Integer sharedMethod() => 0;
    shared default Integer defaultMethod() => 0;
    shared formal Integer formalMethod();
    
    interface CapturingInterface {
        shared Integer values 
                => nonsharedValue
                    + sharedValue 
                    + defaultValue 
                    + formalValue;
        
        shared Integer getters 
                => nonsharedGetter
                    + sharedGetter 
                    + defaultGetter;
        
        shared Integer variables 
                => nonsharedVariable
                    + sharedVariable
                    + defaultVariable 
                    + formalVariable;
        
        shared Integer getterSetters 
                => nonsharedGetterSetter
                    + sharedGetterSetter 
                    + defaultGetterSetter;
        
        shared Integer methods
                => nonsharedMethod()
                    + sharedMethod() 
                    + defaultMethod() 
                    + formalMethod();
    }
}