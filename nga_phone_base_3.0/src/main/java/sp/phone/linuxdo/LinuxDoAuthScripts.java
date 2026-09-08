package sp.phone.linuxdo;

import org.json.JSONObject;

/** No captcha site keys, token bridges or replayed login POSTs: the official site owns login. */
public final class LinuxDoAuthScripts {
    public static String fill(String identifier, String password) {
        return fill(identifier, password, true);
    }

    public static String fill(String identifier, String password, boolean replace) {
        return "(()=>{if(location.origin!=='https://linux.do')return false;"
                + "const u=document.querySelector('#login-account-name,input[autocomplete=username]');"
                + "const p=document.querySelector('#login-account-password,input[autocomplete=current-password]');"
                + "if(!u||!p||p.type!=='password')return false;"
                + (replace ? "" : "if(u.value||p.value)return true;")
                + "const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "[[u," + JSONObject.quote(identifier) + "],[p," + JSONObject.quote(password) + "]]"
                + ".forEach(([e,v])=>{set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));});return true;})()";
    }

    public static String probe(boolean verification, String key) {
        String path = verification ? "/latest.json" : "/session/current.json";
        return "(()=>{if(location.origin!=='https://linux.do')return;const k="
                + JSONObject.quote(key) + ";window[k]=null;"
                + "const c=new AbortController();const t=setTimeout(()=>c.abort(),8000);"
                + "fetch('" + path + "',{credentials:'same-origin',cache:'no-store',"
                + "headers:{Accept:'application/json'},signal:c.signal}).then(async r=>{"
                + "if(r.headers.get('cf-mitigated')==='challenge'){window[k]={kind:'challenge'};return;}"
                + "if(!(r.headers.get('content-type')||'').includes('json')){"
                + "window[k]={kind:'blocked'};return;}const j=await r.json();"
                + "if(r.ok&&j.current_user&&j.current_user.username){window[k]={kind:'account',"
                + "username:j.current_user.username};return;}"
                + "if(r.ok&&j.topic_list&&Array.isArray(j.topic_list.topics)){window[k]={kind:'verified'};return;}"
                + "window[k]={kind:(r.status===401||r.ok)?'guest':'blocked'};"
                + "}).catch(()=>window[k]={kind:'network'}).finally(()=>clearTimeout(t));})()";
    }
    private LinuxDoAuthScripts() { }
}
