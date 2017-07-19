package com.crawler.osn.common

import java.beans.Transient

import akka.actor.{ActorRef, ActorSelection}
import com.mongodb.BasicDBObject
import com.crawler.dao.{MemorySaverInfo, Saver, SaverInfo}
import com.crawler.logger.CrawlerLogger
import com.crawler.util.Util

import scala.collection.mutable
import scalaj.http.HttpRequest


/**
  * Created by vipmax on 10.08.16.
  **/

trait Task {

  /** Unique task id */
  val id = java.util.UUID.randomUUID.toString.substring(0, 10)

  /** task name */
  val name: String

  /** task name */
  val appname: String

  /** task type */
  val taskType = this.getClass.getSimpleName

  /** task attempt */
  var attempt = 0

  /** task proxy */
  @Transient var proxy: Option[CrawlerProxy] = Option(null)

  /** crawler balancer */
  @Transient var balancer: Option[AnyRef] = Option(null)

  /** task logger. Must be injected by crawler */
  @Transient var logger: CrawlerLogger = _

  /** task saver meta info. base in it crawler injects com.crawler.dao.Saver object */
  var saverInfo: Option[SaverInfo] = Option(MemorySaverInfo())

  /** task saver. Must be injected by crawler */
  @Transient var saver: Option[Saver]  = Option(null)

  /** task account (osn's credential). Can be injected by crawler */
  var account: Option[Account]  = Option(null)

  /** tasks parameters */
  val otherTaskParameters = mutable.HashMap[String, Any]()

  /** Method for run task. Must include network logic to different OSNs */
  def run(network: AnyRef = null)

  /** experimental, for data normalization, should contains field regexps
    * use List(".") for all fields to be presented
    * use List("id$") for "id" field to be presented
    * use List("medium.url") for "medium*url" field to be presented
    * default there is no denormalization
    * */
  var dataSchema: List[String] = List()

  /** experimental, for WF generation */
  var onResult: (Array[BasicDBObject]) => Unit = _
  var onException: (Array[BasicDBObject]) => Unit = _

  def next(tasks:Array[Task]) {
    println(s"sending ${tasks.size} tasks to balancer $balancer")

    tasks.foreach{ task =>
      balancer.get match {
        case  Some(b: ActorRef) => b ! task
        case  Some(b: ActorSelection) => b ! task
        case  _ => logger.warning("balancer not found")
      }
    }

  }
}

case class DataResponse() extends BasicDBObject

trait TaskDataResponse {
  def task: Task
  def resultData: Array[BasicDBObject]
}

case class TaskStatusResponse(task: Task, status: String, exception: Exception)

trait ResponseTask extends Task {
  var isStream = false
  var responseActor: Option[AnyRef] = _

  def response(responseActor: Option[AnyRef], response: TaskDataResponse) {
    responseActor match {
      case Some(ar: ActorRef) => ar ! response
      case Some(as: ActorSelection) => as ! response
      case None => logger.warning("responseActor not found")
    }
  }

  def response(response: TaskDataResponse) {
    this.response(this.responseActor, response)
  }
}


trait SaveTask extends Task {
  def save(datas: Any) {
    if(saver.isDefined) {
      datas match {
        case many: Iterable[BasicDBObject] if many.nonEmpty =>
          if(dataSchema.isEmpty) saver.get.saveMany(many)
          else saver.get.saveMany(many.map(m => Util.denorm(m, dataSchema)))

        case many: Array[BasicDBObject] if many.nonEmpty =>
          if(dataSchema.isEmpty) saver.get.saveMany(many)
          else saver.get.saveMany(many.map(m => Util.denorm(m, dataSchema)))

        case one: BasicDBObject if one != null =>
          if(dataSchema.isEmpty) saver.get.saveOne(one)
          else saver.get.saveOne(Util.denorm(one, dataSchema))
        case _ =>
      }
    }
  }
}


trait StateTask extends Task {
  var state = Map[String, Any]()

  def saveState(newState: Map[String, Any]): Unit = synchronized {
    state = newState
  }
}

trait FrequencyLimitedTask {
  def sleep(time: Int = 3000): Unit = {
    Thread.sleep(time)
  }
}



trait TwitterTask extends Task {
  def newRequestsCount(): Int
}

trait VkontakteTask extends Task {

  override def run(network: AnyRef = null) {

    /* using  balancer account first */
    var crawlerAccount: Account = network match {
      case acc:VkontakteAccount => acc
      case _ => null
    }

    /* can be overrided by own user account */
    if(this.account.isDefined) crawlerAccount = this.account.get

    if(crawlerAccount == null) throw NoAccountFoundException("account is null")
    if(!crawlerAccount.isInstanceOf[VkontakteAccount]) throw AccountTypeMismatchException("account is not VkontakteAccount")

    extract(crawlerAccount.asInstanceOf[VkontakteAccount])
  }

  def extract(account: VkontakteAccount)

  def exec(httpRequest: HttpRequest, account: VkontakteAccount): String = {
    logger.debug(s"exec task.id $id with access_token=${account.accessToken}")
    var httpReq = httpRequest.param("access_token", account.accessToken)

    httpReq = proxy match {
      case Some(p:CrawlerProxy) =>
        logger.debug(s"exec task.id $id with proxy=$proxy")
        httpReq.proxy(p.url, p.port.toInt, p.proxyType match {
          case "http" => java.net.Proxy.Type.HTTP
          case "socks" => java.net.Proxy.Type.SOCKS
        })
      case None => httpReq
    }

    logger.debug(s"${httpReq.url}?${httpReq.params.map { case (p, v) => s"$p=$v" }.mkString("&")}")
    val json = httpReq.timeout(60 * 1000 * 10, 60 * 1000 * 10).execute().body.toString

    if(json.contains("error_code")) logger.error(json)

    json
  }
}


trait InstagramTask extends Task

trait YoutubeTask extends Task {
  def exec(httpRequest: HttpRequest): String = {
    var httpReq = account match {
      case Some(YoutubeAccount(key)) =>
        logger.debug(s"exec task ${taskType} task.id $id with key=$key")
        httpRequest.param("key", key)
      case None => httpRequest
    }

    httpReq = proxy match {
      case p:CrawlerProxy =>
        logger.debug(s"exec task ${taskType} task.id $id with proxy=$proxy")
        httpReq.proxy(p.url, p.port.toInt, p.proxyType match {
          case "http" => java.net.Proxy.Type.HTTP
          case "socks" => java.net.Proxy.Type.SOCKS
        })
      case null => httpReq
    }

    logger.debug(s"${httpReq.url}?${httpReq.params.map { case (p, v) => s"$p=$v" }.mkString("&")}")
    val json = httpReq.timeout(60 * 1000 * 10, 60 * 1000 * 10).execute().body.toString

    if(json.contains("error")) logger.error(json)

    json
  }
}

trait LiveJournalTask extends Task
trait FacebookTask extends Task
