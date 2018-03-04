package com.crawler.util

import java.io.File

import akka.actor.ActorRef
import com.mongodb.{BasicDBList, BasicDBObject}
import com.mongodb.util.JSON
import com.crawler.osn.common.{CrawlerProxy, Task, TwitterAccount, VkontakteAccount}
import com.crawler.dao._
import com.crawler.logger.{CrawlerLogger, CrawlerLoggerFactory}
import scala.collection.JavaConversions._

import scala.collection.mutable
import scala.io.Source

/**
  * Created by vipmax on 31.10.16.
  */
object Util {
  abstract class Result[+T]

  case class Stop[B](result: B) extends Result[B]

  case object Continue extends Result[Nothing]


  def ringLoop[B, T](loopable: Seq[B], start: Int)(codeblock: (B) => Result[T]): (Int, Option[T]) = {
    var i = start
    if (i >= loopable.length)
      throw new RuntimeException("Start is incorrect")
    do {
      codeblock(loopable(i)) match {
        case Stop(result: T) =>
          return (i, Some(result))
        case Continue =>
          i = if (i + 1 < loopable.length) i + 1 else 0
      }
    }
    while (i != start)

    (i, None)
  }

  def getTwitterAccounts(): Array[TwitterAccount] = {
    println("getTwitterAccounts")
    val accounts = recursiveListFiles(new File("../"))
      .filter(f => f.getAbsolutePath.contains("twitterAccounts") && f.isFile)
      .flatMap(file => {
        Source.fromFile(file)
          .getLines()
          .filter(_.length > 10)
          .grouped(4)
          .map(group => TwitterAccount(group.head.split("key=")(1),
            group(1).split("secret=")(1),
            group(2).split("token=")(1),
            group(3).split("token_secret=")(1)
          ))
      })

    accounts.foreach(println)
    accounts
  }

  def getVkAccounts(): Array[VkontakteAccount] = {
    println("getVkAccounts")
    val accounts = recursiveListFiles(new File("../"))
      .filter(f => f.getAbsolutePath.contains("vkAccounts") && f.isFile)
      .flatMap(file => {
        Source.fromFile(file)
          .getLines()
          .filter(_.length > 10)
          .filter(!_.contains("//"))
          .map(line => VkontakteAccount(line))
      })
    accounts.foreach(println)
    accounts
  }

  def getHttpProxies(): Array[CrawlerProxy] = {
    val maybeFile = recursiveListFiles(new File("../"))
      .find(_.getAbsolutePath.contains("resources/http_proxyes.txt"))
    Source.fromFile(maybeFile.get)
      .getLines()
      .map(line => {
        val Array(url, port) = line.split(":")
        CrawlerProxy("http", url, port)
      })
    .toArray
  }

  def getCurrentIp(): String = {
    import java.net.NetworkInterface
    import collection.JavaConversions._

    NetworkInterface.getNetworkInterfaces.foreach{ ee =>
      ee.getInetAddresses.foreach { i =>
        if (i.getHostAddress.startsWith("192.168"))
          return i.getHostAddress
      }
    }

    return "127.0.0.1"
  }

  def injectDependencies(logger: CrawlerLogger, balancer: ActorRef, task: Task) {
    task.logger = logger
    task.balancer =  Option(balancer)

    ConnectionManager.getSaver(task.saverInfo.get) match {
      case saver  => task.saver = Option(saver)
      case _ => logger.debug("Unknown saver")
    }
  }

  def uninjectDependencies(task: Task): Unit = {
    task.logger = null
    task.balancer = null
    task.saver = null  // TODO: check Close connections
  }

  def denorm(basicDBObject: BasicDBObject, dataSchema: List[String]) = {
    val dd = traverse(basicDBObject, dataSchema)
    new BasicDBObject(dd)
  }

  def traverse(o: Any,
               dataSchema: List[String],
               key: String = "",
               acc: mutable.Map[String,Any] = mutable.LinkedHashMap()
               ): mutable.Map[String, Any] = {
    o match {
      case b: BasicDBObject if key == "" => b.keys.map(k => traverse(b.get(k), dataSchema, k, acc))
      case b: BasicDBObject => b.keys.map(k => traverse(b.get(k),dataSchema, key + "_" + k, acc))
      case b: BasicDBList => b.mkString(",") // TODO: supported only simple values
      case b if dataSchema.exists(sc => sc.r.findAllIn(key).nonEmpty) => acc.put(key, b)
      case _ => // ignore
    }
    acc
  }

  import java.io.File
  def recursiveListFiles(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}


object Test {
  def main(args: Array[String]): Unit = {
   Util.getTwitterAccounts().foreach(println)
  }
}