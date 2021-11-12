package controllers

import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

object ImageController {
  private val logger = Logger(getClass)
}

/**
 * Main controller to handle CRUD Rest operations for IMAGES
 * @param imageResourceHandler dto to handle operations on imageresources
 * @param cc i18N support
 * @param ec implicit execution context (default is a fork join in play)
 */
class ImageController @Inject()(imageResourceHandler: ImageResourceHandler,
                                  cc: MessagesControllerComponents
                                )(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  import ImageController.logger
  /**
   * TODO: handle failures
   * @return
   */
  def create = Action.async { implicit request =>
    val json = request.body.asJson.get
    //todo should use Form?
    val imageResource = json.as[ImageResource]
    imageResourceHandler.create(imageResource).map { newImage =>
      Ok(Json.toJson(newImage))
    }
  }

  /**
   * A REST endpoint that gets all the images as JSON.
   * If none, returns empty json array.
   */
  def list = Action.async { implicit request =>
    imageResourceHandler.list().map { images =>
      Ok(Json.toJson(images))
    }
  }

  /**
   * Return an image given a valid id.  If not found, returns 404 with json error message
   * @param id String value that should be able to converted to an Int
   * @return
   */
  def get(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).map { image =>
      image.map(i => Ok(Json.toJson(i)))
        .getOrElse(NotFound(Json.toJson(ErrorResource(s"Image with id $id not found."))))
    }
  }

  /**
   * Remove an image if exists.
   * TODO: If not found, returns 404 with json error message
   * @param id String value that should be able to converted to an Int
   * @return
   */
  def remove(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).flatMap {
      case None => Future.successful(
        NotFound(Json.toJson(ErrorResource(s"Image with id $id could not be found.")))
      )
      case Some(found) => imageResourceHandler.remove(found).map(_ => NoContent)
    }
  }
}