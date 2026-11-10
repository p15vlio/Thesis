config.module.rules.push({
    test: /\.wasm$/,
    type: 'asset/resource',
    generator: {
        filename: '[name][ext]'
    }
});

config.ignoreWarnings = [
    ...(config.ignoreWarnings || []),
    /Critical dependency: the request of a dependency is an expression/
];

config.devServer = config.devServer || {};
config.devServer.client = config.devServer.client || {};
config.devServer.client.overlay = {
    errors: true,
    warnings: false
};
