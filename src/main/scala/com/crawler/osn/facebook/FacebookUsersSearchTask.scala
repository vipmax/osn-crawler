package com.crawler.osn.facebook

import com.crawler.osn.common.FacebookTask
import com.crawler.dao.SaverInfo

/**
  * Created by max on 24.04.17.
  */
case class FacebookUsersSearchTask() extends FacebookTask {
  /** task name */
  val name: String = ???

  /** task name */
  val appname: String = ???

  /** Method for run task. Must include network logic to different OSNs */
  override def run(network: AnyRef): Unit = ???
}
