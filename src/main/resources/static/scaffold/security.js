(() => {
    const csrfHeader = 'X-XSRF-TOKEN';
    const deletePath = /^\/(p|ezadmin|topezadmin)\/form\/delete-/;
    const reads = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
    const originalFetch = window.fetch.bind(window);

    function csrfToken() {
        const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
        return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : '';
    }

    window.fetch = (input, options) => {
        const url = new URL(input instanceof Request ? input.url : input, window.location.href);
        let method = (options?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
        if (url.origin !== window.location.origin) return originalFetch(input, options);
        if (method === 'GET' && deletePath.test(url.pathname)) method = 'POST';
        if (reads.has(method)) return originalFetch(input, options);

        const headers = new Headers(options?.headers || (input instanceof Request ? input.headers : undefined));
        headers.set(csrfHeader, csrfToken());
        return originalFetch(input, { ...options, method, headers });
    };

    if (window.jQuery) {
        window.jQuery.ajaxPrefilter((options, _original, xhr) => {
            const url = new URL(options.url, window.location.href);
            if (url.origin !== window.location.origin) return;
            if (options.type === 'GET' && deletePath.test(url.pathname)) options.type = 'POST';
            if (!reads.has(options.type)) xhr.setRequestHeader(csrfHeader, csrfToken());
        });
    }
})();
