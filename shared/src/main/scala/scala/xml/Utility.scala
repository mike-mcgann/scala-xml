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

import scala.collection.mutable
import scala.language.implicitConversions
import scala.collection.Seq

/**
 * The `Utility` object provides utility functions for processing instances
 * of bound and not bound XML classes, as well as escaping text nodes.
 *
 * @author Burak Emir
 */
object Utility extends AnyRef with parsing.TokenTests {
  final val SU: Char = '\u001A'

  // [Martin] This looks dubious. We don't convert StringBuilders to
  // Strings anywhere else, why do it here?
  implicit def implicitSbToString(sb: StringBuilder): String = sb.toString()

  // helper for the extremely oft-repeated sequence of creating a
  // StringBuilder, passing it around, and then grabbing its String.
  private[xml] def sbToString(f: StringBuilder => Unit): String = {
    val sb: StringBuilder = new StringBuilder
    f(sb)
    sb.toString
  }
  private[xml] def isAtomAndNotText(x: Node): Boolean = x.isAtom && !x.isInstanceOf[Text]

  /**
   * Trims an element - call this method, when you know that it is an
   *  element (and not a text node) so you know that it will not be trimmed
   *  away. With this assumption, the function can return a `Node`, rather
   *  than a `Seq[Node]`. If you don't know, call `trimProper` and account
   *  for the fact that you may get back an empty sequence of nodes.
   *
   *  Precondition: node is not a text node (it might be trimmed)
   */
  def trim(x: Node): Node = x match {
    case Elem(pre, lab, md, scp, child@_*) =>
      val children: Seq[Node] = combineAdjacentTextNodes(child) flatMap trimProper
      Elem(pre, lab, md, scp, children.isEmpty, children: _*)
  }

  private def combineAdjacentTextNodes(children: Seq[Node]): Seq[Node] = {
    children.foldRight(Seq.empty[Node]) {
      case (Text(left), Text(right) +: nodes) => Text(left + right) +: nodes
      case (n, nodes) => n +: nodes
    }
  }

  /**
   * trim a child of an element. `Attribute` values and `Atom` nodes that
   *  are not `Text` nodes are unaffected.
   */
  def trimProper(x: Node): Seq[Node] = x match {
    case Elem(pre, lab, md, scp, child@_*) =>
      val children: Seq[Node] = combineAdjacentTextNodes(child) flatMap trimProper
      Elem(pre, lab, md, scp, children.isEmpty, children: _*)
    case Text(s) =>
      new TextBuffer().append(s).toText
    case _ =>
      x
  }

  /** returns a sorted attribute list */
  def sort(md: MetaData): MetaData = if ((md eq Null) || (md.next eq Null)) md else {
    val key: String = md.key
    val smaller: MetaData = sort(md.filter { m => m.key < key })
    val greater: MetaData = sort(md.filter { m => m.key > key })
    smaller.foldRight (md copy greater) ((x, xs) => x copy xs)
  }

  /**
   * Return the node with its attribute list sorted alphabetically
   *  (prefixes are ignored)
   */
  def sort(n: Node): Node = n match {
    case Elem(pre, lab, md, scp, child@_*) =>
      val children: Seq[Node] = child map sort
      Elem(pre, lab, sort(md), scp, children.isEmpty, children: _*)
    case _ => n
  }

  /**
   * Escapes the characters &lt; &gt; &amp; and &quot; from string.
   */
  final def escape(text: String): String = sbToString(escape(text, _))

  object Escapes {
    /**
     * For reasons unclear escape and unescape are a long ways from
     * being logical inverses.
     */
    val pairs: Map[String, Char] = Map(
      "lt" -> '<',
      "gt" -> '>',
      "amp" -> '&',
      "quot" -> '"',
      "apos"  -> '\''
    )
    val escMap: Map[Char, String] = (pairs - "apos") map { case (s, c) => c -> ("&%s;" format s) }
    val unescMap: Map[String, Char] = pairs
  }
  import Escapes.{ escMap, unescMap }

  /**
   * Appends escaped string to `s`.
   */
  final def escape(text: String, s: StringBuilder): StringBuilder = {
    // Implemented per XML spec:
    // http://www.w3.org/International/questions/qa-controls
    text.iterator.foldLeft(s) { (s, c) =>
      escMap.get(c) match {
        case Some(str)                             => s ++= str
        case _ if c >= ' ' || "\n\r\t".contains(c) => s += c
        case _ => s // noop
      }
    }
  }

  /**
   * Appends unescaped string to `s`, `amp` becomes `&amp;`,
   * `lt` becomes `&lt;` etc..
   *
   * @return    `'''null'''` if `ref` was not a predefined entity.
   */
  final def unescape(ref: String, s: StringBuilder): StringBuilder =
    ((unescMap get ref) map (s append _)).orNull

  /**
   * Returns a set of all namespaces used in a sequence of nodes
   * and all their descendants, including the empty namespaces.
   */
  def collectNamespaces(nodes: Seq[Node]): mutable.Set[String] =
    nodes.foldLeft(new mutable.HashSet[String]) { (set, x) => collectNamespaces(x, set); set }

  /**
   * Adds all namespaces in node to set.
   */
  def collectNamespaces(n: Node, set: mutable.Set[String]): Unit = {
    if (n.doCollectNamespaces) {
      set += n.namespace
      for (a <- n.attributes) a match {
        case _: PrefixedAttribute =>
          set += a.getNamespace(n)
        case _ =>
      }
      for (i <- n.child)
        collectNamespaces(i, set)
    }
  }

  // def toXML(
  //   x: Node,
  //   pscope: NamespaceBinding = TopScope,
  //   sb: StringBuilder = new StringBuilder,
  //   stripComments: Boolean = false,
  //   decodeEntities: Boolean = true,
  //   preserveWhitespace: Boolean = false,
  //   minimizeTags: Boolean = false): String =
  // {
  //   toXMLsb(x, pscope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags)
  //   sb.toString()
  // }

  /**
   * Serialize the provided Node to the provided StringBuilder.
   * <p/>
   * Note that calling this source-compatible method will result in the same old, arguably almost universally unwanted,
   * behaviour.
   */
  @deprecated("Please use `serialize` instead and specify a `minimizeTags` parameter", "2.10.0")
  def toXML(
    x: Node,
    pscope: NamespaceBinding = TopScope,
    sb: StringBuilder = new StringBuilder,
    stripComments: Boolean = false,
    decodeEntities: Boolean = true,
    preserveWhitespace: Boolean = false,
    minimizeTags: Boolean = false): StringBuilder =
    {
      serialize(x, pscope, sb, stripComments, decodeEntities, preserveWhitespace, if (minimizeTags) MinimizeMode.Always else MinimizeMode.Never)
    }

  /**
   * Serialize an XML Node to a StringBuilder.
   *
   * This is essentially a minor rework of `toXML` that can't have the same name due to an unfortunate
   * combination of named/default arguments and overloading.
   *
   * @todo use a Writer instead
   */
  def serialize(
    x: Node,
    pscope: NamespaceBinding = TopScope,
    sb: StringBuilder = new StringBuilder,
    stripComments: Boolean = false,
    decodeEntities: Boolean = true,
    preserveWhitespace: Boolean = false,
    minimizeTags: MinimizeMode.Value = MinimizeMode.Default): StringBuilder =
    {
      x match {
        case c: Comment                   => if (!stripComments) c buildString sb; sb
        case s: SpecialNode               => s buildString sb
        case g: Group                     =>
          for (c <- g.nodes) serialize(c, g.scope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags); sb
        case el: Elem =>
          // print tag with namespace declarations
          sb.append('<')
          el.nameToString(sb)
          if (el.attributes ne null) el.attributes.buildString(sb)
          el.scope.buildString(sb, pscope)
          if (el.child.isEmpty &&
            (minimizeTags == MinimizeMode.Always ||
              (minimizeTags == MinimizeMode.Default && el.minimizeEmpty))) {
            // no children, so use short form: <xyz .../>
            sb.append("/>")
          } else {
            // children, so use long form: <xyz ...>...</xyz>
            sb.append('>')
            sequenceToXML(el.child, el.scope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags)
            sb.append("</")
            el.nameToString(sb)
            sb.append('>')
          }
        case _ => throw new IllegalArgumentException("Don't know how to serialize a " + x.getClass.getName)
      }
    }

  def sequenceToXML(
    children: Seq[Node],
    pscope: NamespaceBinding = TopScope,
    sb: StringBuilder = new StringBuilder,
    stripComments: Boolean = false,
    decodeEntities: Boolean = true,
    preserveWhitespace: Boolean = false,
    minimizeTags: MinimizeMode.Value = MinimizeMode.Default
  ): Unit =
    {
      if (children.isEmpty) ()
      else if (children forall isAtomAndNotText) { // add space
        val it: Iterator[Node] = children.iterator
        val f: Node = it.next()
        serialize(f, pscope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags)
        while (it.hasNext) {
          val x: Node = it.next()
          sb.append(' ')
          serialize(x, pscope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags)
        }
      } else children foreach { serialize(_, pscope, sb, stripComments, decodeEntities, preserveWhitespace, minimizeTags) }
    }

  /**
   * Returns prefix of qualified name if any.
   */
  final def prefix(name: String): Option[String] = name.indexOf(':') match {
    case -1 => None
    case i  => Some(name.substring(0, i))
  }

  /**
   * Returns a hashcode for the given constituents of a node
   */
  def hashCode(pre: String, label: String, attribHashCode: Int, scpeHash: Int, children: Seq[Node]): Int =
    scala.util.hashing.MurmurHash3.orderedHash(label +: attribHashCode +: scpeHash +: children, pre.##)

  def appendQuoted(s: String): String = sbToString(appendQuoted(s, _))

  /**
   * Appends &quot;s&quot; if string `s` does not contain &quot;,
   * &apos;s&apos; otherwise.
   */
  def appendQuoted(s: String, sb: StringBuilder): StringBuilder = {
    val ch: Char = if (s contains '"') '\'' else '"'
    sb.append(ch).append(s).append(ch)
  }

  /**
   * Appends &quot;s&quot; and escapes and &quot; i s with \&quot;
   */
  def appendEscapedQuoted(s: String, sb: StringBuilder): StringBuilder = {
    sb.append('"')
    for (c <- s) c match {
      case '"' =>
        sb.append('\\'); sb.append('"')
      case _   => sb.append(c)
    }
    sb.append('"')
  }

  def getName(s: String, index: Int): String = {
    if (index >= s.length) null
    else {
      val xs: String = s drop index
      if (xs.nonEmpty && isNameStart(xs.head)) xs takeWhile isNameChar
      else ""
    }
  }

  /**
   * Returns `'''null'''` if the value is a correct attribute value,
   * error message if it isn't.
   */
  def checkAttributeValue(value: String): String = {
    var i: Int = 0
    while (i < value.length) {
      value.charAt(i) match {
        case '<' =>
          return "< not allowed in attribute value"
        case '&' =>
          val n: String = getName(value, i + 1)
          if (n eq null)
            return "malformed entity reference in attribute value [" + value + "]"
          i = i + n.length + 1
          if (i >= value.length || value.charAt(i) != ';')
            return "malformed entity reference in attribute value [" + value + "]"
        case _ =>
      }
      i = i + 1
    }
    null
  }

  def parseAttributeValue(value: String): Seq[Node] = {
    val sb: StringBuilder = new StringBuilder
    var rfb: StringBuilder = null
    val nb: NodeBuffer = new NodeBuffer()

    val it: Iterator[Char] = value.iterator
    while (it.hasNext) {
      var c: Char = it.next()
      // entity! flush buffer into text node
      if (c == '&') {
        c = it.next()
        if (c == '#') {
          c = it.next()
          val theChar: String = parseCharRef ({ () => c }, { () => c = it.next() }, { s => throw new RuntimeException(s) }, { s => throw new RuntimeException(s) })
          sb.append(theChar)
        } else {
          if (rfb eq null) rfb = new StringBuilder()
          rfb append c
          c = it.next()
          while (c != ';') {
            rfb.append(c)
            c = it.next()
          }
          val ref: String = rfb.toString()
          rfb.clear()
          unescape(ref, sb) match {
            case null =>
              if (sb.nonEmpty) { // flush buffer
                nb += Text(sb.toString())
                sb.clear()
              }
              nb += EntityRef(ref) // add entityref
            case _ =>
          }
        }
      } else sb append c
    }
    if (sb.nonEmpty) { // flush buffer
      val x: Text = Text(sb.toString())
      if (nb.isEmpty)
        return x
      else
        nb += x
    }
    nb
  }

  /**
   * {{{
   *   CharRef ::= "&amp;#" '0'..'9' {'0'..'9'} ";"
   *             | "&amp;#x" '0'..'9'|'A'..'F'|'a'..'f' { hexdigit } ";"
   * }}}
   * See [66]
   */
  def parseCharRef(ch: () => Char, nextch: () => Unit, reportSyntaxError: String => Unit, reportTruncatedError: String => Unit): String = {
    val hex: Boolean = (ch() == 'x') && { nextch(); true }
    val base: Int = if (hex) 16 else 10
    var i: Int = 0
    while (ch() != ';') {
      ch() match {
        case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
          i = i * base + ch().asDigit
        case 'a' | 'b' | 'c' | 'd' | 'e' | 'f'
          | 'A' | 'B' | 'C' | 'D' | 'E' | 'F' =>
          if (!hex)
            reportSyntaxError("hex char not allowed in decimal char ref\n" +
              "Did you mean to write &#x ?")
          else
            i = i * base + ch().asDigit
        case SU =>
          reportTruncatedError("")
        case _ =>
          reportSyntaxError("character '" + ch() + "' not allowed in char ref\n")
      }
      nextch()
    }
    new String(Array(i), 0, 1)
  }
}
