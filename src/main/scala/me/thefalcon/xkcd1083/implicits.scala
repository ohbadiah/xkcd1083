package me.thefalcon.xkcd1083


object PromiseImplicits {
  import akka.dispatch.{Future => AkkaFuture, ExecutionContext, Promise}
  import dispatch.{Promise => DispatchPromise}

  implicit def DispatchPromiseToAkkaFuture[T](
    dp: DispatchPromise[Either[Throwable, T]])(
    implicit ec: ExecutionContext
  ): AkkaFuture[T] = {
    val p = Promise[T]()
    for (t <- dp.either.right) p.complete(t)
    for (f <- dp.either.left)  p.failure(f)
    p
  }
}
