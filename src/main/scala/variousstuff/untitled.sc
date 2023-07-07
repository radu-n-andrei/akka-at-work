case class TestClass(i: Int) {
  private val innerI: Int = i * 2
}

object TestClass {
  def unapply(tc: TestClass): Option[Int] = Some(tc.innerI)
}

val t1 = new TestClass(12)

t1 match {
  case TestClass(i) => println(s"matched on $i")
}