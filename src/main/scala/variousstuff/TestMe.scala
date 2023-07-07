package variousstuff

object TestMe extends App {


  val p = Perso("Radu")

  p match {
    case Perso("Radu", "o") => println("found'im")
    case PersoNameAndLength("Radu", 4) => println("almost fooled me")
    case _ => println("Who?")
  }

}
