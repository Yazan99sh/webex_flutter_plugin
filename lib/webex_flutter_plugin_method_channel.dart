import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'webex_flutter_plugin_platform_interface.dart';

class MethodChannelWebexFlutterPlugin extends WebexFlutterPluginPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('webex_flutter_plugin');

  @override
  Future<Map<String, dynamic>?> startWebexCalling({
    required String callerId,
    required String jwtToken,
  }) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'startWebexCalling',
      {'caller_id': callerId, 'jwt_token': jwtToken},
    );
    return result?.map((key, value) => MapEntry(key.toString(), value));
  }
}
