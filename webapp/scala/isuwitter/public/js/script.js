var container = document.querySelector('.container');
var timeline = document.querySelector('.timeline');
var readmore = document.querySelector('.readmore');

readmore && readmore.addEventListener('click', function() {
  var tweets = document.querySelectorAll('.tweet');
  var until = tweets[tweets.length-1].dataset.time;
  until = encodeURIComponent(until);
  readmore.disabled = true;

  var xhr = new XMLHttpRequest();
  xhr.onreadystatechange = function(e) {
    if (xhr.readyState !== 4 || xhr.status !== 200) {
      return;
    }

    var res = xhr.responseText.trim();
    if (res) {
      timeline.innerHTML += res;
      readmore.disabled = false;
    } else {
      container.removeChild(readmore);
    }
  };

  var query = '';
  var match = location.search.match(/q=(.*?)(&|$)/);
  if(match) {
    query = decodeURIComponent(match[1]);
  }

  if (query) {
    xhr.open('GET', location.pathname + '?q=' + query + '&append=1&until=' + until, true);
  } else {
    xhr.open('GET', location.pathname + '?append=1&until=' + until, true);
  }
  xhr.setRequestHeader('Content-Type', 'text/html');
  xhr.send();
});
