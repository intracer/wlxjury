'use strict';

/** chatModel service, provides chat rooms (could as well be loaded from server) */
angular.module('sseChat.services', []).service('chatModel', function () {
    var getRooms = function () {
        return [ {name: 'Jury chat/Чат журі', value: '1'},
            {name: 'Ask organizers/Питання до оргкому', value: '2'}, {name: 'Чат оргкому', value: '3'},
            {name: 'Флуд і тролінг', value: '4'} ];
    };
    return { getRooms: getRooms };
});