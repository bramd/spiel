package info.spielproject.spiel.scripting

import java.io.{File, FileInputStream, FileOutputStream, InputStream}

import android.content.{Context => AContext}
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import com.db4o._
//import com.neodatis.odb.plugin.engine.berkeleydb.NeoDatisBerkeleyDBPlugin
import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}
//import org.neodatis.odb.{NeoDatis, NeoDatisGlobalConfig, Objects, ODB}

import info.spielproject.spiel._
import handlers.{Callback, Handler}

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    Context.enter
    var args = new Array[Object](1)
    args(0) = e
    try {
      Context.toBoolean(f.call(Scripter.context, Scripter.scope, Scripter.scope, args))
    } catch {
      case e =>
        TTS.speak("Script error: "+e.getMessage, true)
        Log.e("spiel", "Error running script: "+e.getMessage)
        false
    }
  }
}


class Script(
  context:Context,
  scope:ScriptableObject,
  code:String,
  filename:String,
  val pkg:String = ""
) {

  run

  def run = {
    scope.put("__pkg__", scope, pkg)
    try {
      context.evaluateString(scope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => Log.e("spiel", e.toString)
    }finally {
      scope.put("__pkg__", scope, null)
    }
  }

}

object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  private var db:ObjectContainer = null

  def apply(service:SpielService)  {
    myCx = Context.enter
    myCx.setOptimizationLevel(-1)
    myScope = myCx.initStandardObjects()

    val wrappedHandler = Context.javaToJS(Handler, myScope)
    ScriptableObject.putProperty(myScope, "Handler", wrappedHandler)

    val wrappedScripter = Context.javaToJS(this, myScope)
    ScriptableObject.putProperty(myScope, "Scripter", wrappedScripter)

    val wrappedTTS = Context.javaToJS(TTS, myScope)
    ScriptableObject.putProperty(myScope, "TTS", wrappedTTS)

    def run(code:String, filename:String) = {
      val p = filename.substring(0, filename.lastIndexOf("."))
      val pkg = if(p.startsWith("_")) p.substring(1, p.size) else p
      new Script(myCx, scope, code, filename, pkg)
    }

    val assets = service.getAssets

    def readAllAvailable(is:InputStream):String = {
      val a = is.available
      val b = new Array[Byte](a)
      is.read(b)
      new String(b)
    }

    def runScriptAsset(f:String) = {
      val is = assets.open("scripts/"+f)
      run(readAllAvailable(is), f)
    }

    runScriptAsset("api.js")
    for(fn <- assets.list("scripts") if(fn != "api.js")) {
      runScriptAsset(fn)
    }

    //val config = NeoDatisGlobalConfig.get
    //config.setStorageEngineClass(classOf[NeoDatisBerkeleyDBPlugin])

    val directory = service.getDir("data", AContext.MODE_PRIVATE)

    val dbFile = new File(directory, "spiel.db")

    //odb = NeoDatis.open(directory.getAbsolutePath+"/spiel.db")
    db = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration, dbFile.getAbsolutePath)

    db.queryByExample(classOf[Script]).toArray.foreach (
      _.asInstanceOf[Script].run
    )

    true
  }

  def onDestroy = {
    Context.exit
    db.close
  }

  def registerHandlerFor(pkg:String, cls:String, s:Object) {
    val scr = s.asInstanceOf[ScriptableObject]
    val h = new Handler(pkg, cls)

    scr.getIds.foreach { property =>

      val id = property.asInstanceOf[String]
      val chars = id.substring(2, id.length).toCharArray
      chars(0) = chars(0).toLower
      val func = new String(chars)

      if(Handler.dispatchers.valuesIterator.contains(func)) {
        val f = scr.get(id, myScope)
        if(f.isInstanceOf[Function])
          h.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
      } else
        Log.e("spiel", func+" is not a valid handler. Skipping.")
    }

    h
  }

}
