package com.crawler.osn.vkontakte.tasks

import com.crawler.dao.MemorySaver
import com.crawler.logger.CrawlerLoggerFactory
import com.crawler.util.Util

object TestSearchPosts {
  def main(args: Array[String]) {
    val task = VkSearchPostsTask(query = "vkfest")("test")
    task.otherTaskParameters += ("count" -> "20", "allcount" -> "20")
    task.saver = Option(MemorySaver())
    task.logger = CrawlerLoggerFactory.logger("tests","VkSearchPostsTask")
    task.account = Util.getVkAccounts().headOption

    task.run()
    task.saver.asInstanceOf[MemorySaver].savedData.foreach(println)
  }
}
