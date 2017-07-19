package com.crawler.osn.vkontakte.tasks

import akka.actor.ActorRef
import com.mongodb.util.JSON
import com.mongodb.{BasicDBList, BasicDBObject}
import com.crawler.osn.common._
import com.crawler.dao.{MemorySaverInfo, SaverInfo}
import org.joda.time.DateTime

import scalaj.http.{Http, HttpRequest}

/**
  * Created by vipmax on 31.10.16.
  */

case class VkSearchPostsTaskDataResponse(task: VkSearchPostsTask, resultData: Array[BasicDBObject], params: Map[String, String])  extends TaskDataResponse

case class VkSearchPostsTask(query: String,
                             startTime: Long = 0,
                             endTime: Long = 0
                            )(implicit app: String)
  extends VkontakteTask
    with SaveTask
    with ResponseTask
    with StateTask {

  val name = s"VkSearchPostsTask(query=$query)"
  val appname = app


  private def buildRequest() = {
    var httpRequest = Http("https://api.vk.com/method/newsfeed.search")
      .param("q", query.toString)
      .param("count", otherTaskParameters.getOrElse("count", "20").toString)
      .param("v", otherTaskParameters.getOrElse("start_time", "5.13").toString)

    httpRequest = if(startTime != 0) httpRequest.param("start_time", startTime.toString) else httpRequest
    httpRequest = if(endTime != 0) httpRequest .param("end_time", endTime.toString) else httpRequest
    httpRequest
  }


  override def extract(account: VkontakteAccount) = {
    var end = false
    var startFrom = ""
    var allcount = 0
    val httpRequest = buildRequest()

    while(!end) {
      val json = exec(httpRequest.param("start_from", startFrom), account)
      val (posts, nextFrom, totalCount) = parse(json,httpRequest)
      startFrom = nextFrom
      allcount += posts.length

      if(allcount >= otherTaskParameters.getOrElse("allcount", 20).toString.toLong) {
        logger.debug(s"allcount achieved for task=$id")
        end = true
      }

      logger.debug(s"Found ${posts.length} posts, totalCount=$totalCount with startfrom=$startFrom nextFrom=$nextFrom for $id")

      if (nextFrom == "" ) end = true

      save(posts)
      response(VkSearchPostsTaskDataResponse(this.copy(), posts, httpRequest.params.toMap))
      if( onResult != null) onResult(posts)
    }
  }

  private def parse(json: String, httpRequest: HttpRequest) = {
    val bObject = JSON.parse(json).asInstanceOf[BasicDBObject]
      .get("response").asInstanceOf[BasicDBObject]

    val totalCount = bObject.getInt("total_count")
    val nextFrom = bObject.getString("next_from")

    val posts = bObject
      .get("items").asInstanceOf[BasicDBList].toArray
      .map { case b: BasicDBObject => b
        .append("key", s"${b.getString("from_id")}_${b.getString("id")}")
        .append("search_query_params", httpRequest.params.mkString(", "))
        .append("totalCount", totalCount)
        .append("network", "vkontakte")
      }

    (posts, nextFrom, totalCount)
  }

  private def timeIsEnd(posts: Array[BasicDBObject]): Boolean = {
    val lastPostDate = posts.last.get("date").asInstanceOf[Int]
    startTime <= lastPostDate && lastPostDate <= endTime
  }
}