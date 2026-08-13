(function () {
    'use strict';

    var snapshot = window.__ngaJwArticleSnapshot = {state: 'loading', text: ''};
    var MAX_ROWS = 200;
    var MAX_USERS = 300;
    var MAX_STRING = 512 * 1024;

    function parseCall(source, marker, fromIndex) {
        var markerIndex = source.indexOf(marker, fromIndex || 0);
        if (markerIndex < 0) return null;
        var start = markerIndex + marker.length;
        var args = [];
        var partStart = start;
        var round = 0;
        var square = 0;
        var curly = 0;
        var quote = '';
        var escaped = false;
        for (var index = start; index < source.length; index++) {
            var character = source.charAt(index);
            if (quote) {
                if (escaped) escaped = false;
                else if (character === '\\') escaped = true;
                else if (character === quote) quote = '';
                continue;
            }
            if (character === '"' || character === "'") {
                quote = character;
            } else if (character === '(') {
                round++;
            } else if (character === '[') {
                square++;
            } else if (character === '{') {
                curly++;
            } else if (character === ']') {
                square--;
            } else if (character === '}') {
                curly--;
            } else if (character === ')' && round === 0 && square === 0 && curly === 0) {
                args.push(source.substring(partStart, index).trim());
                return {args: args, end: index + 1};
            } else if (character === ')') {
                round--;
            } else if (character === ',' && round === 0 && square === 0 && curly === 0) {
                args.push(source.substring(partStart, index).trim());
                partStart = index + 1;
            }
            if (round < 0 || square < 0 || curly < 0) return null;
        }
        return null;
    }

    function decodeQuoted(token) {
        token = (token || '').trim();
        if (token.length < 2) return null;
        var quote = token.charAt(0);
        if ((quote !== '"' && quote !== "'") || token.charAt(token.length - 1) !== quote) {
            return null;
        }
        var result = '';
        for (var index = 1; index < token.length - 1; index++) {
            var character = token.charAt(index);
            if (character !== '\\') {
                result += character;
                continue;
            }
            if (++index >= token.length - 1) return null;
            var escaped = token.charAt(index);
            if (escaped === 'n') result += '\n';
            else if (escaped === 'r') result += '\r';
            else if (escaped === 't') result += '\t';
            else if (escaped === 'b') result += '\b';
            else if (escaped === 'f') result += '\f';
            else if (escaped === 'v') result += '\v';
            else if (escaped === '\n') continue;
            else if (escaped === '\r') {
                if (token.charAt(index + 1) === '\n') index++;
            } else if (escaped === 'x' && /^[0-9a-fA-F]{2}$/.test(token.substr(index + 1, 2))) {
                result += String.fromCharCode(parseInt(token.substr(index + 1, 2), 16));
                index += 2;
            } else if (escaped === 'u' && /^[0-9a-fA-F]{4}$/.test(token.substr(index + 1, 4))) {
                result += String.fromCharCode(parseInt(token.substr(index + 1, 4), 16));
                index += 4;
            } else {
                result += escaped;
            }
        }
        return result;
    }

    function scalar(token) {
        token = (token || '').trim();
        var stringValue = decodeQuoted(token);
        if (stringValue !== null) return stringValue;
        if (/^-?\d+(?:\.\d+)?$/.test(token)) return Number(token);
        if (token === 'true') return true;
        if (token === 'false') return false;
        if (token === 'null' || token === 'undefined' || token === '') return null;
        return null;
    }

    function integer(token, fallback) {
        var value = scalar(token);
        if (value === null || value === '') return fallback;
        var number = typeof value === 'number' ? value : Number(value);
        return isFinite(number) ? Math.trunc(number) : fallback;
    }

    function scriptTexts() {
        var scripts = document.querySelectorAll('script');
        var texts = [];
        for (var index = 0; index < scripts.length; index++) {
            var text = scripts[index].textContent || '';
            if (text.indexOf('commonui.postArg.') >= 0
                    || text.indexOf('commonui.userInfo.setAll(') >= 0) {
                texts.push(text);
            }
        }
        return texts;
    }

    function firstCall(texts, marker) {
        for (var index = 0; index < texts.length; index++) {
            var call = parseCall(texts[index], marker, 0);
            if (call) return call.args;
        }
        return null;
    }

    function allCalls(texts, marker) {
        var calls = [];
        for (var scriptIndex = 0; scriptIndex < texts.length; scriptIndex++) {
            var source = texts[scriptIndex];
            var from = 0;
            while (calls.length <= MAX_ROWS) {
                var call = parseCall(source, marker, from);
                if (!call) break;
                calls.push(call.args);
                from = call.end;
            }
        }
        return calls;
    }

    function escapeControlsInJson(source) {
        var result = '';
        var inString = false;
        var escaped = false;
        for (var index = 0; index < source.length; index++) {
            var character = source.charAt(index);
            var code = source.charCodeAt(index);
            if (inString && code < 0x20) {
                if (character === '\n') result += '\\n';
                else if (character === '\r') result += '\\r';
                else if (character === '\t') result += '\\t';
                else result += '\\u' + ('0000' + code.toString(16)).slice(-4);
                escaped = false;
                continue;
            }
            result += character;
            if (!inString && character === '"') {
                inString = true;
                escaped = false;
            } else if (inString) {
                if (escaped) escaped = false;
                else if (character === '\\') escaped = true;
                else if (character === '"') inString = false;
            }
        }
        return result;
    }

    function extractObjectAfter(source, marker) {
        var markerIndex = source.indexOf(marker);
        if (markerIndex < 0) return null;
        var start = source.indexOf('{', markerIndex + marker.length);
        if (start < 0) return null;
        var depth = 0;
        var inString = false;
        var escaped = false;
        for (var index = start; index < source.length; index++) {
            var character = source.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (character === '\\') escaped = true;
                else if (character === '"') inString = false;
                continue;
            }
            if (character === '"') inString = true;
            else if (character === '{') depth++;
            else if (character === '}' && --depth === 0) {
                try {
                    return JSON.parse(escapeControlsInJson(source.substring(start, index + 1)));
                } catch (ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    function pageUsers(texts) {
        for (var index = 0; index < texts.length; index++) {
            var result = extractObjectAfter(texts[index], 'commonui.userInfo.setAll(');
            if (result) return result;
        }
        return {};
    }

    function safeString(value, limit) {
        if (value === null || value === undefined) return null;
        var stringValue = String(value);
        return stringValue.length <= limit ? stringValue : stringValue.substring(0, limit);
    }

    function safeUserMap(rawUsers) {
        var result = Object.create(null);
        var fields = [
            'uid', 'username', 'avatar', 'yz', 'mute_time', 'rvrc', 'postnum',
            'memberid', 'signature', 'ipLoc', 'ip_loc', 'ipLocation', 'ip_location'
        ];
        var userCount = 0;
        Object.keys(rawUsers || {}).forEach(function (key) {
            if (userCount >= MAX_USERS) return;
            if (key === '__GROUPS') {
                var groups = rawUsers[key];
                if (groups && typeof groups === 'object') {
                    var groupResult = Object.create(null);
                    Object.keys(groups).slice(0, MAX_USERS).forEach(function (groupKey) {
                        var group = groups[groupKey];
                        if (group && typeof group === 'object') {
                            groupResult[groupKey] = {
                                '0': safeString(group[0] !== undefined ? group[0] : group['0'], 256)
                            };
                        }
                    });
                    result.__GROUPS = groupResult;
                }
                return;
            }
            if (!/^-?\d+$/.test(key)) return;
            var rawUser = rawUsers[key];
            if (!rawUser || typeof rawUser !== 'object') return;
            var user = Object.create(null);
            fields.forEach(function (field) {
                if (rawUser[field] !== undefined && rawUser[field] !== null) {
                    user[field] = safeString(rawUser[field], field === 'signature' ? 8192 : 2048);
                }
            });
            if (rawUser.buffs && typeof rawUser.buffs === 'object') {
                var buffs = Object.create(null);
                Object.keys(rawUser.buffs).slice(0, 100).forEach(function (buffKey) {
                    buffs[buffKey] = 1;
                });
                user.buffs = buffs;
            }
            result[key] = user;
            userCount++;
        });
        return result;
    }

    function allowedUrl(rawUrl) {
        if (!rawUrl) return null;
        try {
            var url = new URL(rawUrl, document.baseURI);
            if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
            url.username = '';
            url.password = '';
            return url.href;
        } catch (ignored) {
            return null;
        }
    }

    function sanitize(source) {
        if (!source) return {html: '', images: []};
        var root = source.cloneNode(true);
        var forbidden = root.querySelectorAll(
            'script,style,iframe,object,embed,form,input,button,textarea,select,link,meta,base,template,svg,canvas'
        );
        for (var forbiddenIndex = forbidden.length - 1; forbiddenIndex >= 0; forbiddenIndex--) {
            forbidden[forbiddenIndex].remove();
        }
        var images = [];
        var elements = [root].concat(Array.prototype.slice.call(root.querySelectorAll('*')));
        elements.forEach(function (element) {
            var tag = (element.tagName || '').toLowerCase();
            var lazySource = tag === 'img'
                ? (element.getAttribute('data-src') || element.getAttribute('data-original'))
                : null;
            var attributes = Array.prototype.slice.call(element.attributes || []);
            attributes.forEach(function (attribute) {
                var name = attribute.name.toLowerCase();
                var keep = name === 'class' || name === 'title'
                    || (tag === 'a' && name === 'href')
                    || (tag === 'img' && (name === 'src' || name === 'alt'))
                    || ((tag === 'video' || tag === 'audio')
                        && (name === 'src' || name === 'poster' || name === 'controls' || name === 'preload'))
                    || (tag === 'source' && (name === 'src' || name === 'type'))
                    || ((tag === 'td' || tag === 'th') && (name === 'colspan' || name === 'rowspan'));
                if (!keep || name.indexOf('on') === 0 || name === 'srcdoc' || name === 'style') {
                    element.removeAttribute(attribute.name);
                }
            });
            if (tag === 'img' && !element.getAttribute('src') && lazySource) {
                element.setAttribute('src', lazySource);
            }
            ['href', 'src', 'poster'].forEach(function (name) {
                if (!element.hasAttribute || !element.hasAttribute(name)) return;
                var normalized = allowedUrl(element.getAttribute(name));
                if (normalized) element.setAttribute(name, normalized);
                else element.removeAttribute(name);
            });
            if (tag === 'a' && element.hasAttribute('href')) {
                element.setAttribute('rel', 'noopener noreferrer');
            }
            if (tag === 'video' || tag === 'audio') {
                element.setAttribute('controls', 'controls');
                element.setAttribute('preload', 'metadata');
            }
            if (tag === 'img' && element.hasAttribute('src')) {
                images.push(element.getAttribute('src'));
            }
        });
        var html = root.innerHTML || '';
        if (html.length > MAX_STRING) throw new Error('content-size');
        return {html: html, images: images.slice(0, 200)};
    }

    function textOf(id) {
        var element = document.getElementById(id);
        return element ? (element.textContent || '').trim() : '';
    }

    function localityFor(floor) {
        var element = document.getElementById('postauthor' + floor)
            || document.getElementById('post1strow' + floor);
        if (!element) return null;
        var match = (element.textContent || '').match(/(?:属地|IP属地)\s*[:：]\s*([^\s|]+)/);
        return match ? safeString(match[1], 128) : null;
    }

    function authorFromDom(floor) {
        var element = document.getElementById('postauthor' + floor);
        if (!element) return '';
        var link = element.querySelector('a[href*="uid="],a[class*="author"],a');
        return (link ? link.textContent : element.textContent || '').trim();
    }

    function pageNumber() {
        try {
            var value = Number(new URL(location.href).searchParams.get('page'));
            return isFinite(value) && value > 0 ? Math.trunc(value) : 1;
        } catch (ignored) {
            return 1;
        }
    }

    function cleanTitle(value) {
        return (value || '')
            .replace(/\s*(?:[-–—|]\s*)?NGA(?:玩家社区|玩家社区论坛)?\s*$/i, '')
            .trim();
    }

    try {
        var texts = scriptTexts();
        var defaults = firstCall(texts, 'commonui.postArg.setDefault(');
        var procCalls = allCalls(texts, 'commonui.postArg.proc(');
        if (!defaults || defaults.length < 14 || !procCalls.length) throw new Error('shape');

        var fid = integer(defaults[0], 0);
        var tid = integer(defaults[2], 0);
        var topicAuthorId = integer(defaults[3], 0);
        var replies = Math.max(0, integer(defaults[11], procCalls.length - 1));
        var lastPost = Math.max(0, integer(defaults[12], 0));
        if (tid <= 0 || procCalls.length > MAX_ROWS) throw new Error('identity');

        var rawUsers = pageUsers(texts);
        var users = safeUserMap(rawUsers);
        var rows = Object.create(null);
        var firstSubject = '';
        var firstAuthor = '';
        var firstPostTime = 0;
        var accepted = 0;

        procCalls.forEach(function (args) {
            if (accepted >= MAX_ROWS || args.length < 16) return;
            var floor = integer(args[0], -1);
            var postTid = integer(args[9], tid);
            var pid = integer(args[10], 0);
            var authorId = integer(args[13], 0);
            if (floor < 0 || postTid !== tid || (pid <= 0 && floor !== 0)) return;

            var subject = textOf('postsubject' + floor);
            if (!subject && floor === 0) subject = cleanTitle(document.title);
            var contentElement = document.getElementById('postcontent' + floor);
            if (!contentElement) return;
            var content = sanitize(contentElement);
            var attachmentElement = document.getElementById('postattach' + floor);
            if (attachmentElement) {
                var attachment = sanitize(attachmentElement);
                content.html += attachment.html;
                content.images = content.images.concat(attachment.images).slice(0, 200);
            }
            var signatureElement = document.getElementById('postsigncontent' + floor);
            var signature = signatureElement ? sanitize(signatureElement).html : '';

            var user = users[String(authorId)] || {};
            var author = safeString(user.username, 512) || authorFromDom(floor);
            var postTime = Math.max(0, integer(args[14], 0));
            var recommend = scalar(args[15]);
            var score = 0;
            if (typeof recommend === 'string') {
                var scoreParts = recommend.split(',');
                if (scoreParts.length > 1) score = Math.max(0, Number(scoreParts[1]) || 0);
            }
            var postDate = textOf('postdate' + floor) || String(postTime);
            var locality = localityFor(floor);
            var row = {
                tid: tid,
                fid: integer(args[8], fid),
                pid: pid,
                type: integer(args[11], 0),
                authorid: authorId,
                author: author || '',
                postdate: postDate,
                lou: floor,
                subject: subject || null,
                content: content.html,
                score: Math.trunc(score),
                from_client: safeString(scalar(args[19]), 2048),
                ipLoc: locality,
                js_escap_avatar: safeString(user.avatar, 8192),
                isanonymous: authorId < 0 || (author || '').indexOf('#anony_') === 0,
                __WEB_FALLBACK_HTML: true,
                __WEB_IMAGE_URLS: content.images,
                __WEB_SIGNATURE_HTML: signature
            };
            rows[String(accepted++)] = row;
            if (!firstSubject && subject) firstSubject = subject;
            if (!firstAuthor && author) firstAuthor = author;
            if (!firstPostTime) firstPostTime = postTime;
        });

        if (!accepted) throw new Error('rows');
        var topicUser = users[String(topicAuthorId)] || {};
        var threadInfo = {
            tid: tid,
            fid: fid,
            authorid: topicAuthorId,
            author: safeString(topicUser.username, 512) || firstAuthor,
            subject: firstSubject || cleanTitle(document.title),
            replies: replies,
            postdate: firstPostTime,
            lastpost: lastPost,
            page: pageNumber()
        };
        snapshot.text = JSON.stringify({data: {
            __ROWS: replies + 1,
            __R__ROWS: accepted,
            __T: threadInfo,
            __R: rows,
            __U: users
        }});
        snapshot.state = 'done';
    } catch (ignored) {
        snapshot.text = '';
        snapshot.state = 'error';
    }
}());
void(0);
