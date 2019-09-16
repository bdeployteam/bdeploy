
var isIE = /*@cc_on!@*/false || !!document.documentMode;

var isEdge = !isIE && !!window.StyleMedia;

if (isIE || isEdge) {
  window.location('/badbrowser.html');
}

