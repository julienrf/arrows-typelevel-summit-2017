package data.applicativefunctors


import scala.language.higherKinds
import scalaz.Applicative

trait DataDescr {

  type Data[A]

  implicit def applicativeData: Applicative[Data]

  def field(name: String): Data[String]

}

trait MapDecoder extends DataDescr {

  type Data[A] = Map[String, String] => Option[A]

  def field(name: String): Map[String, String] => Option[String] = kvs => kvs.get(name)

  implicit def applicativeData: Applicative[Data] =
    new Applicative[Data] {
      def point[A](x: => A): Data[A] = _ => Some(x)
      def ap[A, B](fa: => Data[A])(ff: => Data[A => B]): Data[B] =
        kvs => {
          (ff(kvs), fa(kvs)) match {
            case (Some(f), Some(a)) => Some(f(a))
            case _ => None
          }
        }
    }

}

trait Documentation extends DataDescr {

  type Data[A] = Record
  case class Record(fields: List[String])

  def field(key: String): Record = Record(key :: Nil)

  implicit def applicativeData: Applicative[Data] =
    new Applicative[Data] {
      def point[A](x: => A): Record = Record(Nil)
      def ap[A, B](fa: => Record)(ff: => Record): Record = Record(ff.fields ++ fa.fields)
    }

  def jsonSchema(record: Record, title: String): String = {

    def field(name: String): String =
      s"""    "$name": {
         |      "type": "string"
         |    }""".stripMargin

    s"""
      |{
      |  "title": "$title",
      |  "type": "object",
      |  "properties": {
      |${record.fields.map(field).mkString(",\n")}
      |  }
      |}
    """.stripMargin
  }

}

trait Program extends DataDescr {

  import scalaz.syntax.all._

  case class User(name: String, email: String)

  def dataUser: Data[User] =
    (field("name") tuple field("email")).map(User.tupled)

}

object Main extends App {

  new Program with Documentation {
    println("JSON schema of User:")
    println(jsonSchema(dataUser, "User"))
    println()
  }

  new Program with MapDecoder {
    val kvs = Map("foo" -> "bar", "name" -> "Julien", "email" -> "julien@richard-foy.fr")

    val decodedUser = dataUser(kvs)

    println(s"Decoded user: $decodedUser")
  }

}