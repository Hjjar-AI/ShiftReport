import copy
import time
import unittest
from unittest.mock import Mock, MagicMock
from desktop_protocol import Client
from desktop_protocol_common import BridgeError, UncertainDelivery, LIMIT
from desktop_telegram_transport import Telegram
from tests.support import client


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.config = dict(botToken='synthetic:fixture', chatId=-100)
        self.transport = Telegram(self.config)
        self.addCleanup(self.transport._http.close)
        self.http = Mock()
        self.transport._http = self.http

    def response(self, status=200, payload=None):
        response = Mock(status_code=status, ok=200 <= status < 300)
        response.json.return_value = payload or {'ok': True, 'result': {'message_id': 12}}
        self.http.post.return_value = response
        return response

    def test_server_error_on_send_remains_uncertain_without_retry(self):
        self.response(503)
        with self.assertRaises(UncertainDelivery): self.transport.call('sendMessage')
        self.http.post.assert_called_once()

    def test_network_exception_on_mutation_never_exposes_url_or_token(self):
        self.http.post.side_effect = RuntimeError('https://api.telegram.org/botsynthetic:fixture/sendMessage')
        with self.assertRaises(UncertainDelivery) as caught: self.transport.call('sendMessage')
        self.assertNotIn(self.config['botToken'], str(caught.exception))
        self.assertNotIn('https://', str(caught.exception))
        self.http.post.assert_called_once()

    def test_explicit_rejection_is_not_reported_as_success(self):
        self.response(403, {'ok': False, 'error_code': 403, 'description': 'private payload'})
        with self.assertRaises(BridgeError) as caught: self.transport.call('getChat')
        self.assertNotIn('private payload', str(caught.exception))

    def test_malformed_response_during_send_is_uncertain(self):
        self.response().json.side_effect = ValueError('synthetic invalid JSON')
        with self.assertRaises(UncertainDelivery): self.transport.call('sendDocument')

    def test_read_failure_is_safe_connection_error(self):
        self.http.post.side_effect = RuntimeError('private credentials')
        with self.assertRaises(BridgeError) as caught: self.transport.call('getMe')
        self.assertNotIsInstance(caught.exception, UncertainDelivery)
        self.assertNotIn('private credentials', str(caught.exception))

    def test_requests_disable_redirects_and_use_bounded_timeouts(self):
        self.response(); self.transport.call('getMe')
        self.assertFalse(self.http.post.call_args.kwargs['allow_redirects'])
        self.assertEqual((10, 35), self.http.post.call_args.kwargs['timeout'])

    def test_unsafe_download_paths_rejected_before_get(self):
        for path in ('/absolute/file', '../private', 'folder/../private', ''):
            self.transport.call = Mock(return_value={'file_path': path})
            with self.subTest(path=path), self.assertRaises(BridgeError): self.transport.download('synthetic')
        self.http.get.assert_not_called()

    def test_oversized_download_rejected_without_accepting_partial_content(self):
        self.transport.call = Mock(return_value={'file_path': 'synthetic.bin'})
        response = Mock(ok=True)
        response.iter_content.return_value = iter([b'x' * (LIMIT // 2), b'x' * (LIMIT // 2), b'x'])
        self.http.get.return_value = MagicMock()
        self.http.get.return_value.__enter__.return_value = response
        with self.assertRaises(BridgeError): self.transport.download('synthetic')


class AuthorityTests(unittest.TestCase):
    def setUp(self):
        self.actor = copy.deepcopy(client().doctors['cedar'])
        self.owner = Client(dict(botToken='synthetic:fixture', chatId=-100))
        self.addCleanup(self.owner.telegram._http.close)
        self.owner._actor = self.actor['uid']; self.owner._verified_telegram = self.actor['telegram_id']
        self.owner._expires = time.time() + 60
        self.owner.telegram.call = Mock(return_value={'status': 'member'})

    def test_live_registry_identity_change_rejects_existing_session(self):
        for doctors in ({}, {'cedar': dict(self.actor, telegram_id=999)}):
            with self.assertRaises(BridgeError): self.owner.authorize(doctors)
        self.owner.telegram.call.assert_not_called()

    def test_expired_session_cannot_call_membership_or_mutate(self):
        self.owner._expires = 0
        with self.assertRaises(BridgeError): self.owner.authorize({'cedar': self.actor})
        self.owner.telegram.call.assert_not_called()

    def test_removed_group_member_cannot_continue_despite_matching_identity(self):
        for status in ('left', 'kicked', 'restricted'):
            self.owner.telegram.call.return_value = {'status': status, 'is_member': False}
            with self.subTest(status=status), self.assertRaises(BridgeError): self.owner.authorize({'cedar': self.actor})

    def test_live_demotion_blocks_admin_operation(self):
        demoted = dict(self.actor, **{'#': '0', 'options': ''})
        self.owner.fetch = Mock(return_value={'doctors': {'cedar': demoted}})
        with self.assertRaises(BridgeError): self.owner.require_admin()

    def test_restricted_current_member_can_authorize_clinical_identity(self):
        self.owner.telegram.call.return_value = {'status': 'restricted', 'is_member': True}
        self.assertEqual(self.actor, self.owner.authorize({'cedar': self.actor}))

    def test_local_pin_identity_does_not_manufacture_telegram_session(self):
        self.owner._actor = None
        self.owner.fetch = Mock(return_value={'doctors': {'cedar': self.actor}})
        with self.assertRaises(BridgeError): self.owner.require_local_identity()


if __name__ == '__main__': unittest.main()
