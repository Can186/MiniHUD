#!/system/bin/sh
MODDIR=${0%/*}
APK="$MODDIR/system/app/MiniHUD/MiniHUD.apk"
PKG=com.can186.minihud

if ! pm list packages | grep -q "$PKG"; then
    pm install -r "$APK"
fi

appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>/dev/null

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 5

am start-foreground-service -n "$PKG/.HudService" 2>/dev/null
