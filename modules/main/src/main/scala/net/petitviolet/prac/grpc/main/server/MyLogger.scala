package net.petitviolet.prac.grpc.main.server

import java.util.UUID

import io.grpc._
import org.slf4j.LoggerFactory
import net.petitviolet.operator._

class MyLogger(name: String) {
  protected lazy val logger = LoggerFactory.getLogger(name)

  def debug(msg: => String): Unit = if (logger.isDebugEnabled) logger.debug(msg)

  def info(msg: => String): Unit = if (logger.isInfoEnabled) logger.info(msg)

  def warn(msg: => String): Unit = if (logger.isWarnEnabled) logger.warn(msg)

  def error(msg: => String, t: Throwable): Unit = if (logger.isErrorEnabled) logger.error(msg, t)
}

private object AccessLogger extends MyLogger("access_log") with ServerInterceptor {
  private val requestIdKey = Metadata.Key.of("request_id", Metadata.ASCII_STRING_MARSHALLER)
  private def generateRequestId(): String = UUID.randomUUID().toString

  private implicit class withRequestId(val metadata: Metadata) extends AnyVal {
    private def putRequestId(): Unit = metadata.put(
      requestIdKey, generateRequestId()
    )

    def withRequestId: Metadata =
      metadata <| { meta =>
        if (!meta.containsKey(requestIdKey)) {
          putRequestId()
        }
      }

    def requestId: String =
      Option(metadata.get(requestIdKey)).get  // ensure call `withRequestId` before `requestId`
  }

  override def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    _headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val headers = _headers.withRequestId
    val logCall = new ForwardingServerCall.SimpleForwardingServerCall[ReqT, RespT](call) {
      override def request(numMessages: Int): Unit = {
        logger.info(s"[Request][${ headers.requestId }]")
        super.request(numMessages)
      }

      override def sendMessage(message: RespT): Unit = {
        logger.info(s"[Response][${ headers.requestId }]message = $message")
        super.sendMessage(message)
      }

      override def close(status: Status, trailers: Metadata): Unit = {
        logger.info(s"[Close][${ headers.requestId }]status = $status")
        super.close(status, trailers)
      }
    }

    next.startCall(logCall, headers)
  }

}
