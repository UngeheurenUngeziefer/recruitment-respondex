// Shell navigation for the Respondex prototype.
// Each screen is an iframe (screens/<id>.html) inside a <section class="board" data-screen="<id>">.
// Only the active board is shown; the URL hash (#<id>) selects the screen.
(function () {
    'use strict';
    var DEFAULT_SCREEN = 'dashboard';
    var boards = Array.prototype.slice.call(document.querySelectorAll('.prototype-stage .board'));
    var buttons = Array.prototype.slice.call(document.querySelectorAll('.prototype-nav button'));
    var screenIds = boards.map(function (board) { return board.dataset.screen; });
    function indexOfScreen(id) {
        return screenIds.indexOf(id);
    }
    function activateScreen(index, updateHistory) {
        boards.forEach(function (board, i) {
            board.hidden = i !== index;
        });
        buttons.forEach(function (button, i) {
            if (i === index) {
                button.setAttribute('aria-current', 'page');
            } else {
                button.removeAttribute('aria-current');
            }
        });
        document.title = buttons[index].textContent + ' | Respondex';
        if (updateHistory && window.location.hash !== '#' + screenIds[index]) {
            window.history.pushState(null, '', '#' + screenIds[index]);
        }
    }
    buttons.forEach(function (button, i) {
        button.addEventListener('click', function () {
            activateScreen(i, true);
        });
    });
    window.addEventListener('popstate', function () {
        var index = indexOfScreen(window.location.hash.slice(1));
        if (index >= 0) {
            activateScreen(index, false);
        }
    });
    var initial = indexOfScreen(window.location.hash.slice(1));
    if (initial < 0) {
        initial = indexOfScreen(DEFAULT_SCREEN);
    }
    activateScreen(initial, false);
})();