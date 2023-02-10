console.log(navigator.userAgent);

const isIE = !!document.documentMode;
const isEdge = !isIE && !!window.StyleMedia;
const isSafari = window.safari !== undefined;

if (isIE || isEdge || isSafari) {
  window.location.assign('/badbrowser.html');
}
