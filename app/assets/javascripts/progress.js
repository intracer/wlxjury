    $(function () {
        "use strict";

        var logged = false;
        var socket = atmosphere;
        var subSocket;
        var transport = 'websocket';

        $('.progress .progress-bar').progressbar();

        // We are now ready to cut the request
        var request = {
            url: 'http://localhost:9000/progress',
            contentType: "application/json",
            logLevel: 'debug',
            transport: transport,
            trackMessageLength: true,
            reconnectInterval: 5000,
            fallbackTransport: 'long-polling'
        };

        request.onOpen = function (response) {
            transport = response.transport;
        };

        request.onTransportFailure = function (errorMsg, request) {
            if (window.EventSource) {
                request.fallbackTransport = "sse";
            }
        };

        request.onMessage = function (response) {
            var message = response.responseBody;

            $('.progress-bar').attr('data-transitiongoal', message).progressbar();

            content.append('<p><span style="color:' + color + '">' + message + '</span> &#64; ' +
                + (datetime.getHours() < 10 ? '0' + datetime.getHours() : datetime.getHours()) + ':'
                + (datetime.getMinutes() < 10 ? '0' + datetime.getMinutes() : datetime.getMinutes())
                + ': ' + message + '</p>');

        };

        request.onError = function (response) {
            logged = false;
        };

        subSocket = socket.subscribe(request);

    });
