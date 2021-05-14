#import "JPushPlugin.h"
#ifdef NSFoundationVersionNumber_iOS_9_x_Max
#import <UserNotifications/UserNotifications.h>
#endif

#define JPLog(fmt, ...) NSLog((@"| JPUSH | Flutter | iOS | " fmt), ##__VA_ARGS__)

@interface NSError (FlutterError)
@property(readonly, nonatomic) FlutterError *flutterError;
@end

@implementation NSError (FlutterError)
- (FlutterError *)flutterError {
    return [FlutterError errorWithCode:[NSString stringWithFormat:@"Error %d", (int)self.code]
                               message:self.domain
                               details:self.localizedDescription];
}
@end


#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
@interface JPushPlugin ()
@end
#endif

@implementation JPushPlugin {
}

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    FlutterMethodChannel* channel = [FlutterMethodChannel
                                     methodChannelWithName:@"jpush"
                                     binaryMessenger:[registrar messenger]];
    JPushPlugin* instance = [[JPushPlugin alloc] init];
    instance.channel = channel;
    
    
    [registrar addApplicationDelegate:instance];
    [registrar addMethodCallDelegate:instance channel:channel];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    JPLog(@"handleMethodCall:%@",call.method);
    
    if ([@"getPlatformVersion" isEqualToString:call.method]) {
        result([@"iOS " stringByAppendingString:[[UIDevice currentDevice] systemVersion]]);
    } else if ([@"getLaunchAppNotification" isEqualToString:call.method]) {
        result(nil);
    } else {
        result(FlutterMethodNotImplemented);
    }
}

@end
