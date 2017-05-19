package data.monads

import scala.language.higherKinds
import scalaz.Monad

trait DataDescr {

  type Data[A]

  implicit def monadData: Monad[Data]

  def field(name: String): Data[String]

}

trait MapDecoder extends DataDescr {

  type Data[A] = Map[String, String] => Option[A]

  def field(name: String): Map[String, String] => Option[String] = kvs => kvs.get(name)

  implicit def monadData: Monad[Data] =
    new Monad[Data] {
      def bind[A, B](fa: Data[A])(f: A => Data[B]): Data[B] =
        kvs => {
          fa(kvs) match {
            case Some(a) => f(a)(kvs)
            case None    => None
          }
        }

      def point[A](x: => A): Data[A] = _ => Some(x)
    }

}

trait Optimization extends DataDescr {
  // TODO
}

trait Documentation extends DataDescr {

  type Data[A] = List[String]

  def field(key: String): List[String] = key :: Nil

  implicit def monadData: Monad[Data] =
    new Monad[Data] {
      def point[A](x: => A): List[String] = Nil
      def bind[A, B](fa: List[String])(f: A => List[String]): List[String] = fa
    }

  def jsonSchema[A](decoder: Data[A], title: String): String = {

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


trait Program extends DataDescr {

  import scalaz.syntax.all._

  case class User(name: String, email: String)

  def userData: Data[User] =
    (field("name") tuple field("email")).map(User.tupled)

  sealed trait Shape
  case class Circle(radius: String) extends Shape
  case class Rectangle(width: String, height: String) extends Shape

  def shapeData: Data[Shape] =
    for {
      tpe   <- field("type")
      shape <- tpe match {
        case "Circle"    => field("radius").map(Circle)
        case "Rectangle" => (field("width") tuple field("height")).map(Rectangle.tupled)
      }
    } yield shape

}

object Main extends App {

  new Program with Documentation {
    println("JSON schema of User:")
    println(jsonSchema(userData, "User"))
    println()

    println("JSON schema for Shape:")
    println(jsonSchema(shapeData, "Shape"))
    println()
  }

  new Program with MapDecoder {
    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = userData(kvs)

    println(s"Decoded user: $decodedUser")

    val kvs2 = Map("type" -> "Circle", "radius" -> "42")

    println(s"Decoded shape: ${shapeData(kvs2)}")
  }

}