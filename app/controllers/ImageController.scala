package controllers

import models._
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext

class ImageController @Inject()(imageResourceHandler: ImageResourceHandler,
                                  cc: MessagesControllerComponents
                                )(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

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
   */
  def list = Action.async { implicit request =>
    imageResourceHandler.list().map { images =>
      Ok(Json.toJson(images))
    }
  }

  def get(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).map { image =>
      Ok(Json.toJson(image))
    }
  }

  def remove(id: String) = Action.async { implicit request =>
    imageResourceHandler.remove(id).map { _ =>
      NoContent
    }
  }
}