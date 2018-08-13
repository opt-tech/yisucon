package model

import java.time.LocalDateTime

case class Tweet(
  userId: Int,
  text: String = null,
  createdAt: LocalDateTime = null,
  userName: String = null,
  html: String = null,
  time: String = null
)
