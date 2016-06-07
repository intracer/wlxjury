$(function () {
    var apiUrl = 'https://commons.wikimedia.org/w/api.php';
    var source = $("#source");
    source.attr("autocomplete", "off");
    source.typeahead({
        ajax: {
            url: apiUrl,
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
                return data.query.prefixsearch.map(function (elem) {
                    return elem.title;
                });
            }
        },
        onSelect: function (item) {
            $.ajax({
                url: apiUrl + "?action=query&prop=categoryinfo&format=json&titles=" + encodeURI(item.value),
                dataType: 'jsonp',
                context: document.body,
                success: function (obj) {
                    $.each(obj.query.pages, function(key, value) {
                        var files = value.categoryinfo.files;
                        var commonscat = $("#commonscat");
                        commonscat.html(files);
                        commonscat.attr("href", "https://commons.wikimedia.org/wiki/" + encodeURI(item.value));
                    });
                }
            });
        }
    });
});
