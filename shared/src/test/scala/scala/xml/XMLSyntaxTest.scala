package scala.xml

import scala.collection.Seq
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals

class XMLSyntaxTest {

  private def handle[A](x: Node): A = {
    x.child(0).asInstanceOf[Atom[A]].data
  }

  @Test
  def test1(): Unit = {
    val xNull: Elem = <hello>{null}</hello> // these used to be Atom(unit), changed to empty children
    assertTrue(xNull.child sameElements Nil)

    val x0: Elem = <hello>{}</hello> // these used to be Atom(unit), changed to empty children
    val x00: Elem = <hello>{ }</hello> //  dto.
    val xa: Elem = <hello>{ "world" }</hello>

    assertTrue(x0.child sameElements Nil)
    assertTrue(x00.child sameElements Nil)
    assertEquals("world", handle[String](xa))

    val xb: Elem = <hello>{ 1.5 }</hello>
    assertEquals(1.5, handle[Double](xb), 0.0)

    val xc: Elem = <hello>{ 5 }</hello>
    assertEquals(5, handle[Int](xc).toLong)

    val xd: Elem = <hello>{ true }</hello>
    assertEquals(true, handle[Boolean](xd))

    val xe: Elem = <hello>{ 5:Short }</hello>
    assertEquals((5:Short).toLong, handle[Short](xe).toLong)

    val xf: Elem = <hello>{ val x = 27; x }</hello>
    assertEquals(27, handle[Int](xf).toLong)

    val xg: Elem = <hello>{ List(1,2,3,4) }</hello>
    assertEquals("<hello>1 2 3 4</hello>", xg.toString)
    assertFalse(xg.child.map(_.isInstanceOf[Text]).exists(identity))

    val xh: Elem = <hello>{ for(x <- List(1,2,3,4) if x % 2 == 0) yield x }</hello>
    assertEquals("<hello>2 4</hello>", xh.toString)
    assertFalse(xh.child.map(_.isInstanceOf[Text]).exists(identity))
  }

  /** see SVN r13821 (emir): support for <elem key={x:Option[Seq[Node]]} />,
   *  so that Options can be used for optional attributes.
   */
  @Test
  def test2(): Unit = {
    val x1: Option[Seq[Node]] = Some(<b>hello</b>)
    val n1: Elem = <elem key={x1} />
    assertEquals(x1, n1.attribute("key"))

    val x2: Option[Seq[Node]] = None
    val n2: Elem = <elem key={x2} />
    assertEquals(x2, n2.attribute("key"))
  }
}
