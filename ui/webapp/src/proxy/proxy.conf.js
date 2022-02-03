const targetServer = 'localhost:7701';

// Set secure: false if you want to proxy to a HTTPS-Server with an invalid/self-signed certificate.
// https://angular.io/guide/build#proxying-to-a-backend-server
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
