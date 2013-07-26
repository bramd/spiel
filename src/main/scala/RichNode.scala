package info.spielproject.spiel

import android.graphics.Rect
import android.os._
import Build.VERSION
import android.util.Log
import android.view.accessibility._
import AccessibilityNodeInfo._

object Action extends Enumeration {
  val AccessibilityFocus = Value(ACTION_ACCESSIBILITY_FOCUS)
  val ClearAccessibilityFocus = Value(ACTION_CLEAR_ACCESSIBILITY_FOCUS)
  val ClearFocus = Value(ACTION_CLEAR_FOCUS)
  val ClearSelection = Value(ACTION_CLEAR_SELECTION)
  val Click = Value(ACTION_CLICK)
  val Focus = Value(ACTION_FOCUS)
  val LongClick = Value(ACTION_LONG_CLICK)
  val NextAtMovementGranularity = Value(ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
  val NextHtmlElement = Value(ACTION_NEXT_HTML_ELEMENT)
  val PreviousAtMovementGranularity = Value(ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
  val PreviousHtmlElement = Value(ACTION_PREVIOUS_HTML_ELEMENT)
  val ScrollBackward = Value(ACTION_SCROLL_BACKWARD)
  val ScrollForward = Value(ACTION_SCROLL_FORWARD)
  val Select = Value(ACTION_SELECT)
  
}

object Focus extends Enumeration {
  val Accessibility = Value(FOCUS_ACCESSIBILITY)
  val Input = Value(FOCUS_INPUT)
}

case class RichNode(node:AccessibilityNodeInfo) {

  def text = Option(node.getText).map(_.toString)

  def nonEmptyText = text.filterNot(_.isEmpty)

  def nonEmptyText_? = nonEmptyText != None

  def contentDescription = Option(node.getContentDescription).map(_.toString)

  def nonEmptyContentDescription = contentDescription.filterNot(_.isEmpty)

  def parent = Option(node.getParent)

  def ancestors:List[AccessibilityNodeInfo] = parent.map { p =>
    p :: p.ancestors
  }.getOrElse(Nil)

  def root = {
    lazy val r = ancestors match {
      case Nil => node
      case v => v.reverse.head
    }
    if(VERSION.SDK_INT >= 16)
      SpielService.rootInActiveWindow.getOrElse(r)
    else
      r
  }

  def children =
    (for(i <- 0 to node.getChildCount-1) yield(node.getChild(i))).toList.filterNot(_ == null)

  def visibleChildren = children.filter(_.isVisibleToUser)

  def siblings = parent.map(_.children).getOrElse(Nil)

  def visibleSiblings = siblings.filter(_.isVisibleToUser)

  def nextVisibleSibling = {
    val vs = visibleSiblings
    vs.indexOf(node) match {
      case -1 => None
      case v if(v == vs.length-1) => None
      case v => Some(vs(v+1))
    }
  }

  def prevVisibleSibling = {
    val vs = visibleSiblings
    vs.indexOf(node) match {
      case v if(v <= 0) => None
      case v => Some(vs(v-1))
    }
  }

  def firstVisibleLeaf:AccessibilityNodeInfo = visibleChildren match {
    case Nil => node
    case hd :: tl => hd.firstVisibleLeaf
  }

  def lastVisibleLeaf:AccessibilityNodeInfo = visibleChildren match {
    case Nil => node
    case hd :: Nil => hd.lastVisibleLeaf
    case hd :: tl => tl.last.lastVisibleLeaf
  }

  def descendants:List[AccessibilityNodeInfo] =children++children.map { c =>
    c.descendants
  }.flatten

  def interactive_? =
    node.isCheckable || node.isClickable || node.isLongClickable || node.isFocusable

  def rect = {
    val r = new Rect()
    node.getBoundsInScreen(r)
    r
  }

  def row = {
    val origin = new Rect(0, rect.top, Int.MaxValue, rect.bottom)
    val descendants = if(VERSION.SDK_INT >= 16)
      root.descendants.filter(_.isVisibleToUser)
    else root.descendants
    descendants.filter(_.rect.intersect(origin)).sortBy(_.rect.left)
  }

  def classAncestors = {
    val nodeClass = utils.classForName(node.getClassName.toString, node.getPackageName.toString)
    nodeClass.map(utils.ancestors(_).map(_.getName)).getOrElse(Nil)
  }

  protected def isA_?(cls:String) =
    node.getClassName == cls || classAncestors.contains(cls)

  def label = {
    def isTextView(n:AccessibilityNodeInfo) =
      n.isA_?("android.widget.TextView") && !n.isA_?("android.widget.EditText") && !n.isA_?("android.widget.Button")
    val explicitLabel = if(VERSION.SDK_INT >= 17)
      Option(node.getLabeledBy)
    else None
    explicitLabel.orElse {
      if(
        List("android.widget.CheckBox", "android.widget.EditText", "android.widget.ProgressBar", "android.widget.RadioButton", "android.widget.RatingBar")
        .exists(isA_?(_))
      ) {
        row.find(v => isTextView(v) && v.nonEmptyText_?)
        } .orElse {
          val descendants = if(VERSION.SDK_INT >= 16)
            root.descendants.filter(_.isVisibleToUser)
          else root.descendants
          descendants.filter(_.rect.bottom <= rect.top)
          .sortBy(_.rect.bottom)
          .reverse.headOption.filter { c =>
            isTextView(c) && !c.interactive_? && c.nonEmptyText_?
          }
        }
      else
        None
    }
  }

  def supports_?(action:Action.Value) =
    (node.getActions&action.id) != 0

  def perform(action:Action.Value) =
    node.performAction(action.id)

  def perform(action:Action.Value, bundle:Bundle) =
    node.performAction(action.id, bundle)

  def find(focus:Focus.Value) = Option(node.findFocus(focus.id))

  def nextAccessibilityFocus:Option[AccessibilityNodeInfo] =
    nextVisibleSibling.map(_.firstVisibleLeaf)
    .orElse(parent.flatMap(_.nextAccessibilityFocus))
    .orElse(Some(root.firstVisibleLeaf))
    .orElse(None)

  def prevAccessibilityFocus:Option[AccessibilityNodeInfo] =
    prevVisibleSibling.map(_.lastVisibleLeaf)
    .orElse(parent.flatMap(_.prevAccessibilityFocus))
    .orElse(Some(root.lastVisibleLeaf))
    .orElse(None)

}
