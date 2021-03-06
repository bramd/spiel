package info.spielproject.spiel
package presenters

import collection.JavaConversions._

import android.app.{ActivityManager, Service}
import android.content.Context
import android.os.Build.VERSION
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
import AccessibilityEvent._
import AccessibilityNodeInfo._

import routing._

/**
 * Represents code that is called when a specific <code>AccessibilityEvent</code> is received.
*/

abstract class Callback {

  /**
   * Called with an <code>AccessibilityEvent</code>.
   * @return <code>true</code> if event processing should stop, false otherwise
  */

  def apply(e:AccessibilityEvent):Boolean

}

/**
 * Represents a callback that is written in native Scala code.
*/

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback {
  def apply(e:AccessibilityEvent) = f(e)
}

/**
 * Singleton that stores the 50 most recent <code>AccessibilityEvent</code> objects for review.
*/

object EventReviewQueue extends collection.mutable.Queue[AccessibilityEvent] {

  /**
   * Adds an event to the queue, stripping excess items if necessary.
  */

  def apply(e:AccessibilityEvent) = {
    enqueue(e)
    while(length > 50) dequeue()
  }

}

case class EventPayload(event:AccessibilityEvent, eventType:Int)

/**
 * Maps a given <code>Callback</code> to events originating from a given 
 * package and class.
 *
 * Passing a blank string for either indicates events from all packages or all classes.
*/

class Presenter(directive:Option[Directive] = None) extends Handler[EventPayload](Presenter, directive) {

  def this(pkg:String, cls:String) = this(Some(new Directive(pkg, cls)))
  def this(c:String) = this(Some(new Directive(c)))

  import Presenter._

  Presenter.register(this)

  // Convenience method for converting functions to callbacks.

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  /**
   * Maps strings to <code>Callback</code> classes for specific event types 
   * related to the specified package and class.
  */

  val dispatches = collection.mutable.Map[String, Callback]()

  // Register <code>Callback</code> instances for the various <code>AccessibilityEvent</code> types.

  protected def onAnnouncement(c:Callback) = dispatches(dispatchers(TYPE_ANNOUNCEMENT)) = c

  protected def onGestureDetectionEnd(c:Callback) = dispatches(dispatchers(TYPE_GESTURE_DETECTION_END)) = c

  protected def onGestureDetectionStart(c:Callback) = dispatches(dispatchers(TYPE_GESTURE_DETECTION_START)) = c

  protected def onNotificationStateChanged(c:Callback) = dispatches(dispatchers(TYPE_NOTIFICATION_STATE_CHANGED)) = c

  protected def onTouchExplorationGestureEnd(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_END)) = c

  protected def onTouchExplorationGestureStart(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_START)) = c

  protected def onTouchInteractionEnd(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_INTERACTION_END)) = c

  protected def onTouchInteractionStart(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_INTERACTION_START)) = c

  protected def onViewAccessibilityFocusCleared(c:Callback) = dispatches(dispatchers(TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)) = c

  protected def onViewAccessibilityFocused(c:Callback) = dispatches(dispatchers(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) = c

  protected def onViewClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_CLICKED)) = c

  protected def onViewFocused(c:Callback) = dispatches(dispatchers(TYPE_VIEW_FOCUSED)) = c

  protected def onViewHoverEnter(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_ENTER)) = c

  protected def onViewHoverExit(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_EXIT)) = c

  protected def onViewLongClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_LONG_CLICKED)) = c

  protected def onViewScrolled(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SCROLLED)) = c

  protected def onViewSelected(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SELECTED)) = c

  protected def onViewTextChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_CHANGED)) = c

  protected def onViewTextSelectionChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_SELECTION_CHANGED)) = c

  protected def onViewTextTraversedAtMovementGranularity(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY)) = c

  protected def onWindowContentChanged(c:Callback) = dispatches(dispatchers(TYPE_WINDOW_CONTENT_CHANGED)) = c

  protected def onWindowStateChanged(c:Callback) = dispatches(dispatchers(TYPE_WINDOW_STATE_CHANGED)) = c

  // Called if no other events match.

  protected def byDefault(c:Callback) = dispatches("default") = c

  protected def interactables(source:AccessibilityNodeInfo) = 
    (source :: source.descendants).filter(_.interactive_?)

  /**
   * Run a given <code>AccessibilityEvent</code> through this <code>Presenter</code>
   *
   * @return <code>true</code> if processing should stop, <code>false</code> otherwise.
  */

  def apply(payload:EventPayload):Boolean = {

    def dispatchTo(callback:String):Boolean = dispatches.get(callback).map(_(payload.event)).getOrElse(false)

    val fallback = dispatchers.get(payload.eventType).map(dispatchTo(_)).getOrElse(false)

    if(!fallback)
      dispatchTo("default")
    else fallback
  }

}

// Now, finally, we reach the presentation logic for generic Android widgets.

/**
 * Encapsulates generic handling for multiple types of buttons.
*/

trait GenericButtonPresenter extends Presenter {
  onViewFocused { e:AccessibilityEvent =>
    val text = e.utterances(addBlank=false).mkString(": ")
    if(text == "") {
      e.source.flatMap { source =>
        val descendants = source.root.descendants
        val index = descendants.indexOf(source)+1
        if(index > 0)
          Some(speak(getString(R.string.button) :: getString(R.string.listItem, index.toString, descendants.size.toString) :: Nil))
        else None
      }.getOrElse(speak(getString(R.string.button).toString))
      true
    } else
      speak(getString(R.string.labeledButton, text))
  }
}

object Before extends Presenter {

  onViewAccessibilityFocused { e:AccessibilityEvent => Presenter.process(e, Some(TYPE_VIEW_FOCUSED)) }

  private def setAccessibilityFocus(event:AccessibilityEvent) = {
    event.source.map { n =>  
      if(VERSION.SDK_INT >= 16) {
        if(n.isAccessibilityFocused)
          n.perform(Action.ClearAccessibilityFocus)
        n.children == Nil && n.perform(Action.AccessibilityFocus)
      } else false
    }.getOrElse(false)
  }

  onViewHoverEnter { e:AccessibilityEvent =>
    stopSpeaking()
    if(SystemClock.uptimeMillis-e.getEventTime <= 100)
      shortVibration()
    setAccessibilityFocus(e)
    false
  }

  onViewHoverExit { e:AccessibilityEvent =>
    if(SystemClock.uptimeMillis-e.getEventTime <= 100)
      shortVibration()
    false
  }

}

/**
 * Run after every event.
*/

object After extends Presenter {

  onViewClicked { e:AccessibilityEvent =>
    if(VERSION.SDK_INT >= 16)
      e.source.foreach { source =>
        source.find(Focus.Accessibility).getOrElse {
          source.perform(Action.AccessibilityFocus)
        }
      }
    false
  }

  private val absListViews = collection.mutable.Map[AccessibilityNodeInfo, Tuple3[Int, Int, Long]]()

  onViewFocused { e:AccessibilityEvent =>
    e.source.foreach { source =>
      if(e.utterances(addBlank = false, stripBlanks = true) != Nil && source.getChildCount == 0 && source.interactive_? && !e.isEnabled)
        speak(getString(R.string.disabled), false)
      val all = source :: source.ancestors
      var counter = -1
      all.map { n =>
        counter += 1
        (utils.classForName(n.getClassName.toString, n.getPackageName.toString).getOrElse(classOf[Any]), counter)
      }.find { cls =>
        utils.ancestors(cls._1).contains(classOf[android.widget.AbsListView])
      }.map { v =>
        val viewIndex = v._2
        val absListView = all(viewIndex)
        val childWidgetIndex = viewIndex-1
        if(childWidgetIndex != -1) {
          val positionOffset = absListView.children.indexOf(all(childWidgetIndex))+1
          val position = absListViews.get(absListView).map(_._1+positionOffset).getOrElse(positionOffset)
          val total = absListViews.get(absListView).map(_._2).getOrElse(absListView.children.length)
          speak(getString(R.string.listItem, position.toString, total.toString), false)
        }
      }
    }
    false
  }

  onViewScrolled { e:AccessibilityEvent =>
    e.source.foreach { source =>
      val eventClass = utils.classForName(e.getClassName.toString, e.getPackageName.toString)
      eventClass.foreach { cls =>
        if(e.getClassName == "android.widget.ListView" || utils.ancestors(cls).contains(classOf[android.widget.AbsListView])) {
          val min = e.getFromIndex
          val total = e.getItemCount
          if(min != -1 && total != -1) {
            absListViews += (source -> (min, total, System.currentTimeMillis))
          } else
            absListViews -= source
        }
      }
    }
    false
  }

  byDefault { e:AccessibilityEvent =>
    absListViews.filter(System.currentTimeMillis-_._2._3 <= 3600000)
    true
  }

}

/**
 * By placing all <code>Presenter</code> classes here, we can use the power of 
 * reflection to avoid manually registering each and every one.
*/

class Presenters {

  class ActionMenuItemView extends Presenter("com.android.internal.view.menu.ActionMenuItemView") {

    onViewFocused { e:AccessibilityEvent =>
      speak(e.utterances(stripBlanks=true) ::: (getString(R.string.menuItem) :: Nil))
    }

  }

  class AdapterView extends Presenter("android.widget.AdapterView") {

    private def focusedOnList(e:AccessibilityEvent) = {
      val utterances = e.utterances(addBlank = false, stripBlanks = true)
      if(utterances != Nil && e.getCurrentItemIndex != -1)
        speak(utterances.mkString(": ") :: getString(R.string.listItem, (e.getCurrentItemIndex+1).toString, e.getItemCount.toString) :: Nil)
      else
        if(e.getItemCount == 0)
          speak(getString(R.string.emptyList))
        else if(e.getItemCount == 1)
          speak(getString(R.string.listWithItem))
        else if(e.getItemCount > 1)
          speak(getString(R.string.listWithItems, e.getItemCount.toString))
      nextShouldNotInterrupt()
      true
    }

    onViewFocused { e:AccessibilityEvent => focusedOnList(e) }

    onViewScrolled { e:AccessibilityEvent =>
      if(e.getToIndex >= 0 && e.getItemCount > 0) {
        val percentage = e.getToIndex.toDouble/e.getItemCount*100
        TTS.presentPercentage(percentage)
      }
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      e.source.map { source =>
        if(!source.isFocused)
          true
        else
          false
      }.getOrElse(false) || {
        if(e.getCurrentItemIndex >= 0)
          speak(e.utterances.mkString(": ") :: getString(R.string.listItem, (e.getCurrentItemIndex+1).toString, e.getItemCount.toString) :: Nil)
        else if(e.getItemCount == 0)
          speak(getString(R.string.emptyList))
        true
      }
    }

    onWindowStateChanged { e:AccessibilityEvent => focusedOnList(e) }

  }

  class Button extends Presenter("android.widget.Button") with GenericButtonPresenter

  class Checkable extends Presenter("android.widget.Checkable") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(getString(R.string.checked), true)
      else
        speak(getString(R.string.notChecked), true)
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(getString(R.string.checkbox, e.utterances(addBlank=false, guessLabelIfTextShorterThan = Some(2)).mkString(": ")))
      if(VERSION.SDK_INT >= 16)
        speak(getString((if(e.isChecked) R.string.checked else R.string.notChecked)), false)
      true
    }

  }

  class Dialog extends Presenter("android.app.Dialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(e.utterances(stripBlanks=true).mkString(": "), true)
      nextShouldNotInterrupt()
    }
  }

  class EditText extends Presenter("android.widget.EditText") {

    onViewFocused { e:AccessibilityEvent =>
      speak(e.utterances(guessLabelIfContentDescriptionMissing = true), false)
      if(e.isPassword) {
        speak(getString(R.string.password))
        val length = if(e.getItemCount > 0)
          e.getItemCount
        else if(e.getText != null && e.getText.mkString.length > 0)
          e.getText.mkString.length
        else 0
        if(length > 0)
          speak("."*length, false)
      }
      speak(getString(R.string.editText), false)
    }

    private var lastTextChangedReceived = 0l

    onViewTextChanged { e:AccessibilityEvent =>
      lastTextChangedReceived = e.getEventTime
      if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          speak(".", true)
        else {
          val text = e.getText.mkString
          val before = e.getBeforeText.toString
          if(before == text)
            true
          else {
            val diff = if(before.length > text.length) before.diff(text) else text.diff(before)
            if(diff.length == 1 && before.length < text.length) {
              var flush = true
              if(Preferences.echoByChar) {
                speak(diff, true)
                flush = false
              }
              if(Preferences.echoByWord && !Character.isLetterOrDigit(diff(0))) {
                val word = (text.substring(0, e.getFromIndex)
                .reverse.takeWhile(_.isLetterOrDigit).reverse+diff).trim
                if(word.length > 1)
                  speak(word, flush)
                }
              true
            } else
              speak(diff, true)
          }
        }
      } else true
    }

    onViewTextSelectionChanged { e:AccessibilityEvent =>
      e.getEventTime-lastTextChangedReceived <= 10
    }

  }

  trait MenuView {
    self: Presenter =>
    onViewFocused { e:AccessibilityEvent => speak(getString(R.string.menu)) }
  }

  class ExpandedMenuView extends Presenter("com.android.internal.view.menu.ExpandedMenuView") with MenuView

  class HomeView extends Presenter("com.android.internal.widget.ActionBarView$HomeView") {

    private def process(e:AccessibilityEvent) = {
      val utterances = e.utterances(addBlank = false)
      if(utterances != Nil)
        speak(utterances.mkString(" "))
      true
    }

    onViewFocused { e:AccessibilityEvent => process(e) }

    onViewHoverEnter { e:AccessibilityEvent => process(e) }

  }

  class ImageButton extends Presenter("android.widget.ImageButton") with GenericButtonPresenter

  class ImageView extends Presenter("android.widget.ImageView") {
    onViewFocused { e:AccessibilityEvent =>
      val text = e.utterances(addBlank=false).mkString(": ")
      if(text == "")
        if(e.getItemCount > 0 && e.getCurrentItemIndex >= 0)
          speak(getString(R.string.image) :: getString(R.string.listItem, (e.getCurrentItemIndex+1).toString, e.getItemCount.toString) :: Nil)
        else e.source.map { source =>
          val descendants = source.root.descendants
          val index = descendants.indexOf(source)+1
          if(index > 0)
            speak(getString(R.string.image) :: getString(R.string.listItem, index.toString, descendants.length.toString) :: Nil)
          else
            speak(getString(R.string.image).toString)
          true
        }.getOrElse {
          speak(getString(R.string.image).toString)
        }
      else
        speak(getString(R.string.labeledImage, text))
    }
  }

  class IconMenuView extends Presenter("com.android.internal.view.menu.IconMenuView") with MenuView

  class Menu extends Presenter("com.android.internal.view.menu.MenuView") {

    onViewSelected { e:AccessibilityEvent =>
      speak(e.utterances)
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex == -1) {
        speak(getString(R.string.menu), true)
        nextShouldNotInterrupt()
      }
      true
    }

  }

  class ProgressBar extends Presenter("android.widget.ProgressBar") {

    onViewFocused { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toDouble/e.getItemCount*100).toInt
      speak(e.utterances(addBlank = false, guessLabelIfContentDescriptionMissing = true, providedText=Some(percent+"%")))
    }

    onViewSelected { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toFloat/e.getItemCount*100).toInt
      TTS.presentPercentage(percent)
    }

  }

  class RadioButton extends Presenter("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(getString(R.string.selected))
      else
        speak(getString(R.string.notSelected))
    }

    onViewFocused { e:AccessibilityEvent =>
      var text = e.utterances(guessLabelIfTextShorterThan = Some(2)).mkString(": ")
      if(!text.isEmpty) text += ": "
      speak(text+getString(R.string.radioButton))
      if(VERSION.SDK_INT >= 16)
        speak(getString((if(e.isChecked) R.string.selected else R.string.notSelected)), false)
      true
    }

  }

  class RatingBar extends Presenter("android.widget.RatingBar") {

    onViewFocused { e:AccessibilityEvent =>
      val label = e.source.flatMap(_.label).map(_.getText.toString).getOrElse(getString(R.string.rating))
      val rating = getString(R.string.listItem, e.getCurrentItemIndex.toString, e.getItemCount.toString)
      speak(e.utterances(addBlank = false, providedText=Some(label+": "+rating)))
    }

    onViewSelected { e:AccessibilityEvent =>
      e.source.map { source =>
        if(source.isFocused)
          speak(e.getCurrentItemIndex.toString)
        else true
      }.getOrElse(true)
    }

  }

  class ScrollView extends Presenter("android.widget.ScrollView") {
    onViewFocused { e:AccessibilityEvent => true }
  }

  class Switch extends Presenter("android.widget.Switch") with GenericButtonPresenter {
    onViewClicked { e:AccessibilityEvent => Presenter.process(e, Some(TYPE_VIEW_FOCUSED)) }
  }

  class TabWidget extends Presenter("android.widget.TabWidget") {

    private def present(e:AccessibilityEvent) = {
      e.text.headOption.map { t =>
        speak(getString(R.string.tabWidget, t))
        speak(getString(R.string.listItem, (e.getCurrentItemIndex+1).toString, e.getItemCount.toString), false)
        nextShouldNotInterrupt()
      }.getOrElse(false)
    }

    onViewFocused { e:AccessibilityEvent => present(e) }

    onViewSelected { e:AccessibilityEvent => present(e) }

  }

  class TextView extends Presenter("android.widget.TextView") {
    onViewFocused { e:AccessibilityEvent => speak(e.utterances(stripBlanks=true)) }
  }

  class ViewGroup extends Presenter("android.view.ViewGroup") {

    onViewFocused { e:AccessibilityEvent => 
      val utterances = e.utterances(stripBlanks = true, addBlank=false)
      if(utterances != Nil)
        speak(utterances)
      else
        e.source.map { source =>
          if(source.interactive_?)
            speak(utterances)
          else
            true
        }.getOrElse(speak(e.utterances(stripBlanks = true)))
    }

    onViewHoverEnter { e:AccessibilityEvent =>
      (for(
        source <- e.source;
        contentDescription <- source.contentDescription
      ) yield { speak(contentDescription)})
      .getOrElse(true)
    }

  }

  class WebView extends Presenter("android.webkit.WebView") {

    private def utterancesFor(x:xml.Node):List[String] = {

      def name(n:xml.Node):String =
        n.nameToString(new StringBuilder()).toString

      def recurse(nodes:List[xml.Node]):List[String] = nodes match {
        case Nil => Nil
        case hd :: tl if(name(hd) == "a" && hd.text != null) =>
          hd.text :: getString(R.string.link) :: Nil
        case hd :: tl if(hd.descendant.size == 0 && hd.text != null && hd.text != "") =>
          hd.text :: recurse(tl)
        case hd :: tl => recurse(tl)
      }

      try {
        recurse(x.descendant)
      } catch {
        case e:Throwable =>
          Log.e("spiel", "Error parsing HTML", e)
          Nil
      }
    }

    private def focus(e:AccessibilityEvent) = {
      val x = Option(e.getText.map(v => if(v == null) "<span/>" else v)
      .mkString).map { t =>
        if(t == "")
          <span/>
        else
          utils.htmlToXml(t)
      }.getOrElse(<span/>)
      speak(utterancesFor(x))
    }

    onViewSelected { e:AccessibilityEvent => focus(e) }

    onViewTextTraversedAtMovementGranularity { e:AccessibilityEvent => focus(e) }

  }

  /**
   * Default catch-all Presenter which catches unresolved <code>AccessibilityEvent</code>s.
  */

  class Default extends Presenter(Some(Directive(All, All))) {

    onAnnouncement { e:AccessibilityEvent =>
      speak(e.utterances(addBlank = false, stripBlanks = true), false)
      nextShouldNotInterrupt()
    }

    onNotificationStateChanged { e:AccessibilityEvent =>
      val utterances = e.utterances(addBlank=false, stripBlanks=true)
      if(!utterances.isEmpty) {
        nextShouldNotInterrupt()
        if(VERSION.SDK_INT >= 16)
          e.source.filter(_.isVisibleToUser).map { source =>
            speakNotification(utterances)
          }.getOrElse(speakNotification(utterances))
        else
          speakNotification(utterances)
      }
      true
    }

    onTouchExplorationGestureEnd { e:AccessibilityEvent => true }

    onTouchExplorationGestureStart { e:AccessibilityEvent => stopSpeaking() }

    onViewClicked { e:AccessibilityEvent => true }

    onViewAccessibilityFocused { e:AccessibilityEvent => true }

    onViewAccessibilityFocusCleared { e:AccessibilityEvent => true }

    onViewFocused { e:AccessibilityEvent =>
      val utterances = e.utterances(addBlank=false, stripBlanks=true) match {
        case Nil if(e.getEventType != TYPE_VIEW_HOVER_ENTER) => 
          val className = e.getClassName.toString.split("\\.").last
          if(className == "View")
            List("")
          else
            List(className)
        case u => u
      }
      if(!utterances.isEmpty)
        speak(utterances)
      true
    }

    onViewHoverExit { e:AccessibilityEvent => true }

    onViewLongClicked { e:AccessibilityEvent => true }

    onViewScrolled { e:AccessibilityEvent =>
      val utterances = e.utterances(addBlank=false, stripBlanks=true)
      if(e.getMaxScrollX != -1 || e.getMaxScrollY != -1) {
        var percent = 0d
        if(e.getMaxScrollX > 0 && e.getMaxScrollY == 0)
          percent = e.getScrollX.toFloat/e.getMaxScrollX
        else if(e.getMaxScrollX == 0 && e.getMaxScrollY > 0)
          percent = e.getScrollY.toFloat/e.getMaxScrollY
        percent = percent*100
        if(percent > 0)
          TTS.presentPercentage(percent)
      } else if(!utterances.isEmpty) {
        speak(utterances)
        nextShouldNotInterrupt()
      }
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      val utterances = e.utterances(addBlank=false)
      if(utterances.length > 0) {
        if(e.getCurrentItemIndex == -1)
          if(e.getItemCount == 1)
            speak(getString(R.string.item, utterances.mkString(" ")))
          else if(e.getItemCount >= 0)
            speak(getString(R.string.items, utterances.mkString(" "), e.getItemCount.toString))
          else
            speak(utterances)
        else
          speak(utterances)
      } else
        speak("")
    }

    onViewTextChanged { e:AccessibilityEvent => speak(e.utterances(addBlank = false, stripBlanks = true)) }

    private var oldSelectionFrom:Option[Int] = None
    private var oldSelectionTo:Option[Int] = None

    onViewTextSelectionChanged { e:AccessibilityEvent =>
      e.source.map(_.getText).foreach { text =>
        val txt = if(e.isPassword) Some("."*e.getItemCount) else Option(text)
        txt.map { t =>
          var from = e.getFromIndex
          var to = if(e.getToIndex == e.getFromIndex && e.getToIndex < t.length)
            e.getToIndex+1
          else {
            if(from > 0)
              from -= 1
            e.getToIndex
          }
          val width = to-from
          val source = e.getSource
          if(from >= 0 && to >= 0 && source != null && source.isFocused) {
            if(from > to) {
              val tmp = to
              to = from
              from = tmp
            }
            val selection = try {
              t.subSequence(from, to).toString
            } catch {
              case e:Throwable =>
                Log.d("spiel", "Error determining selection", e)
                ""
            }
            (for(
              osf <- oldSelectionFrom;
              ost <- oldSelectionTo;
              distance = List(
                math.abs(osf-from),
                math.abs(osf-to),
                math.abs(ost-from),
                math.abs(ost-to)
              ).min if(distance > 1)
            ) yield {
              val interval = try {
                (if(ost < from)
                  text.subSequence(ost, from)
                else
                  text.subSequence(to, math.min(osf, text.length-1))
                ).toString
              } catch {
                case _:Throwable => selection
              }
              if(interval.contains("\n")) {
                val ending = t.subSequence(from, t.length).toString
                val nextNewLine = if(ending.indexOf("\n") == -1) t.length else from+ending.indexOf("\n")
                val start = t.subSequence(0, from).toString.reverse
                val previousNewLine = if(start.indexOf("\n") == -1) from-start.length else from-start.indexOf("\n")
                speak(t.subSequence(previousNewLine, nextNewLine).toString, true)
              } else {
                speak(selection.toString, true)
                false
              }
            }).getOrElse {
              if(selection == "")
                true
              else
                speak(selection.toString, true)
            }
            oldSelectionFrom = Some(from)
            oldSelectionTo = Some(to)
          } else if(from == -1 || to == -1) {
            oldSelectionFrom = None
            oldSelectionTo = None
          }
        }
      }
      true
    }

    onViewTextTraversedAtMovementGranularity { e:AccessibilityEvent =>
      val text = e.getText.mkString
      if(e.getToIndex <= text.length)
        speak(text.substring(e.getFromIndex, e.getToIndex), true)
      true
    }

    onWindowContentChanged { e:AccessibilityEvent => true }

    private var lastWindow:AccessibilityNodeInfo = null

    onWindowStateChanged { e:AccessibilityEvent =>
      if(e.getSource != lastWindow) {
        speak(e.utterances(addBlank = false, stripBlanks = true), true)
        lastWindow = e.getSource
        nextShouldNotInterrupt()
      }
      true
    }

    byDefault { e:AccessibilityEvent =>
      //Log.d("spiel", "Unhandled event: "+e.toString)
      speak(e.utterances(addBlank = false, stripBlanks = true))
    }

  }
}

/**
 * Companion for <code>Presenter</code> class.
*/

object Presenter extends Router[EventPayload](Some(() => Before), Some(() => After)) {

  def apply() {
    utils.instantiateAllMembers(classOf[Presenters])
  }

  private[presenters] def process(e:AccessibilityEvent, eventType:Option[Int] = None):Boolean = {

    Log.d("spiel", "Event "+e.toString+"; Activity: "+currentActivity+"\nSource: "+e.getSource)

    if(e == null)
      return true

    if(
      SystemClock.uptimeMillis-e.getEventTime > 100 &&
      List(TYPE_TOUCH_EXPLORATION_GESTURE_END, TYPE_TOUCH_EXPLORATION_GESTURE_START, TYPE_VIEW_HOVER_ENTER, TYPE_VIEW_HOVER_EXIT).contains(e.getEventType)
    )
      return true

    if(eventType == None) {
      EventReviewQueue(AccessibilityEvent.obtain(e))
      if(e.records != Nil)
        Log.d("spiel", "Records: "+e.records.map(_.toString))
    }

    if(!Device.screenOn_? && !List(TYPE_ANNOUNCEMENT, TYPE_NOTIFICATION_STATE_CHANGED
    ).contains(e.getEventType))
      return true

    if(e.getEventType == TYPE_NOTIFICATION_STATE_CHANGED && Preferences.notificationFilters.contains(e.getPackageName))
      return true

    if(eventType == None)
      nextShouldNotInterruptCalled = false

    val eType = eventType.getOrElse(e.getEventType)

    val payload = EventPayload(e, eType)
    val directive = Directive(
      Option(e.getPackageName).map { v =>
        Value(v.toString)
      }.getOrElse(All),
      Option(e.getClassName).map { v =>
        Value(v.toString)
      }.getOrElse(All)
    )

    try {
      dispatch(payload, directive)
    } catch {
      case e:Throwable =>
        Log.e("spiel", "Error in AccessibilityEvent dispatch", e)
    }

    if(myNextShouldNotInterrupt && spoke && !nextShouldNotInterruptCalled && eventType == None) {
      Log.d("spiel", "Next event can interrupt speech")
      myNextShouldNotInterrupt = false
    }

    spoke = false
    true
  }

  events.AccessibilityEventReceived += { e:AccessibilityEvent => process(e) }

  /**
   * Map of <code>AccessibilityEvent</code> types to more human-friendly strings.
  */

  val dispatchers = Map(
    TYPE_ANNOUNCEMENT -> "announcement",
    TYPE_GESTURE_DETECTION_END -> "gestureDetectionEnd",
    TYPE_GESTURE_DETECTION_START -> "gestureDetectionStart",
    TYPE_NOTIFICATION_STATE_CHANGED -> "notificationStateChanged",
    TYPE_TOUCH_EXPLORATION_GESTURE_END -> "touchExplorationGestureEnd",
    TYPE_TOUCH_EXPLORATION_GESTURE_START -> "touchExplorationGestureStart",
    TYPE_TOUCH_INTERACTION_END -> "touchInteractionEnd",
    TYPE_TOUCH_INTERACTION_START -> "touchInteractionStart",
    TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "viewAccessibilityFocusCleared",
    TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "viewAccessibilityFocused",
    TYPE_VIEW_CLICKED -> "viewClicked",
    TYPE_VIEW_FOCUSED -> "viewFocused",
    TYPE_VIEW_HOVER_ENTER -> "viewHoverEnter",
    TYPE_VIEW_HOVER_EXIT -> "viewHoverExit",
    TYPE_VIEW_LONG_CLICKED -> "viewLongClicked",
    TYPE_VIEW_SCROLLED -> "viewScrolled",
    TYPE_VIEW_SELECTED -> "viewSelected",
    TYPE_VIEW_TEXT_CHANGED -> "viewTextChanged",
    TYPE_VIEW_TEXT_SELECTION_CHANGED -> "viewTextSelectionChanged",
    TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> "viewTextTraversedAtMovementGranularity",
    TYPE_WINDOW_CONTENT_CHANGED -> "windowContentChanged",
    TYPE_WINDOW_STATE_CHANGED -> "windowStateChanged"
  )

  /**
   * @return <code>Activity</code> currently in foreground
  */

  def currentActivity = {
    val manager = SpielService.context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    val tasks = manager.getRunningTasks(1)
    if(!tasks.isEmpty)
      tasks.head.topActivity.getClassName
    else null
  }

}
