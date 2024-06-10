const { CycloneDxWebpackPlugin } = require('@cyclonedx/webpack-plugin');

module.exports = {
  plugins: [
    new CycloneDxWebpackPlugin({
      specVersion: '1.6',
      outputLocation: '../../../../build/repots/webapp',
      reproducibleResults: true,
      validateResults: true,
    }),
  ],
};
