package io.crowds.util;

public record SemVer(int major, int minor, int patch, String preRelease, String build) implements Comparable<SemVer> {

    private static int parseNumericIdentifier(String str, int pos, int len) {
        if (pos >= len || !isDigit(str.charAt(pos))) return -1;
        if (str.charAt(pos) == '0') {
            // leading zero not allowed, unless it's just "0"
            if (pos + 1 < len && isDigit(str.charAt(pos + 1))) return -1;
            return pos + 1;
        }
        int end = pos;
        while (end < len && isDigit(str.charAt(end))) end++;
        return end;
    }

    private static void validatePreRelease(String preRelease, String fullStr) {
        String[] identifiers = preRelease.split("\\.", -1);
        for (String id : identifiers) {
            if (id.isEmpty()) throw new IllegalArgumentException("invalid semver: " + fullStr);
            if (!id.matches("[0-9A-Za-z-]+")) throw new IllegalArgumentException("invalid semver: " + fullStr);
            // If all digits, it must be a valid numeric identifier (no leading zeros)
            if (id.matches("\\d+")) {
                if (id.length() > 1 && id.charAt(0) == '0')
                    throw new IllegalArgumentException("invalid semver: " + fullStr);
            }
        }
    }

    private static void validateBuild(String build, String fullStr) {
        String[] identifiers = build.split("\\.", -1);
        for (String id : identifiers) {
            if (id.isEmpty()) throw new IllegalArgumentException("invalid semver: " + fullStr);
            if (!id.matches("[0-9A-Za-z-]+")) throw new IllegalArgumentException("invalid semver: " + fullStr);
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isValidPreReleaseChar(char c) {
        return isDigit(c) || isLetter(c) || c == '-' || c == '.';
    }

    private static boolean isValidBuildChar(char c) {
        return isDigit(c) || isLetter(c) || c == '-' || c == '.';
    }

    private static boolean isLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    public static SemVer parse(String str) {
        if (str == null || str.isEmpty()) throw new IllegalArgumentException("invalid semver: empty");
        int pos = 0;
        int len = str.length();

        // parse major
        int majorEnd = parseNumericIdentifier(str, pos, len);
        if (majorEnd < 0 || majorEnd >= len || str.charAt(majorEnd) != '.')
            throw new IllegalArgumentException("invalid semver: " + str);
        int major = Integer.parseInt(str.substring(pos, majorEnd));
        pos = majorEnd + 1;

        // parse minor
        int minorEnd = parseNumericIdentifier(str, pos, len);
        if (minorEnd < 0 || minorEnd >= len || str.charAt(minorEnd) != '.')
            throw new IllegalArgumentException("invalid semver: " + str);
        int minor = Integer.parseInt(str.substring(pos, minorEnd));
        pos = minorEnd + 1;

        // parse patch
        int patchEnd = parseNumericIdentifier(str, pos, len);
        if (patchEnd < 0) throw new IllegalArgumentException("invalid semver: " + str);
        int patch = Integer.parseInt(str.substring(pos, patchEnd));
        pos = patchEnd;

        // parse optional pre-release and build
        String preRelease = null;
        String build = null;

        if (pos < len && str.charAt(pos) == '-') {
            pos++;
            int preStart = pos;
            while (pos < len && str.charAt(pos) != '+') {
                if (!isValidPreReleaseChar(str.charAt(pos)))
                    throw new IllegalArgumentException("invalid semver: " + str);
                pos++;
            }
            preRelease = str.substring(preStart, pos);
            if (preRelease.isEmpty()) throw new IllegalArgumentException("invalid semver: " + str);
            validatePreRelease(preRelease, str);
        }

        if (pos < len && str.charAt(pos) == '+') {
            pos++;
            int buildStart = pos;
            while (pos < len) {
                if (!isValidBuildChar(str.charAt(pos))) throw new IllegalArgumentException("invalid semver: " + str);
                pos++;
            }
            build = str.substring(buildStart, pos);
            if (build.isEmpty()) throw new IllegalArgumentException("invalid semver: " + str);
            validateBuild(build, str);
        }

        if (pos != len) throw new IllegalArgumentException("invalid semver: " + str);

        return new SemVer(major, minor, patch, preRelease, build);
    }

    @Override
    public int compareTo(SemVer o) {
        if (o==null){
            return 1;
        }
        if (this==o){
            return 0;
        }
        if (major > o.major){
            return 1;
        }
        if (major==o.major && minor > o.minor){
            return 1;
        }
        if (major==o.major && minor == o.minor && patch > o.patch){
            return 1;
        }
        if (major == o.major && minor == o.minor && patch == o.patch){
            if (o.preRelease==null){
                return preRelease==null?0:-1;
            }else if (preRelease==null){
                return 1;
            }
            var self = preRelease.split("\\.");
            var other = o.preRelease.split("\\.");
            int length = Math.min(self.length,other.length);
            for (int i = 0; i < length; i++) {
                var sid = self[i];
                var oid = other[i];
                try {
                    int sNum = Integer.parseInt(sid);
                    int oNum = Integer.parseInt(oid);
                    if (sNum!=oNum){
                        return sNum>oNum?1:-1;
                    }
                } catch (NumberFormatException _) {}
                for (int ii = 0; ii < Math.min(sid.length(),oid.length()); ii++) {
                    if (sid.charAt(ii)!=oid.charAt(ii)){
                        return sid.charAt(ii) > oid.charAt(ii)? 1: -1;
                    }
                }
                if (sid.length()!=oid.length()){
                    return sid.length()>oid.length()?1:-1;
                }
            }
            if (self.length>=other.length){
                return self.length==other.length?0:1;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        var ver =  major+"."+minor+"."+patch;
        if (preRelease!=null){
            ver += "-"+preRelease;
        }
        if (build!=null){
            ver += "+"+build;
        }
        return ver;
    }
}
