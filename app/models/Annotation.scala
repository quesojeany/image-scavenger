package models

import play.api.libs.json.Json

case class Annotation(name: String)
object Annotation {
  implicit val annotationFormat = Json.using[Json.WithDefaultValues].format[Annotation]
}
