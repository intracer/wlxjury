$(function () {
    $("#source").typeahead({
        ajax: {
            url: 'https://commons.wikimedia.org/w/api.php',
            timeout: 200,
            triggerLength: 1,
            method: 'get',
            preDispatch: function preDispatch(query) {
                return {
                    action: 'query',
                    list: 'prefixsearch',
                    format: 'json',
                    pssearch: query
                };
            },
            preProcess: function preProcess(data) {
                var results = data.query.prefixsearch.map(function (elem) {
                    return elem.title;
                });
                return results;
            }
        }
    });
});
