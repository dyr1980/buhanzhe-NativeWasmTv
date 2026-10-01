package xiao.bu.tv;

/** Platform and target-SDK permission contracts; no new framework references on old TVs. */
final class CastPermissionPolicy {
    static String directPermission(int sdk, int target) {
        if (sdk < 23) return "";
        // If targetSdk reaches 33, declare NEARBY_WIFI_DEVICES in the manifest.
        return sdk >= 33 && target >= 33
                ? "android.permission.NEARBY_WIFI_DEVICES"
                : "android.permission.ACCESS_FINE_LOCATION";
    }

    static boolean requiresLocalNetworkPermission(int sdk, int target) {
        // Legacy targets keep implicit LAN access through INTERNET. If the
        // target advances to 37, declare ACCESS_LOCAL_NETWORK in the manifest.
        return sdk >= 37 && target >= 37;
    }
}
