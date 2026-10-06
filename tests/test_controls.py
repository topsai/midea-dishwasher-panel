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

if __name__ == '__main__':
    unittest.main()
