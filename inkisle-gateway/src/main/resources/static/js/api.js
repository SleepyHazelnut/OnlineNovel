/**
 * 前端公共脚本。页面就是原始 HTML，没框架也不打包，三端共用这一份
 */

// 三端的登录态分开存，否则作者端登录会使读者端失效
const ROLES = {
    reader: { token: 'inkisle_token', user: 'inkisle_user', login: 'login.html' },
    author: { token: 'inkisle_author_token', user: 'inkisle_author', login: 'author-login.html' },
    admin: { token: 'inkisle_admin_token', user: 'inkisle_admin', login: 'admin-login.html' }
};

// 页面没写 data-role 就当读者端
function currentRole() {
    return (document.body && document.body.dataset.role) || 'reader';
}

/* ---------------- token ---------------- */
const Auth = {
    role() { return currentRole(); },
    token() { return localStorage.getItem(ROLES[currentRole()].token); },
    user() {
        try { return JSON.parse(localStorage.getItem(ROLES[currentRole()].user) || 'null'); }
        catch (e) { return null; }
    },
    save(token, user) {
        const r = ROLES[currentRole()];
        localStorage.setItem(r.token, token);
        localStorage.setItem(r.user, JSON.stringify(user || null));
    },
    clear() {
        const r = ROLES[currentRole()];
        localStorage.removeItem(r.token);
        localStorage.removeItem(r.user);
    },
    logged() { return !!this.token(); },
    /** 未登录跳对应端的登录页，带上 redirect 好回来 */
    require(redirect) {
        if (!this.logged()) {
            location.href = ROLES[currentRole()].login + '?redirect=' + encodeURIComponent(redirect || location.pathname);
            return false;
        }
        return true;
    },
    logout() {
        this.clear();
        location.href = ROLES[currentRole()].login;
    }
};

/* ---------------- 请求 ---------------- */
const BASE = '';

/**
 * 统一请求口，带 token、拼请求体、拆掉外层拿 data，页面里不用每次判断 code
 */
async function request(path, options) {
    options = options || {};
    const role = options.role || currentRole();
    const headers = {};
    const token = localStorage.getItem(ROLES[role].token);
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }

    const opt = { method: options.method || 'GET', headers: headers };
    if (options.body !== undefined) {
        if (options.body instanceof FormData) {
            opt.body = options.body;
        } else {
            headers['Content-Type'] = 'application/json';
            opt.body = JSON.stringify(options.body);
        }
    }

    const resp = await fetch(BASE + path, opt);

    // 401 按当前页面所属端跳，不能统一跳读者登录页
    if (resp.status === 401) {
        Auth.clear();
        const back = encodeURIComponent(location.pathname + location.search);
        location.href = ROLES[role].login + '?redirect=' + back;
        throw new Error('请先登录');
    }

    let data;
    try {
        data = await resp.json();
    } catch (e) {
        // 500 或者网关拦截时可能返回 HTML，解析会失败
        throw new Error('服务返回异常（HTTP ' + resp.status + '）');
    }

    // 后端统一返回 {code, msg, data}，code 非 200 即视为失败并向上抛出，
    // 页面统一 catch 弹 toast，无需每个接口单独判断
    if (data.code !== 200) {
        throw new Error(data.msg || '操作失败');
    }
    return data.data;
}

// GET，值为 null 或空串的参数直接丢掉，别拼出 ?a=&b= 这种
function get(path, params) {
    if (params) {
        const qs = Object.keys(params)
            .filter(k => params[k] !== null && params[k] !== undefined && params[k] !== '')
            .map(k => encodeURIComponent(k) + '=' + encodeURIComponent(params[k]))
            .join('&');
        if (qs) path += (path.indexOf('?') >= 0 ? '&' : '?') + qs;
    }
    return request(path, { method: 'GET' });
}

function post(path, body) {
    return request(path, { method: 'POST', body: body === undefined ? {} : body });
}

function put(path, body) {
    return request(path, { method: 'PUT', body: body === undefined ? {} : body });
}

function del(path) {
    return request(path, { method: 'DELETE' });
}

/* ---------------- 小工具 ---------------- */
function $(sel) { return document.querySelector(sel); }
function $all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

// 后端给的是 2025-09-01T10:20:30 这种带 T 的串，页面上截到分钟
function fmtTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
}

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** 从地址栏取参数，比如详情页的 ?id=3 */
function param(name) {
    return new URLSearchParams(location.search).get(name);
}

/**
 * 全站统一的轻提示，接口报错与操作成功均走此处
 * 首次调用时页面尚无 #toast 节点，就地创建一个挂到 body，之后复用同一个节点
 * 每次先清除上一个定时器，否则连续弹出两条时，前一条的定时器会提前关闭后一条
 */
let toastTimer = null;
function toast(msg) {
    let el = document.getElementById('toast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'toast';
        document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
}

/** 书籍连载状态：0 连载 1 完结 */
function bookStatusTag(status) {
    return Number(status) === 1
        ? '<span class="tag tag-blue">已完结</span>'
        : '<span class="tag tag-green">连载中</span>';
}

/** 审核状态：0 待审 1 通过 2 驳回 */
function auditStatusTag(status) {
    const map = { 0: ['tag-orange', '待审核'], 1: ['tag-green', '已通过'], 2: ['tag-red', '已驳回'] };
    const it = map[Number(status)] || ['tag-gray', '未提交'];
    return '<span class="tag ' + it[0] + '">' + it[1] + '</span>';
}

/* 作品上架状态 0-4，与上面审核记录的 0-2 不是一套，别混用 */
function bookAuditTag(status) {
    const map = {
        0: ['tag-gray', '未提交'],
        1: ['tag-orange', '待审核'],
        2: ['tag-green', '已上架'],
        3: ['tag-red', '已驳回'],
        4: ['tag-blue', '已下架']
    };
    const it = map[Number(status)] || ['tag-gray', '未提交'];
    return '<span class="tag ' + it[0] + '">' + it[1] + '</span>';
}

// 有封面图就把图铺成背景；没图就不设背景，让封面块上显示书名第一个字（配合下面的 coverText）
function coverStyle(url, name) {
    if (url) {
        return 'background-image:url(' + encodeURI(url) + ')';
    }
    return '';
}

// 与 coverStyle 配套：有图返回空串，无图时返回书名的第一个字
function coverText(url, name) {
    return url ? '' : escapeHtml((name || '书').slice(0, 1));
}

// 大数字缩写，一万以上显示成几万，卡片不会溢出
function fmtCount(n) {
    const v = Number(n) || 0;
    if (v >= 10000) {
        return (v / 10000).toFixed(1).replace(/\.0$/, '') + ' 万';
    }
    return String(v);
}

/* ---------------- 导航 ---------------- */
/**
 * role 三选一，items 是 [{key, text, href}]，active 匹配 key
 */
function renderNav(active, items) {
    const el = document.getElementById('nav');
    if (!el) return;

    const role = currentRole();
    const user = Auth.user();
    const name = user ? (user.nickName || user.penName || user.realName || user.username) : '';

    let menu = '';
    items.forEach(function (it) {
        menu += '<a href="' + it.href + '" class="' + (it.key === active ? 'active' : '') + '">' + it.text + '</a>';
    });

    let area;
    if (Auth.logged()) {
        area = '<span class="who">' + escapeHtml(name) + '</span>'
            + '<button onclick="Auth.logout()">退出</button>';
    } else {
        area = '<a href="' + ROLES[role].login + '" style="color:#c8d2dc;margin-right:8px">登录</a>';
    }

    el.className = 'navbar';
    el.innerHTML = '<a class="logo" href="' + homeOf(role) + '">墨屿小说</a>'
        + '<div class="menu">' + menu + '</div>'
        + '<div class="user-area">' + area + '</div>';
}

// 三端点击 logo 返回的首页不同：作者端、管理端、读者端各自对应
function homeOf(role) {
    if (role === 'author') return 'author-home.html';
    if (role === 'admin') return 'admin-stat.html';
    return 'index.html';
}

/** 读者端顶部菜单，三个页面共用 */
function readerNav(active) {
    renderNav(active, [
        { key: 'index', text: '书库', href: 'index.html' },
        { key: 'rank', text: '排行榜', href: 'rank.html' },
        { key: 'sign', text: '签到', href: 'sign.html' },
        { key: 'shelf', text: '我的书架', href: 'shelf.html' },
        { key: 'user', text: '个人中心', href: 'user.html' }
    ]);
}

/** 作者端 / 管理端：顶栏只留 logo 和账号，功能走左侧菜单 */
const SIDE_MENUS = {
    author: [
        { key: 'home', text: '我的作品', href: 'author-home.html' },
        { key: 'new', text: '新建作品', href: 'author-book.html' },
        { key: 'chapter', text: '章节管理', href: 'author-chapter.html' },
        { key: 'ai', text: 'AI 创作助手', href: 'author-ai.html' }
    ],
    admin: [
        { key: 'stat', text: '数据看板', href: 'admin-stat.html' },
        { key: 'home', text: '首页推荐', href: 'admin-home.html' },
        { key: 'audit', text: '书籍审核', href: 'admin-audit.html' },
        { key: 'chapter', text: '章节审核', href: 'admin-chapter.html' },
        { key: 'author', text: '作者审核', href: 'admin-author.html' },
        { key: 'comment', text: '评论管理', href: 'admin-comment.html' },
        { key: 'category', text: '分类管理', href: 'admin-category.html' },
        { key: 'user', text: '用户管理', href: 'admin-user.html' }
    ]
};

function renderSide(active) {
    const el = document.getElementById('side');
    if (!el) return;
    const menu = SIDE_MENUS[currentRole()] || [];
    let html = '';
    menu.forEach(function (it) {
        html += '<a href="' + it.href + '" class="' + (it.key === active ? 'active' : '') + '">' + it.text + '</a>';
    });
    el.className = 'side';
    el.innerHTML = html;
}

// 后台页面统一入口，顶栏加侧边栏，同时校验登录。返回 false 表示已跳转登录页，调用方不需继续执行
function setupBackend(active) {
    const role = currentRole();
    if (!Auth.require()) return false;
    renderNav(active, []);
    renderSide(active);
    const items = SIDE_MENUS[role] || [];
    const hit = items.filter(i => i.key === active)[0];
    if (hit) document.title = hit.text + ' - 墨屿小说';
    return true;
}
