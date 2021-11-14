package persistence

import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.lifted.MappedTo

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

//todo: we should probably use UUID for uniqueness in a potential replicated environment
final case class ImageData(id: ImageId, name: String, path: String, detectionEnabled: Boolean = false)


case class ImageId(value: Long) extends MappedTo[Long] {
  override def toString: String = value.toString
}

object ImageId {
  def apply(raw: String): ImageId = {
    require(raw != null)
    new ImageId(Integer.parseInt(raw))
  }
}


/**
 * A repository for images.
 * TODO: investigate transaction/session capability
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class ImageRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  // We want the JdbcProfile for this provider
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  // These imports are important, the first one brings db into scope, which will let you do the actual db operations.
  // The second one brings the Slick DSL into scope, which lets you define the table and other queries.
  import dbConfig._
  import profile.api._

  /**
   * Here we define the table. It will have a name of images
   */
  private class ImagesTable(tag: Tag) extends Table[ImageData](tag, "images") {

    /** The ID column, which is the primary key, and auto incremented */
    def id: Rep[ImageId] = column[ImageId]("id", O.PrimaryKey, O.AutoInc)

    /** The name column */
    def name: Rep[String] = column[String]("name")

    /** The file path column */
    def path: Rep[String] = column[String]("path")

    /** The detectionEnabled column */
    def detectionEnabled: Rep[Boolean] = column[Boolean]("detectionEnabled", O.Default(false))

    /**
     * This is the tables default "projection".
     *
     * It defines how the columns are converted to and from the ImageData object.
     *
     * In this case, we are simply passing the id, name and path parameters to the ImageData case classes
     * apply and unapply methods.
     */
    def * = (id, name, path, detectionEnabled) <> ((ImageData.apply _).tupled, ImageData.unapply)
  }

  /**
   * The starting point for all queries on the images table.
   */
  private val images = TableQuery[ImagesTable]

  /**
   * Define a precompiled parameterized query to find by id
   */
  private val queryById = Compiled(
    (id: Rep[ImageId]) => images.filter(_.id === id))

  /**
   * Create an image
   *
   * This is an asynchronous operation, it will return a future of the created image, which can be used to obtain the
   * id for that image.
   */
  def create(data: ImageData): Future[ImageData] = db.run {
    // We create a projection of the 3 columns, since we're not inserting a value for the id column
    (images.map(i => (i.name, i.path, i.detectionEnabled))
      // Now define it to return the id, because we want to know what id was generated for the person
      returning images.map(_.id)
      // And we define a transformation for the returned value, which combines our original parameters with the
      // returned id
      into ((namePathDetectionEnabled, id) => ImageData(id, namePathDetectionEnabled._1, namePathDetectionEnabled._2, namePathDetectionEnabled._3))
    // And finally, insert the person into the database
    ) += (data.name, data.path, data.detectionEnabled)
  }

  def findById(id: ImageId): Future[Option[ImageData]] = db.run {
    queryById(id).result.headOption
  }

  /**
   * List all the images in the database.
   */
  def list(): Future[Seq[ImageData]] = db.run {
    images.result
  }

  def remove(data: ImageData): Future[Int] = db.run {
    images.filter(_.id === data.id).delete
  }

  def remove(id: ImageId): Future[Int] = db.run {
    queryById(id).delete
  }
}
