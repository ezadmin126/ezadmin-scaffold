(function (window, document) {
  'use strict';

  if (window.EZLayout) return;

  var defaults = {
    layout: 'sidebar',
    menuUrl: '/laynavs.html',
    header: { enabled: true, height: 56 },
    footer: { enabled: false, height: 40, text: 'System' },
    sidebar: { enabled: true, width: 240, collapsedWidth: 64, collapsible: true, defaultCollapsed: false },
    menu: { expandAll: false, activeByUrl: true },
    responsive: { enabled: true, breakpoint: 768, closeOnOutsideClick: true, closeOnMenuClick: true },
    content: { padding: 10, maxWidth: null },
    theme: { primaryColor: '#206bc4', backgroundColor: '#f5f7fb', sidebarBackgroundColor: '#ffffff', headerBackgroundColor: '#ffffff', footerBackgroundColor: '#ffffff', borderColor: '#e5e7eb', textColor: '#374151', mutedColor: '#6b7280' },
    storage: { enabled: true, key: 'ez-layout' }
  };

  function isObject(value) { return value && Object.prototype.toString.call(value) === '[object Object]'; }
  function merge(base, extra) {
    var result = {}, key;
    for (key in base) {
      if (Object.prototype.hasOwnProperty.call(base, key)) result[key] = isObject(base[key]) ? merge(base[key], {}) : base[key];
    }
    for (key in extra || {}) {
      if (!Object.prototype.hasOwnProperty.call(extra, key)) continue;
      result[key] = isObject(extra[key]) && isObject(result[key]) ? merge(result[key], extra[key]) : extra[key];
    }
    return result;
  }
  function element(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }
  function safeHref(value) { return typeof value === 'string' && /^(?:\/|https?:\/\/|#)/.test(value) ? value : ''; }
  function isStaticIcon(value) { return typeof value === 'string' && /^\/static\/.*\.(?:svg|png|jpe?g|gif|webp)(?:[?#].*)?$/i.test(value); }
  var MENU_CACHE_TTL = 3 * 24 * 60 * 60 * 1000;
  function menuCacheKey(menuUrl) { return 'ez-layout:menu:20261030-legacy-menu-cleanup:' + menuUrl; }
  function validMenuResponse(response) { return !!(response && response.success === true && response.data); }
  function readMenuCache(menuUrl) {
    var key = menuCacheKey(menuUrl), cached;
    try {
      cached = window.sessionStorage.getItem(key);
      if (!cached) return null;
      cached = JSON.parse(cached);
      if (!cached || !cached.response || !validMenuResponse(cached.response) || !cached.expiresAt || cached.expiresAt <= Date.now()) {
        window.sessionStorage.removeItem(key);
        return null;
      }
      // Sliding expiration: each use extends the cached menu for another three days.
      cached.expiresAt = Date.now() + MENU_CACHE_TTL;
      window.sessionStorage.setItem(key, JSON.stringify(cached));
      return cached.response;
    } catch (error) { return null; }
  }
  function writeMenuCache(menuUrl, response) {
    if (!validMenuResponse(response)) return;
    try {
      window.sessionStorage.setItem(menuCacheKey(menuUrl), JSON.stringify({ response: response, expiresAt: Date.now() + MENU_CACHE_TTL }));
    } catch (error) {}
  }
  function loadCurrentUser(actions, onLoaded) {
    fetch('/login/currentUser.html', { cache: 'no-store', headers: { Accept: 'application/json' }, credentials: 'same-origin' }).then(function (response) {
      if (!response.ok) throw new Error('current user request failed');
      return response.json();
    }).then(function (response) {
      var userName = response && response.data && response.data.userName;
      if (response.success !== true || !userName) return;
      var menu = element('details', 'ez-layout__user-menu');
      var summary = element('summary', 'ez-layout__user-summary');
      summary.setAttribute('aria-label', '当前用户：' + userName);
      summary.appendChild(element('span', 'ez-layout__user-name', userName));
      summary.appendChild(element('span', 'ez-layout__user-caret', '⌄'));
      var dropdown = element('div', 'ez-layout__user-dropdown');
      var changePassword = element('a', '', '修改密码');
      changePassword.href = '/login/resetPassword.html';
      dropdown.appendChild(changePassword);
      var logout = element('a', '', '登出');
      logout.href = '/login/signout.html';
      dropdown.appendChild(logout);
      menu.appendChild(summary);
      menu.appendChild(dropdown);
      actions.appendChild(menu);
      if (onLoaded) onLoaded(response.data);
    }).catch(function () {});
  }
  function currentScriptConfig() {
    var scripts = document.getElementsByTagName('script'), script, index;
    for (index = scripts.length - 1; index >= 0; index--) {
      script = scripts[index];
      if (/\/ez-layout\/ez-layout\.js(?:\?|$)/.test(script.src || '')) {
        var config = {}, layout = script.getAttribute('data-layout'), menuUrl = script.getAttribute('data-menu'), padding = Number(script.getAttribute('data-content-padding'));
        if (layout) config.layout = layout;
        if (script.getAttribute('data-enabled') === 'false') config.enabled = false;
        if (menuUrl) config.menuUrl = menuUrl;
        if (padding > 0) config.content = { padding: padding };
        return config;
      }
    }
    return {};
  }
  function initResponsiveHeaderActions() {
    var media = window.matchMedia('(max-width: 1000px)');
    document.querySelectorAll('.topbar-inner').forEach(function (header) {
      if (header.hasAttribute('data-ez-header-actions-ready')) return;
      var children = Array.prototype.slice.call(header.children), spacerIndex = children.findIndex(function (node) { return node.classList.contains('spacer'); });
      if (spacerIndex < 0) return;
      var more = children.find(function (node) { return node.classList.contains('more-wrap'); });
      var controls = children.slice(spacerIndex + 1).filter(function (node) {
        if (node === more || node.hasAttribute('data-header-action-exclude') || node.classList.contains('spacer')) return false;
        if (node.matches('.back, .order-head, .top-no, .top-supplier, .copy-order-number, .copy-trader-name, .copy-status')) return false;
        return node.matches('button, a, details, span.actions, span[id="checkBtns"], span[id="checkButtons"], span[id="auditActions"]');
      });
      if (!controls.length) return;
      function isDefaultMoreAction(node) {
        return node.matches('button, a') && (node.getAttribute('data-ez-header-action-default-more') === 'true' || /打印/.test(node.textContent || '') || /print/i.test(node.getAttribute('onclick') || ''));
      }
      var hasDefaultMoreAction = controls.some(isDefaultMoreAction);
      controls.forEach(function (node) {
        if (isDefaultMoreAction(node)) node.setAttribute('data-ez-header-action-default-more', 'true');
      });
      if (more) header.appendChild(more);
      header.setAttribute('data-ez-header-actions-ready', 'true');

      var generated = false, button, menu;
      if (!more) {
        generated = true;
        more = element('div', 'more-wrap ez-responsive-more ez-responsive-more--generated');
        button = element('button', 'btn more-btn', '更多');
        button.type = 'button';
        button.setAttribute('aria-haspopup', 'menu');
        button.setAttribute('aria-expanded', 'false');
        button.appendChild(element('span', 'more-chevron', '⌄'));
        button.setAttribute('aria-label', '更多操作');
        button.addEventListener('click', function (event) {
          event.stopPropagation();
          var open = !menu.hasAttribute('hidden');
          if (open) menu.setAttribute('hidden', ''); else menu.removeAttribute('hidden');
          button.setAttribute('aria-expanded', String(!open));
        });
        menu = element('div', 'more-menu');
        menu.setAttribute('role', 'menu');
        menu.setAttribute('hidden', '');
        more.appendChild(button); more.appendChild(menu); header.appendChild(more);
        menu.addEventListener('click', function () { menu.setAttribute('hidden', ''); button.setAttribute('aria-expanded', 'false'); });
        document.addEventListener('click', function (event) {
          if (!more.contains(event.target)) { menu.setAttribute('hidden', ''); button.setAttribute('aria-expanded', 'false'); }
        });
      } else {
        menu = more.querySelector('.more-menu');
        if (!menu) return;
      }

      var copies = [];
      function clearCloneAttributes(root) {
        [root].concat(Array.prototype.slice.call(root.querySelectorAll('*'))).forEach(function (node) {
          node.removeAttribute('id');
          node.removeAttribute('data-ez-header-action-default-more');
          Array.prototype.slice.call(node.attributes || []).forEach(function (attribute) {
            if (/^on/i.test(attribute.name)) node.removeAttribute(attribute.name);
          });
          if (node.matches('a')) node.removeAttribute('href');
          if (node.matches('button')) { node.type = 'button'; node.removeAttribute('form'); }
        });
      }
      function buildCopy(source, oldState) {
        var copyTree = source.cloneNode(true);
        clearCloneAttributes(copyTree);
        var isGroup = source.matches('span, div');
        var copiesForMenu = isGroup
          ? Array.prototype.filter.call(copyTree.childNodes, function (node) { return node.nodeType === 1; })
          : [copyTree];
        copiesForMenu.forEach(function (copy) { copy.classList.add('ez-header-action-copy'); });
        var sourceControls = (source.matches('button, a') ? [source] : []).concat(Array.prototype.slice.call(source.querySelectorAll('button, a')));
        var copyControls = [];
        copiesForMenu.forEach(function (copy) {
          if (copy.matches('button, a')) copyControls.push(copy);
          copyControls = copyControls.concat(Array.prototype.slice.call(copy.querySelectorAll('button, a')));
        });
        copyControls.forEach(function (copyControl, index) {
          copyControl.classList.add('ez-header-action-copy-control');
          copyControl.addEventListener('click', function (event) {
            event.preventDefault();
            var original = sourceControls[index];
            if (!original || original.disabled) return;
            original.click();
            if (more.tagName === 'DETAILS') more.open = false;
            else if (menu) menu.setAttribute('hidden', '');
          });
        });
        var anchor = oldState && oldState.anchor && oldState.anchor.parentNode === menu ? oldState.anchor : null;
        if (anchor) {
          copiesForMenu.forEach(function (copy) { menu.insertBefore(copy, anchor); });
          oldState.nodes.forEach(function (oldCopy) { if (oldCopy.parentNode === menu) menu.removeChild(oldCopy); });
        } else {
          var oldFirstCopy = oldState && oldState.nodes && oldState.nodes.length && oldState.nodes[0].parentNode === menu
            ? oldState.nodes[0]
            : null;
          if (oldFirstCopy) {
            copiesForMenu.forEach(function (copy) { menu.insertBefore(copy, oldFirstCopy); });
            oldState.nodes.forEach(function (oldCopy) { if (oldCopy.parentNode === menu) menu.removeChild(oldCopy); });
          } else copiesForMenu.forEach(function (copy) { menu.appendChild(copy); });
        }
        if (isGroup && !anchor) { anchor = document.createComment('responsive header action position'); menu.appendChild(anchor); }
        return { nodes: copiesForMenu, anchor: anchor };
      }
      controls.forEach(function (source, index) {
        source.setAttribute('data-ez-header-action-source', String(index));
        copies[index] = buildCopy(source);
        if (window.MutationObserver) {
          var observer = new MutationObserver(function () { copies[index] = buildCopy(source, copies[index]); });
          observer.observe(source, { childList: true, subtree: true, attributes: true, characterData: true });
        }
      });
      function update() {
        var collapsed = media.matches;
        header.classList.toggle('ez-header-actions-collapsed', collapsed);
        more.classList.toggle('ez-responsive-more--generated', generated);
        more.classList.toggle('ez-responsive-more--active', collapsed || hasDefaultMoreAction);
      }
      update();
      if (media.addEventListener) media.addEventListener('change', update);
      else if (media.addListener) media.addListener(update);
    });
  }
  function normalizeItem(item) {
    item = item || {};
    return {
      id: String(item.id || item.title || ''),
      title: String(item.title || ''),
      url: safeHref(item.href || item.url || ''),
      icon: String(item.icon || ''),
      target: item.target === '_blank' ? '_blank' : '_self',
      children: Array.isArray(item.child) ? item.child.map(normalizeItem) : (Array.isArray(item.children) ? item.children.map(normalizeItem) : [])
    };
  }
  function implicitActive(item) {
    return item.title === '销售订单' && /^\/saleorder(?:\/|$)/.test(window.location.pathname);
  }
  function createMenu(items, config, onNavigate, isTopLayout) {
    var list = element('ul', 'ez-layout__menu-list');
    (items || []).forEach(function (item) {
      if (!item.title) return;
      var hasChildren = item.children.length > 0;
      var li = element('li', 'ez-layout__menu-item' + (hasChildren ? ' ez-layout__menu-item--has-children' : ''));
      var isActive = (config.menu.activeByUrl && item.url && item.url === window.location.pathname) || implicitActive(item);
      var childResult;
      if (hasChildren) {
        childResult = createMenu(item.children, config, onNavigate, isTopLayout);
        if (childResult.active) isActive = true;
      }
      if (isActive) li.classList.add('ez-layout__menu-item--active');
      if (hasChildren && (config.menu.expandAll || isActive)) li.classList.add('ez-layout__menu-item--expanded');
      var link = element(hasChildren ? 'button' : 'a', 'ez-layout__menu-link');
      if (hasChildren) link.type = 'button';
      else if (item.url) { link.href = item.url; link.target = item.target; }
      else link.setAttribute('aria-disabled', 'true');
      link.setAttribute('data-menu-id', item.id);
      link.setAttribute('aria-label', item.title);
      link.setAttribute('title', item.title);
      var icon;
      if (isStaticIcon(item.icon)) {
        icon = element('img', 'ez-layout__menu-icon ez-layout__menu-icon--image');
        icon.src = item.icon;
        icon.alt = '';
        icon.setAttribute('aria-hidden', 'true');
      } else {
        icon = element('span', 'ez-layout__menu-icon', item.icon.indexOf('cart') >= 0 ? '▣' : item.icon.indexOf('user') >= 0 ? '●' : item.icon.indexOf('rmb') >= 0 ? '¥' : item.icon.indexOf('app') >= 0 ? '▦' : item.icon.indexOf('set') >= 0 ? '⚙' : '•');
      }
      var title = element('span', 'ez-layout__menu-title', item.title);
      link.appendChild(icon); link.appendChild(title);
      if (hasChildren) {
        link.appendChild(element('span', 'ez-layout__menu-caret', '›'));
        link.setAttribute('aria-expanded', String(li.classList.contains('ez-layout__menu-item--expanded')));
      }
      link.addEventListener('click', function () {
        if (hasChildren) {
          if (isTopLayout()) {
            Array.prototype.forEach.call(li.parentElement.children, function (sibling) {
              if (sibling !== li) {
                sibling.classList.remove('ez-layout__menu-item--expanded');
                if (sibling.firstElementChild && sibling.firstElementChild.tagName === 'BUTTON') sibling.firstElementChild.setAttribute('aria-expanded', 'false');
              }
            });
          }
          li.classList.toggle('ez-layout__menu-item--expanded');
          link.setAttribute('aria-expanded', String(li.classList.contains('ez-layout__menu-item--expanded')));
        } else onNavigate();
      });
      li.appendChild(link);
      if (hasChildren) li.appendChild(childResult.list);
      list.appendChild(li);
    });
    return { list: list, active: !!list.querySelector('.ez-layout__menu-item--active') };
  }
  function storageGet(key) { try { return window.localStorage.getItem(key); } catch (error) { return null; } }
  function storageSet(key, value) { try { window.localStorage.setItem(key, value); } catch (error) {} }
  function applyTheme(root, config) {
    var theme = config.theme, sidebar = config.sidebar, header = config.header, footer = config.footer, content = config.content;
    root.style.setProperty('--ez-primary', theme.primaryColor);
    root.style.setProperty('--ez-background', theme.backgroundColor);
    root.style.setProperty('--ez-sidebar-background', theme.sidebarBackgroundColor);
    root.style.setProperty('--ez-header-background', theme.headerBackgroundColor);
    root.style.setProperty('--ez-footer-background', theme.footerBackgroundColor);
    root.style.setProperty('--ez-border', theme.borderColor);
    root.style.setProperty('--ez-text', theme.textColor);
    root.style.setProperty('--ez-muted', theme.mutedColor);
    root.style.setProperty('--ez-sidebar-width', sidebar.width + 'px');
    root.style.setProperty('--ez-sidebar-collapsed-width', sidebar.collapsedWidth + 'px');
    root.style.setProperty('--ez-header-height', header.height + 'px');
    root.style.setProperty('--ez-footer-height', footer.height + 'px');
    root.style.setProperty('--ez-content-padding', content.padding + 'px');
    if (content.maxWidth) root.style.setProperty('--ez-content-max-width', content.maxWidth + 'px');
  }
  function hideInitialLoader() {
    document.querySelectorAll('.layuimini-loader').forEach(function (loader) { loader.remove(); });
  }
  function init(input) {
    // Embedded forms and lists share their parent's navigation.
    if (window.self !== window.top || (input && input.enabled === false)) return null;
    initResponsiveHeaderActions();
    if (document.querySelector('.ez-layout')) {
      hideInitialLoader();
      return window.EZLayout.instance;
    }
    var config = merge(defaults, input || {}), body = document.body, nodes = Array.prototype.slice.call(body.childNodes), root = element('div', 'ez-layout'), header = element('header', 'ez-layout__header'), headerInner = element('div', 'ez-layout__header-inner'), sidebar = element('aside', 'ez-layout__sidebar'), sidebarInner = element('div', 'ez-layout__sidebar-inner'), sidebarHeader = element('div', 'ez-layout__sidebar-header', '导航菜单'), nav = element('nav', 'ez-layout__menu'), overlay = element('div', 'ez-layout__overlay'), main = element('main', 'ez-layout__main'), content = element('div', 'ez-layout__content'), footer = element('footer', 'ez-layout__footer', config.footer.text), isMobile = function () { return window.innerWidth <= config.responsive.breakpoint; };
    if (['sidebar', 'top', 'none'].indexOf(config.layout) === -1) config.layout = 'sidebar';
    var layoutStateKey = config.storage.key + ':layout';
    var storedLayout = config.storage.enabled ? storageGet(layoutStateKey) : null;
    var preferredLayout = storedLayout === 'top' || storedLayout === 'sidebar' ? storedLayout : config.layout;
    var activeLayout = preferredLayout, responsiveFallback = false;
    config.layout = preferredLayout;
    root.setAttribute('data-layout', config.layout);
    body.classList.add('ez-layout-page');
    applyTheme(root, config);
    var toggler = element('button', 'ez-layout__toggler', '☰'); toggler.type = 'button'; toggler.setAttribute('aria-label', '切换菜单');
    var brand = element('div', 'ez-layout__sidebar-brand'), brandLink = element('a'), brandImage = document.createElement('img'), brandTitle = element('span', 'ez-layout__brand-title');
    brandImage.hidden = true; brandLink.appendChild(brandImage); brandLink.appendChild(brandTitle); brand.appendChild(brandLink);
    var headerContent = element('div', 'ez-layout__header-content', document.title || '');
    var actions = element('div', 'ez-layout__header-actions');
    headerInner.appendChild(toggler); headerInner.appendChild(headerContent); headerInner.appendChild(actions); header.appendChild(headerInner);
    sidebarInner.appendChild(brand); sidebarInner.appendChild(sidebarHeader); sidebarInner.appendChild(nav); sidebar.appendChild(sidebarInner);
    main.appendChild(content);
    root.appendChild(header); root.appendChild(sidebar); root.appendChild(overlay); root.appendChild(main); root.appendChild(footer);
    nodes.forEach(function (node) { content.appendChild(node); });
    body.appendChild(root);
    if (!config.header.enabled) { header.hidden = true; root.style.setProperty('--ez-header-height', '0px'); }
    if (!config.footer.enabled) { footer.hidden = true; root.classList.add('ez-layout--footer-hidden'); }
    if (!config.sidebar.enabled) sidebar.hidden = true;
    if (config.layout === 'none') {
      root.classList.add('ez-layout--none');
      header.hidden = true;
      footer.hidden = true;
      toggler.hidden = true;
      root.style.setProperty('--ez-header-height', '0px');
    }
    var stateKey = config.storage.key + ':collapsed';
    var storedCollapsed = config.storage.enabled ? storageGet(stateKey) : null;
    var collapsed = storedCollapsed === null ? config.sidebar.defaultCollapsed : storedCollapsed === 'true';
    var temporaryExpanded = false, temporaryTrigger = null;
    function renderSidebarState() {
      var compact = activeLayout === 'sidebar' && collapsed && !isMobile();
      root.classList.toggle('ez-layout--collapsed', compact);
      root.classList.toggle('ez-layout--temporary-expanded', compact && temporaryExpanded);
      sidebar.inert = activeLayout === 'sidebar' && isMobile() && !root.classList.contains('ez-layout--mobile-open');
      toggler.setAttribute('aria-expanded', String(isMobile() ? root.classList.contains('ez-layout--mobile-open') : !compact || temporaryExpanded));
      nav.querySelectorAll('button.ez-layout__menu-link').forEach(function (link) {
        link.setAttribute('aria-expanded', String((!compact || temporaryExpanded) && link.parentElement.classList.contains('ez-layout__menu-item--expanded')));
      });
    }
    function closeTemporarySidebar(restoreFocus) {
      var trigger = temporaryTrigger;
      temporaryExpanded = false; temporaryTrigger = null;
      renderSidebarState();
      if (restoreFocus && trigger && trigger.isConnected) trigger.focus();
    }
    function setCollapsed(value) {
      collapsed = value;
      closeTemporarySidebar(false);
      if (config.storage.enabled) storageSet(stateKey, String(value));
    }
    nav.addEventListener('click', function (event) {
      if (activeLayout !== 'sidebar' || isMobile() || !collapsed || temporaryExpanded) return;
      var link = event.target.closest && event.target.closest('button.ez-layout__menu-link');
      if (!link || !nav.contains(link)) return;
      // Open an already-active branch instead of letting its normal click handler close it.
      event.preventDefault(); event.stopPropagation();
      temporaryExpanded = true; temporaryTrigger = link;
      link.parentElement.classList.add('ez-layout__menu-item--expanded');
      renderSidebarState();
    }, true);
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && temporaryExpanded) {
        event.preventDefault(); event.stopPropagation(); closeTemporarySidebar(true);
      } else if (event.key === 'Escape' && isMobile() && root.classList.contains('ez-layout--mobile-open')) {
        event.preventDefault(); closeMobile(); toggler.focus();
      }
    });
    document.addEventListener('focusin', function (event) {
      if (temporaryExpanded && !sidebar.contains(event.target)) closeTemporarySidebar(false);
    });
    setCollapsed(collapsed);
    var layoutToggle = element('button', 'ez-layout__layout-toggle');
    layoutToggle.type = 'button';
    actions.appendChild(layoutToggle);
    function closeMobile() { root.classList.remove('ez-layout--mobile-open'); renderSidebarState(); }
    function clearTopMenus() { nav.querySelectorAll('.ez-layout__menu-item--expanded').forEach(function (item) { item.classList.remove('ez-layout__menu-item--expanded'); if (item.firstElementChild) item.firstElementChild.setAttribute('aria-expanded', 'false'); }); }
    function setActiveLayout(layout, fallback) {
      closeTemporarySidebar(false);
      activeLayout = layout;
      responsiveFallback = !!fallback;
      config.layout = layout;
      root.setAttribute('data-layout', layout);
      root.classList.toggle('ez-layout--top', layout === 'top');
      root.classList.toggle('ez-layout--responsive-sidebar', fallback);
      if (layout === 'none') {
        sidebar.hidden = true;
        layoutToggle.hidden = true;
        toggler.hidden = true;
        renderSidebarState();
        return;
      }
      if (layout === 'top') {
        sidebar.hidden = true;
        headerInner.insertBefore(brand, headerContent);
        headerContent.classList.add('ez-layout__top-menu');
        headerContent.replaceChildren(nav);
        toggler.hidden = true;
      } else {
        sidebar.hidden = !config.sidebar.enabled;
        sidebarInner.insertBefore(brand, sidebarHeader);
        headerContent.classList.remove('ez-layout__top-menu');
        headerContent.replaceChildren(document.createTextNode(document.title || ''));
        sidebarInner.appendChild(nav);
        toggler.hidden = false;
        clearTopMenus();
        closeMobile();
      }
      renderSidebarState();
      layoutToggle.textContent = preferredLayout === 'top' ? '侧栏布局' : '顶部布局';
      layoutToggle.setAttribute('aria-label', preferredLayout === 'top' ? '切换到侧栏布局' : '切换到顶部布局');
      layoutToggle.setAttribute('title', preferredLayout === 'top' ? '切换到侧栏布局' : '切换到顶部布局');
    }
    function updateResponsiveLayout() {
      if (!config.responsive.enabled || preferredLayout !== 'top') return;
      if (isMobile()) { setActiveLayout('sidebar', true); return; }
      if (activeLayout === 'sidebar' && !responsiveFallback) return;
      if (activeLayout === 'sidebar') setActiveLayout('top', false);
      var requiredWidth = brand.offsetWidth + nav.scrollWidth + actions.scrollWidth + 48;
      if (requiredWidth > headerInner.clientWidth) setActiveLayout('sidebar', true);
    }
    layoutToggle.addEventListener('click', function () {
      preferredLayout = preferredLayout === 'top' ? 'sidebar' : 'top';
      if (config.storage.enabled) storageSet(layoutStateKey, preferredLayout);
      setActiveLayout(preferredLayout, false);
      updateResponsiveLayout();
    });
    toggler.addEventListener('click', function () { if (activeLayout !== 'sidebar') return; if (isMobile()) { root.classList.toggle('ez-layout--mobile-open'); renderSidebarState(); } else if (config.sidebar.collapsible) setCollapsed(!collapsed); });
    overlay.addEventListener('click', function () { if (config.responsive.closeOnOutsideClick) closeMobile(); });
    window.addEventListener('resize', function () { closeTemporarySidebar(false); if (!isMobile()) closeMobile(); renderSidebarState(); updateResponsiveLayout(); });
    document.addEventListener('click', function (event) {
      if (temporaryExpanded && !sidebar.contains(event.target)) closeTemporarySidebar(false);
      if (activeLayout === 'top' && !event.target.closest('.ez-layout__top-menu .ez-layout__menu-item')) clearTopMenus();
      var userMenu = root.querySelector('.ez-layout__user-menu');
      if (userMenu && userMenu.open && !userMenu.contains(event.target)) userMenu.open = false;
    });
    setActiveLayout(activeLayout, false);
    updateResponsiveLayout();
    if (config.layout === 'none') {
      window.EZLayout.instance = { root: root, config: config, destroy: function () { closeTemporarySidebar(false); closeMobile(); } };
      hideInitialLoader();
      return window.EZLayout.instance;
    }
    var currentSystemName = '';
    loadCurrentUser(actions, function (user) {
      currentSystemName = typeof user.systemName === 'string' ? user.systemName.trim() : '';
      if (currentSystemName) {
        brandTitle.textContent = currentSystemName;
        if (config.footer.text === '海解货票款') footer.textContent = currentSystemName;
      }
      updateResponsiveLayout();
    });
    var cachedMenu = readMenuCache(config.menuUrl);
    var menuRequest = cachedMenu ? Promise.resolve(cachedMenu) : fetch(config.menuUrl, { headers: { Accept: 'application/json' }, credentials: 'same-origin' }).then(function (response) {
      if (!response.ok) throw new Error('menu request failed');
      return response.json();
    }).then(function (response) {
      if (!validMenuResponse(response)) throw new Error('menu response invalid');
      writeMenuCache(config.menuUrl, response);
      return response;
    });
    menuRequest.then(function (response) {
      if (!validMenuResponse(response)) throw new Error('menu response invalid');
      var data = response.data, logo = data.logoInfo || {}, home = data.homeInfo || {}, items = [];
      if (home.title) items.push(normalizeItem(home));
      (data.menuInfo || []).forEach(function (item) { items.push(normalizeItem(item)); });
      brandLink.href = safeHref(logo.href) || '/'; brandTitle.textContent = currentSystemName || logo.title || '海解货票款';
      if (config.footer.text === '海解货票款') footer.textContent = brandTitle.textContent;
      if (logo.image) { brandImage.src = logo.image; brandImage.hidden = false; }
      var menu = createMenu(items, config, function () { closeTemporarySidebar(false); if (config.responsive.closeOnMenuClick && isMobile()) closeMobile(); }, function () { return activeLayout === 'top'; });
      nav.replaceChildren(menu.list);
      renderSidebarState();
      updateResponsiveLayout();
      hideInitialLoader();
    }).catch(function () {
      nav.replaceChildren(element('div', 'ez-layout__menu-error', '菜单加载失败，当前页面仍可正常使用。'));
      hideInitialLoader();
    });
    window.EZLayout.instance = { root: root, config: config, destroy: function () { closeTemporarySidebar(false); closeMobile(); } };
    return window.EZLayout.instance;
  }
  window.EZLayout = { init: init, defaults: defaults, instance: null };
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { init(currentScriptConfig()); });
  } else {
    init(currentScriptConfig());
  }
})(window, document);
