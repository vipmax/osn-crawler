package com.crawler.osn.twitter.tasks

import com.mongodb.BasicDBObject
import com.mongodb.util.JSON
import com.crawler.osn.common.{SaveTask, TwitterTask}
import com.crawler.dao.SaverInfo
import twitter4j.{Twitter, TwitterObjectFactory, User}

/**
  * Created by vipmax on 29.11.16.
  */
case class TwitterProfileTask(profileId: Any)(implicit app: String)
  extends TwitterTask
    with SaveTask{

  val name = s"TwitterProfileTask(profileId=$profileId)"
  val appname = app

  override def run(network: AnyRef) {
    network match {
      case twitter: Twitter => extract(twitter)
      case _ => logger.debug("No TwitterTemplate object found")
    }
  }

  def extract(twitter: Twitter) {
    val userProfile = profileId match {
      case id: String =>
        twitter.showUser(id)
      case id: Long =>
        twitter.showUser(id)
    }

    logger.debug("userProfile = " + userProfile)
    val data = getData(userProfile)

    save(data)
  }


  private def getData(userProfile: User) = {
    val json = TwitterObjectFactory.getRawJSON(userProfile)
    val basicDBObject = JSON.parse(json).asInstanceOf[BasicDBObject]
    basicDBObject.append("key", s"${basicDBObject.getString("id")}")
  }

  override def newRequestsCount() = 1
}
