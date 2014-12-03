$(document).ready(function() {
    var $window = $(window);

    // Function to handle changes to style classes based on window width
    function checkWidth() {
        if ($window.width() < 1175) {
            $('#mainContent').removeClass('span10').addClass('span9');
        }


        if ($window.width() >= 1175) {
            $('#mainContent').removeClass('span9').addClass('span10');
        }
    }

    // Execute on load
    checkWidth();

    // Bind event listener
    $(window).resize(checkWidth);
});
