import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'webex_flutter_plugin_method_channel.dart';

abstract class WebexFlutterPluginPlatform extends PlatformInterface {
  WebexFlutterPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static WebexFlutterPluginPlatform _instance = MethodChannelWebexFlutterPlugin();

  static WebexFlutterPluginPlatform get instance => _instance;

  static set instance(WebexFlutterPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<Map<String, dynamic>?> startWebexCalling({
    required String callerId,
    required String jwtToken,
  }) {
    throw UnimplementedError('startWebexCalling() has not been implemented.');
  }
}
