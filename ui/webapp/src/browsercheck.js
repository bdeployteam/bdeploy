console.log(navigator.userAgent);

var isIE = !!document.documentMode;

var isEdge = !isIE && !!window.StyleMedia;

var isSafari = window.safari !== undefined;

if (isIE || isEdge || isSafari) {
  window.location.assign('/badbrowser.html');
}
