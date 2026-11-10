config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
const path = require('path');
config.devServer.static = config.devServer.static || [];
config.devServer.static.push(path.resolve(__dirname, 'kotlin'));
