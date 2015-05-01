package org.broadinstitute.dsde.rawls

import java.text.SimpleDateFormat

/**
 * Created by abaumann on 4/22/15.
 */
object Timer {
  def millisToTime(time:Long):String = {
    new SimpleDateFormat("mm:ss.SSS").format(time)
  }

  // adapted from http://stackoverflow.com/questions/9160001/how-to-profile-methods-in-scala
  implicit def timeIt[R](timerName:String)(block: => R): R = {
    val start = System.currentTimeMillis()
    val result = block    // call-by-name
    val end = System.currentTimeMillis()
    println("("+timerName+")" + " elapsed: " + millisToTime(end-start))
    result
  }
}