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
package parsing

import scala.collection.Seq
import Utility.SU

/**
 * This is not a public trait - it contains common code shared
 *  between the library level XML parser and the compiler's.
 *  All members should be accessed through those.
 */
private[scala] trait MarkupParserCommon extends TokenTests {
  protected def unreachable: Nothing = truncatedError("Cannot be reached.")

  // type HandleType       // MarkupHandler, SymbolicXMLBuilder
  type InputType // Source, CharArrayReader
  type PositionType // Int, Position
  type ElementType // NodeSeq, Tree
  type NamespaceType // NamespaceBinding, Any
  type AttributesType // (MetaData, NamespaceBinding), mutable.Map[String, Tree]

  def mkAttributes(name: String, pscope: NamespaceType): AttributesType
  def mkProcInstr(position: PositionType, name: String, text: String): ElementType

  /**
   * parse a start or empty tag.
   *  [40] STag         ::= '<' Name { S Attribute } [S]
   *  [44] EmptyElemTag ::= '<' Name { S Attribute } [S]
   */
  protected def xTag(pscope: NamespaceType): (String, AttributesType) = {
    val name: String = xName
    xSpaceOpt()

    (name, mkAttributes(name, pscope))
  }

  /**
   * '<?' ProcInstr ::= Name [S ({Char} - ({Char}'>?' {Char})]'?>'
   *
   * see [15]
   */
  def xProcInstr: ElementType = {
    val n: String = xName
    xSpaceOpt()
    xTakeUntil(mkProcInstr(_, n, _), () => tmppos, "?>")
  }

  /**
   * attribute value, terminated by either `'` or `"`. value may not contain `<`.
   * @param endCh either `'` or `"`
   */
  def xAttributeValue(endCh: Char): String = {
    val buf: StringBuilder = new StringBuilder
    while (ch != endCh && !eof) {
      // well-formedness constraint
      if (ch == '<') reportSyntaxError("'<' not allowed in attrib value")
      else if (ch == SU) truncatedError("")
      else buf append ch_returning_nextch
    }
    ch_returning_nextch
    // @todo: normalize attribute value
    buf.toString
  }

  def xAttributeValue(): String = {
    val str: String = xAttributeValue(ch_returning_nextch)
    // well-formedness constraint
    normalizeAttributeValue(str)
  }

  private def takeUntilChar(it: Iterator[Char], end: Char): String = {
    val buf: StringBuilder = new StringBuilder
    while (it.hasNext) it.next() match {
      case `end` => return buf.toString
      case ch    => buf append ch
    }
    scala.sys.error("Expected '%s'".format(end))
  }

  /**
   * [42]  '<' xmlEndTag ::=  '<' '/' Name S? '>'
   */
  def xEndTag(startName: String): Unit = {
    xToken('/')
    if (xName != startName)
      errorNoEnd(startName)

    xSpaceOpt()
    xToken('>')
  }

  /**
   * actually, Name ::= (Letter | '_' | ':') (NameChar)*  but starting with ':' cannot happen
   *  Name ::= (Letter | '_') (NameChar)*
   *
   *  see  [5] of XML 1.0 specification
   *
   *  pre-condition:  ch != ':' // assured by definition of XMLSTART token
   *  post-condition: name does neither start, nor end in ':'
   */
  def xName: String = {
    if (ch == SU)
      truncatedError("")
    else if (!isNameStart(ch))
      return errorAndResult("name expected, but char '%s' cannot start a name" format ch, "")

    val buf: StringBuilder = new StringBuilder

    while ({ buf append ch_returning_nextch
    ; isNameChar(ch)}) ()

    if (buf.last == ':') {
      reportSyntaxError("name cannot end in ':'")
      buf.toString dropRight 1
    } else buf.toString
  }

  private def attr_unescape(s: String): String = s match {
    case "lt"    => "<"
    case "gt"    => ">"
    case "amp"   => "&"
    case "apos"  => "'"
    case "quot"  => "\""
    case "quote" => "\""
    case _       => "&" + s + ";"
  }

  /**
   * Replaces only character references right now.
   *  see spec 3.3.3
   */
  private def normalizeAttributeValue(attval: String): String = {
    val buf: StringBuilder = new StringBuilder
    val it: BufferedIterator[Char] = attval.iterator.buffered

    while (it.hasNext) buf append (it.next() match {
      case ' ' | '\t' | '\n' | '\r' => " "
      case '&' if it.head == '#'    =>
        it.next(); xCharRef(it)
      case '&'                      => attr_unescape(takeUntilChar(it, ';'))
      case c                        => c
    })

    buf.toString
  }

  /**
   * CharRef ::= "&#" '0'..'9' {'0'..'9'} ";"
   *            | "&#x" '0'..'9'|'A'..'F'|'a'..'f' { hexdigit } ";"
   *
   * see [66]
   */
  def xCharRef(ch: () => Char, nextch: () => Unit): String =
    Utility.parseCharRef(ch, nextch, reportSyntaxError, truncatedError)

  def xCharRef(it: Iterator[Char]): String = {
    var c: Char = it.next()
    Utility.parseCharRef(() => c, () => { c = it.next() }, reportSyntaxError, truncatedError)
  }

  def xCharRef: String = xCharRef(() => ch, () => nextch())

  /** Create a lookahead reader which does not influence the input */
  def lookahead(): BufferedIterator[Char]

  /**
   * The library and compiler parsers had the interesting distinction of
   *  different behavior for nextch (a function for which there are a total
   *  of two plausible behaviors, so we know the design space was fully
   *  explored.) One of them returned the value of nextch before the increment
   *  and one of them the new value.  So to unify code we have to at least
   *  temporarily abstract over the nextchs.
   */
  def ch: Char
  def nextch(): Unit
  protected def ch_returning_nextch: Char
  def eof: Boolean

  // def handle: HandleType
  var tmppos: PositionType

  def xHandleError(that: Char, msg: String): Unit
  def reportSyntaxError(str: String): Unit
  def reportSyntaxError(pos: Int, str: String): Unit

  def truncatedError(msg: String): Nothing
  def errorNoEnd(tag: String): Nothing

  protected def errorAndResult[T](msg: String, x: T): T = {
    reportSyntaxError(msg)
    x
  }

  def xToken(that: Char): Unit = {
    if (ch == that) nextch()
    else xHandleError(that, "'%s' expected instead of '%s'".format(that, ch))
  }
  def xToken(that: Seq[Char]): Unit = { that foreach xToken }

  /** scan [S] '=' [S]*/
  def xEQ(): Unit = { xSpaceOpt(); xToken('='); xSpaceOpt() }

  /** skip optional space S? */
  def xSpaceOpt(): Unit = while (isSpace(ch) && !eof) nextch()

  /** scan [3] S ::= (#x20 | #x9 | #xD | #xA)+ */
  def xSpace(): Unit =
    if (isSpace(ch)) { nextch(); xSpaceOpt() }
    else xHandleError(ch, "whitespace expected")

  /** Apply a function and return the passed value */
  def returning[T](x: T)(f: T => Unit): T = { f(x); x }

  /** Execute body with a variable saved and restored after execution */
  def saving[A, B](getter: A, setter: A => Unit)(body: => B): B = {
    val saved: A = getter
    try body
    finally setter(saved)
  }

  /**
   * Take characters from input stream until given String "until"
   *  is seen.  Once seen, the accumulated characters are passed
   *  along with the current Position to the supplied handler function.
   */
  protected def xTakeUntil[T](
    handler: (PositionType, String) => T,
    positioner: () => PositionType,
    until: String): T =
    {
      val sb: StringBuilder = new StringBuilder
      val head: Char = until.head
      val rest: String = until.tail

      while (!eof) {
        if (ch == head && peek(rest))
          return handler(positioner(), sb.toString)
        else if (ch == SU || eof)
          truncatedError(s"died parsing until $until") // throws TruncatedXMLControl in compiler

        sb append ch
        nextch()
      }
      unreachable
    }

  /**
   * Create a non-destructive lookahead reader and see if the head
   *  of the input would match the given String.  If yes, return true
   *  and drop the entire String from input; if no, return false
   *  and leave input unchanged.
   */
  private def peek(lookingFor: String): Boolean =
    (lookahead() take lookingFor.length sameElements lookingFor.iterator) && {
      // drop the chars from the real reader (all lookahead + orig)
      (0 to lookingFor.length) foreach (_ => nextch())
      true
    }
}
