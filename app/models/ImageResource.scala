package models

import play.api.libs.json.{Json, OFormat}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * DTO for displaying image information
 * @param id
 * @param name
 * @param filePath
 * @param detectionEnabled
 */
case class ImageResource(id: Option[Long] = None, name: String, path: String, detectionEnabled: Boolean = false)

object ImageResource {
    implicit val imageFormat: OFormat[ImageResource] = Json.using[Json.WithDefaultValues].format[ImageResource]
}

/**
 * Controls access to the backend data, returning [[ImageResource]]
 */
class ImageResourceHandler @Inject()(imageRepository: ImageRepository)(implicit ec: ExecutionContext) {

    /*def create(postInput: PostFormInput)(
      implicit mc: MarkerContext): Future[PostResource] = {
        val data = PostData(PostId("999"), postInput.title, postInput.body)
        // We don't actually create the post, so return what we have
        postRepository.create(data).map { id =>
            createPostResource(data)
        }
    }*/

    /*def lookup(id: String)(
      implicit mc: MarkerContext): Future[Option[PostResource]] = {
        val postFuture = postRepository.get(PostId(id))
        postFuture.map { maybePostData =>
            maybePostData.map { postData =>
                createPostResource(postData)
            }
        }
    }*/

    /**
     * TODO: should this be
     * @param image
     * @return
     */
    def create (image: ImageResource): Future[ImageResource] = {
        //TODO: yuck fake id, figure out later, egads slick.
        val id = image.id.map(thisId => ImageId(thisId))
        val data = ImageData(id.orNull, name = image.name, path = image.path, detectionEnabled = image.detectionEnabled)
        imageRepository.create(data).map(createImageResource)
    }

    def list(): Future[Seq[ImageResource]] = imageRepository.list()
      .map(imageRows => {
        imageRows.map(createImageResource)
      })

    def get(id: String): Future[Option[ImageResource]] = imageRepository.findById(ImageId(id))
      .map((imageRow => imageRow.map(createImageResource)))

    def remove(id: String): Future[Int] = imageRepository.remove(ImageId(id))

    private def createImageResource(data: ImageData): ImageResource =
        ImageResource(Some(data.id.value), data.name, data.path, data.detectionEnabled)

}