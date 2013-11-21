trait Similarity {
  def isSimilar(x: Any): Boolean
  def isNotSimilar(x: Any): Boolean = !isSimilar(x)
  trait Inner {
    def concrete(x: Any): Boolean = true
  }
}

object Outer {
    def m(b : Boolean): Boolean = {
      trait Trait {
        def concrete(x: Any): Boolean = b
      }
      class C extends Trait {
      
      }
      return new C().concrete();
  }
}
