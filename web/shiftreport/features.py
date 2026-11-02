"""Provisioning, identity and reviewed actions for the local Flask application."""
import base64
import copy
import io
import secrets
import time
from pathlib import Path

from flask import Blueprint, current_app, g, redirect, render_template, request, send_file, url_for
from .core.protocol import Client, BridgeError, require, fingerprint, current_date, packed, is_admin
from .core.access import LocalAccess
from .core.project import ProjectCreator

features = Blueprint('features', __name__)


def home():
    return redirect(url_for('pages.ward'), code=303)


@features.post('/intro/dismiss')
def dismiss_intro():
    g.workspace.intro_dismissed=True
    return '',204


@features.post('/appearance')
def appearance():
    themes={'teal':'VANILLA','blue':'SYSTEM','rose':'COASTAL','amber':'CREAM'}
    value=request.form.get('accent')
    require(value in themes,'لون غير صالح')
    w=g.workspace
    w.print_accent=themes[value]
    text=request.form.get('text_size',w.preferences['text_size'])
    motion=request.form.get('motion',w.preferences['motion'])
    require(text in ('100','115','130') and motion in ('system','reduce'),'خيار مظهر غير صالح')
    choice=dict(text_size=text,motion=motion)
    w.preferences.update(choice)
    if not w.demo and not w.store.locked and w.store.state.get('browserAppearance')!=choice:
        w.ready()
        w.store.commit(browserAppearance=choice)
    return '',204


@features.route('/settings',methods=['GET','POST'])
def settings():
    w=g.workspace
    w.ready()
    if request.method=='POST':
        from .core.protocol import ZoneInfo
        zone=request.form.get('zone','').strip()
        try:
            ZoneInfo(zone)
        except (ValueError,KeyError):
            raise BridgeError('منطقة زمنية غير صالحة') from None
        layout=request.form.get('layout','cards')
        format_name=request.form.get('format','pdf')
        require(layout in ('cards','table','paragraphs') and format_name in ('pdf','text'),'صيغة تقرير غير صالحة')
        w.store.commit(browserSettings=dict(zone=zone),reportPreferences=dict(layout=layout,format=format_name))
        w.client.zone=w.store.zone=zone
        w.notice='حُفظت الإعدادات '+('في ذاكرة التجربة.' if w.demo else 'محلياً.')
        return redirect(url_for('features.settings'),code=303)
    return render_template('settings.html',preferences=w.store.report_preferences(),page='settings')


@features.post('/session/touch')
def touch_session():
    g.workspace.ready()
    return '',204


def data_directory():
    root=current_app.config.get('DATA_DIRECTORY')
    path = (Path(root) if root else Path(current_app.root_path).parent / 'instance') / 'workspaces'
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    return path


def uploaded(limit=8*1024*1024+64):
    file = request.files.get('file')
    require(file is not None and bool(file.filename), 'اختر الملف أولاً')
    raw = file.read(limit+1)
    require(len(raw) <= limit, 'الملف أكبر من الحد المسموح')
    return raw


def download(raw, name, mime='application/octet-stream'):
    return send_file(io.BytesIO(raw), mimetype=mime, as_attachment=True, download_name=name)


@features.route('/connection', methods=['GET','POST'])
def connect():
    w = g.workspace
    if request.method == 'POST':
        from .core.crypto import open_join
        config = open_join(uploaded(1024*1024), request.form.get('password',''))
        candidate = Client(copy.deepcopy(config))
        if not w.demo:
            require(candidate.project_id == w.client.project_id, 'ملف الاتصال يغيّر المشروع أو المفتاح أو المواضيع؛ لن يُستبدل المشروع المفتوح')
            if config['botToken'] != w.client.config['botToken']:
                candidate._actor, candidate._verified_telegram, candidate._expires = w.client._actor, w.client._verified_telegram, w.client._expires
                candidate.require_admin()
        offline = request.form.get('offline') == '1'
        if offline:
            require((data_directory() / (candidate.project_id+'.sqlite3')).is_file(), 'يلزم التحقق عبر الشبكة عند الانضمام لأول مرة')
            require(w.demo or config['botToken']==w.client.config['botToken'], 'استبدال التوكن يتطلب تحققاً مباشراً')
            snapshot = None
        else:
            snapshot = candidate.fetch_registry()
            from .core.destinations import validate_destination
            validate_destination(candidate)
        w.pending_config = dict(config=config, snapshot=snapshot)
        return redirect(url_for('features.join_review'), code=303)
    return render_template('connection.html', page='connection')


@features.route('/connection/review', methods=['GET','POST'])
def join_review():
    w = g.workspace
    require(w.pending_config is not None, 'افتح ملف الانضمام أولاً')
    if request.method == 'POST':
        pending = w.pending_config
        if pending['snapshot'] is not None:
            candidate = Client(copy.deepcopy(pending['config']))
            verified = candidate.fetch_registry()
            require(fingerprint(verified['doctors']) == fingerprint(pending['snapshot']['doctors']), 'تغير السجل؛ أعد فتح الملف ومراجعته')
        w.attach(pending['config'], data_directory(), request.form.get('local_password',''))
        if pending['snapshot'] is not None and not w.client.doctors:
            w.client.doctors = copy.deepcopy(pending['snapshot']['doctors'])
        w.pending_config = None
        return redirect(url_for('features.auth'), code=303)
    return render_template('join_review.html', hospital=w.pending_config['config'].get('hospitalName',''),
                           encrypted=bool(w.pending_config['config'].get('telegramDataKey')), page='connection')


@features.route('/identity', methods=['GET','POST'])
def auth():
    w = g.workspace
    if request.method == 'POST':
        require(not w.demo, 'التجربة لا تحتاج تسجيل الدخول')
        action = request.form.get('action')
        if action == 'request_code':
            w.lock_session()
            w.client.begin_login(request.form.get('doctor',''))
            w.notice = 'أُرسل رمز خاص إلى حسابك؛ أدخله هنا. لا تشاركه.'
        elif action == 'verify_code':
            actor = w.client.check_login(request.form.get('code',''))
            require(actor is not None, 'رمز التحقق غير صحيح')
            snapshot = w.client.fetch()
            w.client.authorize(snapshot['doctors'])
            w.actor, w.unlocked, w.store.locked = w.client._actor, True, False
            w.store.local_actor = w.actor
            w.client.doctors = copy.deepcopy(snapshot['doctors'])
            if w.store.state.get('baseline') is None:
                w.client.baseline = snapshot
                w.store.commit(baseline=snapshot)
            if w.store.state.get('draft') is None:
                shift = copy.deepcopy(snapshot['bundle'][0]) if snapshot['bundle'] else dict(meta=dict(
                    schemaVersion='8',shiftDate=current_date(w.client.zone),shiftId=secrets.token_hex(16),
                    doctors=w.actor,sortSpec='{"levels":[]}',shiftRevision='0',snapshotId='',baseSnapshotId='',deviceId=''),patients=[])
                w.store.commit(draft=shift)
            w.last_activity = time.time()
            w.notice = 'تم التحقق. يمكنك إعداد PIN محلي من صفحة الهوية.'
            return home()
        elif action == 'unlock':
            key = request.form.get('doctor','')
            access = LocalAccess(w.store, w.client)
            require(access.verify(key, request.form.get('pin','')), 'PIN غير صحيح')
            w.actor, w.unlocked, w.store.locked = key, True, False
            w.client._actor,w.client._verified_telegram,w.client._expires=None,None,0
            w.store.local_actor = key
            w.last_activity = time.time()
            return home()
        elif action == 'enroll':
            w.ready(online=True)
            LocalAccess(w.store, w.client).enroll(request.form.get('pin',''), int(request.form.get('minutes','5')))
            w.notice = 'حُفظ PIN مشفراً لهذا الجهاز والحساب.'
        elif action == 'lock':
            w.lock_session()
        else:
            require(False, 'عملية هوية غير صالحة')
    roster = [dict(d,id=k) for k,d in w.client.doctors.items()]
    eligible = [] if w.demo else LocalAccess(w.store,w.client).eligible()
    return render_template('identity.html', doctors=roster, eligible=eligible, page='identity')


@features.post('/demo/enter')
def enter_demo():
    from .workspace import BrowserWorkspace
    w = g.workspace
    replacement = BrowserWorkspace()
    # Keep the owning session object so cookie/session registry identity stays stable.
    w.__dict__.update(replacement.__dict__)
    return home()


@features.route('/project/create', methods=['GET','POST'])
def create_project():
    w = g.workspace
    if request.method == 'POST':
        require(w.demo, 'افتح التجربة قبل إنشاء مشروع مستقل؛ لن نستبدل المشروع الحالي')
        f = request.form
        config = dict(version=1,hospitalName=f.get('hospital','').strip(),botToken=f.get('token','').strip())
        require(config['hospitalName'] and ':' in config['botToken'], 'أدخل اسم المشفى وتوكن البوت')
        for field in ('chatId','reportsTopicId','csvTopicId','doctorsTopicId','residentsChatId','residentsTopicId'):
            raw = f.get(field,'').strip()
            require(not raw or raw.lstrip('-').isascii() and raw.lstrip('-').isdigit(), 'معرّفات الاتصال أرقام صحيحة')
            config[field] = int(raw or '0')
            require(-(2**63) <= config[field] < 2**63 and (config[field]<0 if field=='chatId' else config[field]<=0 if field=='residentsChatId' else config[field]>=0), 'معرّف اتصال خارج النطاق')
        config['telegramDataKey'] = base64.b64encode(secrets.token_bytes(32)).decode('ascii') if f.get('encrypted') else ''
        config['encryptTelegram'] = bool(config['telegramDataKey'])
        creator = ProjectCreator(config)
        w.creation_saved=False
        creator.begin(dict(name=f.get('name',''),telegram_id=f.get('telegram_id',''),gender=f.get('gender'),clinicalRole=f.get('role')))
        w.creator = creator
        # Passphrase is consumed while opening encrypted storage, never retained.
        from .core.store import Workspace
        w.creation_store = Workspace(data_directory(), creator.client, f.get('local_password') or None)
        return redirect(url_for('features.creation'), code=303)
    return render_template('create_project.html', page='connection')


@features.route('/project/creation', methods=['GET','POST'])
def creation():
    w = g.workspace
    require(w.creator is not None, 'ابدأ إنشاء المشروع أولاً')
    if request.method == 'POST':
        if request.form.get('action') == 'resend':
            require(w.creator.profile is not None,'ابدأ إنشاء المشروع أولاً')
            w.creator.begin(w.creator.profile)
            w.notice='تحقق من الرسالة الخاصة؛ يُعاد إرسال الرمز عند انتهاء صلاحيته فقط.'
        elif request.form.get('action') == 'verify':
            require(w.creator.check_code(request.form.get('code','')), 'رمز الإنشاء غير صحيح')
            token, details = w.creator.review(w.creation_store)
            w.creation_review = token
            w.creation_details = {key:details[key] for key in ('hospital','group','admin','role','encrypted')}
        elif request.form.get('action') == 'save_connection':
            require(w.creator.verified, 'أثبت هوية المدير أولاً')
            import hashlib
            from cryptography.hazmat.primitives.ciphers.aead import AESGCM
            password=request.form.get('password','')
            require(len(password.encode('utf-16-le'))//2>=10, 'كلمة مرور ملف الانضمام 10 محارف على الأقل')
            salt,nonce=secrets.token_bytes(16),secrets.token_bytes(12)
            key=hashlib.pbkdf2_hmac('sha256',password.encode(),salt,210000,32)
            raw=b'RUSHDJOIN1'+salt+nonce+AESGCM(key).encrypt(nonce,packed(w.creator.client.config).encode(),None)
            envelope=packed(dict(format='ShiftReportJoin',encryptedPayload=base64.b64encode(raw).decode())).encode()
            w.creation_saved=True
            return download(envelope,'project.srjoin.json','application/json')
        elif request.form.get('action') == 'confirm':
            require(request.form.get('confirm')=='1', 'الموافقة النهائية مطلوبة')
            require(getattr(w,'creation_saved',False), 'احفظ ملف الانضمام المحمي أولاً لحماية استرداد المفتاح والاتصال')
            creator, store = w.creator, w.creation_store
            creator.publish(w.creation_review, store)
            from .workspace import project_lock
            w.client, w.store, w.lock = creator.client, store, project_lock(creator.client.project_id)
            w.client.admin_only = False
            w.client.require_local_admin=w.local_admin
            w.actor, w.demo, w.unlocked = creator.client._actor, False, True
            w.creator, w.creation_store = None, None
            w.store.commit(draft=dict(meta=dict(schemaVersion='8',shiftDate=current_date(w.client.zone),
                shiftId=secrets.token_hex(16),doctors=w.actor,sortSpec='{"levels":[]}',shiftRevision='0',snapshotId='',baseSnapshotId='',deviceId=''),patients=[]))
            return home()
    return render_template('creation.html', verified=w.creator.verified,
                           hospital=w.creator.client.config['hospitalName'], details=getattr(w,'creation_details',None),
                           connection_saved=getattr(w,'creation_saved',False),page='connection')


from . import clinical_views, report_views, data_views
