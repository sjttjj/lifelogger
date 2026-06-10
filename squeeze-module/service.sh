#!/system/bin/sh
# Bridge: watch Elmyra logcat lines → fire custom broadcast to Lifelogger

TARGET_APP="com.sam.lifelogger"
TARGET_RECEIVER="com.sam.lifelogger.squeeze.SqueezeBroadcastReceiver"
ACTION="com.sam.lifelogger.SQUEEZE_DETECTED"
SLEEP_AFTER_BOOT=15

# Wait for system to settle after boot
sleep $SLEEP_AFTER_BOOT

# Outer loop: restarts if pipe breaks
while true; do
    logcat -s "Elmyra/Service" -b main 2>/dev/null | while read line; do
        case "$line" in
            *"Gesture detected"*)
                am broadcast \
                    -a "$ACTION" \
                    -n "$TARGET_APP/$TARGET_RECEIVER" \
                    --user 0 \
                    2>/dev/null
                ;;
        esac
    done
done