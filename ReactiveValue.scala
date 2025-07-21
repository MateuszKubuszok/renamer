package renamer

sealed trait ReactiveValue[A] {

  private var _value: A = scala.compiletime.uninitialized
  private val observers = java.util.WeakHashMap[ReactiveValue.Derived[?], Unit]()

  final def value: A = _value

  final protected def set(value: A): Unit = {
    _value = value
    observers.keySet.forEach(_.update())
  }

  final def map[B](f:         A => B):                             ReactiveValue[B] = ReactiveValue.from(this)(f)
  final def map2[A2, B](var2: ReactiveValue[A2])(f: (A, A2) => B): ReactiveValue[B] = ReactiveValue.from(this, var2)(f)
}
object ReactiveValue {

  def from[A1, B](var1: ReactiveValue[A1])(f: A1 => B): ReactiveValue[B] = {
    val value = Derived(() => f(var1.value))
    var1.observers.put(value, ())
    value
  }
  def from[A1, A2, B](var1: ReactiveValue[A1], var2: ReactiveValue[A2])(f: (A1, A2) => B): ReactiveValue[B] = {
    val value = Derived(() => f(var1.value, var2.value))
    var1.observers.put(value, ())
    var2.observers.put(value, ())
    value
  }
  def from[A1, A2, A3, B](var1: ReactiveValue[A1], var2: ReactiveValue[A2], var3: ReactiveValue[A3])(f: (A1, A2, A3) => B): ReactiveValue[B] = {
    val value = Derived(() => f(var1.value, var2.value, var3.value))
    var1.observers.put(value, ())
    var2.observers.put(value, ())
    var3.observers.put(value, ())
    value
  }

  final private class Derived[A](f: () => A) extends ReactiveValue[A] {

    final def update(): Unit = set(f())

    update() // initialize
  }
}

final class ReactiveVariable[A](initial: A) extends ReactiveValue[A] {

  final def value_=(value: A): Unit = set(value)

  value = initial // initialize
}
