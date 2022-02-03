const targetServer = 'localhost:7706';
const PROXY_CONFIG = [
  {
    context: ['/api'],
    target: 'https://' + targetServer,
    secure: false,
  },
  {
    context: ['/ws/object-changes'],
    target: 'wss://' + targetServer,
    secure: false,
    ws: true,
  },
];
module.exports = PROXY_CONFIG;
