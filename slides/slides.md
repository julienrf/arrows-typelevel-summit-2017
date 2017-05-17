% Do it with (Free?) arrows!
% Julien Richard-Foy <julien.richard-foy@epfl.ch>
   
  Typelevel Summit Copenhagen -- June 3, 2017
   
  [http://julienrf.github.io/2017/arrows](http://julienrf.github.io/2017/arrows)

# Introduction

### What is a monad? {.unnumbered}

~~~ scala
trait Monad[F[_]] {
  def point[A](a: A): F[A]
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
}
~~~

### What is an arrow? {.unnumbered}

For $C$ any category, its *arrow category* $Arr(C)$ is the category such that:

- an object $a$ of $Arr(C)$ is a morphism $a: a_0 \rightarrow{} a_1$ of $C$;
- a morphism $f: a \rightarrow{} b$ of $Arr(C)$ is a commutative
  square <br><img src="square.png" style="max-width: 25%"/> in $C$;
- composition in $Arr(C)$ is given simply by placing commutative squares side
  by side to get a commutative oblong.

### Power? Constraint? {.unnumbered}

> Monads are more *powerful* than applicative functors

- What kind of **power** are we talking about?
- Benefits of **constraining** this power?

### Goals of this talk {.unnumbered}

- Make these things more **intuitive**
- Have **fun**

#### Secondary goals {.unnumbered}

- Make the world a **better place**

### Non-goals of this talk {.unnumbered}

- Explain what a *free monad* is

### Agenda {.unnumbered}

- Context
- Applicative Functors
- Monads
- Arrows

# Context

## Description vs interpretation

### Description vs interpretation {.unnumbered}

![](magritte.png)

### Description: HTML document {.unnumbered}

~~~ html
<html>
  <head><title>This is not a title</title></head>
  <body/>
</html>
~~~

### Description: arithmetic expression {.unnumbered}

~~~ scala
1 + 1   // This is not an addition
~~~

### The essence of descriptions {.unnumbered}

> - “atoms” (`1`, `"foo"`, `point(1, 2)`)
> - operators (`+`, `concat`, `move`)

### Interpretation**s** {.unnumbered}

---------------------------------------------------
Description        Interpretations
------------------ --------------------------------
HTML document      rendering

arithmetic         evaluation, simplification,
expression         pretty print

image              draw on screen, generate a file

API endpoint       client, server, documentation
---------------------------------------------------

### Interpretations {.unnumbered}

- Transform (optimize)
- Evaluate
- Generate (code, assets)

## Domain: data

### Data description {.unnumbered}

- Records with fields
- Sum types

<img src="data-user.svg" style="width: 30%" />

### Interpretations of data descriptions {.unnumbered}

- serialization
- deserialization
- schema documentation
- UI form

### Let’s design a *data description language* {.unnumbered}

~~~ scala
trait DataDescr {

  /** A description of data of type `A` */
  type Data[A]

  /** “axiom” describing a record with one
    * field of type `String`
    */
  def field(name: String): Data[String]

}
~~~

### Let’s try our `DataDescr` language {.unnumbered}

~~~ scala
trait Program extends DataDescr {

  /**
    * A record type with on field named “x”
    * and containing a `String` value
    */
  val x: Data[String] = field("x")

}
~~~

### Let’s describe a `User` data type (1) {.unnumbered}

~~~ scala
trait Program extends DataDescr {

  case class User(name: String, email: String)







}
~~~

### Let’s describe a `User` data type (2) {.unnumbered}

~~~ scala
trait Program extends DataDescr {

  case class User(name: String, email: String)

  val user: Data[User] = {


    ???
  }

}
~~~

### Let’s describe a `User` data type (3) {.unnumbered}

~~~ scala
trait Program extends DataDescr {

  case class User(name: String, email: String)

  val user: Data[User] = {
    val name  = field("name")

    ???
  }

}
~~~

### Let’s describe a `User` data type (4) {.unnumbered}

~~~ scala
trait Program extends DataDescr {

  case class User(name: String, email: String)

  val user: Data[User] = {
    val name  = field("name")
    val email = field("email")
    ???
  }

}
~~~

# Applicative Functors

## We need the power of applicative functors!

### Let’s upgrade our language {.unnumbered}

~~~ scala
import scalaz.Applicative

trait DataDescr {

  type Data[A]

  def field(name: String): Data[String]

  /** Pretend that `Data[_]` is an applicative functor */
  implicit def applicativeData: Applicative[Data]

}
~~~

### Feel the power! (1) {.unnumbered}

~~~ scala
import scalaz.syntax._

trait Program extends DataDescr {

  case class User(name: String, email: String)

  val user: Data[User] = {
    val name  = field("name")
    val email = field("email")
    (name tuple email) // Data[(String, String)]
  }

}
~~~

### Feel the power! (2) {.unnumbered}

~~~ scala
import scalaz.syntax._

trait Program extends DataDescr {

  case class User(name: String, email: String)

  val user: Data[User] = {
    val name  = field("name")
    val email = field("email")
    (name tuple email).map(User.tupled)
  }

}
~~~

### Intuition of an applicative functor {.unnumbered}



## Interpreters

### Decoder {.unnumbered}

### Documentation {.unnumbered}

# Monads

# Arrows

# Choice

# Codecs

# Lenses

# Cartesian Closed Categories

---

- Cartesian closed categories (CCCs) can model lambda calculus [ref]
- Arrows share similarities with CCCs

~~~ scala
trait Category[=>:[_, _]] {
  def id[A]: A =>: A
  def compose[A, B, C](bc: B =>: C, ab: A =>: B): A =>: C
}

trait Cartesian[=>:[_, _]] extends Category[=>:] {
  def &&& [A, B, C](ab: A =>: B, ac: A =>: C): A =>: (B, C)
  def projl[A, B]: (A, B) =>: A
  def projr[A, B]: (A, B) =>: B
}

trait Closed[=>:[_, _]] extends Cartesian[=>:] {
  def apply[A, B]: (A => B, A) =>: B
  def curry[A, B, C](abc: (A, B) =>: C): A =>: (B => C)
  def uncurry[A, B, C](abc: A =>: (B => C)): (A, B) =>: C
}
~~~
