import sbt.Keys._
import sbt._
import complete.DefaultParsers._
import plugins._
import org.json4s._
import org.json4s.jackson.JsonMethods

// FIXME: add name to alias?

package OWMaps {

  import OWMaps.Inner.MapTemplate
  import OWMaps.Inner.MapTypeTemplate
  package Inner {

    object CodeGen {
      def obj(name: String)(fun: Seq[String]): String = {
        val content = fun.mkString("\n\n")
        s"""
object ${name} {
${content}
}
"""
      }
      def pkg(name: String)(fun: Seq[String]): String = {
        val content = fun.mkString("\n\n")
        s"""
package ${name} {
${content}
}
"""
      }
    }

    object Helper {
      def quote(s: String) = "\"" + s + "\""
    }

    case class MapTypeTemplate(val name: String, val alias: Array[String]) {
      def alias_map: Map[String, String] = alias.map(s ⇒ (s, name)).toMap
      def obj_name: String = name.replace(" ", "").replace("'", "").replace(":", "").capitalize
      def maps_fmt(maps: Array[MapTemplate]) =
        maps.map("Overwatch.Maps." + _.obj_name).mkString("Array(", ",", ")")
      def to_cls(maps: Array[MapTemplate]): String = s"""case object ${obj_name} extends MapType {
def name = ${Helper.quote( name )}
def maps = ${maps_fmt(maps)}
}"""
    }
    case class MapTemplate(val name: String,
                           val alias: Array[String],
                           val map_type: String) {
      def alias_map: Map[String, String] = alias.map(s ⇒ (s, name)).toMap
      def obj_name: String = name.replace(" ", "").replace("'", "").replace(":", "").capitalize
      def alias_fmt: String = alias.map(s ⇒ "\"" + s + "\"").mkString("Array(", ", ", ")")
      def to_cls: String = s"""case object ${obj_name} extends Overwatch.Maps.Map {
def name = ${Helper.quote(name)}
def alias = ${alias_fmt}
}
"""
    }
    object MapTemplate {
      def make_alias(tpls: Array[MapTemplate]): String =
        tpls.flatMap(_.alias_map).map{ case (k, v) ⇒ s"${Helper.quote(k)} → ${Helper.quote(v)}" }.mkString("Map(", ", ", ")")
    }
    object MapTypeTemplate {
      def make_alias(tpls: Array[MapTypeTemplate]): String =
        tpls.flatMap(_.alias_map).map{ case (k, v) ⇒ s"${Helper.quote(k)} → ${Helper.quote(v)}" }.mkString("Map(", ", ", ")")
    }
  }
  object OWMapsPlugin extends AutoPlugin {
    override def requires = JvmPlugin
    object autoImport {
      lazy val owMapConfFile = settingKey[File]("Json file containing the map description")
      lazy val owTypConfFile = settingKey[File]("Json file containing the map type description")
    }

    import autoImport._
    import Inner.CodeGen
    import Inner.Helper.quote

    override lazy val buildSettings = Seq(
    )

    lazy val owMapTask = Def.task {
      val logger = streams.value.log
      implicit val formats: Formats = DefaultFormats

      logger.info(s"Parsing JSON file ${owMapConfFile.value.name}")
      val map_content = IO.read(owMapConfFile.value)
      val maps = JsonMethods.parse(map_content).extract[Array[Inner.MapTemplate]]

      logger.info(s"Parsing JSON file ${owTypConfFile.value.name}")
      val typ_content = IO.read(owTypConfFile.value)
      val maptypes = JsonMethods.parse(typ_content).extract[Array[Inner.MapTypeTemplate]]

      logger.info(s"Generating map and map type aliases")
      val map_alias = MapTemplate.make_alias(maps)
      val typ_alias = MapTypeTemplate.make_alias(maptypes)

      logger.info("Generating str → obj table")
      val map_table = maps.map(m ⇒ s"${quote(m.name.toLowerCase)} → Overwatch.Maps.${m.obj_name}").mkString("Map(", ", ", ")")
      val typ_table = maptypes.map(m ⇒ s"${quote(m.name.toLowerCase)} → Overwatch.MapType.${m.obj_name}").mkString("Map(", ", ", ")")

      logger.info(s"Generating code")
      val code = CodeGen.pkg("Overwatch") {
        Seq(CodeGen.obj("Maps") {
              Seq("""sealed trait Map {
def name: String
def alias: Array[String]
override def hashCode(): Int = name.hashCode()
}""") ++
                maps.map(_.to_cls) ++
                Seq("val alias = " + map_alias,
                    "val maps = " + map_table)
            },
            CodeGen.obj("MapType") {
              Seq("""sealed trait MapType{
def name: String
def maps: Array[Overwatch.Maps.Map]
override def hashCode(): Int = name.hashCode()
}""") ++
                maptypes.map(t ⇒ t.to_cls(maps.filter(m ⇒ m.map_type == t.name))) ++
                Seq("val alias = " + typ_alias,
                    "val map_types = " + typ_table)
            })
      }

      val file = (Compile / sourceManaged).value / "sbt-owmaps" / s"owmaps.scala"
      IO.write(file, code)
      Seq(file)
    }

    override lazy val projectSettings = Seq(
      Compile / sourceGenerators += owMapTask.taskValue
    )
  }
}
