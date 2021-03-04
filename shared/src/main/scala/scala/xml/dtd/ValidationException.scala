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
package dtd

case class ValidationException(e: String) extends Exception(e)

/**
 *  @author Burak Emir
 */
object MakeValidationException {
  def fromFixedAttribute(k: String, value: String, actual: String) =
    ValidationException("value of attribute " + k + " FIXED to \"" +
      value + "\", but document tries \"" + actual + "\"")

  def fromNonEmptyElement() =
    new ValidationException("element should be *empty*")

  def fromUndefinedElement(label: String) =
    new ValidationException("element \"" + label + "\" not allowed here")

  def fromUndefinedAttribute(key: String) =
    new ValidationException("attribute " + key + " not allowed here")

  def fromMissingAttribute(allKeys: Set[String]) = {
    val sb = new StringBuilder("missing value for REQUIRED attribute")
    if (allKeys.size > 1) sb.append('s')
    allKeys foreach (k => sb append "'%s'".format(k))
    new ValidationException(sb.toString())
  }

  def fromMissingAttribute(key: String, tpe: String) =
    new ValidationException("missing value for REQUIRED attribute %s of type %s".format(key, tpe))
}
