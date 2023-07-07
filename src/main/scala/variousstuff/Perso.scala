package variousstuff

case class Perso(name: String) {
  private val p: String = name.tail
  private def initial(): String = name.head.toString + Perso.smth
}

object Perso{

  private val smth: String = "o"

  def doSmthSpecial(person: Perso): Unit = println(person.p)

  def unapply(perso: Perso): Option[(String, String)] = Some((perso.name, perso.initial()))
}

object PersoNameAndLength {
  def unapply(perso: Perso): Option[(String, Int)] = Some((perso.name, perso.name.length))
}
