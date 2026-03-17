import 'webex_flutter_plugin_platform_interface.dart';

class WebexFlutterPlugin {
  /// Starts a Webex video call.
  ///
  /// Returns a map with 'status' and 'message' keys when the call ends.
  /// Status values: 'call_ended', 'auth_failed', 'call_failed', 'cancelled', 'error'
  Future<Map<String, dynamic>?> startWebexCalling({
    required String callerId,
    required String jwtToken,
  }) async {
    return WebexFlutterPluginPlatform.instance.startWebexCalling(
      callerId: callerId,
      jwtToken: jwtToken,
    );
  }
}
