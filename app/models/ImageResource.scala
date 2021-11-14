package models

import persistence.{ImageData, ImageId, ImageRepository}
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
case class ImageResource(id: Long = 0,
                         name: String = "defaultPoop",
                         storedPath: String = "defaultStoredPath",
                         detectionEnabled: Boolean = false,
                         //mediaType: MediaType,
                         fileName: String = "defaultFileNamePoop",
                         downloadUrl: String = "thisshouldnotbeaccesibleDownloadUrl",
                        ) extends Storable with Detectable {

    //override def isMediaTypeSupported: Boolean = mediaType.isImage
}

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
        //val id = image.id.map(thisId => ImageId(thisId))
        val data = ImageData(ImageId(image.id), path = image.storedPath, name = image.name, detectionEnabled = image.detectionEnabled)
        imageRepository.create(data).map(toImageResource)
    }

    def list(): Future[Seq[ImageResource]] = imageRepository.list()
      .map(imageRows => {
        imageRows.map(toImageResource)
      })

    def get(id: String): Future[Option[ImageResource]] = imageRepository.findById(ImageId(id))
      .map(imageRow => imageRow.map(toImageResource))

    def exists(id: String): Future[Boolean] = get(id).map(_.isDefined)

    def remove(id: String): Future[Int] = imageRepository.remove(ImageId(id))

    /**
     * Remove an image.
     * TODO: throws generic illegal argument exception should probably handle more elegantly
     * @param image ImageResource the dto
     * @return
     */
    def remove(image: ImageResource): Future[Int] = {
        require(image.id != 0) //todo; lazy way handle handling id is 0 which is the shitty way of indicating
        val imageToDelete = ImageData(ImageId(image.id), image.name, image.storedPath, image.detectionEnabled)
        imageRepository.remove(imageToDelete)
    }

    // probably better way to apply and unapply these models, but for right now blargh time.
    // todo: get rid of hardcoded media type
    private def toImageResource(data: ImageData): ImageResource =
        ImageResource(data.id.value, data.name, data.path, data.detectionEnabled/*mediaType = MediaTypes.`image/jpeg`*/)

    def toImageData(image: ImageResource) = ImageData(ImageId(image.id), image.name, image.storedPath, image.detectionEnabled)

}