package scalaz

/** A monad transformer stack yielding `(R, S1) => F[(W, A, S2)]`. */
sealed abstract class IndexedReaderWriterStateT[F[_], -R, W, -S1, S2, A] {
  self =>
  def run(r: R, s: S1): F[(W, A, S2)]

  /** Discards the writer component. */
  def state(r: R)(implicit F: Monad[F]): IndexedStateT[F, S1, S2, A] =
    IndexedStateT((s: S1) => F.map(run(r, s)) {
      case (w, a, s1) => (s1, a)
    })

  /** Calls `run` using `Monoid[S].zero` as the initial state */
  def runZero[S <: S1](r: R)(implicit S: Monoid[S]): F[(W, A, S2)] =
    run(r, S.zero)

  /** Run, discard the final state, and return the final value in the context of `F` */
  def eval(r: R, s: S1)(implicit F: Functor[F]): F[(W, A)] =
    F.map(run(r,s)) { case (w,a,s2) => (w,a) }

  /** Calls `eval` using `Monoid[S].zero` as the initial state */
  def evalZero[S <: S1](r:R)(implicit F: Functor[F], S: Monoid[S]): F[(W,A)] =
    eval(r,S.zero)

  /** Run, discard the final value, and return the final state in the context of `F` */
  def exec(r: R, s: S1)(implicit F: Functor[F]): F[(W,S2)] =
    F.map(run(r,s)){case (w,a,s2) => (w,s2)}

  /** Calls `exec` using `Monoid[S].zero` as the initial state */
  def execZero[S <: S1](r:R)(implicit F: Functor[F], S: Monoid[S]): F[(W,S2)] =
    exec(r,S.zero)

  def map[B](f: A => B)(implicit F: Functor[F]): IndexedReaderWriterStateT[F, R, W, S1, S2, B] =
    new IndexedReaderWriterStateT[F, R, W, S1, S2, B] {
      def run(r: R, s: S1): F[(W, B, S2)] = F.map(self.run(r, s)) {
        case (w, a, s) => (w, f(a), s)
      }
    }

  def flatMap[B, RR <: R, S3](f: A => IndexedReaderWriterStateT[F, RR, W, S2, S3, B])(implicit F: Bind[F], W: Semigroup[W]): IndexedReaderWriterStateT[F, RR, W, S1, S3, B] =
    new IndexedReaderWriterStateT[F, RR, W, S1, S3, B] {
      def run(r: RR, s1: S1): F[(W, B, S3)] = {
        F.bind(self.run(r, s1)) {
          case (w1, a, s2) => {
            F.map(f(a).run(r, s2)) {
              case (w2, b, s3) => (W.append(w1, w2), b, s3)
            }
          }
        }
      }
    }
}

object IndexedReaderWriterStateT extends ReaderWriterStateTInstances with ReaderWriterStateTFunctions {
  def apply[F[_], R, W, S1, S2, A](f: (R, S1) => F[(W, A, S2)]): IndexedReaderWriterStateT[F, R, W, S1, S2, A] = new IndexedReaderWriterStateT[F, R, W, S1, S2, A] {
    def run(r: R, s: S1): F[(W, A, S2)] = f(r, s)
  }
}

trait ReaderWriterStateTFunctions {

}

sealed abstract class IndexedReaderWriterStateTInstances0 {
  implicit def irwstFunctor[F[_], R, W, S1, S2](implicit F0: Functor[F]): Functor[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]] =
    new IndexedReaderWriterStateTFunctor[F, R, W, S1, S2] {
      implicit def F = F0
    }
}

sealed abstract class IndexedReaderWriterStateTInstances extends IndexedReaderWriterStateTInstances0 {
  implicit def irwstPlus[F[_], R, W, S1, S2](implicit F0: Plus[F]): Plus[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]] =
    new IndexedReaderWriterStateTPlus[F, R, W, S1, S2] {
      override def F = F0
    }

  implicit def rwstBind[F[_], R, W, S](implicit F0: Bind[F], W0: Semigroup[W]): Bind[ReaderWriterStateT[F, R, W, S, ?]] =
    new ReaderWriterStateTBind[F, R, W, S] {
      def F = F0
      def W = W0
    }
}

sealed abstract class ReaderWriterStateTInstances0 extends IndexedReaderWriterStateTInstances {
  implicit def irwstPlusEmpty[F[_], R, W, S1, S2](implicit F0: PlusEmpty[F]): PlusEmpty[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]] =
    new IndexedReaderWriterStateTPlusEmpty[F, R, W, S1, S2] {
      override def F = F0
    }

  implicit def rwstMonad[F[_], R, W, S](implicit W0: Monoid[W], F0: Monad[F]):
  MonadReader[ReaderWriterStateT[F, ?, W, S, ?], R] with
  MonadState[ReaderWriterStateT[F, R, W, ?, ?], S] with
  MonadListen[ReaderWriterStateT[F, R, ?, S, ?], W] =
    new ReaderWriterStateTMonad[F, R, W, S] {
      implicit def F = F0
      implicit def W = W0
    }
}

abstract class ReaderWriterStateTInstances extends ReaderWriterStateTInstances0 {
  implicit def rwstMonadPlus[F[_], R, W, S](implicit W0: Monoid[W], F0: MonadPlus[F]): MonadPlus[ReaderWriterStateT[F, R, W, S, ?]] =
    new ReaderWriterStateTMonadPlus[F, R, W, S] {
      override def F = F0
      override def W = W0
    }

  implicit def rwstHoist[R, W, S](implicit W0: Monoid[W]): Hoist[λ[(α[_], β) => ReaderWriterStateT[α, R, W, S, β]]] = 
    new ReaderWriterStateTHoist[R, W, S] {
      implicit def W = W0
    }

}

private trait IndexedReaderWriterStateTPlus[F[_], R, W, S1, S2] extends Plus[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]] {
  def F: Plus[F]

  override final def plus[A](a: IRWST[F, R, W, S1, S2, A], b: => IRWST[F, R, W, S1, S2, A]) =
    IRWST((r, s) => F.plus(a.run(r, s), b.run(r, s)))
}

private trait IndexedReaderWriterStateTPlusEmpty[F[_], R, W, S1, S2]
  extends PlusEmpty[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]]
  with IndexedReaderWriterStateTPlus[F, R, W, S1, S2] {
  def F: PlusEmpty[F]

  override final def empty[A] = IRWST((_, _) => F.empty)
}

private trait IndexedReaderWriterStateTFunctor[F[_], R, W, S1, S2] extends Functor[IndexedReaderWriterStateT[F, R, W, S1, S2, ?]] {
  implicit def F: Functor[F]

  override final def map[A, B](fa: IndexedReaderWriterStateT[F, R, W, S1, S2, A])(f: A => B): IndexedReaderWriterStateT[F, R, W, S1, S2, B] = fa map f
}

private trait ReaderWriterStateTBind[F[_], R, W, S] extends Bind[ReaderWriterStateT[F, R, W, S, ?]] with IndexedReaderWriterStateTFunctor[F, R, W, S, S] {
  implicit def F: Bind[F]
  implicit def W: Semigroup[W]

  override final def bind[A, B](fa: ReaderWriterStateT[F, R, W, S, A])(f: A => ReaderWriterStateT[F, R, W, S, B]) =
    fa flatMap f
}

private abstract class ReaderWriterStateTMonadPlus[F[_], R, W, S]
  extends MonadPlus[ReaderWriterStateT[F, R, W, S, ?]]
  with ReaderWriterStateTMonad[F, R, W, S]
  with IndexedReaderWriterStateTPlusEmpty[F, R, W, S, S] {
  override def F: MonadPlus[F]
}

private trait ReaderWriterStateTMonad[F[_], R, W, S]
  extends MonadReader[ReaderWriterStateT[F, ?, W, S, ?], R]
  with MonadState[ReaderWriterStateT[F, R, W, ?, ?], S]
  with MonadListen[ReaderWriterStateT[F, R, ?, S, ?], W]
  with ReaderWriterStateTBind[F, R, W, S] {
  implicit def F: Monad[F]
  implicit def W: Monoid[W]

  def point[A](a: => A): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((_, s) => F.point((W.zero, a, s)))
  def ask: ReaderWriterStateT[F, R, W, S, R] =
    ReaderWriterStateT((r, s) => F.point((W.zero, r, s)))
  def local[A](f: R => R)(fa: ReaderWriterStateT[F, R, W, S, A]): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((r, s) => fa.run(f(r), s))
  override def scope[A](k: R)(fa: ReaderWriterStateT[F, R, W, S, A]): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((_, s) => fa.run(k, s))
  override def asks[A](f: R => A): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((r, s) => F.point((W.zero, f(r), s)))
  def init: ReaderWriterStateT[F, R, W, S, S] =
    ReaderWriterStateT((_, s) => F.point((W.zero, s, s)))
  def get = init
  def put(s: S): ReaderWriterStateT[F, R, W, S, Unit] =
    ReaderWriterStateT((r, _) => F.point((W.zero, (), s)))
  override def modify(f: S => S): ReaderWriterStateT[F, R, W, S, Unit] =
    ReaderWriterStateT((r, s) => F.point((W.zero, (), f(s))))
  override def gets[A](f: S => A): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((_, s) => F.point((W.zero, f(s), s)))
  def writer[A](w: W, v: A): ReaderWriterStateT[F, R, W, S, A] =
    ReaderWriterStateT((_, s) => F.point((w, v, s)))
  override def tell(w: W): ReaderWriterStateT[F, R, W, S, Unit] =
    ReaderWriterStateT((_, s) => F.point((w, (), s)))
  def listen[A](ma: ReaderWriterStateT[F, R, W, S, A]): ReaderWriterStateT[F, R, W, S, (A, W)] =
    ReaderWriterStateT((r, s) => F.map(ma.run(r, s)) { case (w, a, s1) => (w, (a, w), s1)})
}

private trait ReaderWriterStateTHoist[R, W, S] extends Hoist[λ[(α[_], β) => ReaderWriterStateT[α, R, W, S, β]]] {
  implicit def W: Monoid[W]
  
  def hoist[M[_], N[_]](f: M ~> N)(implicit M: Monad[M]) =
    new (ReaderWriterStateT[M, R, W, S, ?] ~> ReaderWriterStateT[N, R, W, S, ?]) {
      def apply[A](ma: ReaderWriterStateT[M, R, W, S, A]): ReaderWriterStateT[N, R, W, S, A] = ReaderWriterStateT {
        case (r,s) => f.apply(ma.run(r,s))
      }
    }

  def liftM[M[_], A](ma: M[A])(implicit M: Monad[M]): ReaderWriterStateT[M, R, W, S, A] =
    ReaderWriterStateT( (r,s) => M.map(ma)((W.zero, _, s)))

  implicit def apply[M[_] : Monad]: Monad[ReaderWriterStateT[M, R, W, S, ?]] = 
    IndexedReaderWriterStateT.rwstMonad
}
