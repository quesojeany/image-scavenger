package models

import play.api.libs.json.{Json, OFormat}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * DTO for displaying image information
 * @param id optional long - new cases
 * @param name String name of image
 * @param path String path of image
 * @param detectionEnabled whether to turn on detection for image
 */
case class ImageResource(id: Option[Long] = None, name: String, path: String, detectionEnabled: Boolean = false)

object ImageResource {
    implicit val imageFormat: OFormat[ImageResource] = Json.using[Json.WithDefaultValues].format[ImageResource]
}

/**
 * Controls access to the backend data, returning [[ImageResource]]
 * TODO: figure out caching - play CacheAPI?
 * TODO: update?
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
     * Create an image
     * TODO: error handling with Form
     * @param image ImageResource
     * @return
     */
    def create (image: ImageResource): Future[ImageResource] = {
        //TODO: yuck null (do this later ExistingImage versus Image, with Existing trait maybe)
        val id = image.id.map(thisId => ImageId(thisId))
        val data = ImageData(null, name = image.name, path = image.path, detectionEnabled = image.detectionEnabled)
        imageRepository.create(data).map(createImageResource)
    }

    def list(): Future[Seq[ImageResource]] = imageRepository.list()
      .map(imageRows => {
        imageRows.map(createImageResource)
      })

    def get(id: String): Future[Option[ImageResource]] = imageRepository.findById(ImageId(id))
      .map(imageRow => imageRow.map(createImageResource))

    def exists(id: String): Future[Boolean] = get(id).map(_.isDefined)

    def remove(id: String): Future[Int] = imageRepository.remove(ImageId(id))

    /**
     * Remove an image.
     * TODO: throws generic illegal argument exception should probably handle more elegantly
     * @param image ImageResource the dto
     * @return
     */
    def remove(image: ImageResource): Future[Int] = {
        require(image.id.isDefined) //todo; lazy way handle checking id. just throws generic IllegalArgException
        val imageToDelete = ImageData(ImageId(image.id.get), image.name, image.path, image.detectionEnabled)
        imageRepository.remove(imageToDelete)
    }

    private def createImageResource(data: ImageData): ImageResource =
        ImageResource(Some(data.id.value), data.name, data.path, data.detectionEnabled)

}