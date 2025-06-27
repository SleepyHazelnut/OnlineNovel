/**
 * 墨屿小说 · 前端公共脚本
 * 项目不引入 Vue / React，也不使用打包工具，web/ 目录由 Spring Boot 的静态资源映射托管，
 * 页面为原始 HTML，公共逻辑统一通过 <script> 引入本文件
 * 读者 / 作者 / 管理三端连同一个后端，页面在 body 上声明 data-role，脚本据此选用对应的登录态
 */

// 三端各自独立的存储 key。若共用同一份，作者端登录会覆盖读者端登录态，导致两端互相跳转登录页，故按角色分开
const ROLES = {
    reader: { token: 'inkisle_token', user: 'inkisle_user', login: 'login.html' },
    author: { token: 'inkisle_author_token', user: 'inkisle_author', login: 'author-login.html' },
    admin: { token: 'inkisle_admin_token', user: 'inkisle_admin', login: 'admin-login.html' }
};

// 页面未声明 data-role 时默认按读者端处理
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
    /** 未登录时跳转到对应端的登录页，并将当前地址写入 redirect，登录后可返回原页面 */
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
 * 所有接口统一经由本函数发起。项目刻意保持零依赖、零构建，不使用 axios，
 * 自写一层便于控制每一步
 * 内部完成四件事：携带 token、序列化请求体、将 401 跳转回登录页、
 * 拆解后端统一返回的 {code, msg, data}，业务代码直接拿到 data
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

    // 后端拦截器拦截时返回 401。此处先清除本地登录态，再按当前页面所属端跳转对应登录页：
    // 读者 → login.html、作者 → author-login.html、管理 → admin-login.html，不能统一跳同一个
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
        // 正常错误体是 JSON；但 500 抛异常或网关直接拦截时可能返回 HTML，解析会失败
        throw new Error('服务返回异常（HTTP ' + resp.status + '）');
    }

    // 后端统一返回 {code, msg, data}，code 非 200 即视为失败并向上抛出，
    // 页面统一 catch 后弹 toast，无需每个接口单独判断
    if (data.code !== 200) {
        throw new Error(data.msg || '操作失败');
    }
    return data.data;
}

/** GET。params 中值为 null / undefined / 空串的会被过滤，避免拼出 ?a=&b= 这类空参数 */
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

/* ---------------- 小工具 ---------------- */
function $(sel) { return document.querySelector(sel); }
function $all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

/** 后端返回的时间是 2025-09-01T10:20:30 这种带 T 的格式，页面上一律截到分钟显示 */
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

/**
 * 作品上架状态：0 未提交 1 待审核 2 已通过 3 已驳回 4 已下架
 * 与上面 auditStatusTag 的编号不是一套：那个读的是审核记录，只有三种状态
 * 混用会显示成错误的标签，两者不要互换
 */
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
        { key: 'audit', text: '书籍审核', href: 'admin-audit.html' },
        { key: 'author', text: '作者审核', href: 'admin-author.html' },
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

/** 后台页面统一入口：顶栏 + 侧边栏 + 登录校验。返回 false 表示已跳转登录页，调用方应停止后续处理 */
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
