$(function () {
    "use strict";

    var logged = false;
    var socket = atmosphere;
    var subSocket;
    var transport = 'websocket';

    $('.progress .progress-bar').progressbar();
    var debug = $('#progress-debug');

    // We are now ready to cut the request
    var request = {
        url: 'http://localhost:9000/progress/' + contestId,
        contentType: "application/json",
        logLevel: 'debug',
        transport: transport,
        trackMessageLength: true,
        reconnectInterval: 5000,
        fallbackTransport: 'long-polling'
    };

    request.onOpen = function (response) {
        transport = response.transport;
        debug.html($('<p>', {text: 'Connected using ' + response.transport}));
    };

    request.onReopen = function (response) {
        debug.html($('<p>', {text: 'Re-connected using ' + response.transport}));
    };


    request.onTransportFailure = function (errorMsg, request) {
        if (window.EventSource) {
            request.fallbackTransport = "sse";
        }
        debug.html($('<h3>', {text: 'Atmosphere Chat. Default transport is WebSocket, fallback is ' + request.fallbackTransport}));
    };

    request.onMessage = function (response) {
        var message = response.responseBody;

        $('.progress-bar').attr('data-transitiongoal', message).progressbar();

        var importButton = $("#import-btn");
        if (message > 0 && message < 100 && importButton.is(":visible")) {
            importButton.hide();
        }

        // if (message < 0) {
        //     location.reload();
        // }

        debug.html('<p>' + message + '</p>');
    };

    request.onError = function (response) {
        logged = false;
        debug.html($('<p>', {
            text: 'Sorry, but there\'s some problem with your '
            + 'socket or the server is down'
        }));
    };

    request.onClose = function (response) {
        debug.html($('<p>', {text: 'Client closed the connection after a timeout'}));
    };

    request.onReconnect = function (request, response) {
        debug.html($('<p>', {text: 'Connection lost, trying to reconnect. Trying to reconnect ' + request.reconnectInterval}));
    };

    subSocket = socket.subscribe(request);

});
