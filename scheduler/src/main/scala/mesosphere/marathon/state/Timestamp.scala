package mesosphere.marathon
package state

import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.math.Ordered

/**
  * An ordered wrapper for UTC timestamps.
  */
abstract case class Timestamp private (private val instant: Instant) extends Ordered[Timestamp] {
  def toOffsetDateTime: OffsetDateTime =
    OffsetDateTime.ofInstant(
      instant,
      ZoneOffset.UTC)

  def compare(that: Timestamp): Int = this.instant compareTo that.instant

  def before(that: Timestamp): Boolean = (this.instant compareTo that.instant) < 0
  def after(that: Timestamp): Boolean = (this.instant compareTo that.instant) > 0
  def youngerThan(that: Timestamp): Boolean = this.after(that)
  def olderThan(that: Timestamp): Boolean = this.before(that)

  override def toString: String = Timestamp.formatter.format(instant)

  def toInstant: Instant = instant

  def millis: Long = toInstant.toEpochMilli
  def seconds: Long = TimeUnit.MILLISECONDS.toSeconds(millis)
  def micros: Long = TimeUnit.MILLISECONDS.toMicros(millis)
  def nanos: Long = TimeUnit.MILLISECONDS.toNanos(millis)

  def until(other: Timestamp): FiniteDuration = {
    val duration = Duration.between(this.instant, other.instant)
    FiniteDuration(duration.toMillis, TimeUnit.MILLISECONDS)
  }

  /**
    * @return true if this timestamp is more than "by" duration older than other timestamp.
    */
  def expired(other: Timestamp, by: FiniteDuration): Boolean = this.until(other) > by

  def +(duration: FiniteDuration): Timestamp = Timestamp(instant.plusMillis(duration.toMillis))
  def -(duration: FiniteDuration): Timestamp = Timestamp(instant.minusMillis(duration.toMillis))
}
