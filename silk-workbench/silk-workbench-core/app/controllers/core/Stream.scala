package controllers.core

import models._
import org.silkframework.runtime.activity.Status.Finished
import org.silkframework.runtime.activity.{ActivityControl, Observable, Status}
import play.api.libs.iteratee.{Concurrent, Enumerator}

import scala.collection.mutable

object Stream {

  private val listeners = new mutable.WeakHashMap[Enumerator[_], Listener[_]]()

  def activityValue[T](activity: ActivityControl[T]): Enumerator[T] = {
    val (enumerator, channel) = Concurrent.broadcast[T]
    val listener = new Listener[T] {
      override def onUpdate(value: T) {
        channel.push(value)
      }
    }
    activity.value.onUpdate(listener)
    listeners.put(enumerator, listener)
    enumerator
  }

  def status(statusObservable: Observable[Status], filter: Status => Boolean = _ => true): Enumerator[Status] = {
    val (enumerator, channel) = Concurrent.broadcast[Status]
    val listener = new Listener[Status] {
      override def onUpdate(value: Status) {
        if(filter(value)) {
          channel.push(value)
        }
      }
    }
    // Push initial value
    listener(statusObservable())
    // Push updates
    statusObservable.onUpdate(listener)
    listeners.put(enumerator, listener)
    enumerator
  }
}
