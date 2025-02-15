/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package scala
package xml
package include

/**
 * `XIncludeException` is the generic superclass for all checked exceptions
 * that may be thrown as a result of a violation of XInclude's rules.
 *
 * Constructs an `XIncludeException` with the specified detail message.
 * The error message string `message` can later be retrieved by the
 * `{@link java.lang.Throwable#getMessage}`
 * method of class `java.lang.Throwable`.
 *
 * @param   message   the detail message.
 */
class XIncludeException(message: String) extends Exception(message) {

  /**
   * uses `'''null'''` as its error detail message.
   */
  def this() = this(null)

  private var rootCause: Throwable = _

  /**
   * When an `IOException`, `MalformedURLException` or other generic
   * exception is thrown while processing an XML document for XIncludes,
   * it is customarily replaced by some form of `XIncludeException`.
   * This method allows you to store the original exception.
   *
   * @param   nestedException   the underlying exception which
   * caused the XIncludeException to be thrown
   */
  def setRootCause(nestedException: Throwable): Unit = {
    this.rootCause = nestedException
  }

  /**
   * When an `IOException`, `MalformedURLException` or other generic
   * exception is thrown while processing an XML document for XIncludes,
   * it is customarily replaced by some form of `XIncludeException`.
   * This method allows you to retrieve the original exception.
   * It returns null if no such exception caused this `XIncludeException`.
   *
   * @return Throwable   the underlying exception which caused the
   *                     `XIncludeException` to be thrown
   */
  def getRootCause(): Throwable = this.rootCause
}
