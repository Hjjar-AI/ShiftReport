"""Explicit opt-in transport checks for an owner-selected disposable Telegram group.

Never pins, unpins, deletes, replaces a project's index, revokes tokens, or opens
clinical storage. All created messages/files are synthetic and IDs are recorded.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import threading
import uuid
from desktop_protocol_common import BridgeError, UncertainDelivery
from desktop_telegram_transport import Telegram
from desktop_wire import encode_bundle
from tests.support import shift


def run(config, report):
    assert isinstance(config.get('botToken'), str) and ':' in config['botToken']
    assert type(config.get('chatId')) is int and config['chatId'] < 0
    first, second = Telegram(config), Telegram(config)
    first.call('getMe')
    group = first.call('getChat', {'chat_id': config['chatId']})
    assert group['type'] in ('group', 'supergroup')
    run_id = str(uuid.uuid4())
    record = dict(run=run_id, synthetic=True, messages=[], checks=[])
    def save():
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(record, indent=2) + '\n')
    def created(message):
        record['messages'].append(message['message_id']); save()
        return message
    def params(text, topic=None):
        values = dict(chat_id=config['chatId'], text=text, disable_notification='true')
        if topic: values['message_thread_id'] = topic
        return values
    try:
        raw = encode_bundle([shift()])
        document = created(first.upload(raw, 'synthetic-interop.csv', topic=config.get('csvTopicId'),
                                        caption='TEST ONLY — بيانات وهمية — ' + run_id))
        assert first.download(document['document']['file_id']) == raw
        record['checks'].append('live_document_upload_download_exact_bytes'); save()
        index = created(first.call('sendMessage', params('TEST ONLY index ' + run_id, config.get('csvTopicId'))))
        # Two independent HTTP clients read the same synthetic baseline. Explicit events
        # force the stale writer to finish last, demonstrating accepted last-write-wins.
        second_finished = threading.Event()
        def write_a():
            if not second_finished.wait(45): raise BridgeError('Second synthetic writer did not finish')
            return first.call('editMessageText', dict(chat_id=config['chatId'], message_id=index['message_id'],
                              text='TEST ONLY stale writer A ' + run_id))
        def write_b():
            try:
                return second.call('editMessageText', dict(chat_id=config['chatId'], message_id=index['message_id'],
                                   text='TEST ONLY writer B ' + run_id))
            finally: second_finished.set()
        with ThreadPoolExecutor(max_workers=2) as pool:
            a = pool.submit(write_a); b = pool.submit(write_b)
            b.result(timeout=50); a.result(timeout=95)
        readback = created(first.call('forwardMessage', dict(chat_id=config['chatId'], from_chat_id=config['chatId'],
                                    message_id=index['message_id'], disable_notification='true')))
        assert readback.get('text') == 'TEST ONLY stale writer A ' + run_id
        record['checks'].append('two_live_clients_last_successful_edit_wins'); save()
        message = created(first.call('sendMessage', params('TEST ONLY reviewed report — بيانات وهمية — ' + run_id,
                                                            config.get('reportsTopicId'))))
        assert message.get('message_id')
        record['checks'].append('live_report_destination_accepts_synthetic_message'); save()
        record['complete'] = True; save()
        return record
    except UncertainDelivery:
        record['uncertain'] = True; save()
        raise
    finally:
        first._http.close(); second._http.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = run(json.loads(args.config.read_text()), args.report)
        print('Live synthetic checks passed:', len(result['checks']))
        print('Created message IDs retained in the local report.')
    except (BridgeError, AssertionError, KeyError, ValueError):
        # Never print HTTP objects, config, URLs or raw transport exceptions.
        print('Live check did not complete. Inspect the local report and test group before retrying.')
        raise SystemExit(1)
