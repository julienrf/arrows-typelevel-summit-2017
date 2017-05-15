package arrows.applicativefunctors

import cats.Applicative

import scala.language.higherKinds

trait Decoders {

  type Decoder[A]

  implicit def applicativeDecoder: Applicative[Decoder]

  def field(name: String): Decoder[String]

}

trait Usage {

  val decoders: Decoders

  import decoders._
  import cats.implicits._

  case class User(name: String, email: String)

  def userDecoder: Decoder[User] =
    (field("name"), field("email")).map2(User)

}

trait MapDecoder extends Decoders {

  type Decoder[A] = Map[String, String] => Option[A]

  def field(name: String): Map[String, String] => Option[String] = kvs => kvs.get(name)

  implicit def applicativeDecoder: Applicative[Decoder] =
    new Applicative[Decoder] {
      def pure[A](x: A): Decoder[A] = _ => Some(x)
      def ap[A, B](ff: Decoder[A => B])(fa: Decoder[A]): Decoder[B] =
        kvs => {
          (ff(kvs), fa(kvs)) match {
            case (Some(f), Some(a)) => Some(f(a))
            case _ => None
          }
        }
    }

}

trait Documentation extends Decoders {

  type Decoder[A] = List[String]

  def field(key: String): List[String] = key :: Nil

  implicit def applicativeDecoder: Applicative[Decoder] =
    new Applicative[Decoder] {
      def pure[A](x: A): List[String] = Nil
      def ap[A, B](ff: List[String])(fa: List[String]): List[String] = ff ++ fa
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

  new Usage {
    val decoders = new Documentation {}
    println(s"`userDecoder` uses keys ${userDecoder.mkString(", ")}.")
    println()
    println("JSON schema:")
    println(decoders.jsonSchema(userDecoder, "User"))
    println()
  }

  new Usage {
    val decoders = new MapDecoder {}

    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = userDecoder(kvs)

    println(s"Decoded user: $decodedUser")
  }

}