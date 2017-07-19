package com.crawler.osn.livejournal

import com.crawler.osn.common.LiveJournalTask
import com.crawler.dao.SaverInfo

/**
  * Created by max on 24.04.17.
  */
case class LiveJournalPostsTask () extends LiveJournalTask {
  /** task name */
  val name: String = ???

  /** task name */
  val appname: String = ???

  /** Method for run task. Must include network logic to different OSNs */
  override def run(network: AnyRef): Unit = ???
}
