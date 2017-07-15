package com.quadas.linker.router

import com.twitter.concurrent.AsyncSemaphore
import com.twitter.conversions.time._
import com.twitter.finagle.client.{DefaultPool, Transporter}
import com.twitter.finagle.factory.TimeoutFactory
import com.twitter.finagle.filter.RequestSemaphoreFilter
import com.twitter.finagle.liveness.{FailureAccrualFactory, FailureAccrualPolicy}
import com.twitter.finagle.loadbalancer.{Balancers, LoadBalancerFactory}
import com.twitter.finagle.quadas.linker.QuadasLinker
import com.twitter.finagle.service.{ExpiringService, FailFastFactory, PendingRequestFilter, Retries, TimeoutFilter, Backoff => TBackoff, RetryBudget => TRetryBudget}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.{ListeningServer, Stack, param}
import com.twitter.util.Duration

trait Router {
  def apply(config: RouterConfig): ListeningServer
}

object Router {
  def apply(config: RouterConfig): ListeningServer = {
    choose(config.`type`)(config)
  }

  def choose(proto: RouterProtocol): Router = {
    proto match {
      case RouterProtocols.http => HttpRouter
      case RouterProtocols.thrift => ThriftRouter
    }
  }
}

object Routers {
  import RichStackOps._

  def disable[P](opt: Option[Boolean])(disabled: => P): Option[P] = {
    opt match {
      case Some(false) => Some(disabled)
      case _ => None
    }
  }

  def enable[P](opt: Option[Boolean])(enabled: => P): Option[P] = {
    opt match {
      case Some(true) => Some(enabled)
      case _ => None
    }
  }

  def mkBackoff(b: Backoff): Stream[Duration] = {
    b match {
      case Backoffs.constant(dur) => TBackoff.constant(dur)
      case Backoffs.jittered(min, max) =>
        TBackoff.decorrelatedJittered(min, max)
    }
  }

  def mkSessionPool(sp: SessionPool): DefaultPool.Param = {
    val default = DefaultPool.Param.param.default
    val low = sp.minSize.getOrElse(default.low)
    val high = sp.maxSize.getOrElse(default.high)
    val maxWaiters = sp.maxWaiters.getOrElse(default.maxWaiters)

    default.copy(low = low, high = high, maxWaiters = maxWaiters)
  }

  def mkFailureAccrual(fa: FailureAccrual): FailureAccrualFactory.Param = {
    import QuadasLinker.failureAccrualFactoryParam.{Configured, Disabled}
    fa match {
      case FailureAccruals.none => Disabled
      case FailureAccruals.consecutiveFailures(numFail, backoff) =>
        Configured(
            () =>
              FailureAccrualPolicy.consecutiveFailures(numFail,
                                                       mkBackoff(backoff)))
      case FailureAccruals.successRate(rate, requests, backoff) =>
        Configured(
            () =>
              FailureAccrualPolicy
                .successRate(rate, requests, mkBackoff(backoff)))
      case FailureAccruals.successRateWindowed(rate, window, backoff) =>
        Configured(
            () =>
              FailureAccrualPolicy
                .successRateWithinDuration(rate, window, mkBackoff(backoff)))
    }
  }

  def mkRetry(retry: Retry): Retries.Budget = {
    Retries.Budget(
        TRetryBudget(retry.budget.ttl,
                     retry.budget.minRetriesPerSec,
                     retry.budget.percentCanRetry),
        mkBackoff(retry.backoff)
    )
  }

  def mkExpiringSession(session: Session): ExpiringService.Param = {
    ExpiringService.Param(
        idleTime = session.maxIdleTime.getOrElse(Duration.Top),
        lifeTime = session.maxLifeTime.getOrElse(Duration.Top)
    )
  }

  def mkLoadBalancer(loadBalancer: LoadBalancer): LoadBalancerFactory.Param = {
    val factory = loadBalancer match {
      case LoadBalancers.p2c(_, maxEffort) =>
        Balancers.p2c(maxEffort.getOrElse(Balancers.MaxEffort))
      case LoadBalancers.ewma(_, decayTime, maxEffort) =>
        Balancers.p2cPeakEwma(
            decayTime = decayTime.getOrElse(10.seconds), // default
            maxEffort = maxEffort.getOrElse(Balancers.MaxEffort)
        )
      case LoadBalancers.aperture(_,
                                  smoothWindow,
                                  maxEffort,
                                  lowLoad,
                                  highLoad,
                                  minAperture) =>
        Balancers.aperture(
            smoothWin = smoothWindow.getOrElse(5.seconds),
            maxEffort = maxEffort.getOrElse(Balancers.MaxEffort),
            lowLoad = lowLoad.getOrElse(0.5),
            highLoad = highLoad.getOrElse(2.0),
            minAperture = minAperture.getOrElse(1)
        )
      case LoadBalancers.heap(_) => Balancers.heap()
      case LoadBalancers.roundRobin(_, maxEffort) =>
        Balancers.roundRobin(maxEffort.getOrElse(Balancers.MaxEffort))
    }
    LoadBalancerFactory.Param(factory)
  }

  def mkServerAdmissionControl(
      admissionControl: AdmissionControl): PendingRequestFilter.Param = {
    val limit = admissionControl.maxPendingRequests.filter(_ != Int.MaxValue)
    PendingRequestFilter.Param(limit = limit)
  }

  def mkClientAdmissionControl(
      admissionControl: AdmissionControl): RequestSemaphoreFilter.Param = {
    val conc = admissionControl.maxConcurrentRequests.filter(_ != Int.MaxValue)
    val waiter = admissionControl.maxWaiters.filter(_ != Int.MaxValue)
    val server = conc.map { c =>
      waiter.fold(new AsyncSemaphore(c))(new AsyncSemaphore(c, _))
    }

    RequestSemaphoreFilter.Param(server)
  }

  // format: OFF
  def configureParams(config: RouterConfig)(
      params: Stack.Params): Stack.Params = {
    val misc = config.misc
    val general = config.general
    params
      .having(param.Label(config.name))
      .maybeWith(disable(misc.stats)(param.Stats(NullStatsReceiver)))
      .maybeWith(disable(misc.trace)(param.Tracer(NullTracer)))
      .maybeWith(general.timeout.flatMap(_.total).map(TimeoutFilter.Param.apply))
      .maybeWith(general.timeout.flatMap(_.connect).map(Transporter.ConnectTimeout.apply))
      .maybeWith(general.admission.map(mkServerAdmissionControl))
      .maybeWith(general.admission.map(mkClientAdmissionControl))
      .maybeWith(general.session.flatMap(_.acquisitionTimeout).map(TimeoutFactory.Param.apply))
      .maybeWith(general.session.map(mkExpiringSession))
      .maybeWith(general.session.flatMap(_.pool).map(mkSessionPool))
      .maybeWith(general.session.flatMap(_.qualifier).flatMap(_.failFast).map(FailFastFactory.FailFast.apply))
      .maybeWith(general.session.flatMap(_.qualifier).flatMap(_.failureAccrual).map(mkFailureAccrual))
      .maybeWith(general.retry.map(mkRetry))
      .maybeWith(general.loadbalancer.flatMap(_.enableProbation).map(LoadBalancerFactory.EnableProbation.apply))
      .maybeWith(general.loadbalancer.map(mkLoadBalancer))
  }
  // format: ON
}

object RichStackOps {
  implicit class RichStackParam(private val underlying: Stack.Params)
      extends AnyVal {
    def maybeWith[P: Stack.Param](p: Option[P]): Stack.Params = {
      p match {
        case Some(pp) => underlying + pp
        case None => underlying
      }
    }

    def having[P: Stack.Param](p: P): Stack.Params = underlying + p
  }
  implicit class RichStack[T](private val underlying: Stack[T])
      extends AnyVal {
    def mayRemove(bool: Boolean)(role: Stack.Role): Stack[T] = {
      if (bool) underlying.remove(role)
      else underlying
    }
  }
}
