package data.arrows


import scala.language.higherKinds
import scala.util.Try
import scalaz.{-\/, Arrow, Choice, \/, \/-}

trait DataDescr {

  type Data[A, B]

  type Raw

  implicit def arrowChoiceData: Arrow[Data] with Choice[Data]

  def field(name: String): Data[Raw, String]

  def nonEmpty[A, B](decoder: Data[A, Option[B]]): Data[A, B]

  def validate[A](constraint: A => Boolean): Data[A, A]

  def double: Data[String, Double]

  implicit class WithFstOp[A](data: Data[A, Option[Unit \/ Unit]]) {
    import scalaz.syntax.all._
    val ac = arrowChoiceData
    import ac._

    def withFst: Data[A, Option[A \/ A]] = (data &&& id[A]) >>> arr((_: (Option[Unit \/ Unit], A)) match {
      case (Some(-\/(())), a) => Some(-\/(a))
      case (Some(\/-(())), a) => Some(\/-(a))
      case (None, _)          => None
    })

  }

}

trait Usage extends DataDescr {

  import scalaz.syntax.all._

  case class User(name: String, email: String)

  def userData: Data[Raw, User] = {
    val name = field("name")
    val email = field("email") >>> validate[String](_.contains('@'))
    (name &&& email).mapsnd(User.tupled)
  }

  sealed trait Shape
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: String, height: String) extends Shape

  def shapeData: Data[Raw, Shape] = {
    val Decoder = Arrow[Data]
    import Decoder._

    val circle: Data[Raw, Shape] = (field("radius") >>> double).mapsnd(Circle)

    val rectangle: Data[Raw, Shape] = (field("width") &&& field("height")).mapsnd(Rectangle.tupled)

    val tpe: Data[Raw, Option[Unit \/ Unit]] = field("type") >>> arr((_: String) match {
      case "Circle"    => Some(().left)
      case "Rectangle" => Some(().right)
      case _ => None
    })

    nonEmpty(tpe.withFst) >>> (circle ||| rectangle)
  }
}

trait MapDecoder extends DataDescr {

  type Raw = Map[String, String]
  type Data[In, Out] = In => Option[Out]

  def field(name: String): Data[Raw, String] = kvs => kvs.get(name)

  def nonEmpty[A, B](decoder: A => Option[Option[B]]): A => Option[B] = decoder andThen (_.flatten)

  def validate[A](constraint: A => Boolean): A => Option[A] = a => if (constraint(a)) Some(a) else None

  def double: String => Option[Double] = s => Try(s.toDouble).toOption

  implicit def arrowChoiceData: Arrow[Data] with Choice[Data] =
    new Arrow[Data] with Choice[Data] {
      def arr[A, B](f: A => B): A => Option[B] = a => Some(f(a))
      def id[A]: A => Option[A] = Some(_)
      def compose[A, B, C](f: B => Option[C], g: A => Option[B]): A => Option[C] = a => g(a).flatMap(f)
      def first[A, B, C](fa: A => Option[B]): ((A, C)) => Option[(B, C)] = { case (a, c) => fa(a).map(b => (b, c)) }
      def choice[A, B, C](f: => A => Option[C], g: => B => Option[C]): (A \/ B) => Option[C] = {
        case -\/(a) => f(a)
        case \/-(b) => g(b)
      }
    }

}

trait Optimization extends DataDescr {
  // TODO
}

trait Documentation extends DataDescr {

  type Raw = Nothing
  type Data[In, Out] = Adt
  sealed trait Adt
  case class Record(fields: List[String]) extends Adt
  case class CoProd(alternatives: List[Record]) extends Adt

  def field(key: String): Adt = Record(key :: Nil)

  def nonEmpty[A, B](adt: Adt): Adt = adt

  def validate[A](constraint: A => Boolean): Adt = Record(Nil)

  def double: Adt = Record(Nil)

  implicit def arrowChoiceData: Arrow[Data] with Choice[Data] =
    new Arrow[Data] with Choice[Data] {
      def arr[A, B](f: A => B): Adt = Record(Nil)
      def id[A]: Adt = Record(Nil)
      def compose[A, B, C](f: Adt, g: Adt): Adt = {
        (f, g) match {
          case (Record(fs1),  Record(fs2))  => Record(fs1 ++ fs2)
          case (CoProd(as1),  CoProd(as2))  => CoProd(as1 ++ as2)
          case (Record(fs1),  CoProd(as2))  => CoProd(as2.map(fs => Record(fs.fields ++ fs1)))
          case (CoProd(as1),  Record(fs2))  => CoProd(as1.map(fs => Record(fs.fields ++ fs2)))
        }
      }
      def first[A, B, C](fa: Adt): Adt = fa
      def choice[A, B, C](f: => Adt, g: => Adt): Adt =
        (f, g) match {
          case (fs1: Record,  fs2: Record)  => CoProd(fs1 :: fs2 :: Nil)
          case (CoProd(as1),  CoProd(as2))  => CoProd(as1 ++ as2)
          case (CoProd(as1),  fs2: Record)  => CoProd(fs2 :: as1)
          case (fs1: Record,  CoProd(as2))  => CoProd(fs1 :: as2)
        }
    }

  def jsonSchema[A](adt: Adt, title: String): String = {

    def field(name: String, tab: String): String = {
      s"""$tab"$name": {
         |$tab  "type": "string"
         |$tab}""".stripMargin
    }

    def loop(schema: Adt, tab: String): String =
      schema match {
        case Record(fs) =>
          s"""$tab"properties":{
             |${fs.map(field(_, tab ++ "  ")).mkString(",\n")}
             |$tab}""".stripMargin
        case CoProd(fs) =>
          val fsJson = fs.map(loop(_, tab ++ "  "))
          s"""$tab"oneOf": [
             |${fsJson.mkString(",\n")}
             |$tab]""".stripMargin
      }

    s"""
      |{
      |  "title": "$title",
      |  "type": "object",
      |${loop(adt, "  ")}
      |}
    """.stripMargin
  }

}

object Main extends App {

  new Usage with Documentation {
    println("JSON schema for User:")
    println(jsonSchema(userData, "User"))
    println()

    println("JSON schema for Shape:")
    println(jsonSchema(shapeData, "Shape"))
    println()
  }

  new Usage with MapDecoder {

    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = userData(kvs)

    println(s"Decoded user: $decodedUser")

    val kvs2 = Map("type" -> "Circle", "radius" -> "42")

    println(s"Decoded shape: ${shapeData(kvs2)}")
  }

}
