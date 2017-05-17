package codecs.arrows


import scala.language.higherKinds
import scalaz.{-\/, Arrow, Choice, \/, \/-}

trait Decoders {

  type Decoder[In, Out]

  type Input

  implicit def arrowChoiceDecoder: Arrow[Decoder] with Choice[Decoder]

  def field(name: String): Decoder[Input, String]

  def nonEmpty[A, B](decoder: Decoder[A, Option[B]]): Decoder[A, B]

}

trait Usage {

  val decoders: Decoders

  import decoders._
  import scalaz.syntax.all._

  case class User(name: String, email: String)

  def userDecoder: Decoder[Input, User] =
    (field("name") &&& field("email")).mapsnd(User.tupled)

  sealed trait Shape
  case class Circle(radius: String) extends Shape
  case class Rectangle(width: String, height: String) extends Shape

  def shapeDecoder: Decoder[Input, Shape] = {
    val Decoder = Arrow[Decoder]
    import Decoder._

    val circle: Decoder[Input, Shape] = field("radius").mapsnd(Circle)

    val rectangle: Decoder[Input, Shape] = (field("width") &&& field("height")).mapsnd(Rectangle.tupled)

    val tpe = (field("type") &&& id[Input]) >>> arr((_: (String, Input)) match {
      case ("Circle",    input) => Some(-\/(input))
      case ("Rectancle", input) => Some(\/-(input))
      case _ => None
    })

    nonEmpty(tpe) >>> (circle ||| rectangle)
  }
}

trait MapDecoder extends Decoders {

  type Input = Map[String, String]
  type Decoder[In, Out] = In => Option[Out]

  def field(name: String): Decoder[Input, String] = kvs => kvs.get(name)

  def nonEmpty[A, B](decoder: A => Option[Option[B]]): A => Option[B] = decoder andThen (_.flatten)

  implicit def arrowChoiceDecoder: Arrow[Decoder] with Choice[Decoder] =
    new Arrow[Decoder] with Choice[Decoder] {
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

trait Optimization extends Decoders {
  // TODO
}

trait Documentation extends Decoders {

  type Input = Nothing
  type Decoder[In, Out] = Schema
  sealed trait Schema
  case class Fields(value: List[String]) extends Schema
  case class OneOf(alternatives: List[Fields]) extends Schema

  def field(key: String): Schema = Fields(key :: Nil)

  def nonEmpty[A, B](decoder: Schema): Schema = decoder

  implicit def arrowChoiceDecoder: Arrow[Decoder] with Choice[Decoder] =
    new Arrow[Decoder] with Choice[Decoder] {
      def arr[A, B](f: A => B): Schema = Fields(Nil)
      def id[A]: Schema = Fields(Nil)
      def compose[A, B, C](f: Schema, g: Schema): Schema = {
        (f, g) match {
          case (Fields(fs1), Fields(fs2)) => Fields(fs1 ++ fs2)
          case (OneOf(ss1),  OneOf(ss2))  => OneOf(ss1 ++ ss2)
          case (Fields(fs1), OneOf(ss2))  => OneOf(ss2.map(fs => Fields(fs.value ++ fs1)))
          case (OneOf(ss1),  Fields(fs2)) => OneOf(ss1.map(fs => Fields(fs.value ++ fs2)))
        }
      }
      def first[A, B, C](fa: Schema): Schema = fa
      def choice[A, B, C](f: => Schema, g: => Schema): Schema =
        (f, g) match {
          case (fs1: Fields, fs2: Fields) => OneOf(fs1 :: fs2 :: Nil)
          case (OneOf(ss1),  OneOf(ss2))  => OneOf(ss1 ++ ss2)
          case (OneOf(ss1),  fs2: Fields) => OneOf(fs2 :: ss1)
          case (fs1: Fields, OneOf(ss2))  => OneOf(fs1 :: ss2)
        }
    }

  def jsonSchema[A](decoder: Decoder[Input, A], title: String): String = {

    def field(name: String, tab: String): String = {
      s"""$tab"$name": {
         |$tab  "type": "string"
         |$tab}""".stripMargin
    }

    def loop(schema: Schema, tab: String): String =
      schema match {
        case Fields(fs) =>
          s"""$tab"properties":{
             |${fs.map(field(_, tab ++ "  ")).mkString(",\n")}
             |$tab}""".stripMargin
        case OneOf(fs) =>
          val fsJson = fs.map(loop(_, tab ++ "  "))
          s"""$tab"oneOf": [
             |${fsJson.mkString(",\n")}
             |$tab]""".stripMargin
      }

    s"""
      |{
      |  "title": "$title",
      |  "type": "object",
      |${loop(decoder, "  ")}
      |}
    """.stripMargin
  }

}

object Main extends App {

  new Usage {
    val decoders = new Documentation {}
    println("JSON schema:")
    println(decoders.jsonSchema(userDecoder, "User"))
    println()

    println("JSON schema for Shape:")
    println(decoders.jsonSchema(shapeDecoder, "Shape"))
    println()
  }

  new Usage {
    val decoders = new MapDecoder {}

    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = userDecoder(kvs)

    println(s"Decoded user: $decodedUser")

    val kvs2 = Map("type" -> "Circle", "radius" -> "42")

    println(s"Decoded shape: ${shapeDecoder(kvs2)}")
  }

}
