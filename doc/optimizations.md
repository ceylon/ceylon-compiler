# Optimizations

The Ceylon specification permits compilers to make optimizations where 
these do not affect the semantics of the program being compiled. On the JVM 
those semantics are interpreted as follows:

* All computations performed by code compiled with 
  optimizations **must** have the same results as the same computations performed
  with unoptimized bytecode.
  
* The results **must** be the same even in the 
  event of numeric overflow during the execution of the unoptimized version. 

* If a `ceylon.language::Exception` (though not a JVM `java.lang.Error`) propagates from the unoptimized 
  code then the same type of `ceylon.language::Exception` **must** propagate from the optimized 
  code. The exception messages **must** be equal. The exception stacktraces 
  **may not** be the same.

* If and only if the unoptimized version results in a JVM `java.lang.Error` being thrown (for example 
`OutOfMemoryError`) then execution of the optimized version **may**
also result in a JVM `java.lang.Error` (though it need not be the same type of `java.lang.Error`). 

* The optimized version **should** *usually* execute in less time. 

* The optimized version **should never** execute in substantially more time.

## Boxed primitive types

TODO

## Power unrolling

Given an expression of the form:

    base^power
    
the compiler will use inline multiplication instead of invoking `base.power(power)` if:

* the static type of `base` is `ceylon.language::Integer` or `ceylon.language::Float` and
* `power` is a `NaturalLiteral` (i.e. power is a strictly positive `ceylon.language::Integer` literal) and
* the value of `power` is less than a certain implementation-defined maximum 
(to prevent code bloat with expressions like `2^1_000_000_000`).

This means that `x^3` is no slower than `x*x*x`.


## Iteration using `for`

### `for (element in start..end)`

Given a `for` statement of the form:

    for (element in start..end) {
    
    }

where 

* the static type of the `(start..end)` expression is `Range<Integer>` or `Range<Character>`
   
the compiler will emit a C-style `for` loop using a JVM primitive counter 
instead of using the usual `Iterable` contract.

**TODO When arguments are literals try to use an `int` counter if we can prove there's no overflow**

### `for (element in (start..end).by(step))`

Given a `for` statement of the form:

    for (element in (start..end).by(step)) {
    
    }

where 

* the static type of the `(start..end)` expression is `Range<Integer>` or `Range<Character>`
   
the compiler will emit a C-style `for` loop using a JVM primitive counter 
instead of using the usual `Iterable` contract.

**TODO When arguments are literals try to use an `int` counter if we can prove there's no overflow**

### `for (element in range)` or `for (element in range.by(step))`

Given a `for` statement of the form:

    for (element in range) {
    
    }
    
or 

    for (element in range.by(step)) {
    
    }
    
where:

* the static type of `range` has `Range` as a supertype

the compiler will emit a C-style `for` loop accessing `successor` or `predecessor`
instead of using the usual `Iterable` contract.


### `for (element in arraySequence)`

Given a `for` statement of the form:

    for (element in arraySequence) {
    
    }

where:

* the static type of `arraySequence` has `ArraySequence` as a supertype

the compiler will emit a C-style `for` loop using a primitive counter and
indexed access to the `ArraySequence` instead of using the usual `Iterable` 
contract.

### `for (element in array)`

Given a `for` statement of the form:

    for (element in arraySequence) {
    
    }

where:

* the static type of `array` has `Array` as a supertype

the compiler will emit a C-style `for` loop using a primitive counter and
indexed access to the `Array` instead of using the usual `Iterable` 
contract.

### `for (element in javaArray.array)`

Given a `for` statement of the form:

    for (element in javaArray.array) {
    
    }
    
where:

* the static type of `javaArray` is a JVM array virtual type (e.g. `java.lang::IntArray` which is erased to a JVM `int[]`)
    
the compiler will emit a C-style `for` loop using indexed access instead of using the usual 
`Iterable` contract.

### `for (element in iterable)`

Given a `for` statement of the form:

    for (element in iterable) {
    
    }
    
where:

* the static type of `iterable`

TODO

## Named arguments

### Value arguments (aka attribute arguments)

The most general way to convert a value argument
```ceylon
fun {
    value val {
        return "Hi";
    }
};
```
for a named arguments invocation is to generate an anonymous class that holds the converted body:
```java
final class val$1 implements .com.redhat.ceylon.compiler.java.language.Getter<.java.lang.String> {
    private val$1() {}
    public .java.lang.String get_() {
        return "Hi";
    }
}
final .com.redhat.ceylon.compiler.java.language.Getter<.java.lang.String> val$1 = new val$1();
final .java.lang.String arg$0$0 = val$1.get_();
```

However, this is only rarely necessary, and often it is instead possible to use a `let` expression,
depending on how the original body uses `return` statements.

#### Early returns

A `return` is said to be *early* if it's not followed by statements in any surrounding block.

For example, the following contains an early return:
```ceylon
if (condition) {
    return a;
}
return b;
```
On the other hand, the following does not contain an early return:
```ceylon
if (condition) {
    return a;
} else {
    return b;
}
```

#### Simple case

If the original body contains no early return statements, it is possible to prepend a declaration of
a special variable to hold the return value, and then replace every return with an assignment to that
variable. The entire body can then be inlined into the surrounding let expression:

```java
final .java.lang.String $ceylontmp$returnValue$0;
if (condition) {
    $ceylontmp$returnValue$0 = a;
} else {
    $ceylontmp$returnValue$0 = b;
}
final .java.lang.String arg$0$0 = $ceylontmp$returnValue$0;
```

(The extra `$ceylontmp$` variable is necessary because it sometimes needs to be boxed
before being assigned to `arg$0$0`, which we don't want to do in every return statement.)

#### General case

If the original body contains early returns, then converting a `return` to an assignment changes the
semantics of the block: statements following what was previously an early return would now be executed.
To avoid this, the entire block can be wrapped in a `do { ... } while (false);` loop, and every
return assignment is then accompanied by a break from that loop:

```java
final .java.lang.String $ceylontmp$returnValue$0;
$returnLabel: do {
    if (condition) {
        $ceylontmp$returnValue$0 = a;
        break $returnlabel;
    }
    $ceylontmp$returnValue$0 = b;
    break $returnLabel;
} while (false);
final .java.lang.String arg$0$0 = $ceylontmp$returnValue$0;
```

This optimization is currently disabled because when used in a constructor, it can crash javac.
More precisely, the following crashes javac:
```java
this.x = (
   let
   {
       final .java.lang.Object $ceylontmp$returnValue$15;
       $ceylontmp$returnLabel$16: do {
           {
               $ceylontmp$returnValue$15 = .ceylon.language.finished_.get_();
               break $ceylontmp$returnLabel$16;
           }
       }                 while (false);
       final .java.lang.Object $arg$0 = $ceylontmp$returnValue$15;
       }
   returning $arg$0;
   );
```
while the exact same let expression, assigned to a new `.java.lang.Object $ceylontmp$x$23`, works just fine.
(To investigate this, apply [this patch](https://gist.github.com/lucaswerkmeister/f991b0f2330d47263119)
to your tree, then run the `testInvGetterArgumentNamedInvocation` from `ExpressionTest2`.)
