package com.ippclient

import android.print.PrintAttributes.MediaSize
import android.print.PrintAttributes.MediaSize.ISO_A4
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import de.gmuth.ipp.client.IppPrinter
import de.gmuth.ipp.client.IppClient
import de.gmuth.ipp.client.IppTemplateAttributes.copies
import de.gmuth.ipp.client.IppTemplateAttributes.documentFormat
import de.gmuth.ipp.client.IppTemplateAttributes.jobName
import de.gmuth.ipp.client.IppTemplateAttributes.jobPriority
import de.gmuth.ipp.client.IppTemplateAttributes.numberUp
import de.gmuth.ipp.client.IppTemplateAttributes.printerResolution
import de.gmuth.ipp.client.IppWhichJobs
import de.gmuth.ipp.core.IppAttribute
import de.gmuth.ipp.core.IppAttributeBuilder
import de.gmuth.ipp.core.IppResolution
import de.gmuth.ipp.core.IppTag
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Duration

class IppClientModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  @ReactMethod
  fun getPrinterAttributes(printerUrl: String, promise: Promise) {
    try {
      Log.d("INFO", "RECUPER ATTRIBUTI STAMPANTE")
      val ippPrinter = IppPrinter(URI.create(printerUrl))

      // Retrieve printer attributes
      val attributes = ippPrinter.attributes
      val attributesMap = attributes.map { it.key to it.value.toString() }.toMap()

      // Retrieve marker levels
      val markers = ippPrinter.markers.map { marker ->
        marker.name to marker.levelPercent().toString()
      }.toMap()

      // Combine attributes and markers into a single map
      val result = Arguments.createMap()
      result.putMap("attributes", convertMapToWritableMap(attributesMap))
      result.putMap("markers", convertMapToWritableMap(markers))

      // Resolve the promise with the result map
      promise.resolve(result)

    } catch (e: Exception) {
      promise.reject("Error", e)
    }

  }

  private fun convertMapToWritableMap(map: Map<String, String>): WritableMap {
    val writableMap = Arguments.createMap()
    for ((key, value) in map) {
      writableMap.putString(key, value)
    }
    return writableMap
  }

  @ReactMethod
  fun getPrinterMarkerLevels(printerUrl: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val markers = printer.markers.map { it.toString() }
      promise.resolve(markers)
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun printJob(printerUrl: String, jobName: String, document: String, promise: Promise) {
    var tempFile: File? = null
    try {
      Log.d("INFO", "Starting print job for document: $document")
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))

      // Retrieve printer attributes
      val attributes = printer.attributes
      val documentFormatsSupported = attributes.getValues("document-format-supported") as List<String>

      // Download the image from the URL
      val url = URL(document)
      val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
      connection.doInput = true
      connection.connect()

      Log.d("INFO", "Connected to URL: $document")
      val inputStream = connection.inputStream
      tempFile = File.createTempFile("tempImage", ".jpeg")
      val outputStream = FileOutputStream(tempFile)
      inputStream.copyTo(outputStream)
      outputStream.close()
      inputStream.close()

      Log.d("INFO", "Downloaded document to temp file: ${tempFile.absolutePath}")

      // Verify document format
      val mimeType = "image/jpeg" // Assume the MIME type based on the file extension
      if (!supportsDocumentFormat(documentFormatsSupported, mimeType)) {
        Log.d("INFO", "Printer does not support format: $mimeType")
        promise.reject("Error", "Printer does not support format: $mimeType")
        return
      }


      // Build job attributes
      val jobNameAttribute = IppAttribute("job-name", IppTag.NameWithoutLanguage, jobName)
      val documentFormatAttribute = IppAttribute("document-format", IppTag.MimeMediaType, mimeType)
      val mediaSizeAttribute = IppAttribute("media", IppTag.Keyword, "na_index-4x6_4x6in")

      Log.d("INFO", "Downloaded file size: ${tempFile.length()} bytes")
      if (tempFile.length() == 0L) {
        Log.e("ERROR", "Downloaded file is empty")
        promise.reject("Error", "Downloaded file is empty")
        return
      }

      // Print the job
      val job = printer.printJob(
        tempFile,
        jobNameAttribute,
        documentFormatAttribute,
        mediaSizeAttribute
      )

      //job.waitForTermination()

      tempFile?.delete()
      Log.d("INFO", "Deleted temp file: ${tempFile?.absolutePath}")

      promise.resolve(job.toString())
    } catch (e: Exception) {
      Log.e("ERROR", "Failed to print job: ${e.message}", e)
      tempFile?.delete()
      promise.reject("Error", e)
    }
  }

  private fun supportsDocumentFormat(supportedFormats: List<String>, format: String): Boolean {
    return supportedFormats.contains(format)
  }


  @ReactMethod
  fun createJobAndSendDocument(printerUrl: String, jobName: String, document: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val file = File(document)
      val job = printer.createJob(jobName(jobName))
      job.sendDocument(FileInputStream(file))
      job.waitForTermination()
      promise.resolve(job.toString())
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun manageJobs(printerUrl: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val jobs = printer.getJobs().map { it.toString() }
      promise.resolve(jobs)
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun getCompletedJobs(printerUrl: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val jobs = printer.getJobs(IppWhichJobs.Completed).map { it.toString() }
      promise.resolve(jobs)
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun manageSingleJob(printerUrl: String, jobId: Int, action: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val job = printer.getJob(jobId)
      when (action) {
        "hold" -> job.hold()
        "release" -> job.release()
        "cancel" -> job.cancel()
        "cupsGetDocuments" -> job.cupsGetDocuments()
        else -> throw IllegalArgumentException("Invalid action: $action")
      }
      promise.resolve(job.toString())
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun controlPrinter(printerUrl: String, action: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      when (action) {
        "pause" -> printer.pause()
        "resume" -> printer.resume()
        "sound" -> printer.sound()
        else -> throw IllegalArgumentException("Invalid action: $action")
      }
      promise.resolve("Action $action performed successfully")
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun subscribeAndHandleEvents(printerUrl: String, promise: Promise) {
    try {
      val ippClient = IppClient()
      val printer = IppPrinter(URI.create(printerUrl))
      val subscription = printer.createPrinterSubscription(notifyLeaseDuration = Duration.ofMinutes(5))
      subscription.pollAndHandleNotifications { event ->
        promise.resolve(event.toString())
      }
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun findSupportedMediaBySize(printerUrl: String, size: String, promise: Promise) {
    try {
      throw Error("NOT IMPLEMENTED METHOD")
      /* val ippClient = IppClient()
       val printer = IppPrinter(URI.create(printerUrl))
       val mediaColDatabase = printer.getMediaColDatabase()
       val media = mediaColDatabase.findMediaBySize(Media.Size.valueOf(size))
       promise.resolve(media.toString())
       */
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun checkMediaSizeSupport(printerUrl: String, size: String, promise: Promise) {
    try {
      throw Error("NOT IMPLEMENTED METHOD")
      /*
        val ippClient = IppClient()
        val printer = IppPrinter(URI.create(printerUrl))
        val isSupported = printer.isMediaSizeSupported(Media.Size.valueOf(size))
        promise.resolve(isSupported)

       */
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun checkMediaSizeReady(printerUrl: String, size: String, promise: Promise) {
    try {
      throw Error("NOT IMPLEMENTED METHOD")
      /*
        val ippClient = IppClient()
        val printer = IppPrinter(URI.create(printerUrl))
        val isReady = printer.isMediaSizeReady(Media.Size.valueOf(size))
        promise.resolve(isReady)

       */
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  @ReactMethod
  fun getSourcesOfMediaSizeReady(printerUrl: String, size: String, promise: Promise) {
    try {
      throw Error("NOT IMPLEMENTED METHOD")
      /*
        val ippClient = IppClient()
        val printer = IppPrinter(URI.create(printerUrl))
        val sources = printer.sourcesOfMediaSizeReady(Media.Size.valueOf(size))
        promise.resolve(sources.toString())

       */
    } catch (e: Exception) {
      promise.reject("Error", e)
    }
  }

  companion object {
    const val NAME = "IppClient"
  }
}
