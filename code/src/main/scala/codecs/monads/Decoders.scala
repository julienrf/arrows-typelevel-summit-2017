package codecs.monads

import scala.language.higherKinds
import scalaz.Monad

trait Decoders {

  type Decoder[A]

  implicit def monadDecoder: Monad[Decoder]

  def field(name: String): Decoder[String]

}

trait Usage {

  val decoders: Decoders

  import decoders._
  import scalaz.syntax.all._

  case class User(name: String, email: String)

  def userDecoder: Decoder[User] =
    (field("name") tuple field("email")).map(User.tupled)

  sealed trait Shape
  case class Circle(radius: String) extends Shape
  case class Rectangle(width: String, height: String) extends Shape

  def shapeDecoder: Decoder[Shape] =
    for {
      tpe <- field("type")
      shape <- tpe match {
        case "Circle"    => field("radius").map(Circle)
        case "Rectangle" => (field("width") tuple field("height")).map(Rectangle.tupled)
      }
    } yield shape

}

trait MapDecoder extends Decoders {

  type Decoder[A] = Map[String, String] => Option[A]

  def field(name: String): Map[String, String] => Option[String] = kvs => kvs.get(name)

  implicit def monadDecoder: Monad[Decoder] =
    new Monad[Decoder] {
      def bind[A, B](fa: Decoder[A])(f: A => Decoder[B]): Decoder[B] =
        kvs => {
          fa(kvs) match {
            case Some(a) => f(a)(kvs)
            case None    => None
          }
        }

      def point[A](x: => A): Decoder[A] = _ => Some(x)
    }

}

trait Optimization extends Decoders {
  // TODO
}

trait Documentation extends Decoders {

  type Decoder[A] = List[String]

  def field(key: String): List[String] = key :: Nil

  implicit def monadDecoder: Monad[Decoder] =
    new Monad[Decoder] {
      def point[A](x: => A): List[String] = Nil
      def bind[A, B](fa: List[String])(f: A => List[String]): List[String] = ???
    }

  def jsonSchema[A](decoder: Decoder[A], title: String): String = {

    def field(name: String): String =
      s"""    "$name": {
         |      "type": "string"
         |    }""".stripMargin

    s"""
      |{
      |  "title": "$title",
      |  "type": "object",
      |  "properties": {
      |${decoder.map(field).mkString(",\n")}
      |  }
      |}
    """.stripMargin
  }

}


object Main extends App {

//  new Usage {
//    val decoders = new Documentation {}
//    println(s"`userDecoder` uses keys ${userDecoder.mkString(", ")}.")
//    println()
//    println("JSON schema:")
//    println(decoders.jsonSchema(userDecoder, "User"))
//    println()
//  }

  new Usage {
    val decoders = new MapDecoder {}

    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = userDecoder(kvs)

    println(s"Decoded user: $decodedUser")

    val kvs2 = Map("type" -> "Circle", "radius" -> "42")

    println(s"Decoded shape: ${shapeDecoder(kvs2)}")
  }

}