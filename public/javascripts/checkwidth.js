$(document).ready(function() {
    var $window = $(window);

    // Function to handle changes to style classes based on window width
    function checkWidth() {

        if ($window.width() < 685) {
            $('#mainContent').removeClass('col-xs-8').removeClass('col-xs-9').removeClass('col-xs-10').addClass('col-xs-7');
        }

        if ($window.width() >= 685 && $window.width() < 910) {
            $('#mainContent').removeClass('col-xs-7').removeClass('col-xs-9').removeClass('col-xs-10').addClass('col-xs-8');
        }

        if ($window.width() >= 910 && $window.width() < 1350) {
            $('#mainContent').removeClass('col-xs-7').removeClass('col-xs-8').removeClass('col-xs-10').addClass('col-xs-9');
        }

        if ($window.width() >= 1350) {
            $('#mainContent').removeClass('col-xs-7').removeClass('col-xs-8').removeClass('col-xs-9').addClass('col-xs-10');
        }
    }

    // Execute on load
    checkWidth();

    // Bind event listener
    $(window).resize(checkWidth);
});
