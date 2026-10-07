import sys
import unittest
from pathlib import Path
from unittest.mock import patch
import dishwasher_runtime as runtime

class RuntimeTests(unittest.TestCase):
    def test_source_config_is_next_to_modules(self):
        with patch.object(sys,'frozen',False,create=True):
            self.assertEqual(runtime.config_path(),Path(runtime.__file__).resolve().with_name('dishwasher.json'))

    def test_frozen_config_is_next_to_exe_not_temp_or_cwd(self):
        with patch.object(sys,'frozen',True,create=True), patch.object(sys,'executable',str(Path('C:/Users/example/Desktop/panel/洗碗机面板.exe'))):
            self.assertEqual(runtime.config_path(),Path(sys.executable).resolve().with_name('dishwasher.json'))

if __name__=='__main__': unittest.main()
