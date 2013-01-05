package xkcd1083

import scala.language.implicitConversions

import scala.util.{Try, Success, Failure}

object PromiseImplicits {
  import scala.concurrent.{Future => AkkaFuture, ExecutionContext, Promise}
  import dispatch.{Promise => DispatchPromise}

  implicit def DispatchPromiseToAkkaFuture[T](
    dp: DispatchPromise[Either[Throwable, T]])(
    implicit ec: ExecutionContext
  ): AkkaFuture[T] = {
    val p = Promise[T]()
    for (t <- dp.either.right) p.complete(either2try(t))
    for (f <- dp.either.left)  p.failure(f)
    p.future
  }
  def either2try[T](ei: Either[Throwable, T]): Try[T] = {
    ei match {
       case Right(v) => Success(v)
       case Left(t) => Failure(t)
    }
  }
}
