# Image Scavenger

---

**Overview**

Build a HTTP REST API in Scala for a service that ingests user images, analyzes them for object detection, and returns the enhanced content. It should implement the following specification::

**API Specification**

`GET /images
`

Returns HTTP 200 OK with a JSON response containing all image metadata.

`GET /images?objects="dog,cat"
`

Returns a HTTP 200 OK with a JSON response body containing only images that have the detected objects specified in the query parameter.

`GET /images/{imageId}
`

Returns HTTP 200 OK with a JSON response containing image metadata for the specified image.

`POST /images
`

Send a JSON request body including an image file or URL, an optional label for the image, and an optional field to enable object detection.

Returns a HTTP 200 OK with a JSON response body including the image data, its label (generate one if the user did not provide it), its identifier provided by the persistent data store, and any objects detected (if object detection was enabled).

Object detection instructions
Image object detection can be performed using any API offering of your choosing (such as Google, IBM, Imagga, etc), or with a process managed by your backend. The only requirement is that it must return a list of object names detected within that image.

That is the extent of the API contract. HTTP error codes should be used to indicate the proper level of system failure (i.e. client versus server).

**Database**

A persistent data store is required, any variant of SQL is encouraged.

**Expectations** 

No frontend is required, but you may create one to demo the API. Regardless, a user of the API should be able to:

Upload an optionally labelled image and run image object detection on it

Retrieve all images and any metadata obtained from their analyses

Search for images based on detected objects

---

## Tech Stack 

* Detection Service
  * [Google Vision Api](https://cloud.google.com/vision)
* Storage Service
  [Google Cloud Storage](https://cloud.google.com/)
* Http Server/Client
  * [Play Framework](https://www.playframework.com/) based on [Akka Http](https://doc.akka.io/docs/akka-http/current/index.html)
  * [Play Standalone Client](https://www.playframework.com/documentation/2.8.x/ScalaWS)
* Persistence
  * [Slick](https://scala-slick.org/) (with a lil' help from [Play Slick](https://www.playframework.com/documentation/2.8.x/PlaySlick) 
  * [Evolutions](https://www.playframework.com/documentation/2.8.x/Evolutions)
  * [H2](https://www.h2database.com/html/main.html) (for poc/demo)
* Testing *(stay tuned, poop)*
  * Unit: [ScalaTests](https://www.scalatest.org/) (with some goodies from [Play](https://www.playframework.com/documentation/2.8.x/ScalaTestingWithScalaTest))
  * Integration: [Runscope](https://www.runscope.com/)
  * Manual: [Postman](https://www.postman.com/)

## @TODO Notes
#### *(codeIsNeverDone!)*

Mostly feature complete with a few regrets, ehem improvements (it is a POC):
* Rest API Notes
  * Detection on file urls only. Uploading corrupted files; the pain is real.
  * Removing images is failing.
  * Nice to haves for the future:
    * update
    * count
    * sublist
* Happy path time! 
  * Validation: Leverage Play's Form for json, and play json validate for des/ser of 3rd party requests/responses
  * Error handling: Could have used some TLC, especially regarding dependent service and repo responses
* No Tests **(Shame on me!)** No unit or integration tests. 
  * TDD: In retrospect, should have done them first and wired up all the mocks to save me from dependency heartache while designing/building
  * Helps other developers understand how the code works since ScalaTests is expressive (almost BDD-style)
* Caching? 
  * persistence: Slick is not an ORM with a caching layer, just a plain ole FRM
  * 3rd party services: wsclient has caching, but saved for another day- Caching is a tricky beast that can bite you in the tush.
* Auto-generated docs and client
  * [Swagger](https://swagger.io/) is a thing, but not this round (someday)
* Auto-Code Formatting
  * [scalafmt](https://scalameta.org/scalafmt/)
* Developer Etiquette~ Would love more time to polish/cleanup the code a bit. Make it even more expressive/readable and concise with Scala sugar!
* To be continued when I remember yet another thing...oh yeah, make this readme prettier. (;

###### Happy Hunting, Cheers! 
######~quesojean






