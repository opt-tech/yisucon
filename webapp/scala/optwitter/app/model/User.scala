package model

case class User(
  userId: Int,
  userName: String,
  salt: String,
  password: String
)