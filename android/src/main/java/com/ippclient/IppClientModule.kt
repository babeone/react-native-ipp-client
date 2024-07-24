package com.ippclient

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.github.gmuth.ipp.client.IppClient
import com.github.gmuth.ipp.client.IppPrinter
import com.github.gmuth.ipp.core.DocumentFormat
import com.github.gmuth.ipp.core.Media
import com.github.gmuth.ipp.core.PrintQuality
import com.github.gmuth.ipp.core.Sides
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.time.Duration

class IppClientModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return NAME
    }

    @ReactMethod
    fun getPrinterAttributes(printerUrl: String, promise: Promise) {
        try {
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))
            val attributes = printer.getAttributes()

            val attributesMap = attributes.map { it.name to it.value.toString() }.toMap()
            promise.resolve(attributesMap)
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
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
        try {
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))

            // Download the image from the URL
            val url = URL(document)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            val inputStream = connection.inputStream
            val tempFile = File.createTempFile("tempImage", ".jpeg")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()

            // Read the downloaded image
            val image = ImageIO.read(tempFile)

            val width = 1016 // 101.6 mm in hundredths of a millimeter
            val height = 2540 // 254 mm in hundredths of a millimeter

            val job = printer.printJob(
                jpegFile,
                jobName(jobName),
                DocumentFormat.JPEG,
                MediaCollection(
                  MediaSize(1016, 2540),
                  MediaMargin(300) // 3 mm margin
                ),
                PrintQuality.High,
                Sides.TwoSidedLongEdge
            )
            promise.resolve(job.toString())
        } catch (e: Exception) {
            promise.reject("Error", e)
        } finally {
            tempFile?.delete()
        }
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
            val jobs = printer.getJobs(IppPrinter.WhichJobs.Completed).map { it.toString() }
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
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))
            val mediaColDatabase = printer.getMediaColDatabase()
            val media = mediaColDatabase.findMediaBySize(Media.Size.valueOf(size))
            promise.resolve(media.toString())
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
    }

    @ReactMethod
    fun checkMediaSizeSupport(printerUrl: String, size: String, promise: Promise) {
        try {
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))
            val isSupported = printer.isMediaSizeSupported(Media.Size.valueOf(size))
            promise.resolve(isSupported)
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
    }

    @ReactMethod
    fun checkMediaSizeReady(printerUrl: String, size: String, promise: Promise) {
        try {
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))
            val isReady = printer.isMediaSizeReady(Media.Size.valueOf(size))
            promise.resolve(isReady)
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
    }

    @ReactMethod
    fun getSourcesOfMediaSizeReady(printerUrl: String, size: String, promise: Promise) {
        try {
            val ippClient = IppClient()
            val printer = IppPrinter(URI.create(printerUrl))
            val sources = printer.sourcesOfMediaSizeReady(Media.Size.valueOf(size))
            promise.resolve(sources.toString())
        } catch (e: Exception) {
            promise.reject("Error", e)
        }
    }

    companion object {
        const val NAME = "IppClient"
    }
}
