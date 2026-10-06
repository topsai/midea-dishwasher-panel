"""测试控制边界，避免把只读属性当成可写功能。"""
import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / 'dishwasher_gui.py'
spec = importlib.util.spec_from_file_location('dishwasher_gui', path)
gui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gui)

class CommandTests(unittest.TestCase):
    def test_read_only_attribute_cannot_be_sent(self):
        with self.assertRaises(ValueError):
            gui.validate_command('temperature', 45, {2: 'strong_wash'})

    def test_unknown_and_neutral_modes_cannot_start(self):
        for value in (0, 99):
            with self.assertRaises(ValueError):
                gui.validate_command('mode', value, {0: 'neutral_gear', 2: 'strong_wash'})

    def test_non_boolean_switch_value_is_rejected(self):
        with self.assertRaises(ValueError):
            gui.validate_command('power', 'false', {})

    def test_supported_controls_are_accepted(self):
        for field in ('power', 'storage', 'child_lock'):
            self.assertEqual(gui.validate_command(field, False, {}), (field, False))
        self.assertEqual(gui.validate_command('mode', 2, {2: 'strong_wash'}), ('mode', 2))

    def test_missing_reading_is_not_zero(self):
        self.assertEqual(gui.display_value('temperature', None), '未上报')
        self.assertEqual(gui.display_value('temperature', 0), '0 °C')

    def test_storage_running_flag_is_not_the_enable_switch(self):
        self.assertEqual(gui.display_value('storage', True), '开启')
        self.assertEqual(gui.display_value('storage_status', False), '当前未运行')
        self.assertEqual(gui.display_value('storage_status', True), '运行中')
        self.assertEqual(gui.display_value('storage_status', None), '未上报')

    def test_captured_storage_enabled_while_current_action_idle(self):
        from midealan.devices.e1.message import MessageE1Response
        off = MessageE1Response(bytes.fromhex('aa24e1c500000000000300010d00008100000000001900040000004800000000000003003c'))
        on = MessageE1Response(bytes.fromhex('aa24e1c500000000000300010d0000a10000000000190004000000484800000000000300d4'))
        self.assertFalse(off.storage)
        self.assertEqual(off.storage_remaining, 0)
        self.assertTrue(on.storage)
        self.assertFalse(on.storage_status)
        self.assertEqual(on.storage_remaining, 72)

if __name__ == '__main__':
    unittest.main()
