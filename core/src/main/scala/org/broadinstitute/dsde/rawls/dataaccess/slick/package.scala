package org.broadinstitute.dsde.rawls.dataaccess

import _root_.slick.dbio.Effect.{Read, Write}
import _root_.slick.dbio.{DBIOAction, NoStream}
import cats.Monad

import scala.concurrent.ExecutionContext

/**
 * Created by dvoet on 2/12/16.
 */
package object slick {
  type ReadAction[T] = DBIOAction[T, NoStream, Read]
  type WriteAction[T] = DBIOAction[T, NoStream, Write]
  type ReadWriteAction[T] = DBIOAction[T, NoStream, Read with Write]

  implicit def dbioInstance(implicit ec: ExecutionContext): Monad[ReadWriteAction]  =
    new Monad[ReadWriteAction] {
      override def pure[A](x: A): ReadWriteAction[A] = _root_.slick.dbio.DBIO.successful(x)

      override def flatMap[A, B](fa: ReadWriteAction[A])(f: A => ReadWriteAction[B]): ReadWriteAction[B] = fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => ReadWriteAction[Either[A, B]]): ReadWriteAction[B] =
        f(a).flatMap {
          case Left(a1) => tailRecM(a1)(f)
          case Right(b) => _root_.slick.dbio.DBIO.successful(b)
        }
    }
  lazy val hostname :String = {
    sys.env.getOrElse("HOSTNAME","unknown-rawls")
  }
}
