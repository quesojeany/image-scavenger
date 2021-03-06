package repos

import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.lifted.MappedTo

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

final case class ImageData(id: ImageId, name: String, path: String, detectionEnabled: Boolean = false)
final case class AnnotationData(id: Long, name: String, imageId: ImageId)
final case class FullImageData(imageData: ImageData, annotationData: Seq[AnnotationData] = Seq())
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
 * A repository for images (which includes annotations table)
 *
 * Improvements:
 * TODO 1. abstract persistence layer away from slick (may mean to stop relying on Guice/play-slick)
 * TODO 2. If we want to support more than just images as a requirement--> this really should be generic tables, just StoredFiles, no reference to image
 * TODO 3. sublist def --> start, limit
 * TODO 4. count def
 * TODO 5. remove is broken
 * TODO 6: best practice to use UUID when replication is involved (ensure universal unique identifier)
 * TODO 7: Shame! Its littered throughout code outside of this repo, but I'd like to use a Trait that indicates
 * whether a repo model is peristed or not rather than the lazy code smell identity value of 0
 *
 * Helpful hint: Wanna see generated sql queries? Sure? OK, then!
 * set logging level to debug and search for "Compiled server-side to" in console
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

  private val logger = Logger(getClass)

  /**
   * Images Table
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

  private implicit class ImageExtensions[C[_]](q: Query[ImagesTable, ImageData, C]) {
    // specify mapping of relationship to address
    def withPossibleAnnotations =
      images.joinLeft(annotations).on(_.id === _.imageId)

    //todo: this filter probably should go in an AnnotationExtensions class
    def filterAnnotationsByName(name: String) =
      annotations.filter(a => a.name.toLowerCase === name.toLowerCase)

    def withAnnotations(name: String) =
      images.join(filterAnnotationsByName(name)).on(_.id === _.imageId)

    def withAnnotations = images.join(annotations).on(_.id === _.imageId)
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
   * Create an image and any possible annotations
   * TODO: use FullImageData instead.
   * This is an asynchronous operation, it will return a future of the created image, which can be used to obtain the
   * id for that image.
   */
  def create(imageData: ImageData, annotationData: Seq[AnnotationData] = Seq()): Future[(ImageData, Seq[AnnotationData])] = {
    val insertImageThenAnnotations = for {
      createdImage: ImageData <- insertImage(imageData)
      createdAnnotations: Seq[AnnotationData] <- {
        val annotationDataWithImageId = annotationData.map(data => data.copy(imageId = createdImage.id))
        insertAnnotations(annotationDataWithImageId)
      }
    } yield (createdImage, createdAnnotations)
    db.run(insertImageThenAnnotations.transactionally)
  }

  private def insertImage(imageData: ImageData) = {
    (images.map(i => (i.name, i.path, i.detectionEnabled))
      // Now define it to return the id, because we want to know what id was generated for the image
      returning images.map(_.id)
      // And we define a transformation for the returned value, which combines our original parameters with the
      // returned id
      into ((namePathDetectionEnabled, id) => ImageData(id, namePathDetectionEnabled._1, namePathDetectionEnabled._2, namePathDetectionEnabled._3))
      // And finally, insert the image into the database
      ) += (imageData.name, imageData.path, imageData.detectionEnabled)
  }

  private def insertAnnotations(annotationData: Seq[AnnotationData]) = {
    val annotationTuples = annotationData.map(poop => {
      (poop.name, poop.imageId)
    })

    (annotations.map(a => (a.name, a.imageId))
      // Now define it to return the id, because we want to know what id was generated for the annotation
      returning annotations.map(_.id)
      // And we define a transformation for the returned value, which combines our original parameters with the
      // returned id
      into ((nameImageId, id) => AnnotationData(id, nameImageId._1, nameImageId._2))
      // And finally, insert the annotations into the database
      ) ++= annotationTuples
  }

  /**
   * Return images with optional annotations
   *
   * Performance concerns: Two queries instead of one for now. It's probably
   * best to have less db queries (connection overhead, network latency, load on db),
   * but its also possible left joins can wreak havoc in some cases (causes CPU issues)
   * Multiple queries are fine here for prototype/demo purposes.
   * @param id
   * @return
   */
  def findById(id: ImageId): Future[Option[FullImageData]] = {
    for {
      imageData <- db.run(queryById(id).result.headOption)
      annotationData <- db.run(annotations.filter(_.imageId === id).result)
    } yield imageData.map { image => FullImageData(image, annotationData) }
  }

  /**
   * List all possibly annotated images in the database.
   * One query with a left join!
   */
  def list(): Future[Seq[FullImageData]] = db.run {
    logger.debug("MEGAVERSE(yuck, farcebook): Unfiltered mega-universe of images and their possible annotations")
    images.withPossibleAnnotations.result.map { imageAnnotationRows => {
      val groupedByImageData = imageAnnotationRows.groupBy(x => x._1).map {
        case (image, imageAnnotationTuples) => (image, imageAnnotationTuples.flatMap(_._2))
      }
      groupedByImageData.toSeq.map(tuple => FullImageData(tuple._1, tuple._2))
    }}
  }

  def findByAnnotation(data: AnnotationData): Future[Seq[FullImageData]] = db.run {
    logger.debug("FilteredVerse: by name, i hope we find something!")

    images.withAnnotations(data.name).result.map { imageAnnotationRows => {
      val groupedByImageData = imageAnnotationRows.groupBy(x => x._1).map {
        case (image, imageAnnotationTuples) => (image, imageAnnotationTuples.map(_._2))
      }
      groupedByImageData.toSeq.map(tuple => FullImageData(tuple._1, tuple._2))
    }}
  }

  def remove(data: ImageData): Future[Int] = db.run {
    images.filter(_.id === data.id).delete
  }

  //TODO: broken keep on foreign key constraint violation.
  def remove(id: ImageId): Future[Int] = {
    db.run((annotations.filter(_.imageId === id).delete andThen queryById(id).delete)
      .transactionally)
    /*for {
      _ <- db.run(annotations.filter(_.imageId === id).delete)
      deletedImage <- db.run(images.filter(_.id === id).delete)
    } yield deletedImage*/
  }


  /******************** Annotations Section TODO: rip this out to its own repo home *********************/

  private class AnnotationsTable(tag: Tag) extends Table[AnnotationData](tag, "annotations")  {

    /** The ID column, which is the primary key, and auto incremented */
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    /** The name column */
    def name: Rep[String] = column[String]("name")

    def imageId: Rep[ImageId] = column[ImageId]("imageId")

    def image = foreignKey("FK_IMAGES", imageId, TableQuery[ImagesTable])(_.id, onDelete = ForeignKeyAction.Cascade)

    def * = (id, name, imageId) <> ((AnnotationData.apply _).tupled, AnnotationData.unapply)
  }

  private val annotations = TableQuery[AnnotationsTable]
}
