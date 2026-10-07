"""桌面导航回归：使用真实 Tk 控件，后台网络线程不启动。"""
import importlib.util
from pathlib import Path
import tkinter as tk
import unittest

spec = importlib.util.spec_from_file_location('navigation_gui', Path(__file__).resolve().parents[1] / 'dishwasher_gui.py')
gui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gui)


class NavigationTests(unittest.TestCase):
    def setUp(self):
        self.root = tk.Tk()
        self.app = gui.DishwasherApp(self.root, {'ip_address': '127.0.0.1', 'port': 6444}, start_worker=False)
        self.root.update()

    def tearDown(self):
        self.app.close()

    def test_default_home_and_gear_subpages(self):
        app = self.app
        self.assertEqual(app.current_page, 'home')
        self.assertTrue(app.wash_start.winfo_ismapped())
        self.assertFalse(app.table.winfo_ismapped())
        self.assertFalse(app.home_button.winfo_ismapped())
        self.assertEqual([app.settings_menu.entrycget(i, 'label') for i in range(3)], ['状态', '控制', '设备'])
        for index, name in enumerate(('status', 'control', 'device')):
            app.settings_menu.invoke(index)
            self.root.update()
            self.assertEqual(app.current_page, name)
            self.assertTrue(app.pages[name].winfo_ismapped())
            self.assertFalse(app.wash_start.winfo_ismapped())
            self.assertTrue(app.home_button.winfo_ismapped())
            self.assertEqual(bool(app.table.winfo_ismapped()), name == 'status')
        app.home_button.invoke()
        self.root.update()
        self.assertTrue(app.wash_start.winfo_ismapped())
        self.assertTrue(app.worker.commands.empty())

    def test_power_toggle_is_home_only_and_uses_reported_state(self):
        from unittest.mock import Mock
        app = self.app
        app.control = Mock()
        app.online = True
        for state, target, text in ((True, False, '关闭电源'), (False, True, '开启电源')):
            app.snapshot = {'power': state}
            app.set_controls()
            self.assertEqual(app.power_toggle.cget('text'), text)
            app.power_toggle.invoke()
            app.control.assert_called_once_with('power', target)
            app.control.reset_mock()
        for page in app.pages:
            app.show_page(page)
            self.root.update()
            self.assertEqual(bool(app.power_toggle.winfo_ismapped()), page == 'home')
        app.busy = True
        app.set_controls()
        self.assertTrue(app.power_toggle.instate(['disabled']))
        app.busy = False
        app.snapshot = {}
        app.set_controls()
        app.power_toggle.invoke()
        self.assertTrue(app.power_toggle.instate(['disabled']))
        app.control.assert_not_called()
        app.online = False
        app.snapshot = {'power': True}
        app.set_controls()
        self.assertTrue(app.power_toggle.instate(['disabled']))

    def test_temperature_color_boundaries(self):
        app = self.app
        app.online = True
        for value, expected in ((34, '#edf4ff'), (35, '#fff4df'), (50, '#fff4df'), (51, '#ffefed'), (None, '#f2f5f8')):
            app.snapshot = {'temperature': value}
            app.set_controls()
            self.assertEqual(app.status_cards['temperature'].fill_color, expected)
        app.online = False
        app.set_controls()
        self.assertEqual(app.status_cards['temperature'].fill_color, '#f2f5f8')

    def test_stage_idle_blue_other_reported_stages_orange(self):
        app = self.app
        app.online = True
        for stage, expected in (('idle', '#edf4ff'), ('wash', '#fff4df'), ('complete', '#fff4df'), (None, '#f2f5f8')):
            app.snapshot = {'progress': stage}
            app.set_controls()
            self.assertEqual(app.status_cards['progress'].fill_color, expected)

    def test_action_buttons_align_for_multiline_storage(self):
        app = self.app
        for summary in ('保管状态未上报', '保管：开启\n剩余：72 小时\n当前未运行', '保管：关闭\n剩余：0 小时\n当前未运行'):
            app.wash_storage_status.set(summary)
            self.root.update()
            self.assertEqual(app.wash_start.winfo_rooty(), app.storage_toggle.winfo_rooty())
            self.assertEqual(app.wash_start.winfo_height(), app.storage_toggle.winfo_height())

    def test_navigation_retains_mode_and_live_parameters(self):
        app = self.app
        self.root.after_cancel(app.poll_id)
        app.events.put(('snapshot', ({'temperature': 31, 'storage': True, 'storage_remaining': 72}, {2: 'strong_wash', 3: 'standard_wash'})))
        app.poll()
        app.mode.set('标准洗')
        app.show_page('status')
        self.root.update()
        self.assertEqual(len(app.table.get_children()), 24)
        self.assertEqual(app.table.item('temperature', 'values')[1], '31 °C')
        app.show_page('control')
        app.show_page('device')
        app.show_page('home')
        self.root.update()
        self.assertEqual(app.mode.get(), '标准洗')
        self.assertEqual(app.storage_remaining_label.get(), '剩余 72 小时')
        self.assertEqual(app.storage_toggle.cget('text'), '关闭保管')
        self.assertTrue(app.worker.commands.empty())


if __name__ == '__main__':
    unittest.main()
