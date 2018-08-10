package model

import org.joda.time.LocalDateTime

case class Tweet(
  userId: Int,
  text: String,
  createdAt: LocalDateTime,
  serName: String,
  html: String,
  time: String
)
