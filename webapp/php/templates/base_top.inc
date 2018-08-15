<!DOCTYPE html>
<html>
  <head>
    <title>Optwitter</title>
    <link rel="stylesheet" href="/css/style.css" />
  </head>
  <body>
    <header class="header">
      <a class="title" href="/">Optwitter</a>
      <?php if(isset($name) && $name != ''): ?>
      <form class="logout" action="/logout" method="post">
        <button type="submit">ログアウト</button>
      </form>
      <span class="name">こんにちは <?= $name ?>さん</span>
      <?php else: ?>
      <span class="name">こんにちは ゲストさん</span>
      <?php endif; ?>
      <form class="search" action="/search" method="get">
        <input type="text" name="q" placeholder="search" />
      </form>
    </header>
    <div class="container">
