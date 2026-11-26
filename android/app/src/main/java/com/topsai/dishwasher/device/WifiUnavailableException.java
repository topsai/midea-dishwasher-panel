package com.topsai.dishwasher.device;

import java.io.IOException;

final class WifiUnavailableException extends IOException {
 public WifiUnavailableException() { super("Wi-Fi 未连接"); }
}
