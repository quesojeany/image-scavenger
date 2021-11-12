package models

import play.api.libs.json.{Json, OFormat}

case class ErrorResource(message: String)

object ErrorResource {
  implicit val imageFormat: OFormat[ErrorResource] = Json.format[ErrorResource]
}
